package com.pamurlykin.sportsactivityassistant.data

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LocalIdentityMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrationPreservesAllRelationsWithForeignKeysEnabled() = runBlocking<Unit> {
        val name = "identity-upgrade.db"
        helper.createDatabase(name, 2).apply {
            execSQL("PRAGMA foreign_keys = ON")
            execSQL("INSERT INTO users VALUES (7, 999, 'external', 'Локальный', 'Профиль', 123)")
            execSQL("INSERT INTO users VALUES (8, 1000, NULL, NULL, NULL, 124)")
            execSQL("INSERT INTO sports VALUES (10, 'football', 'Футбол'), (20, 'climbing', 'Скалолазание'), (30, 'historical', 'Старый спорт')")
            execSQL("INSERT INTO sports_complexes VALUES (4, 'Исторический центр', NULL, 125)")
            execSQL("INSERT INTO sports_complex_sports VALUES (1, 4, 10), (2, 4, 20), (3, 4, 30)")
            execSQL("INSERT INTO user_favorite_complexes VALUES (6, 7, 4, 126)")
            execSQL("INSERT INTO trainings VALUES (11, 7, 10, 4, '2020-01-01', 127), (12, 8, 20, 4, '2020-01-02', 128), (13, 7, 30, 4, '2020-01-03', 129)")
            execSQL("INSERT INTO football_trainings VALUES (11, 2, 1, 1, 1, '5.20', 5, 45)")
            execSQL("INSERT INTO climbing_trainings VALUES (12)")
            execSQL("INSERT INTO climbing_routes VALUES (21, 12, 'difficulty', '6B', 1, 4), (22, 12, 'bouldering', '7A', 0, 2)")
            execSQL("INSERT INTO recurrence_rules VALUES (31, 7, 10, 4, '2026-10-01', '2026-12-31', 'weekly', 2, 130)")
            execSQL("INSERT INTO planned_trainings VALUES (41, 7, 10, 4, '2026-10-15', 31, 'canceled', 131)")
            execSQL("UPDATE sqlite_sequence SET seq = 100 WHERE name = 'trainings'")
            // Exercise the rebuilding algorithm itself under FK enforcement, inside one transaction.
            beginTransaction()
            try { AppDatabase.MIGRATION_2_3.migrate(this); setTransactionSuccessful() }
            finally { endTransaction() }
            close()
        }
        // The direct invocation changed the schema, but not Room's version/identity metadata.
        // Let the helper validate that schema and perform Room's metadata bookkeeping.
        helper.runMigrationsAndValidate(name, 3, true, object : Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
        }).close()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5).build()
        try {
            val users = database.referenceDao().getUsers()
            assertEquals(listOf(7L, 8L), users.map { it.id })
            assertEquals("Локальный Профиль", users.first().displayName)
            assertNull(users.last().displayName)
            assertEquals(123L, users.first().createdAt.toEpochMilli())
            val bundles = database.trainingDao().getAllTrainingBundles()
            assertEquals(listOf(11L, 12L, 13L), bundles.map { it.training.id })
            assertEquals("historical", bundles.last().sport.slug)
            assertEquals("5.20", bundles.first().football!!.distanceKm!!.toPlainString())
            assertEquals(45, bundles.first().football!!.durationMinutes)
            val routes = bundles[1].climbing!!.routes.sortedBy { it.id }
            assertEquals(listOf(4, 2), routes.map { it.repeatCount })
            assertEquals(listOf(true, false), routes.map { it.isCompleted })
            assertEquals(31L, database.planningDao().getAllPlannedTrainings().single().recurrenceRuleId)
            assertEquals("canceled", database.planningDao().getAllPlannedTrainings().single().status.storageValue)
            assertEquals(2, database.planningDao().getAllRecurrenceRules().single().intervalWeeks)
            assertEquals(4L, database.referenceDao().getFavoriteComplexes().single().sportsComplexId)
            val ids = bundles.map { it.training.publicId } + routes.map { it.publicId } +
                database.referenceDao().getAllComplexes().map { it.publicId } +
                database.planningDao().getAllPlannedTrainings().map { it.publicId } +
                database.planningDao().getAllRecurrenceRules().map { it.publicId }
            ids.forEach { UUID.fromString(it) }
            assertEquals(ids.size, ids.distinct().size)
            database.openHelper.writableDatabase.query("PRAGMA table_info(users)").use { cursor ->
                val columns = buildSet { while (cursor.moveToNext()) add(cursor.getString(1)) }
                assertEquals(setOf("id", "display_name", "created_at", "public_id"), columns)
            }
            database.openHelper.writableDatabase.query("SELECT seq FROM sqlite_sequence WHERE name = 'trainings'").use {
                assertTrue(it.moveToFirst()); assertEquals(100, it.getInt(0))
            }
        } finally { database.close(); context.deleteDatabase(name) }
    }

    @Test
    fun normalRoomUpgradeFromVersion2ValidatesSchema() = runBlocking<Unit> {
        val name = "normal-room-upgrade.db"
        helper.createDatabase(name, 2).apply {
            execSQL("INSERT INTO users VALUES (1, 0, NULL, NULL, NULL, 123)")
            close()
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5).build()
        try {
            assertEquals(1L, database.referenceDao().getUsers().single().id)
            database.openHelper.writableDatabase.query("PRAGMA foreign_keys").use {
                assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0))
            }
        } finally { database.close() }
        helper.runMigrationsAndValidate(name, 5, true, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5).close()
        context.deleteDatabase(name)
    }

    @Test
    fun brokenPredecessorReferencesAbortWithoutChangingOldData() {
        val name = "broken-predecessor.db"
        val db = helper.createDatabase(name, 2)
        try {
            db.execSQL("PRAGMA foreign_keys = OFF")
            db.execSQL("INSERT INTO users VALUES (1, 100, NULL, 'Имя', NULL, 123)")
            db.execSQL("INSERT INTO trainings VALUES (10, 1, 99, 99, '2020-01-01', 123)")
            db.beginTransaction()
            try {
                val failure = runCatching { AppDatabase.MIGRATION_2_3.migrate(db) }.exceptionOrNull()
                assertTrue(failure is IllegalStateException)
                assertTrue(failure!!.message!!.contains("Нарушены связи"))
            } finally { db.endTransaction() }
            assertEquals(2, db.version)
            db.query("SELECT first_name FROM users WHERE id = 1").use {
                assertTrue(it.moveToFirst()); assertEquals("Имя", it.getString(0))
            }
            db.query("SELECT sport_id, sports_complex_id FROM trainings WHERE id = 10").use {
                assertTrue(it.moveToFirst()); assertEquals(99, it.getInt(0)); assertEquals(99, it.getInt(1))
            }
        } finally {
            db.close()
            InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name)
        }
    }

    @Test
    fun completeVersion1To5ChainPreservesHistoricalRouteCounts() = runBlocking<Unit> {
        val name = "complete-v1-upgrade.db"
        helper.createDatabase(name, 2).apply {
            // Reconstruct only the two v1 differences from the saved v2 schema and original migration.
            execSQL("DROP TABLE climbing_routes")
            execSQL("DROP TABLE football_trainings")
            execSQL("CREATE TABLE football_trainings (training_id INTEGER NOT NULL PRIMARY KEY, team_goals_scored INTEGER NOT NULL, team_goals_conceded INTEGER NOT NULL, user_goals_scored INTEGER NOT NULL, user_assists INTEGER NOT NULL, distance_km TEXT, FOREIGN KEY(training_id) REFERENCES trainings(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            execSQL("CREATE UNIQUE INDEX index_football_trainings_training_id ON football_trainings(training_id)")
            execSQL("CREATE TABLE climbing_routes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, climbing_training_id INTEGER NOT NULL, workout_type TEXT NOT NULL, route_difficulty TEXT NOT NULL, routes_completed INTEGER NOT NULL, FOREIGN KEY(climbing_training_id) REFERENCES climbing_trainings(training_id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            execSQL("CREATE INDEX index_climbing_routes_climbing_training_id ON climbing_routes(climbing_training_id)")
            execSQL("INSERT INTO users VALUES (1, 0, NULL, NULL, NULL, 123)")
            execSQL("INSERT INTO sports VALUES (2, 'climbing', 'Скалолазание')")
            execSQL("INSERT INTO sports_complexes VALUES (1, 'Центр', NULL, 123)")
            execSQL("INSERT INTO trainings VALUES (10, 1, 2, 1, '2020-01-01', 123)")
            execSQL("INSERT INTO climbing_trainings VALUES (10)")
            execSQL("INSERT INTO climbing_routes VALUES (20, 10, 'difficulty', '6B', 4)")
            version = 1
            close()
        }
        helper.runMigrationsAndValidate(name, 5, true, AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5).use { db ->
            db.query("SELECT repeat_count, is_completed FROM climbing_routes").use {
                assertTrue(it.moveToFirst()); assertEquals(4, it.getInt(0)); assertEquals(1, it.getInt(1))
            }
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            db.query("SELECT route_difficulty, grading_system, grade_code FROM climbing_routes").use {
                assertTrue(it.moveToFirst()); assertEquals("6B", it.getString(0)); assertEquals("french", it.getString(1)); assertEquals("6b", it.getString(2))
            }
        }
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name)
    }
}
