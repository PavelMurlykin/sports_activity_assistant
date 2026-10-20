package com.pamurlykin.sportsactivityassistant.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SportVisibilityMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun migration8To9KeepsHistoryAndDefaultCentersAndDefaultsToAllSportsVisible() {
        val name = "sport-visibility-upgrade.db"
        helper.createDatabase(name, 8).apply {
            execSQL("INSERT INTO users VALUES (7,'Профиль',123,'20000000-0000-4000-8000-000000000007')")
            execSQL("INSERT INTO sports VALUES (1,'football','Футбол')")
            execSQL("INSERT INTO sports_complexes VALUES (42,'Арена',NULL,125,'20000000-0000-4000-8000-000000000042',0,0)")
            execSQL("INSERT INTO trainings (id,user_id,sport_id,sports_complex_id,training_date,created_at,public_id) VALUES (9,7,1,42,'2020-02-29',126,'20000000-0000-4000-8000-000000000009')")
            execSQL("INSERT INTO sport_primary_centers VALUES (7,1,42)")
            close()
        }
        helper.runMigrationsAndValidate(name, 9, true, AppDatabase.MIGRATION_8_9).use { db ->
            db.query("SELECT * FROM sport_visibility").use { assertFalse(it.moveToFirst()) }
            db.query("SELECT sports_complex_id FROM sport_primary_centers WHERE user_id=7 AND sport_id=1").use {
                assertTrue(it.moveToFirst()); assertEquals(42, it.getInt(0))
            }
            db.query("SELECT training_date,public_id,created_at FROM trainings WHERE id=9").use {
                assertTrue(it.moveToFirst()); assertEquals("2020-02-29", it.getString(0))
                assertEquals("20000000-0000-4000-8000-000000000009", it.getString(1)); assertEquals(126L, it.getLong(2))
            }
            db.execSQL("INSERT INTO sport_visibility VALUES (7,1,0)")
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name)
    }
}
