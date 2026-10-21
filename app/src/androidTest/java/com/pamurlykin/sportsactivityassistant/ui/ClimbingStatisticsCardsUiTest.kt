package com.pamurlykin.sportsactivityassistant.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.model.*
import com.pamurlykin.sportsactivityassistant.ui.components.ClimbingStatisticsCards
import com.pamurlykin.sportsactivityassistant.ui.components.SportEditors
import com.pamurlykin.sportsactivityassistant.ui.theme.SportsActivityTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

@Suppress("DEPRECATION")
class ClimbingStatisticsCardsUiTest {
    @get:Rule val compose = createComposeRule()
    private val statistics = ClimbingStatisticsUiModel(
        listOf(ClimbingSpeedStatistics(SpeedCourse.STANDARD_15M, ClimbingAttemptCounts(20, 15), 12, 8530, 9725.0)),
        ClimbingDisciplineStatistics(ClimbingWorkoutType.DIFFICULTY, ClimbingAttemptCounts(14, 9), listOf(
            ClimbingGradeStatistics("6a", ClimbingAttemptCounts(4, 3)), ClimbingGradeStatistics("6a+", ClimbingAttemptCounts(6, 5)),
            ClimbingGradeStatistics("6b", ClimbingAttemptCounts(4, 1)))),
        ClimbingDisciplineStatistics(ClimbingWorkoutType.BOULDERING, ClimbingAttemptCounts(10, 4), listOf(
            ClimbingGradeStatistics("6A", ClimbingAttemptCounts(5, 3)), ClimbingGradeStatistics("6A+", ClimbingAttemptCounts(5, 1)))))

    @Test fun requestedCountsPercentagesTimesAndOrderedGradesAreVisible() {
        compose.setContent { SportsActivityTheme(ThemeMode.LIGHT) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()).padding(16.dp)) { ClimbingStatisticsCards(statistics) }
        } }
        compose.onAllNodesWithText("75%", substring = true).assertCountEquals(2)
        compose.onNodeWithText("8,53", substring = true).assertExists()
        compose.onNodeWithText("9,725", substring = true).assertExists()
        compose.onNodeWithTag("climbing-difficulty").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("6a+", substring = true).assertExists()
        screenshot("climbing-statistics-grades.png")
        compose.onNodeWithTag("climbing-bouldering").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("6A+", substring = true).assertExists()
        compose.onNodeWithTag("climbing-speed").performScrollTo()
        screenshot("climbing-statistics-speed.png")
    }

    @Test fun emptyLargeFontDarkStatisticsShowNoInventedTimesOrPercentages() {
        compose.setContent { SportsActivityTheme(ThemeMode.DARK) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                Column(Modifier.width(320.dp).fillMaxHeight().background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState()).padding(16.dp)) {
                    ClimbingStatisticsCards(ClimbingStatisticsUiModel(emptyList(),
                        statistics.difficulty.copy(counts = ClimbingAttemptCounts(), grades = emptyList()),
                        statistics.bouldering.copy(counts = ClimbingAttemptCounts(), grades = emptyList())))
                }
            }
        } }
        compose.onAllNodesWithText("—", substring = true).assertCountEquals(5)
        compose.onNodeWithTag("climbing-difficulty").performScrollTo()
        compose.onAllNodesWithText("Успех, %").onFirst().performScrollTo().assertIsDisplayed()
        screenshot("climbing-statistics-dark-large.png")
    }

    @Test fun decimalSpeedTimeSurvivesRestorationAndIsRemovedWhenAttemptFails() {
        val initial = AddCompletedTrainingInput(2, 1, LocalDate.of(2020, 1, 1), climbingRoutes = listOf(
            ClimbingRouteInput(ClimbingWorkoutType.SPEED, "", true, speedCourse = "standard_15m", speedTimeMillis = 8530)))
        var captured: AddCompletedTrainingInput? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent { SportsActivityTheme {
            val draft = requireNotNull(SportEditors.find("climbing")).rememberDraft(initial)
            Column(Modifier.fillMaxSize()) {
                Button(onClick = { captured = draft.input(2, 1, initial.date) }) { Text("Проверить ввод") }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) { draft.Content() }
            }
        } }
        compose.onNodeWithTag("route-0-time").performScrollTo().performTextReplacement("7,125")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("route-0-time").performScrollTo().assertTextContains("7,125")
        compose.onNodeWithText("Проверить ввод").performClick()
        compose.runOnIdle { assertEquals(7125L, captured!!.climbingRoutes.single().speedTimeMillis) }
        compose.onNodeWithText("Не пройдена").performScrollTo().performClick()
        compose.onNodeWithTag("route-0-time").assertDoesNotExist()
        compose.onNodeWithText("Проверить ввод").performClick()
        compose.runOnIdle { assertNull(captured!!.climbingRoutes.single().speedTimeMillis) }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), name)
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
