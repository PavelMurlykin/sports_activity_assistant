package com.pamurlykin.sportsactivityassistant.data.repo

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.backup.*
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DocumentedExamplesTest {
    @Test fun publicJson6AndCsvExamplesMatchTheirDocumentedCountsAndLinks() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext,AppDatabase::class.java).build()
        try {
            val repo = AppRepository(db)
            val json = instrumentation.context.assets.open("training-backup-v6.json").use { it.readBytes() }
            val user = repo.localProfileId()
            val imported = repo.importData(json,user)
            assertEquals(3,imported.importedTrainings)
            assertEquals(4,imported.importedPlans)
            assertEquals(1,imported.importedRules)
            assertEquals(1,imported.importedFavorites)
            assertEquals(3,repo.observeStatisticsOverview().first().totalTrainings)
            val sports = db.referenceDao().getSports().associateBy { it.slug }
            val football = repo.observeSportStatistics(sports.getValue("football").id).first()!!.metrics.associate { it.label to it.value }
            assertEquals("3:1",football["Счёт команд"])
            assertEquals("7.35 км",football["Учтённая дистанция"])
            assertEquals("1 из 2",football["Игр с дистанцией"])
            val climbing = repo.observeSportStatistics(sports.getValue("climbing").id).first()!!.metrics.associate { it.label to it.value }
            assertEquals("4",climbing["Записи трасс"])
            assertEquals("5",climbing["Попытки (с повторами)"])
            assertEquals("2 (40%)",climbing["Успешно пройдено"])
            assertEquals("6b+",climbing["Максимум · Трудность (Французская)"])
            val snapshot = BackupCodec.decode(repo.createBackup())
            val completed = snapshot.plannedTrainings.single { it.status == "completed" }
            assertEquals("30000000-0000-4000-8000-000000000010",completed.completedTrainingPublicId)
            assertEquals("2025-12-28",snapshot.plannedTrainings.single { it.date == "2025-12-29" }.occurrenceDate)
            val day = LocalDate.of(2025,12,29)
            assertEquals(1,repo.getScheduleMonth(user,YearMonth.from(day),day,LocalDate.of(2026,10,9)).selectedDayEvents.size)
            assertEquals(3,repo.importData(json,user).skippedTrainings)
            val csv = instrumentation.context.assets.open("football-history.csv").use { it.readBytes() }
            val parsed = repo.prepareImport(csv)
            assertEquals(2,parsed.document.trainings.size)
            assertNull(parsed.document.trainings.last().football!!.distanceKm)
            assertFalse(repo.previewImport(parsed).canApply) // Center 42 is a file hint, not a local ID.
            assertEquals(3,repo.observeStatisticsOverview().first().totalTrainings)
        } finally { db.close() }
    }
}
