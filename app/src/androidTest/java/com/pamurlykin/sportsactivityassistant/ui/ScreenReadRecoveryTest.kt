package com.pamurlykin.sportsactivityassistant.ui

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.repo.AppRepository
import com.pamurlykin.sportsactivityassistant.ui.screen.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ScreenReadRecoveryTest {
    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 8000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(10)
        assertTrue(condition())
    }

    @Test fun databaseReadFailureIsSeparateFromFilesAndCanBeRetried() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, AppDatabase::class.java).build()
        val repository = AppRepository(db)
        runBlocking { repository.localProfileId() }
        // A reversible read failure in a disposable database, never in the installed user database.
        db.openHelper.writableDatabase.execSQL("ALTER TABLE trainings RENAME TO unavailable_trainings")
        val store = ViewModelStore()
        val collectors = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        lateinit var vm: MainViewModel
        try {
            instrumentation.runOnMainSync {
                vm = MainViewModel(repository)
                store.put("read", vm)
                collectors.launch { vm.statisticsReadState.collect() }
            }
            await { vm.statisticsReadState.value.failed && vm.scheduleReadState.value.failed }
            assertNull(vm.dataOperationState.value.message)
            assertFalse(vm.fileBusy.value)
            assertNull(vm.scheduleState.value)
            instrumentation.runOnMainSync { vm.reportDataError(IllegalArgumentException("Ошибка выбранного файла")) }
            db.openHelper.writableDatabase.execSQL("ALTER TABLE unavailable_trainings RENAME TO trainings")
            instrumentation.runOnMainSync { vm.retryReads() }
            await { vm.statisticsReadState.value.data != null && vm.scheduleReadState.value.data != null }
            assertEquals(0, vm.statisticsReadState.value.data!!.totalTrainings)
            assertEquals("Ошибка выбранного файла", vm.dataOperationState.value.message)
        } finally {
            instrumentation.runOnMainSync { collectors.cancel(); store.clear() }
            db.close()
        }
    }

    @Test fun restoredCalendarDateSurvivesViewModelRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, AppDatabase::class.java).build()
        val repository = AppRepository(db)
        runBlocking { repository.localProfileId() }
        val store = ViewModelStore()
        val state = SavedStateHandle()
        lateinit var first: MainViewModel
        lateinit var restored: MainViewModel
        try {
            instrumentation.runOnMainSync {
                first = MainViewModel(repository, state) { LocalDate.parse("2026-10-09") }
                store.put("calendar", first)
                first.selectDate(LocalDate.parse("2020-02-29"))
            }
            await { first.scheduleReadState.value.data?.selectedDate == LocalDate.parse("2020-02-29") }
            instrumentation.runOnMainSync {
                store.clear()
                restored = MainViewModel(repository, SavedStateHandle(mapOf("scheduleDate" to state.get<String>("scheduleDate")))) {
                    LocalDate.parse("2026-10-10")
                }
                store.put("calendar", restored)
            }
            await { restored.scheduleReadState.value.data?.selectedDate == LocalDate.parse("2020-02-29") }
        } finally { instrumentation.runOnMainSync { store.clear() }; db.close() }
    }

    @Test fun canceledExportPreparationCannotReopenAFileOperation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, AppDatabase::class.java).build()
        val repository = AppRepository(db)
        runBlocking { repository.localProfileId() }
        val store = ViewModelStore()
        lateinit var vm: MainViewModel
        try {
            instrumentation.runOnMainSync {
                vm = MainViewModel(repository)
                store.put("files", vm)
                vm.prepareExport()
                vm.cancelFile()
                assertTrue(vm.beginImportSelection())
            }
            await { vm.scheduleReadState.value.data != null }
            SystemClock.sleep(200)
            assertTrue(vm.fileBusy.value)
            assertTrue(vm.dataOperationState.value.inProgress)
            assertFalse(vm.exportReady.value)
            assertNull(vm.importPreview.value)
            instrumentation.runOnMainSync { vm.cancelFile() }
        } finally { instrumentation.runOnMainSync { store.clear() }; db.close() }
    }

    @Test fun unavailableSportProducesAnErrorRatherThanEndlessLoading() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, AppDatabase::class.java).build()
        val repository = AppRepository(db)
        runBlocking { repository.localProfileId() }
        val store = ViewModelStore()
        lateinit var vm: MainViewModel
        try {
            instrumentation.runOnMainSync { vm = MainViewModel(repository); store.put("sport", vm) }
            val result = runBlocking { withTimeout(5000) {
                vm.sportStatisticsReadStates(Int.MAX_VALUE).first { !it.loading }
            } }
            assertTrue(result.failed)
            assertNull(result.data)
            assertNull(vm.dataOperationState.value.message)
        } finally { instrumentation.runOnMainSync { store.clear() }; db.close() }
    }
}
