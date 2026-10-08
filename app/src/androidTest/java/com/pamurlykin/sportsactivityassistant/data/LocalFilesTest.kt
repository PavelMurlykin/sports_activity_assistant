package com.pamurlykin.sportsactivityassistant.data

import android.net.Uri
import androidx.room.Room
import androidx.lifecycle.ViewModelStore
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.backup.LocalFiles
import com.pamurlykin.sportsactivityassistant.data.repo.AppRepository
import com.pamurlykin.sportsactivityassistant.ui.screen.MainViewModel
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocalFilesTest {
    @Test fun fileWriteTruncatesAndVerifiesReadbackAndMissingProviderIsNotSuccess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("sports-backup-test-", ".json", context.cacheDir)
        try {
            val uri = Uri.fromFile(file)
            LocalFiles.writeVerified(context.contentResolver, uri, "long old contents".toByteArray())
            LocalFiles.writeVerified(context.contentResolver, uri, "{}".toByteArray())
            assertTrue(LocalFiles.read(context.contentResolver, uri).contentEquals("{}".toByteArray()))
            val failure = runCatching { LocalFiles.writeVerified(context.contentResolver, Uri.parse("content://invalid.test/no-provider"), "{}".toByteArray()) }.exceptionOrNull()
            assertNotNull(failure); assertTrue(failure!!.message!!.contains("Копия не подтверждена"))
        } finally { file.delete() }
    }

    @Test fun repeatedFileOperationIsBlockedAndPickerCancelReportsNoSuccess() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val store = ViewModelStore()
        val repository = AppRepository(db)
        runBlocking { repository.localProfileId() }
        lateinit var vm: MainViewModel
        try {
            instrumentation.runOnMainSync {
                vm = MainViewModel(repository)
                store.put("main", vm)
                assertTrue(vm.beginImportSelection()); assertFalse(vm.beginImportSelection())
                vm.prepareExport(); assertFalse(vm.exportReady.value)
                vm.importSelected(context.contentResolver, null)
                assertFalse(vm.fileBusy.value); assertNull(vm.dataOperationState.value.message)
                assertTrue(vm.beginImportSelection()); vm.cancelFile()
                assertFalse(vm.fileBusy.value)
            }
            await { vm.scheduleState.value != null }
        } finally { instrumentation.runOnMainSync { store.clear() }; db.close() }
    }

    @Test fun completedOrFailedExportReleasesGuardAndCanceledExportHasNoSuccessMessage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val store = ViewModelStore()
        val repository = AppRepository(db)
        runBlocking { repository.localProfileId() }
        val file = File.createTempFile("sports-export-state-", ".json", context.cacheDir)
        lateinit var vm: MainViewModel
        try {
            instrumentation.runOnMainSync { vm = MainViewModel(repository); store.put("main", vm) }
            await { vm.scheduleState.value != null }
            instrumentation.runOnMainSync { vm.prepareExport() }
            await { vm.exportReady.value }
            instrumentation.runOnMainSync { vm.exportLaunched(); vm.exportSelected(context.contentResolver, Uri.fromFile(file)) }
            await { !vm.fileBusy.value }
            assertTrue(vm.dataOperationState.value.message!!.contains("проверена"))
            assertTrue(file.readText().contains("\"schemaVersion\": 4"))
            instrumentation.runOnMainSync { vm.prepareExport() }
            await { vm.exportReady.value }
            instrumentation.runOnMainSync { vm.exportLaunched(); vm.exportSelected(context.contentResolver, null) }
            assertFalse(vm.fileBusy.value); assertNull(vm.dataOperationState.value.message)
            instrumentation.runOnMainSync { vm.prepareExport() }
            await { vm.exportReady.value }
            instrumentation.runOnMainSync { vm.exportLaunched(); vm.exportSelected(context.contentResolver, Uri.parse("content://invalid.test/no-provider")) }
            await { !vm.fileBusy.value }
            assertTrue(vm.dataOperationState.value.isError)
        } finally { instrumentation.runOnMainSync { store.clear() }; db.close(); file.delete() }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(10)
        assertTrue("Фоновая операция не завершилась", condition())
    }
}
