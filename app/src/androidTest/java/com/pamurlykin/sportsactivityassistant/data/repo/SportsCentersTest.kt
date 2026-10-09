package com.pamurlykin.sportsactivityassistant.data.repo

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.backup.*
import com.pamurlykin.sportsactivityassistant.data.entity.*
import com.pamurlykin.sportsactivityassistant.data.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class SportsCentersTest {
    private suspend fun db(test: suspend (AppDatabase, AppRepository) -> Unit) {
        val database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try { test(database, AppRepository(database)) } finally { database.close() }
    }
    private val date = LocalDate.parse("2026-10-08")
    private fun football(sport: Int, center: Long) = AddCompletedTrainingInput(sport, center, date, FootballTrainingInput(0, 0, 0, 0, null, null, null))
    private fun plan(sport: Int, center: Long, repeat: Boolean = false) = AddPlannedTrainingInput(sport, center, date, repeat, 1, date.plusWeeks(2))

    @Test fun createsMultipleSportsAndRejectsNormalizedDuplicatesIncludingArchivedAndNullCity() = runBlocking {
        db { database, repo ->
            repo.localProfileId()
            val sports = database.referenceDao().getSports().map { it.id }.toSet()
            val id = repo.saveSportsCenter(SaveSportsCenterInput(name = "  Большая\u00a0  Арена ", city = " ", sportIds = sports))
            assertEquals("Большая Арена", database.referenceDao().getComplex(id)!!.name)
            assertNull(database.referenceDao().getComplex(id)!!.city)
            sports.forEach { assertTrue(repo.getComplexOptionsForSport(it).any { c -> c.id == id }) }
            listOf(null, "", " \t ").forEach { city ->
                assertTrue(runCatching { repo.saveSportsCenter(SaveSportsCenterInput(name = "БОЛЬШАЯ арена", city = city, sportIds = sports)) }.isFailure)
            }
            repo.setSportsCenterArchived(id, true)
            assertTrue(runCatching { repo.saveSportsCenter(SaveSportsCenterInput(name = "большая арена", city = null, sportIds = sports)) }.isFailure)
            assertNotEquals(id, repo.saveSportsCenter(SaveSportsCenterInput(name = "Большая Арена", city = "Другой город", sportIds = sports)))
        }
    }

    @Test fun invalidEditsRollBackAndValidEditsPreserveIdentityAndCreationTime() = runBlocking {
        db { database, repo ->
            repo.localProfileId()
            val sports = database.referenceDao().getSports().map { it.id }.toSet()
            val id = repo.saveSportsCenter(SaveSportsCenterInput(name = "Мой центр", city = null, sportIds = sports))
            val before = database.referenceDao().getComplex(id)!!
            listOf(SaveSportsCenterInput(id, " ", null, sports), SaveSportsCenterInput(id, "Изменён", null, emptySet()),
                SaveSportsCenterInput(id, "Изменён", null, setOf(999)), SaveSportsCenterInput(id, "x".repeat(201), null, sports)).forEach {
                assertTrue(runCatching { repo.saveSportsCenter(it) }.isFailure)
                assertEquals(before, database.referenceDao().getComplex(id))
                assertEquals(sports, database.referenceDao().getComplexSports().filter { it.sportsComplexId == id }.map { it.sportId }.toSet())
            }
            repo.saveSportsCenter(SaveSportsCenterInput(id, "МОЙ ЦЕНТР", " Казань ", sports))
            val edited = database.referenceDao().getComplex(id)!!
            assertEquals("МОЙ ЦЕНТР", edited.name); assertEquals("Казань", edited.city)
            assertEquals(before.publicId, edited.publicId); assertEquals(before.createdAt, edited.createdAt)
        }
    }

    @Test fun concurrentCreationCannotInsertTwoNormalizedDuplicates() = runBlocking {
        db { database, repo ->
            repo.localProfileId()
            val sports = database.referenceDao().getSports().map { it.id }.toSet()
            val results = coroutineScope { listOf(" Новая Арена ", "новая  арена").map { name ->
                async { runCatching { AppRepository(database).saveSportsCenter(SaveSportsCenterInput(name = name, city = null, sportIds = sports)) } }
            }.awaitAll() }
            assertEquals(1, results.count { it.isSuccess })
            assertEquals(1, database.referenceDao().getAllComplexes().count { CenterNames.key(it.name, it.city) == CenterNames.key("Новая арена", null) })
        }
    }

    @Test fun removingSportOrArchivingKeepsHistoryPlansSeriesAndFavoritesButBlocksStaleNewInputs() = runBlocking {
        db { database, repo ->
            val user = repo.localProfileId()
            val sports = database.referenceDao().getSports()
            val f = sports.first { it.slug == "football" }.id
            val c = sports.first { it.slug == "climbing" }.id
            val id = repo.saveSportsCenter(SaveSportsCenterInput(name = "Общий центр", city = null, sportIds = setOf(f, c)))
            repo.addCompletedTraining(user, football(f, id))
            repo.addPlannedTraining(user, plan(f, id))
            repo.addPlannedTraining(user, plan(f, id, true))
            database.referenceDao().insertFavoriteComplexes(listOf(UserFavoriteComplexEntity(userId = user, sportsComplexId = id)))
            repo.saveSportsCenter(SaveSportsCenterInput(id, "Общий центр", null, setOf(c)))
            assertFalse(repo.getComplexOptionsForSport(f).any { it.id == id })
            assertTrue(runCatching { repo.addCompletedTraining(user, football(f, id)) }.isFailure)
            assertTrue(runCatching { repo.addPlannedTraining(user, plan(f, id)) }.isFailure)
            repo.setSportsCenterArchived(id, true)
            assertFalse(repo.getComplexOptionsForSport(c).any { it.id == id })
            assertTrue(runCatching { repo.addPlannedTraining(user, plan(c, id, true)) }.isFailure)
            assertTrue(runCatching { repo.addCompletedTraining(user, AddCompletedTrainingInput(c, id, date,
                climbingRoutes = listOf(ClimbingRouteInput(ClimbingWorkoutType.DIFFICULTY, "6a", true)))) }.isFailure)
            assertEquals(1, database.trainingDao().getAllTrainingBundles().size)
            assertEquals(1, database.planningDao().getAllPlannedTrainings().size)
            assertEquals(1, database.planningDao().getAllRecurrenceRules().size)
            assertEquals(1, database.referenceDao().getFavoriteComplexes().size)
            assertEquals(1, repo.observeStatisticsOverview().first().totalTrainings)
            assertEquals(5, repo.getScheduleMonth(user, YearMonth.from(date), date, date).days.flatMap { it.events }.size)
            repo.setSportsCenterArchived(id, false)
            assertTrue(repo.getComplexOptionsForSport(c).any { it.id == id })
            assertFalse(repo.getComplexOptionsForSport(f).any { it.id == id })
        }
    }

    @Test fun json5RestoresArchiveAndAllReferencesAndIsIdempotent() = runBlocking {
        db { source, repo ->
            val user = repo.localProfileId()
            val f = source.referenceDao().getSportBySlug("football")!!.id
            val id = repo.saveSportsCenter(SaveSportsCenterInput(name = "Закрытая Арена", city = null, sportIds = setOf(f)))
            repo.addCompletedTraining(user, football(f, id)); repo.addPlannedTraining(user, plan(f, id)); repo.addPlannedTraining(user, plan(f, id, true))
            repo.setSportsCenterArchived(id, true)
            val backup = repo.createBackup()
            val doc = BackupCodec.decode(backup)
            assertEquals(6, doc.schemaVersion); assertTrue(doc.centers.first { it.name == "Закрытая Арена" }.isArchived)
            db { target, restore ->
                val result = restore.importData(backup.toByteArray(), restore.localProfileId())
                assertEquals(1, result.importedTrainings); assertEquals(1, result.importedPlans); assertEquals(1, result.importedRules)
                val archived = target.referenceDao().getAllComplexes().first { it.name == "Закрытая Арена" }
                assertTrue(archived.isArchived)
                assertFalse(restore.getComplexOptionsForSport(f).any { it.id == archived.id })
                assertEquals(doc.copy(exportedAt = ""), BackupCodec.decode(restore.createBackup()).copy(exportedAt = ""))
                assertEquals(0, restore.importData(backup.toByteArray(), restore.localProfileId()).importedTrainings)
            }
        }
    }

    @Test fun olderCopyDoesNotReopenLocalArchiveAndNewArchiveConflictNeedsConfirmation() = runBlocking {
        db { database, repo ->
            val user = repo.localProfileId()
            val f = database.referenceDao().getSportBySlug("football")!!.id
            val center = database.referenceDao().getComplexesForSport(f).first()
            repo.addCompletedTraining(user, football(f, center.id))
            val current = BackupCodec.decode(repo.createBackup())
            val v4 = repo.prepareImport(BackupCodec.encode(current.copy(schemaVersion = 4)).toByteArray())
            repo.setSportsCenterArchived(center.id, true)
            val oldPreview = repo.previewImport(v4)
            assertTrue(oldPreview.errors.toString(), oldPreview.canApply)
            repo.applyImport(v4, oldPreview.choices)
            assertTrue(database.referenceDao().getComplex(center.id)!!.isArchived)
            val v5 = repo.prepareImport(BackupCodec.encode(current).toByteArray())
            val newPreview = repo.previewImport(v5)
            assertFalse(newPreview.canApply)
            repo.applyImport(v5, newPreview.choices.copy(keepLocalIds = setOf(center.publicId)))
            assertTrue(database.referenceDao().getComplex(center.id)!!.isArchived)
        }
    }

    @Test fun historicalDuplicateCentersRemainSeparateEditableAndRestorable() = runBlocking {
        db { database, repo ->
            val user = repo.localProfileId()
            val f = database.referenceDao().getSportBySlug("football")!!.id
            val first = database.referenceDao().insertComplex(SportsComplexEntity(name = "Историческая Арена", city = null))
            val second = database.referenceDao().insertComplex(SportsComplexEntity(name = "Историческая Арена", city = null))
            listOf(first, second).forEach { id ->
                database.referenceDao().insertComplexSports(listOf(SportsComplexSportEntity(sportsComplexId = id, sportId = f)))
                repo.addCompletedTraining(user, football(f, id))
            }
            repo.saveSportsCenter(SaveSportsCenterInput(first, "ИСТОРИЧЕСКАЯ арена", null, setOf(f)))
            repo.setSportsCenterArchived(second, true)
            val doc = BackupCodec.decode(repo.createBackup())
            db { restored, restore ->
                restore.importData(BackupCodec.encode(doc).toByteArray(), restore.localProfileId())
                val history = restored.trainingDao().getAllTrainingBundles()
                assertEquals(2, history.map { it.complex.publicId }.distinct().size)
                assertEquals(setOf(false, true), history.map { it.complex.isArchived }.toSet())
                assertEquals(doc.copy(exportedAt = ""), BackupCodec.decode(restore.createBackup()).copy(exportedAt = ""))
            }
        }
    }
}
