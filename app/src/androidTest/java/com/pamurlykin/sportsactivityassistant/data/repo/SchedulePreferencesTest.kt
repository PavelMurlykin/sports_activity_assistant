package com.pamurlykin.sportsactivityassistant.data.repo

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.backup.*
import com.pamurlykin.sportsactivityassistant.data.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SchedulePreferencesTest {
    @Test fun timesExceptionsAndPrimaryCentersSurviveExportAndRestore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val restored = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = AppRepository(original); val owner = repo.localProfileId()
            val sport = original.referenceDao().getSports().first { it.slug == "football" }.id
            val center = repo.getComplexOptionsForSport(sport).first().id
            repo.setPrimaryCenter(sport, center)
            val date = LocalDate.parse("2020-02-29")
            val rule = repo.addPlannedTraining(owner, AddPlannedTrainingInput(sport, center, date, true, 1, null, "23:30", "01:00"))
            val snapshot = repo.loadPlan("rule-$rule-$date")
            assertEquals(90, TrainingTimes.duration(snapshot.startTime, snapshot.endTime))
            repo.updatePlan(snapshot, PlanScope.EVENT, snapshot.input().copy(startTime = "22:30"))
            val climbing = original.referenceDao().getSports().first { it.slug == "climbing" }.id
            val climbingCenter = repo.getComplexOptionsForSport(climbing).first().id
            val training = repo.addCompletedTraining(owner, AddCompletedTrainingInput(climbing, climbingCenter, date,
                climbingRoutes = listOf(ClimbingRouteInput(ClimbingWorkoutType.DIFFICULTY, "6a", true)), durationMinutes = 90))
            assertEquals(90, repo.loadTrainingForEdit(training).input.durationMinutes)
            val backup = repo.createBackup()
            val document = BackupCodec.decode(backup)
            assertEquals("23:30", document.recurrenceRules.single().startTime)
            assertEquals("22:30", document.plannedTrainings.single().startTime)
            assertEquals(1, document.primaryCenters.size)
            val target = AppRepository(restored)
            target.importData(backup.toByteArray(), target.localProfileId())
            val result = BackupCodec.decode(target.createBackup())
            assertEquals(document.primaryCenters, result.primaryCenters)
            assertEquals(90, result.trainings.single().durationMinutes)
            assertEquals(document.plannedTrainings, result.plannedTrainings)
            assertEquals(document.recurrenceRules, result.recurrenceRules)
        } finally { original.close(); restored.close() }
    }

    @Test fun archivingAndRemovingSportClearOnlyUnavailableDefaults() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val repo = AppRepository(db); repo.localProfileId()
            val sports = db.referenceDao().getSports()
            val center = repo.saveSportsCenter(SaveSportsCenterInput(name = "Общий", city = null, sportIds = sports.map { it.id }.toSet()))
            sports.forEach { repo.setPrimaryCenter(it.id, center) }
            assertEquals(sports.map { it.id }.toSet(), repo.observeSportsCenters().first().single { it.id == center }.primarySportIds)
            repo.saveSportsCenter(SaveSportsCenterInput(center, "Общий", null, setOf(sports.first().id)))
            assertEquals(setOf(sports.first().id), repo.observeSportsCenters().first().single { it.id == center }.primarySportIds)
            repo.setSportsCenterArchived(center, true)
            assertTrue(db.referenceDao().getPrimaryCenters().isEmpty())
            assertTrue(runCatching { repo.setPrimaryCenter(sports.first().id, center) }.isFailure)
        } finally { db.close() }
    }
}
