package com.pamurlykin.sportsactivityassistant.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.repo.AppRepository
import com.pamurlykin.sportsactivityassistant.ui.screen.MainViewModel
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking

class CalendarViewModelTest {
    private fun await(condition: () -> Boolean) {
        val limit = SystemClock.elapsedRealtime() + 5000
        while (!condition() && SystemClock.elapsedRealtime() < limit) SystemClock.sleep(10)
        assertTrue(condition())
    }
    private fun fixture(test: (MainViewModel, (LocalDate) -> Unit) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext,AppDatabase::class.java).build()
        val repo = AppRepository(db)
        runBlocking { repo.localProfileId() }
        val store = ViewModelStore()
        var now = LocalDate.parse("2020-02-29")
        lateinit var vm: MainViewModel
        try {
            instrumentation.runOnMainSync { vm = MainViewModel(repo) { now }; store.put("calendar",vm) }
            await { vm.scheduleState.value != null }
            test(vm) { value -> instrumentation.runOnMainSync { now = value; vm.refreshToday() } }
        } finally { instrumentation.runOnMainSync { store.clear() }; db.close() }
    }
    @Test fun rapidMonthSwitchPublishesOnlyLastSelectionAcrossYearBoundary() {
        fixture { vm,_ ->
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.runOnMainSync {
                repeat(30) { vm.nextMonth() }
                repeat(19) { vm.previousMonth() }
                vm.selectDate(LocalDate.parse("2024-02-29"))
            }
            await { vm.scheduleState.value?.selectedDate == LocalDate.parse("2024-02-29") }
            SystemClock.sleep(100)
            assertEquals(YearMonth.of(2024,2),vm.scheduleState.value!!.month)
            assertTrue(vm.scheduleState.value!!.days.any { it.date == LocalDate.parse("2024-02-29") })
        }
    }
    @Test fun midnightMovesTodaySelectionAcrossLeapMonthButKeepsBrowsedDate() {
        fixture { vm,changeTime ->
            changeTime(LocalDate.parse("2020-03-01"))
            await { vm.scheduleState.value?.selectedDate == LocalDate.parse("2020-03-01") }
            assertTrue(vm.scheduleState.value!!.days.single { it.isToday }.date == LocalDate.parse("2020-03-01"))
            InstrumentationRegistry.getInstrumentation().runOnMainSync { vm.selectDate(LocalDate.parse("2019-12-31")) }
            await { vm.scheduleState.value?.selectedDate == LocalDate.parse("2019-12-31") }
            changeTime(LocalDate.parse("2020-03-02"))
            await { vm.scheduleState.value?.selectedDate == LocalDate.parse("2019-12-31") }
            assertEquals(YearMonth.of(2019,12),vm.scheduleState.value!!.month)
        }
    }
}
