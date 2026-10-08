package com.pamurlykin.sportsactivityassistant.data.repo

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.backup.*
import com.pamurlykin.sportsactivityassistant.data.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ClimbingExchangeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun database() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

    @Test fun newGradesAndSpeedCoursesRoundTripAndRepeatedImportIsIdempotent() = runBlocking<Unit> {
        val source = database()
        val target = database()
        try {
            val repository = AppRepository(source)
            val user = repository.localProfileId()
            val sport = source.referenceDao().getSportBySlug("climbing")!!
            val center = repository.getComplexOptionsForSport(sport.id).first()
            repository.addCompletedTraining(user, AddCompletedTrainingInput(sport.id, center.id, LocalDate.parse("2026-10-07"), climbingRoutes = listOf(
                ClimbingRouteInput(ClimbingWorkoutType.DIFFICULTY, "6b+", true),
                ClimbingRouteInput(ClimbingWorkoutType.BOULDERING, "7A", false),
                ClimbingRouteInput(ClimbingWorkoutType.SPEED, "", true, speedCourse = "standard_15m"),
                ClimbingRouteInput(ClimbingWorkoutType.SPEED, "", false, speedCourse = "other"),
            )))
            val backup = BackupCodec.decode(repository.createBackup())
            assertEquals(4, backup.schemaVersion)
            val rows = backup.trainings.single().climbingRoutes
            assertEquals(listOf("french", "fontainebleau", "none", "none"), rows.map { it.gradingSystem })
            val importer = AppRepository(target)
            val first = importer.importData(BackupCodec.encode(backup).toByteArray(), importer.localProfileId())
            assertEquals(1, first.importedTrainings)
            assertEquals(0L, first.historicalRouteAttempts)
            val restored = BackupCodec.decode(importer.createBackup()).trainings.single().climbingRoutes
            assertEquals(rows, restored)
            assertEquals(0, importer.importData(BackupCodec.encode(backup).toByteArray(), importer.localProfileId()).importedTrainings)
        } finally { source.close(); target.close() }
    }

    @Test fun oldBoulderSpeedAndUnknownGradesRemainVisibleWithWarning() = runBlocking<Unit> {
        val db = database()
        try {
            val repository = AppRepository(db)
            val user = repository.localProfileId()
            val backup = BackupDocument(schemaVersion = 2, exportedAt = "2026-10-07T00:00:00Z",
                sports = listOf(SportBackup("climbing", "Скалолазание")),
                centers = listOf(CenterBackup(name = "Исторический зал", sportSlugs = listOf("climbing"))),
                trainings = listOf(TrainingBackup(date = "2020-01-01", sportSlug = "climbing", centerName = "Исторический зал", climbingRoutes = listOf(
                    ClimbingRouteBackup("bouldering", " 7B ", true, 4),
                    ClimbingRouteBackup("speed", "6A", false, 3),
                    ClimbingRouteBackup("difficulty", "10Z", true, 2),
                    ClimbingRouteBackup("difficulty", "6B", true, 1),
                ))))
            val bytes = BackupCodec.encode(backup).toByteArray()
            val first = repository.importData(bytes, user)
            assertEquals(9L, first.historicalRouteAttempts)
            assertEquals(0, repository.importData(bytes, user).importedTrainings)
            val sport = db.referenceDao().getSportBySlug("climbing")!!
            val metrics = repository.observeSportStatistics(sport.id).first()!!.metrics.associate { it.label to it.value }
            assertEquals("10", metrics["Трассы"])
            assertEquals("9", metrics["Исторические категории без сравнения"])
            assertEquals("6b", metrics["Максимум · Трудность (Французская)"])
            assertNull(metrics["Максимум · Болдер (Fontainebleau)"])
            val details = repository.observeTrainingsForSport(sport.id).first().single().details
            assertTrue(details.any { it.contains(" 7B  (историческая") })
            assertTrue(details.any { it.contains("10Z (историческая") })
            assertEquals(" 7B ", BackupCodec.decode(repository.createBackup()).trainings.single().climbingRoutes.first().routeDifficulty)
        } finally { db.close() }
    }

    @Test fun incompatibleScaleRollsBackWholeFileIncludingNewCenter() = runBlocking<Unit> {
        val db = database()
        try {
            val repository = AppRepository(db)
            val user = repository.localProfileId()
            val before = BackupCodec.decode(repository.createBackup()).copy(exportedAt = "")
            val document = BackupDocument(schemaVersion = 3, exportedAt = "2026-10-07T00:00:00Z", sports = listOf(SportBackup("climbing", "Скалолазание")),
                centers = listOf(CenterBackup(name = "Новый зал", sportSlugs = listOf("climbing"))),
                trainings = listOf(TrainingBackup(date = "2026-10-07", sportSlug = "climbing", centerName = "Новый зал", climbingRoutes = listOf(
                    ClimbingRouteBackup("bouldering", "6A", true, gradingSystem = "french", gradeCode = "6a")))))
            assertTrue(runCatching { repository.importData(BackupCodec.encode(document).toByteArray(), user) }.isFailure)
            assertEquals(before, BackupCodec.decode(repository.createBackup()).copy(exportedAt = ""))
        } finally { db.close() }
    }
}
