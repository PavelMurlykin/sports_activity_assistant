package com.pamurlykin.sportsactivityassistant.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.model.SaveSportsCenterInput
import com.pamurlykin.sportsactivityassistant.data.repo.AppRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CentersArchiveMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun upgrade5To6PreservesDuplicatesAllReferencesIdentitiesAndTimes() = runBlocking {
        val name = "centers-archive-upgrade.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        helper.createDatabase(name, 5).apply {
            execSQL("INSERT INTO users VALUES (7, 'Профиль', 123, '20000000-0000-4000-8000-000000000007')")
            execSQL("INSERT INTO sports VALUES (1, 'football', 'Футбол')")
            execSQL("INSERT INTO sports_complexes VALUES (42, 'Арена', NULL, 125, '20000000-0000-4000-8000-000000000042', 0), (43, 'Арена', NULL, 126, '20000000-0000-4000-8000-000000000043', 0)")
            execSQL("INSERT INTO sports_complex_sports VALUES (1, 42, 1), (2, 43, 1)")
            execSQL("INSERT INTO trainings VALUES (10, 7, 1, 42, '2020-01-01', 127, '20000000-0000-4000-8000-000000000010'), (11, 7, 1, 43, '2020-01-01', 128, '20000000-0000-4000-8000-000000000011')")
            execSQL("INSERT INTO football_trainings VALUES (10, 0, 0, 0, 0, NULL, NULL, NULL), (11, 0, 0, 0, 0, NULL, NULL, NULL)")
            execSQL("INSERT INTO user_favorite_complexes VALUES (1, 7, 43, 129)")
            execSQL("INSERT INTO recurrence_rules (id,user_id,sport_id,sports_complex_id,start_date,end_date,frequency,interval_weeks,created_at,public_id) VALUES (3,7,1,43,'2020-01-01',NULL,'weekly',1,130,'20000000-0000-4000-8000-000000000003')")
            execSQL("INSERT INTO planned_trainings (id,user_id,sport_id,sports_complex_id,planned_date,recurrence_rule_id,status,created_at,public_id) VALUES (4,7,1,43,'2020-01-01',3,'canceled',131,'20000000-0000-4000-8000-000000000004')")
            execSQL("INSERT INTO import_aliases VALUES ('center','historic42','20000000-0000-4000-8000-000000000042')")
            close()
        }
        helper.runMigrationsAndValidate(name, 6, true, AppDatabase.MIGRATION_5_6).close()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            val centers = db.referenceDao().getAllComplexes()
            assertEquals(listOf(42L, 43L), centers.map { it.id })
            assertTrue(centers.none { it.isArchived }); assertEquals(listOf(125L,126L), centers.map { it.createdAt.toEpochMilli() })
            assertEquals(2, db.trainingDao().getAllTrainingBundles().size)
            assertEquals(43L, db.planningDao().getAllPlannedTrainings().single().sportsComplexId)
            assertEquals(3L, db.planningDao().getAllPlannedTrainings().single().recurrenceRuleId)
            assertEquals(43L, db.planningDao().getAllRecurrenceRules().single().sportsComplexId)
            assertEquals(43L, db.referenceDao().getFavoriteComplexes().single().sportsComplexId)
            assertEquals(centers.first().publicId, db.referenceDao().getImportAliases().single().targetPublicId)
            val repo = AppRepository(db)
            repo.saveSportsCenter(SaveSportsCenterInput(42, "Арена", null, setOf(1)))
            repo.setSportsCenterArchived(43, true)
            assertEquals(centers.first(), db.referenceDao().getComplex(42))
            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { db.close() }
        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            assertTrue(reopened.referenceDao().getComplex(43)!!.isArchived)
            assertEquals(2, reopened.trainingDao().getAllTrainingBundles().size)
        } finally { reopened.close(); context.deleteDatabase(name) }
    }
}
