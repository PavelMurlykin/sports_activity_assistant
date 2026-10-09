package com.pamurlykin.sportsactivityassistant.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.model.*
import com.pamurlykin.sportsactivityassistant.data.repo.AppRepository
import com.pamurlykin.sportsactivityassistant.ui.screen.MainViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StatisticsViewModelTest {
    @Test fun appliedFilterIsSharedByOverviewSportAndPageAndSurvivesViewModelRecreation() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext,AppDatabase::class.java).build()
        val repo = AppRepository(db)
        val store = ViewModelStore()
        val state = SavedStateHandle()
        lateinit var vm: MainViewModel
        try {
            val user = repo.localProfileId()
            val sport = db.referenceDao().getSportBySlug("football")!!.id
            val center = db.referenceDao().getComplexesForSport(sport).first().id
            val day = LocalDate.parse("2020-02-29")
            repo.addCompletedTraining(user,AddCompletedTrainingInput(sport,center,day,FootballTrainingInput(0,0,0,0,null,null,null)))
            val filter = StatisticsFilter(day,day,center)
            instrumentation.runOnMainSync {
                vm = MainViewModel(repo,state); store.put("statistics",vm)
                repeat(20) { vm.setStatisticsFilter(StatisticsFilter(day.minusDays(it + 1L),day.minusDays(it + 1L))) }
                vm.setStatisticsFilter(filter)
            }
            withTimeout(5000) {
                assertEquals(1,vm.statisticsState.first { it.filter == filter && it.sports.isNotEmpty() }.totalTrainings)
                assertEquals(filter,vm.statisticsForSport(sport).first { it != null }!!.filter)
                assertEquals(1,vm.trainingsPageForSport(sport,0).first { it != null }!!.total)
            }
            // Same primitive payload the SavedStateRegistry restores after process recreation.
            val restored = SavedStateHandle(state.keys().associateWith { state.get<Any>(it) })
            instrumentation.runOnMainSync { store.clear(); vm = MainViewModel(repo,restored); store.put("restored",vm) }
            assertEquals(filter,vm.statisticsFilter.value)
            withTimeout(5000) { assertEquals(1,vm.statisticsState.first { it.sports.isNotEmpty() }.totalTrainings) }
            instrumentation.runOnMainSync { vm.setStatisticsFilter(StatisticsFilter()) }
            withTimeout(5000) { assertEquals(StatisticsFilter(),vm.statisticsState.first { it.filter == StatisticsFilter() }.filter) }
        } finally { instrumentation.runOnMainSync { store.clear() }; db.close() }
    }
}
