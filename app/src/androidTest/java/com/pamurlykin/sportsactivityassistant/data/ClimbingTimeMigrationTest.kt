package com.pamurlykin.sportsactivityassistant.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ClimbingTimeMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun migration9To10PreservesRouteIdentityRepeatsAndUnknownTime() {
        val name = "climbing-time-upgrade.db"
        try {
            helper.createDatabase(name, 9).apply {
                execSQL("INSERT INTO users VALUES (7,'Профиль',123,'20000000-0000-4000-8000-000000000007')")
                execSQL("INSERT INTO sports VALUES (2,'climbing','Скалолазание')")
                execSQL("INSERT INTO sports_complexes VALUES (42,'Зал',NULL,125,'20000000-0000-4000-8000-000000000042',0,0)")
                execSQL("INSERT INTO trainings (id,user_id,sport_id,sports_complex_id,training_date,created_at,public_id) VALUES (9,7,2,42,'2020-02-29',126,'20000000-0000-4000-8000-000000000009')")
                execSQL("INSERT INTO climbing_trainings VALUES (9)")
                execSQL("INSERT INTO climbing_routes (id,climbing_training_id,workout_type,route_difficulty,is_completed,repeat_count,public_id,grading_system,speed_course) VALUES (11,9,'speed','',1,4,'20000000-0000-4000-8000-000000000011','none','standard_15m')")
                close()
            }
            helper.runMigrationsAndValidate(name, 10, true, AppDatabase.MIGRATION_9_10).use { db ->
                db.query("SELECT public_id,repeat_count,speed_time_millis,climbing_training_id FROM climbing_routes WHERE id=11").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("20000000-0000-4000-8000-000000000011", it.getString(0))
                    assertEquals(4, it.getInt(1)); assertTrue(it.isNull(2)); assertEquals(9, it.getInt(3))
                }
                db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            }
        } finally { InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name) }
    }
}
