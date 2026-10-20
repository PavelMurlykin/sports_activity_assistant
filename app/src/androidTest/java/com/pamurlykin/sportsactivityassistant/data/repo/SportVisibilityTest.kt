package com.pamurlykin.sportsactivityassistant.data.repo

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.backup.*
import com.pamurlykin.sportsactivityassistant.data.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class SportVisibilityTest {
    private fun database() = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
    private val date = LocalDate.parse("2020-02-29")

    @Test fun hidingSportFiltersCalendarTotalsAndMonthsWithoutRemovingHistoryOrDefaultCenter() = runBlocking {
        val db = database()
        try {
            val repo = AppRepository(db); val owner = repo.localProfileId()
            val football = db.referenceDao().getSportBySlug("football")!!.id
            val climbing = db.referenceDao().getSportBySlug("climbing")!!.id
            val center = repo.getComplexOptionsForSport(football).first().id
            repo.setPrimaryCenter(football, center)
            repo.addCompletedTraining(owner, AddCompletedTrainingInput(football, center, date, FootballTrainingInput(3, 1, 1, 1, null, null, null)))
            repo.addPlannedTraining(owner, AddPlannedTrainingInput(football, center, date, true, 1, null))
            repo.addCompletedTraining(owner, AddCompletedTrainingInput(climbing, repo.getComplexOptionsForSport(climbing).first().id, date,
                climbingRoutes = listOf(ClimbingRouteInput(ClimbingWorkoutType.DIFFICULTY, "6a", true))))
            assertTrue(repo.observeSportPreferences().first().all { it.isVisible })
            repo.setSportVisible(football, false)
            val statistics = repo.observeStatisticsOverview().first()
            assertEquals(listOf("climbing"), statistics.sports.map { it.slug })
            assertEquals(1, statistics.totalTrainings); assertEquals(1, statistics.months.single().totalTrainings)
            assertTrue(statistics.months.single().countsBySport.none { it.contains("Футбол") })
            val calendar = repo.getScheduleMonth(owner, YearMonth.from(date), date, date)
            assertTrue(calendar.days.flatMap { it.events }.all { it.sportId == climbing })
            assertEquals(1, calendar.selectedDayEvents.size)
            assertEquals(1, repo.observeTrainingPage(football).first().total)
            val backup = BackupCodec.decode(repo.createBackup())
            assertEquals(2, backup.trainings.size); assertEquals(1, backup.recurrenceRules.size)
            assertEquals(1, backup.primaryCenters.size)
            assertEquals(listOf("climbing"), backup.sportSelections.single().visibleSportSlugs)
            repo.setSportVisible(football, true)
            assertEquals(2, repo.observeStatisticsOverview().first().totalTrainings)
            assertEquals(2, repo.observeSportPreferences().first().size)
            repo.setSportVisible(football, false); repo.setSportVisible(climbing, false)
            assertTrue(repo.observeStatisticsOverview().first().sports.isEmpty())
            assertEquals(0, repo.observeStatisticsOverview().first().totalTrainings)
            assertTrue(repo.getScheduleMonth(owner, YearMonth.from(date), date, date).days.all { it.events.isEmpty() })
        } finally { db.close() }
    }

    @Test fun backupRestoresSelectionAndDefaultsOnFreshAndPopulatedInstallationsIncludingEmptySelection() = runBlocking {
        val original = database(); val restored = database()
        try {
            val source = AppRepository(original); source.localProfileId()
            val target = AppRepository(restored); target.localProfileId()
            val sports = original.referenceDao().getSports()
            sports.forEach { source.setSportVisible(it.id, false) }
            val football = sports.first { it.slug == "football" }.id
            val center = source.getComplexOptionsForSport(football).first().id
            source.setPrimaryCenter(football, center)
            val backup = source.createBackup()
            target.importData(backup.toByteArray(), target.localProfileId())
            val document = BackupCodec.decode(target.createBackup())
            assertTrue(document.sportSelections.single().visibleSportSlugs.isEmpty())
            assertEquals(BackupCodec.decode(backup).primaryCenters, document.primaryCenters)
            val newRepository = AppRepository(restored)
            assertTrue(newRepository.observeSportPreferences().first().none { it.isVisible })
            // Restore into an existing diary replaces settings, including an explicit no-default choice.
            sports.forEach { source.setSportVisible(it.id, it.id == football) }
            source.setPrimaryCenter(football, null)
            val updated = source.createBackup()
            target.importData(updated.toByteArray(), target.localProfileId())
            assertEquals(listOf("football"), target.observeSportPreferences().first().filter { it.isVisible }.map { it.sport.slug })
            assertTrue(BackupCodec.decode(target.createBackup()).primaryCenters.isEmpty())
            target.importData(updated.toByteArray(), target.localProfileId())
            assertTrue(BackupCodec.decode(target.createBackup()).primaryCenters.isEmpty())
        } finally { original.close(); restored.close() }
    }

    @Test fun oldBackupLeavesVisibilityAndExistingDefaultUnchangedAndInvalidSettingsCannotBeImported() = runBlocking {
        val db = database()
        try {
            val repo = AppRepository(db); repo.localProfileId()
            val football = db.referenceDao().getSportBySlug("football")!!.id
            repo.setSportVisible(football, false)
            repo.setPrimaryCenter(football, repo.getComplexOptionsForSport(football).first().id)
            val document = BackupCodec.decode(repo.createBackup())
            val old = document.copy(schemaVersion = 7, sportSelections = emptyList(), primaryCenters = emptyList())
            repo.importData(BackupCodec.encode(old).toByteArray(), repo.localProfileId())
            assertFalse(repo.observeSportPreferences().first().first { it.sport.id == football }.isVisible)
            assertEquals(1, db.referenceDao().getPrimaryCenters().size)
            val selection = document.sportSelections.single()
            val invalid = listOf(
                document.copy(sportSelections = listOf(selection, selection)),
                document.copy(sportSelections = listOf(selection.copy(visibleSportSlugs = listOf("unknown")))),
                document.copy(sportSelections = listOf(selection.copy(visibleSportSlugs = listOf("football", "football")))),
                document.copy(sportSelections = listOf(selection.copy(profilePublicId = "missing"))))
            invalid.forEach {
                val parsed = repo.prepareImport(BackupCodec.encode(it).toByteArray())
                assertTrue(BackupValidation.errors(parsed).isNotEmpty())
                assertTrue(runCatching { repo.applyImport(parsed, ImportChoices(selectedProfile = document.primaryProfilePublicId)) }.isFailure)
            }
            assertEquals(document.sportSelections, BackupCodec.decode(repo.createBackup()).sportSelections)
        } finally { db.close() }
    }
}
