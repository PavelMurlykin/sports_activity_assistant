package com.pamurlykin.sportsactivityassistant.ui

import android.os.SystemClock
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.backup.BackupCodec
import com.pamurlykin.sportsactivityassistant.data.backup.ImportFiles
import com.pamurlykin.sportsactivityassistant.data.repo.AppRepository
import com.pamurlykin.sportsactivityassistant.data.repo.LargeHistoryFixture
import com.pamurlykin.sportsactivityassistant.ui.screen.MainViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@Suppress("DEPRECATION")
class LargeHistoryAcceptanceTest {
    @get:Rule val compose = createComposeRule()

    // Thresholds documented before measurement in docs/stage-11-local-testing.md.
    private suspend fun <T> measured(label: String, maxMs: Long, operation: suspend () -> T): T {
        val start = SystemClock.elapsedRealtime()
        val result = operation()
        val elapsed = SystemClock.elapsedRealtime() - start
        Log.i("SportsAcceptance", "$label=$elapsed ms; limit=$maxMs ms; API=${android.os.Build.VERSION.SDK_INT}")
        assertTrue("$label: $elapsed > $maxMs ms", elapsed <= maxMs)
        return result
    }

    @Test fun tenYearFileHistoryRestoresExactlyAndScreensMeetPredeclaredThresholds() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val names = List(2) { "acceptance-${UUID.randomUUID()}.db" }
        val databases = names.map { Room.databaseBuilder(context,AppDatabase::class.java,it).build() }
        val repo = AppRepository(databases[0])
        val restored = AppRepository(databases[1])
        val visible = mutableStateOf(true)
        val store = ViewModelStore()
        lateinit var vm: MainViewModel
        try {
            repo.localProfileId()
            restored.localProfileId()
            val bytes = BackupCodec.encode(LargeHistoryFixture.document()).toByteArray()
            assertTrue(bytes.size < ImportFiles.MAX_BYTES)
            Log.i("SportsAcceptance","fixture=${bytes.size} bytes; trainings=2000; routeRows=10000")
            measured("import-empty",30000) {
                val source = repo.prepareImport(bytes)
                val preview = repo.previewImport(source)
                assertTrue(preview.errors.joinToString(),preview.canApply)
                assertEquals(2000,repo.applyImport(source,preview.choices).importedTrainings)
            }
            val overview = measured("overview-query",2000) { repo.observeStatisticsOverview().first() }
            assertEquals(2000,overview.totalTrainings)
            assertEquals(120,overview.months.size)
            val sports = databases[0].referenceDao().getSports().associateBy { it.slug }
            val football = measured("football-query",2000) { repo.observeSportStatistics(sports.getValue("football").id).first()!! }
            assertEquals("1000",football.metrics.single { it.label == "Игры" }.value)
            assertEquals("3250 км",football.metrics.single { it.label == "Учтённая дистанция" }.value)
            val climbing = measured("climbing-query",2000) { repo.observeSportStatistics(sports.getValue("climbing").id).first()!! }
            assertEquals("10000",climbing.metrics.single { it.label == "Записи трасс" }.value)
            assertEquals("11000",climbing.metrics.single { it.label == "Попытки (с повторами)" }.value)
            val page = measured("last-page-query",2000) { repo.observeTrainingPage(sports.getValue("climbing").id,page=49).first() }
            assertEquals(1000,page.total)
            assertEquals(20,page.items.size)
            measured("calendar-query",2000) {
                repo.getScheduleMonth(repo.localProfileId(),YearMonth.of(2016,1),LocalDate.of(2016,1,17),LocalDate.of(2026,10,9))
            }
            val backup = measured("backup",15000) { repo.createBackup() }.toByteArray()
            val snapshot = BackupCodec.decode(backup.toString(Charsets.UTF_8))
            measured("restore-empty",30000) { assertEquals(2000,restored.importData(backup,restored.localProfileId()).importedTrainings) }
            measured("repeat-import",30000) {
                val result = restored.importData(backup,restored.localProfileId())
                assertEquals(0,result.importedTrainings)
                assertEquals(2000,result.skippedTrainings)
            }
            val roundtrip = BackupCodec.decode(restored.createBackup())
            assertEquals(snapshot.trainings.map { it.copy(profilePublicId=null) }.sortedBy { it.publicId },
                roundtrip.trainings.map { it.copy(profilePublicId=null) }.sortedBy { it.publicId })
            assertEquals(snapshot.plannedTrainings.map { it.copy(profilePublicId=null) }.sortedBy { it.publicId },
                roundtrip.plannedTrainings.map { it.copy(profilePublicId=null) }.sortedBy { it.publicId })
            assertEquals(snapshot.recurrenceRules.map { it.copy(profilePublicId=null) },roundtrip.recurrenceRules.map { it.copy(profilePublicId=null) })
            databases.forEach { db -> db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) } }
            instrumentation.runOnMainSync { vm = MainViewModel(repo); store.put("large-history",vm) }
            compose.setContent { if (visible.value) MaterialTheme { SportsActivityApp(vm) } }
            compose.waitUntil(5000) { compose.onAllNodesWithText("Статистика").fetchSemanticsNodes().isNotEmpty() }
            measured("overview-screen",5000) {
                compose.onNodeWithText("Статистика").performClick()
                compose.waitUntil(5000) { compose.onAllNodesWithText("2000").fetchSemanticsNodes().isNotEmpty() }
            }
            measured("football-screen",5000) {
                compose.onNodeWithText("Футбол").performScrollTo().performClick()
                compose.waitUntil(5000) { compose.onAllNodesWithText("Игр с числом игроков").fetchSemanticsNodes().isNotEmpty() }
            }
            compose.onNodeWithContentDescription("Назад к общей статистике").performClick()
            measured("climbing-screen",5000) {
                compose.onNodeWithText("Скалолазание").performScrollTo().performClick()
                compose.waitUntil(5000) { compose.onAllNodesWithText("Записи трасс").fetchSemanticsNodes().isNotEmpty() }
            }
            compose.onNodeWithContentDescription("Назад к общей статистике").performClick()
            measured("calendar-screen",5000) {
                compose.onNodeWithText("Расписание").performClick()
                compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Добавить тренировку").fetchSemanticsNodes().isNotEmpty() }
            }
        } finally {
            compose.runOnIdle { visible.value=false }
            compose.waitForIdle()
            instrumentation.runOnMainSync { store.clear() }
            databases.forEach { it.close() }
            names.forEach { context.deleteDatabase(it) }
        }
    }
}
