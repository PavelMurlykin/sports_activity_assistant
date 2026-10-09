package com.pamurlykin.sportsactivityassistant.data.repo

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.backup.*
import com.pamurlykin.sportsactivityassistant.data.entity.*
import com.pamurlykin.sportsactivityassistant.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class TrainingCrudTest {
    private val date = LocalDate.parse("2020-01-01")
    private suspend fun db(test: suspend (AppDatabase, AppRepository, Long) -> Unit) {
        val database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try { val repo = AppRepository(database); test(database, repo, repo.localProfileId()) }
        finally { database.close() }
    }
    private fun football(sport: Int, center: Long) = AddCompletedTrainingInput(sport, center, date,
        FootballTrainingInput(0, 0, 0, 0, null, null, null))
    private fun route(grade: String = "6a") = ClimbingRouteInput(ClimbingWorkoutType.DIFFICULTY, grade, true,
        publicId = UUID.randomUUID().toString())
    private fun count(database: AppDatabase, table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    @Test fun footballEditPreservesIdentityAndUpdatesCalendarStatisticsAndBackup() = runBlocking {
        db { database, repo, user ->
            val sport = database.referenceDao().getSports().first { it.slug == "football" }
            val center = repo.getComplexOptionsForSport(sport.id).first()
            val second = repo.saveSportsCenter(SaveSportsCenterInput(name = "Другой зал", city = null, sportIds = setOf(sport.id)))
            val id = repo.addCompletedTraining(user, football(sport.id, center.id))
            val original = database.trainingDao().getTrainingBundle(id)!!.training
            val snapshot = repo.loadTrainingForEdit(id)
            val edited = snapshot.input.copy(date = date.plusDays(1), complexId = second,
                football = FootballTrainingInput(5, 2, 2, 1, "7.35".toBigDecimal(), 5, 60))
            repo.updateCompletedTraining(snapshot, edited)
            val after = database.trainingDao().getTrainingBundle(id)!!
            assertEquals(original.publicId, after.training.publicId); assertEquals(original.createdAt, after.training.createdAt)
            assertEquals(second, after.training.sportsComplexId)
            assertEquals(2, after.football!!.userGoalsScored)
            assertEquals(1, repo.observeStatisticsOverview().first().totalTrainings)
            val calendar = repo.getScheduleMonth(user, YearMonth.from(edited.date), edited.date, LocalDate.now())
            assertEquals(listOf("Счёт: 5:2", "Личные голы: 2", "Голевые передачи: 1", "Дистанция: 7.35 км",
                "Игроков в команде: 5", "Время игры: 60 мин"), calendar.selectedDayEvents.single().details)
            val backup = BackupCodec.decode(repo.createBackup()).trainings.single()
            assertEquals(original.publicId, backup.publicId); assertEquals("7.35", backup.football!!.distanceKm)
            val next = repo.loadTrainingForEdit(id)
            repo.updateCompletedTraining(next, next.input.copy(football = next.input.football!!.copy(distanceKm = null, playersPerTeam = null, durationMinutes = null)))
            assertNull(database.trainingDao().getTrainingBundle(id)!!.football!!.durationMinutes)
        }
    }

    @Test fun climbingEditRetainsRouteIdsAndDeleteCascadesOnlyWorkoutDetails() = runBlocking {
        db { database, repo, user ->
            val sport = database.referenceDao().getSports().first { it.slug == "climbing" }
            val center = repo.getComplexOptionsForSport(sport.id).first()
            val id = repo.addCompletedTraining(user, AddCompletedTrainingInput(sport.id, center.id, date, climbingRoutes = listOf(route(), route("7a"))))
            repo.addPlannedTraining(user, AddPlannedTrainingInput(sport.id, center.id, date, false, 1, null))
            val original = database.trainingDao().getTrainingBundle(id)!!
            val retained = original.climbing!!.routes.first()
            val snapshot = repo.loadTrainingForEdit(id)
            val edited = snapshot.input.copy(climbingRoutes = listOf(
                snapshot.input.climbingRoutes.first { it.publicId == retained.publicId }.copy(repeatCount = 4, isCompleted = false),
                route("8a").copy(repeatCount = 2),
            ))
            repo.updateCompletedTraining(snapshot, edited)
            val after = database.trainingDao().getTrainingBundle(id)!!
            assertEquals(original.training.publicId, after.training.publicId)
            val kept = after.climbing!!.routes.first { it.publicId == retained.publicId }
            assertEquals(retained.id, kept.id); assertEquals(4, kept.repeatCount); assertFalse(kept.isCompleted)
            val stats = repo.observeSportStatistics(sport.id).first()!!
            assertEquals("6", stats.metrics.first { it.label == "Попытки (с повторами)" }.value)
            repo.deleteCompletedTraining(repo.loadTrainingForEdit(id))
            assertNull(database.trainingDao().getTrainingBundle(id))
            assertEquals(0, count(database, "climbing_routes")); assertEquals(0, count(database, "climbing_trainings"))
            assertEquals(1, count(database, "planned_trainings"))
            assertNotNull(database.referenceDao().getComplex(center.id))
            assertEquals(0, repo.observeStatisticsOverview().first().totalTrainings)
        }
    }

    @Test fun staleEditDeleteAndForeignProfileMutationsAreRejected() = runBlocking {
        db { database, repo, user ->
            val sport = database.referenceDao().getSports().first { it.slug == "football" }
            val center = repo.getComplexOptionsForSport(sport.id).first()
            val id = repo.addCompletedTraining(user, football(sport.id, center.id))
            val stale = repo.loadTrainingForEdit(id)
            val updated = stale.input.copy(football = FootballTrainingInput(2, 0, 1, 0, null, null, null))
            repo.updateCompletedTraining(stale, updated)
            repo.updateCompletedTraining(stale, updated) // same request after process restoration is harmless
            assertTrue(runCatching { repo.updateCompletedTraining(stale, stale.input) }.isFailure)
            assertTrue(runCatching { repo.deleteCompletedTraining(stale) }.isFailure)
            val other = database.referenceDao().insertUser(UserEntity(displayName = "Другой"))
            val foreign = repo.addCompletedTraining(other, football(sport.id, center.id))
            assertTrue(runCatching { repo.loadTrainingForEdit(foreign) }.isFailure)
            val forged = stale.copy(id = foreign)
            assertTrue(runCatching { repo.updateCompletedTraining(forged, stale.input) }.isFailure)
            assertTrue(runCatching { repo.deleteCompletedTraining(forged) }.isFailure)
            assertEquals(2, count(database, "trainings"))
        }
    }

    @Test fun failureDuringRouteInsertRollsBackDateUpdatedAndDeletedRoutes() = runBlocking {
        db { database, repo, user ->
            val sport = database.referenceDao().getSports().first { it.slug == "climbing" }
            val center = repo.getComplexOptionsForSport(sport.id).first()
            val id = repo.addCompletedTraining(user, AddCompletedTrainingInput(sport.id, center.id, date, climbingRoutes = listOf(route(), route("7a"))))
            val before = database.trainingDao().getTrainingBundle(id)!!
            val snapshot = repo.loadTrainingForEdit(id)
            database.openHelper.writableDatabase.execSQL("CREATE TRIGGER stage07_fail BEFORE INSERT ON climbing_routes BEGIN SELECT RAISE(ABORT, 'test write failure'); END")
            val edit = snapshot.input.copy(date = date.plusDays(1), climbingRoutes = listOf(snapshot.input.climbingRoutes.first().copy(isCompleted = false), route("8a")))
            assertTrue(runCatching { repo.updateCompletedTraining(snapshot, edit) }.isFailure)
            val after = database.trainingDao().getTrainingBundle(id)!!
            assertEquals(before.training, after.training)
            assertEquals(before.climbing!!.routes.sortedBy { it.id }, after.climbing!!.routes.sortedBy { it.id })
        }
    }

    @Test fun archivedHistoricalCenterCanRemainButNewArchivedAssignmentAndSportChangeCannot() = runBlocking {
        db { database, repo, user ->
            val sports = database.referenceDao().getSports()
            val sport = sports.first { it.slug == "football" }
            val center = repo.getComplexOptionsForSport(sport.id).first()
            val id = repo.addCompletedTraining(user, football(sport.id, center.id))
            val snapshot = repo.loadTrainingForEdit(id)
            repo.saveSportsCenter(SaveSportsCenterInput(center.id, "Переименован", null, setOf(sports.first { it.slug == "climbing" }.id)))
            repo.setSportsCenterArchived(center.id, true)
            repo.updateCompletedTraining(snapshot, snapshot.input.copy(date = date.plusDays(1)))
            val next = repo.loadTrainingForEdit(id)
            val archived = repo.saveSportsCenter(SaveSportsCenterInput(name = "Закрытый", city = null, sportIds = setOf(sport.id)))
            repo.setSportsCenterArchived(archived, true)
            assertTrue(runCatching { repo.updateCompletedTraining(next, next.input.copy(complexId = archived)) }.isFailure)
            assertTrue(runCatching { repo.updateCompletedTraining(next, next.input.copy(sportId = sports.first { it.slug == "climbing" }.id)) }.isFailure)
            assertEquals(center.id, database.trainingDao().getTrainingBundle(id)!!.training.sportsComplexId)
        }
    }

    @Test fun futureManualResultsRejectedButImportedFutureHistoryCanBeCorrected() = runBlocking {
        db { database, repo, user ->
            val sport = database.referenceDao().getSports().first { it.slug == "football" }
            val center = repo.getComplexOptionsForSport(sport.id).first()
            val future = LocalDate.now().plusDays(10)
            assertTrue(runCatching { repo.addCompletedTraining(user, football(sport.id, center.id).copy(date = future)) }.isFailure)
            val backup = BackupDocument(schemaVersion = 3, exportedAt = "2020-01-01T00:00:00Z",
                sports = listOf(SportBackup("football", "Футбол")), centers = listOf(CenterBackup(name = "Исторический", sportSlugs = listOf("football"))),
                trainings = listOf(TrainingBackup(date = future.toString(), sportSlug = "football", centerName = "Исторический", football = FootballBackup(0,0,0,0))))
            repo.importData(BackupCodec.encode(backup).toByteArray(), user)
            val id = database.trainingDao().getAllTrainingBundles().single().training.id
            val snapshot = repo.loadTrainingForEdit(id)
            repo.updateCompletedTraining(snapshot, snapshot.input.copy(football = FootballTrainingInput(1,0,1,0,null,null,null)))
            val next = repo.loadTrainingForEdit(id)
            assertTrue(runCatching { repo.updateCompletedTraining(next, next.input.copy(date = future.plusDays(1))) }.isFailure)
            repo.updateCompletedTraining(next, next.input.copy(date = date))
            assertEquals(date, database.trainingDao().getTrainingBundle(id)!!.training.trainingDate)
        }
    }

    @Test fun concurrentRetryWithSameRequestUuidDoesNotDuplicateWorkoutOrRoutes() = runBlocking {
        db { database, repo, user ->
            val sport = database.referenceDao().getSports().first { it.slug == "climbing" }
            val center = repo.getComplexOptionsForSport(sport.id).first()
            val input = AddCompletedTrainingInput(sport.id, center.id, date, climbingRoutes = listOf(route()))
            val request = UUID.randomUUID().toString()
            val ids = coroutineScope { (1..2).map { async { AppRepository(database).addCompletedTraining(user, input, request) } }.awaitAll() }
            assertEquals(ids.first(), ids.last()); assertEquals(1, count(database, "trainings")); assertEquals(1, count(database, "climbing_routes"))
            repo.setSportsCenterArchived(center.id, true)
            assertEquals(ids.first(), repo.addCompletedTraining(user, input, request))
            assertTrue(runCatching { repo.addCompletedTraining(user, input.copy(date = date.plusDays(1)), request) }.isFailure)
        }
    }

    @Test fun historicalRoutesKeepRawMetadataAndRepeatCountsWithoutInventedScale() = runBlocking {
        db { database, repo, user ->
            val backup = BackupDocument(schemaVersion = 3, exportedAt = "2020-01-01T00:00:00Z",
                sports = listOf(SportBackup("climbing", "Скалолазание")), centers = listOf(CenterBackup(name = "Старый зал", sportSlugs = listOf("climbing"))),
                trainings = listOf(TrainingBackup(date = date.toString(), sportSlug = "climbing", centerName = "Старый зал",
                    climbingRoutes = listOf(ClimbingRouteBackup("mystery", "4+", true, 12, "legacy", legacyWorkoutType = "mystery")))))
            repo.importData(BackupCodec.encode(backup).toByteArray(), user)
            val before = database.trainingDao().getAllTrainingBundles().single()
            val snapshot = repo.loadTrainingForEdit(before.training.id)
            val old = snapshot.input.climbingRoutes.single()
            val edited = snapshot.input.copy(climbingRoutes = listOf(old.copy(repeatCount = 7, isCompleted = false), route()))
            repo.updateCompletedTraining(snapshot, edited)
            val kept = database.trainingDao().getTrainingBundle(snapshot.id)!!.climbing!!.routes.first { it.publicId == old.publicId }
            assertEquals(before.climbing!!.routes.single().id, kept.id)
            assertEquals("4+", kept.routeDifficulty); assertEquals("mystery", kept.legacyWorkoutType); assertEquals("legacy", kept.gradingSystem)
            assertEquals(7, kept.repeatCount)
            val next = repo.loadTrainingForEdit(snapshot.id)
            assertTrue(runCatching { repo.updateCompletedTraining(next, next.input.copy(climbingRoutes = next.input.climbingRoutes + old.copy(publicId = UUID.randomUUID().toString()))) }.isFailure)
            assertTrue(runCatching { repo.updateCompletedTraining(next, next.input.copy(climbingRoutes = listOf(old.copy(routeDifficulty = "5")))) }.isFailure)
            assertEquals(2, BackupCodec.decode(repo.createBackup()).trainings.single().climbingRoutes.size)
        }
    }
}
