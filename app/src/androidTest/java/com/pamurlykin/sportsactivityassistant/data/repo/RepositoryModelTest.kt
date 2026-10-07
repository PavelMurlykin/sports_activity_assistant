package com.pamurlykin.sportsactivityassistant.data.repo

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.backup.BackupCodec
import com.pamurlykin.sportsactivityassistant.data.backup.BackupDocument
import com.pamurlykin.sportsactivityassistant.data.backup.CenterBackup
import com.pamurlykin.sportsactivityassistant.data.backup.ClimbingRouteBackup
import com.pamurlykin.sportsactivityassistant.data.backup.SportBackup
import com.pamurlykin.sportsactivityassistant.data.backup.TrainingBackup
import com.pamurlykin.sportsactivityassistant.data.entity.SportEntity
import com.pamurlykin.sportsactivityassistant.data.entity.SportsComplexEntity
import com.pamurlykin.sportsactivityassistant.data.entity.UserEntity
import com.pamurlykin.sportsactivityassistant.data.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RepositoryModelTest {
    private suspend fun withDatabase(test: suspend (AppDatabase, AppRepository) -> Unit) {
        val database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try { test(database, AppRepository(database)) } finally { database.close() }
    }

    @Test
    fun firstConcurrentReadsWaitForExactlyOneInitialization() = runBlocking {
        withDatabase { database, repository ->
            coroutineScope {
                (1..12).map {
                    async {
                        val repo = if (it % 2 == 0) AppRepository(database) else repository
                        assertEquals(2, repo.observeStatisticsOverview().first().sports.size)
                        val date = LocalDate.parse("2026-10-07")
                        assertTrue(repo.getScheduleMonth(repo.localProfileId(), YearMonth.from(date), date, date).days.all { it.events.isEmpty() })
                    }
                }.awaitAll()
            }
            assertEquals(1, database.referenceDao().getUsers().size)
            assertEquals(2, database.referenceDao().getSports().size)
            assertEquals(3, database.referenceDao().getAllComplexes().size)
            assertEquals(3, database.referenceDao().getComplexSports().size)
        }
    }

    @Test
    fun partialInitializationPreservesExistingKeysAndCustomCenter() = runBlocking {
        withDatabase { database, repository ->
            database.referenceDao().insertUsers(listOf(UserEntity(9, "Мой профиль", Instant.ofEpochMilli(123))))
            database.referenceDao().insertSports(listOf(SportEntity(99, "football", "Мой футбол")))
            database.referenceDao().insertComplex(SportsComplexEntity(42, "Мой центр", null))
            assertEquals(9L, repository.localProfileId())
            assertEquals(9L, AppRepository(database).localProfileId())
            assertEquals(99, database.referenceDao().getSportBySlug("football")!!.id)
            assertEquals("Мой футбол", database.referenceDao().getSportBySlug("football")!!.title)
            assertEquals(2, database.referenceDao().getSports().size)
            assertEquals(listOf(42L), database.referenceDao().getAllComplexes().map { it.id })
            assertEquals("Мой профиль", database.referenceDao().getUsers().single().displayName)
        }
    }

    @Test
    fun identicalRealWorkoutsHaveDifferentStableIdentitiesAndCenterEditKeepsIdentity() = runBlocking {
        withDatabase { database, repository ->
            val user = repository.localProfileId()
            val sport = database.referenceDao().getSportBySlug("football")!!
            val center = database.referenceDao().getComplexesForSport(sport.id).first()
            val input = AddCompletedTrainingInput(sport.id, center.id, LocalDate.parse("2026-10-01"), FootballTrainingInput(0, 0, 0, 0, null, null, null))
            repository.addCompletedTraining(user, input)
            repository.addCompletedTraining(user, input)
            val bundles = database.trainingDao().getAllTrainingBundles()
            assertEquals(2, bundles.size)
            assertEquals(2, bundles.map { it.training.publicId }.distinct().size)
            bundles.forEach { UUID.fromString(it.training.publicId) }
            repository.saveSportsCenter(SaveSportsCenterInput(center.id, "Переименованный центр", "Город", setOf(sport.id)))
            val updated = database.referenceDao().getComplex(center.id)!!
            assertEquals(center.publicId, updated.publicId)
            assertEquals(center.createdAt, updated.createdAt)
            assertEquals(2, database.trainingDao().getAllTrainingBundles().size)
        }
    }

    @Test
    fun invalidDetailsNeverLeaveAnOrphanTraining() = runBlocking {
        withDatabase { database, repository ->
            val user = repository.localProfileId()
            val football = database.referenceDao().getSportBySlug("football")!!
            val climbing = database.referenceDao().getSportBySlug("climbing")!!
            val footballCenter = database.referenceDao().getComplexesForSport(football.id).first().id
            val climbingCenter = database.referenceDao().getComplexesForSport(climbing.id).first().id
            val date = LocalDate.parse("2026-10-01")
            val details = FootballTrainingInput(0, 0, 1, 0, null, null, null)
            listOf(
                AddCompletedTrainingInput(football.id, footballCenter, date, football = details),
                AddCompletedTrainingInput(football.id, footballCenter, date),
                AddCompletedTrainingInput(climbing.id, climbingCenter, date, football = details),
                AddCompletedTrainingInput(climbing.id, climbingCenter, date, climbingRoutes = listOf(ClimbingRouteInput(ClimbingWorkoutType.DIFFICULTY, "wrong", true))),
            ).forEach { assertTrue(runCatching { repository.addCompletedTraining(user, it) }.isFailure) }
            assertTrue(database.trainingDao().getAllTrainingBundles().isEmpty())
        }
    }

    @Test
    fun unsupportedSportImportIsRejectedWithoutChangingDatabase() = runBlocking {
        withDatabase { database, repository ->
            val user = repository.localProfileId()
            val before = repository.createBackup()
            val backup = BackupDocument(exportedAt = Instant.now().toString(),
                sports = listOf(SportBackup("unsupported", "Другой спорт")),
                centers = listOf(CenterBackup(name = "Новый центр", city = null, sportSlugs = listOf("unsupported"))),
                trainings = emptyList())
            val failure = runCatching { repository.importData(BackupCodec.encode(backup).toByteArray(), user) }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertTrue(failure!!.message!!.contains("не поддерживается"))
            // exportedAt varies, so compare parsed content except that timestamp.
            assertEquals(BackupCodec.decode(before).copy(exportedAt = ""), BackupCodec.decode(repository.createBackup()).copy(exportedAt = ""))
            assertEquals(2, database.referenceDao().getSports().size)
        }
    }

    @Test
    fun malformedImportedDetailsRollBackNewCentersAndDoNotCoerceDiscipline() = runBlocking {
        withDatabase { database, repository ->
            val user = repository.localProfileId()
            val backup = BackupDocument(schemaVersion = 2, exportedAt = Instant.now().toString(),
                sports = listOf(SportBackup("climbing", "Скалолазание")),
                centers = listOf(CenterBackup(name = "Импортируемый центр", city = null, sportSlugs = listOf("climbing"))),
                trainings = listOf(TrainingBackup(date = "2026-10-01", sportSlug = "climbing", centerName = "Импортируемый центр", centerCity = null,
                    climbingRoutes = listOf(ClimbingRouteBackup("unknown", "6A", true, 1)))))
            val failure = runCatching { repository.importData(BackupCodec.encode(backup).toByteArray(), user) }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertTrue(database.trainingDao().getAllTrainingBundles().isEmpty())
            assertEquals(3, database.referenceDao().getAllComplexes().size)
        }
    }
}
