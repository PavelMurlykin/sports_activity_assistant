package com.pamurlykin.sportsactivityassistant.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.backup.BackupCodec
import com.pamurlykin.sportsactivityassistant.data.model.ClimbingWorkoutType
import com.pamurlykin.sportsactivityassistant.data.repo.AppRepository
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ClimbingGradesMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun roomUpgradePreservesRawHistoryIdentitiesCountsAndUnknownDiscipline() = runBlocking<Unit> {
        val name = "climbing-grade-upgrade.db"
        val types = listOf("difficulty", "bouldering", "speed", "difficulty", "old discipline", "difficulty")
        val raw = listOf(" 6A+ ", "7B", "6A", "10Z", "weird raw", "4+")
        val uuids = types.map { UUID.randomUUID().toString() }
        helper.createDatabase(name, 3).apply {
            execSQL("PRAGMA foreign_keys = ON")
            execSQL("INSERT INTO users VALUES (1, NULL, 123)")
            execSQL("INSERT INTO sports VALUES (2, 'climbing', 'Скалолазание')")
            execSQL("INSERT INTO sports_complexes VALUES (1, 'Скалодром', NULL, 123, ?)", arrayOf(UUID.randomUUID().toString()))
            execSQL("INSERT INTO sports_complex_sports VALUES (1, 1, 2)")
            execSQL("INSERT INTO trainings VALUES (10, 1, 2, 1, '2020-01-01', 123, ?)", arrayOf(UUID.randomUUID().toString()))
            execSQL("INSERT INTO climbing_trainings VALUES (10)")
            types.forEachIndexed { index, type ->
                execSQL("INSERT INTO climbing_routes VALUES (?, 10, ?, ?, ?, ?, ?)",
                    arrayOf<Any>(index + 1, type, raw[index], if (index % 2 == 0) 1 else 0, index + 1, uuids[index]))
            }
            close()
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6).build()
        val restored = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val routes = database.trainingDao().getAllTrainingBundles().single().climbing!!.routes.sortedBy { it.id }
            assertEquals(raw, routes.map { it.routeDifficulty })
            assertEquals(uuids, routes.map { it.publicId })
            assertEquals((1..6).toList(), routes.map { it.repeatCount })
            assertEquals(listOf(true, false, true, false, true, false), routes.map { it.isCompleted })
            assertEquals(listOf("french", "legacy", "legacy", "legacy", "legacy", "legacy"), routes.map { it.gradingSystem })
            assertEquals("6a+", routes.first().gradeCode)
            assertEquals(ClimbingWorkoutType.UNKNOWN, routes[4].workoutType)
            assertEquals("old discipline", routes[4].legacyWorkoutType)
            assertTrue(routes.all { it.speedCourse == null })
            database.openHelper.writableDatabase.query("SELECT workout_type FROM climbing_routes WHERE id = 5").use {
                assertTrue(it.moveToFirst()); assertEquals("old discipline", it.getString(0))
            }
            database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            val exported = AppRepository(database).createBackup()
            assertEquals(raw, BackupCodec.decode(exported).trainings.single().climbingRoutes.map { it.routeDifficulty })
            val repository = AppRepository(restored)
            val result = repository.importData(exported.toByteArray(), repository.localProfileId())
            assertEquals(1, result.importedTrainings)
            assertEquals(20L, result.historicalRouteAttempts)
            val restoredRoutes = restored.trainingDao().getAllTrainingBundles().single().climbing!!.routes.sortedBy { it.id }
            assertEquals(raw, restoredRoutes.map { it.routeDifficulty })
            assertEquals("old discipline", restoredRoutes[4].legacyWorkoutType)
            assertEquals(ClimbingWorkoutType.UNKNOWN, restoredRoutes[4].workoutType)
        } finally { database.close(); restored.close() }
        helper.runMigrationsAndValidate(name, 6, true, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6).close()
        context.deleteDatabase(name)
    }
}
