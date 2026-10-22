package com.pamurlykin.sportsactivityassistant.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.model.*
import com.pamurlykin.sportsactivityassistant.data.repo.AppRepository
import com.pamurlykin.sportsactivityassistant.ui.screen.MainViewModel
import com.pamurlykin.sportsactivityassistant.ui.screen.StatisticsScreen
import com.pamurlykin.sportsactivityassistant.ui.theme.SportsActivityTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class StatisticsOverviewUiTest {
    @get:Rule val compose = createComposeRule()
    private val day = mutableStateOf(LocalDate.of(2020, 4, 1))
    private var opened = 0
    private var added = 0

    private fun fixture(large: Boolean = false, test: (MainViewModel, Int) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, AppDatabase::class.java).build()
        val repo = AppRepository(db)
        val football = runBlocking {
            val user = repo.localProfileId()
            val f = db.referenceDao().getSportBySlug("football")!!.id
            val c = db.referenceDao().getSportBySlug("climbing")!!.id
            val center = repo.saveSportsCenter(SaveSportsCenterInput(name = "Зал", city = null, sportIds = setOf(f, c)))
            listOf(LocalDate.of(2020, 3, 1), LocalDate.of(2020, 2, 1)).forEach {
                repo.addCompletedTraining(user, AddCompletedTrainingInput(f, center, it, FootballTrainingInput(0, 0, 0, 0, null, null, null)))
            }
            repo.addCompletedTraining(user, AddCompletedTrainingInput(c, center, LocalDate.of(2020, 1, 1),
                climbingRoutes = listOf(ClimbingRouteInput(ClimbingWorkoutType.DIFFICULTY, "6a", true))))
            repo.addPlannedTraining(user, AddPlannedTrainingInput(f, center, day.value.plusDays(2), false, 1, null))
            f
        }
        val store = ViewModelStore()
        lateinit var vm: MainViewModel
        val visible = mutableStateOf(true)
        try {
            instrumentation.runOnMainSync { vm = MainViewModel(repo) { day.value }; store.put("overview", vm) }
            compose.setContent { if (visible.value) SportsActivityTheme(if (large) ThemeMode.DARK else ThemeMode.LIGHT) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 1.6f else 1f)) {
                    Box(if (large) Modifier.width(320.dp).fillMaxHeight() else Modifier.fillMaxSize()) {
                    StatisticsScreen(vm, { opened = it }, { added = it })
                    }
                }
            } }
            compose.waitUntil(5000) { compose.onAllNodesWithTag("statistics-sport-football").fetchSemanticsNodes().isNotEmpty() }
            test(vm, football)
        } finally {
            compose.runOnIdle { visible.value = false; store.clear() }
            db.close()
        }
    }

    @Test fun cardsOnlyShowCountsAndDatesAndMonthsUseTwoColumnsIncludingAnOddLastMonth() = fixture { _, f ->
        compose.onNodeWithText("Всего завершённых тренировок").assertDoesNotExist()
        compose.onNodeWithText("Тренировок: 2", substring = true).assertExists()
        compose.onNodeWithTag("sport-last-$f", useUnmergedTree = true).onChildren().filter(hasText("01.03.2020")).assertCountEquals(1)
        compose.onNodeWithTag("sport-next-$f", useUnmergedTree = true).onChildren().filter(hasText("03.04.2020")).assertCountEquals(1)
        compose.onNodeWithContentDescription("Записать тренировку: Футбол").performClick()
        compose.runOnIdle { assertEquals(f, added) }
        compose.onNodeWithTag("statistics-sport-football").performClick()
        compose.runOnIdle { assertEquals(f, opened) }
        screenshot("statistics-overview-cards.png")
        compose.onNodeWithTag("statistics-overview").performScrollToNode(hasTestTag("statistics-month-2020-03"))
        val march = compose.onNodeWithTag("statistics-month-2020-03").fetchSemanticsNode().boundsInRoot
        val february = compose.onNodeWithTag("statistics-month-2020-02").fetchSemanticsNode().boundsInRoot
        assertEquals(march.top, february.top, 1f)
        assertTrue(march.right < february.left)
        compose.onNodeWithTag("statistics-overview").performScrollToNode(hasTestTag("statistics-month-2020-01"))
        val january = compose.onNodeWithTag("statistics-month-2020-01").fetchSemanticsNode().boundsInRoot
        assertEquals(march.left, january.left, 1f)
        screenshot("statistics-overview-months.png")
    }

    @Test fun passingThePlannedDayClearsItsDateAndLargeFontKeepsTheTwoColumnGrid() = fixture(large = true) { vm, f ->
        compose.runOnIdle { day.value = LocalDate.of(2020, 4, 4); vm.refreshToday() }
        compose.waitUntil(5000) {
            compose.onAllNodes(hasText("—") and hasAnyAncestor(hasTestTag("sport-next-$f")), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("statistics-overview").performScrollToNode(hasTestTag("statistics-month-2020-03"))
        val march = compose.onNodeWithTag("statistics-month-2020-03").fetchSemanticsNode().boundsInRoot
        val february = compose.onNodeWithTag("statistics-month-2020-02").fetchSemanticsNode().boundsInRoot
        assertEquals(march.top, february.top, 1f)
        assertTrue(march.right < february.left)
        screenshot("statistics-overview-dark-large.png")
    }

    private fun screenshot(name: String) {
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), name)
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
