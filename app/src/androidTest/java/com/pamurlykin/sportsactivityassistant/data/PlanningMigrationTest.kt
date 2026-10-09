package com.pamurlykin.sportsactivityassistant.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.model.PlannedTrainingStatus
import com.pamurlykin.sportsactivityassistant.data.repo.AppRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PlanningMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),AppDatabase::class.java)
    @Test fun migration6To7IsAdditiveAndPreservesDuplicateHistoricalSeriesLinks() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "planning-upgrade.db"
        helper.createDatabase(name,6).apply {
            execSQL("INSERT INTO users VALUES (7,'Профиль',123,'20000000-0000-4000-8000-000000000007')")
            execSQL("INSERT INTO sports VALUES (1,'football','Футбол')")
            execSQL("INSERT INTO sports_complexes VALUES (42,'Арена',NULL,125,'20000000-0000-4000-8000-000000000042',0,1)")
            execSQL("INSERT INTO sports_complex_sports VALUES (1,42,1)")
            execSQL("INSERT INTO recurrence_rules (id,user_id,sport_id,sports_complex_id,start_date,end_date,frequency,interval_weeks,created_at,public_id) VALUES (3,7,1,42,'2020-02-29',NULL,'weekly',1,130,'20000000-0000-4000-8000-000000000003')")
            execSQL("INSERT INTO planned_trainings (id,user_id,sport_id,sports_complex_id,planned_date,recurrence_rule_id,status,created_at,public_id) VALUES (4,7,1,42,'2020-02-29',3,'planned',131,'20000000-0000-4000-8000-000000000004'),(5,7,1,42,'2020-02-29',3,'canceled',132,'20000000-0000-4000-8000-000000000005')")
            close()
        }
        helper.runMigrationsAndValidate(name,7,true,AppDatabase.MIGRATION_6_7).use {
            it.query("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.moveToFirst()) }
        }
        val db = Room.databaseBuilder(context,AppDatabase::class.java,name).build()
        try {
            val plans = db.planningDao().getAllPlannedTrainings()
            assertEquals(listOf(4L,5L),plans.map { it.id })
            assertEquals(listOf(3L,3L),plans.map { it.recurrenceRuleId })
            assertEquals(listOf(131L,132L),plans.map { it.createdAt.toEpochMilli() })
            assertEquals(listOf(PlannedTrainingStatus.PLANNED,PlannedTrainingStatus.CANCELED),plans.map { it.status })
            assertTrue(plans.all { it.occurrenceDate == null && it.completedTrainingId == null })
            assertFalse(db.planningDao().getRule(3)!!.isCanceled)
            val date = LocalDate.parse("2020-02-29")
            val events = AppRepository(db).getScheduleMonth(7,YearMonth.from(date),date,date).selectedDayEvents
            assertEquals(2,events.size) // two stored historical plans, not a third generated duplicate
            assertEquals(listOf("planned-4","planned-5"),events.map { it.id })
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
