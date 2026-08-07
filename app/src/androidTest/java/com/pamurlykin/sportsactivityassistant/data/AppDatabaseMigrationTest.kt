package com.pamurlykin.sportsactivityassistant.data

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDatabaseMigrationTest {
    @Test
    fun migration1To2PreservesAggregatedClimbingCount() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        val db = helper.writableDatabase
        db.execSQL("CREATE TABLE sports (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, slug TEXT NOT NULL, title TEXT NOT NULL)")
        db.execSQL("CREATE TABLE football_trainings (training_id INTEGER PRIMARY KEY NOT NULL, team_goals_scored INTEGER NOT NULL, team_goals_conceded INTEGER NOT NULL, user_goals_scored INTEGER NOT NULL, user_assists INTEGER NOT NULL, distance_km TEXT)")
        db.execSQL("CREATE TABLE climbing_trainings (training_id INTEGER PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE climbing_routes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, climbing_training_id INTEGER NOT NULL, workout_type TEXT NOT NULL, route_difficulty TEXT NOT NULL, routes_completed INTEGER NOT NULL, FOREIGN KEY(climbing_training_id) REFERENCES climbing_trainings(training_id) ON DELETE CASCADE)")
        db.execSQL("INSERT INTO climbing_trainings(training_id) VALUES (10)")
        db.execSQL("INSERT INTO climbing_routes(climbing_training_id, workout_type, route_difficulty, routes_completed) VALUES (10, 'difficulty', '6B', 4)")

        AppDatabase.MIGRATION_1_2.migrate(db)

        db.query("SELECT is_completed, repeat_count FROM climbing_routes").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals(4, cursor.getInt(1))
        }
        db.query("PRAGMA table_info(football_trainings)").use { cursor ->
            val columns = buildSet { while (cursor.moveToNext()) add(cursor.getString(1)) }
            assertTrue("players_per_team" in columns)
            assertTrue("duration_minutes" in columns)
        }
        helper.close()
        context.deleteDatabase(name)
    }
}
