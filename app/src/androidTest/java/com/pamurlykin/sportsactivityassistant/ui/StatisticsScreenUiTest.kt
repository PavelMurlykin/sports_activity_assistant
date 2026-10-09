package com.pamurlykin.sportsactivityassistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.model.*
import com.pamurlykin.sportsactivityassistant.data.repo.AppRepository
import com.pamurlykin.sportsactivityassistant.ui.screen.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@Suppress("DEPRECATION")
class StatisticsScreenUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun overviewAndSportShareFilterAndNextPageShowsRemainingRecordsNotWholeHistory() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext,AppDatabase::class.java).build()
        val repo = AppRepository(db)
        val store = ViewModelStore()
        lateinit var vm: MainViewModel
        val visible = mutableStateOf(true)
        try {
            val user = repo.localProfileId()
            val sport = db.referenceDao().getSportBySlug("football")!!.id
            val center = db.referenceDao().getComplexesForSport(sport).first().id
            val day = LocalDate.parse("2020-02-29")
            repeat(21) { repo.addCompletedTraining(user,AddCompletedTrainingInput(sport,center,day,FootballTrainingInput(0,0,0,0,null,null,null))) }
            repo.addCompletedTraining(user,AddCompletedTrainingInput(sport,center,day.minusDays(1),FootballTrainingInput(0,0,0,0,null,null,null)))
            instrumentation.runOnMainSync {
                vm = MainViewModel(repo); store.put("statistics",vm)
                vm.setStatisticsFilter(StatisticsFilter(day,day,center))
            }
            compose.setContent { if (visible.value) MaterialTheme {
                var opened by remember { mutableIntStateOf(0) }
                if (opened == 0) StatisticsScreen(vm,{ opened = it })
                else StatisticsDetailScreen(opened,vm,{ opened = 0 })
            } }
            compose.waitUntil(5000) { compose.onAllNodesWithText("21 тренировок").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Футбол").performScrollTo().performClick()
            compose.onNodeWithTag("active-statistics-period").assertTextEquals("2020-02-29 — 2020-02-29")
            compose.waitUntil(5000) { compose.onAllNodesWithText("Игр с числом игроков").fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Показано 1–20 из 21"))
            compose.onAllNodesWithText("Следующая").onFirst().performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("Показано 21–21 из 21").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithText("Следующая").onFirst().assertIsNotEnabled()
            compose.onAllNodesWithText("Предыдущая").onFirst().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("Показано 1–20 из 21").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Назад к общей статистике").performClick()
            compose.onNodeWithTag("active-statistics-period").assertTextEquals("2020-02-29 — 2020-02-29")
            compose.onNodeWithText("Сбросить").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("22 тренировок").fetchSemanticsNodes().isNotEmpty() }
        } finally {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            instrumentation.runOnMainSync { store.clear() }
            db.close()
        }
    }
}
