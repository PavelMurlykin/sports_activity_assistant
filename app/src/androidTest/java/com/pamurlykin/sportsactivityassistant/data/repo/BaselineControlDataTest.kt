package com.pamurlykin.sportsactivityassistant.data.repo

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.model.ScheduleEventState
import com.pamurlykin.sportsactivityassistant.data.seed.LocalSeed
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineControlDataTest {
    @Test
    fun productionReferenceDataContainsNoSampleHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            LocalSeed.initialize(database)
            assertEquals(1, database.referenceDao().getUsers().size)
            assertEquals(2, database.referenceDao().getSports().size)
            assertTrue(database.trainingDao().getAllTrainingBundles().isEmpty())
            assertTrue(database.planningDao().getAllPlannedTrainings().isEmpty())
            assertTrue(database.planningDao().getAllRecurrenceRules().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun localControlFileMatchesHandCalculatedStatisticsAndCalendar() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val database = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, AppDatabase::class.java).build()
        try {
            LocalSeed.initialize(database)
            val bytes = instrumentation.context.assets.open("control-backup-v2.json").use { it.readBytes() }
            val repository = AppRepository(database)
            val result = repository.importData(bytes, 1)
            assertEquals(3, result.importedTrainings)
            assertEquals(0, result.skippedTrainings)
            val sports = database.referenceDao().getSports()
            val bundles = database.trainingDao().getAllTrainingBundles()
            val overview = StatisticsMapper.overview(sports, bundles)
            assertEquals(3, overview.totalTrainings)
            assertEquals(mapOf("football" to 2, "climbing" to 1), overview.sports.associate { it.slug to it.completedTrainings })

            fun metrics(slug: String) = StatisticsMapper.sport(
                sports.single { it.slug == slug }, bundles.filter { it.sport.slug == slug },
            ).metrics.associate { it.label to it.value }
            val football = metrics("football")
            assertEquals("1 / 1 / 0", football["Победы / ничьи / поражения"])
            assertEquals("2:1", football["Счёт команд"])
            assertEquals("1", football["Личные голы"])
            assertEquals("1", football["Голевые передачи"])
            assertEquals("4 км", football["Учтённая дистанция"])
            assertEquals("40 мин", football["Средняя длительность"])
            val climbing = metrics("climbing")
            assertEquals("3", climbing["Трассы"])
            assertEquals("2 (66%)", climbing["Успешно пройдено"])
            assertEquals("6A+", climbing["Максимальная сложность"])

            val date = LocalDate.parse("2026-10-07")
            val schedule = repository.getScheduleMonth(1, YearMonth.from(date), date, date)
            val events = schedule.days.flatMap { it.events }
            assertEquals(3, events.count { it.state == ScheduleEventState.COMPLETED })
            assertEquals(4, events.count { it.state == ScheduleEventState.PLANNED })
            assertEquals(listOf(7, 14, 21), events.filter { it.isRecurring }.map { it.date.dayOfMonth })
            assertEquals(0, repository.importData(bytes, 1).importedTrainings)
        } finally {
            database.close()
        }
    }
}
