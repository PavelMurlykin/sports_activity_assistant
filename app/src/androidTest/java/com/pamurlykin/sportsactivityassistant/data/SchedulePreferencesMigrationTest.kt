package com.pamurlykin.sportsactivityassistant.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SchedulePreferencesMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun migration7To8PreservesLinkedResultsAndAddsOnlyOptionalValues() {
        val name = "schedule-preferences-upgrade.db"
        helper.createDatabase(name, 7).apply {
            execSQL("INSERT INTO users VALUES (7,'Профиль',123,'20000000-0000-4000-8000-000000000007')")
            execSQL("INSERT INTO sports VALUES (1,'football','Футбол')")
            execSQL("INSERT INTO sports_complexes VALUES (42,'Арена',NULL,125,'20000000-0000-4000-8000-000000000042',0,0)")
            execSQL("INSERT INTO trainings VALUES (9,7,1,42,'2020-02-29',126,'20000000-0000-4000-8000-000000000009')")
            execSQL("INSERT INTO football_trainings VALUES (9,3,1,1,1,'7.35',5,90)")
            execSQL("INSERT INTO planned_trainings (id,user_id,sport_id,sports_complex_id,planned_date,status,created_at,public_id,completed_training_id) VALUES (4,7,1,42,'2020-02-29','completed',131,'20000000-0000-4000-8000-000000000004',9)")
            close()
        }
        helper.runMigrationsAndValidate(name, 8, true, AppDatabase.MIGRATION_7_8).use { db ->
            db.query("SELECT completed_training_id,start_time,end_time,created_at FROM planned_trainings WHERE id=4").use {
                assertTrue(it.moveToFirst()); assertEquals(9L, it.getLong(0)); assertTrue(it.isNull(1)); assertTrue(it.isNull(2)); assertEquals(131L, it.getLong(3))
            }
            db.query("SELECT duration_minutes FROM football_trainings WHERE training_id=9").use {
                assertTrue(it.moveToFirst()); assertEquals(90, it.getInt(0))
            }
            db.query("SELECT duration_minutes,public_id FROM trainings WHERE id=9").use {
                assertTrue(it.moveToFirst()); assertTrue(it.isNull(0)); assertEquals("20000000-0000-4000-8000-000000000009", it.getString(1))
            }
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            db.execSQL("INSERT INTO sport_primary_centers VALUES (7,1,42)")
        }
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name)
    }
}
