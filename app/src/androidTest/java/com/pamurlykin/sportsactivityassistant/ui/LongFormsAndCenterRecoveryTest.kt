package com.pamurlykin.sportsactivityassistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.pamurlykin.sportsactivityassistant.data.model.*
import com.pamurlykin.sportsactivityassistant.ui.components.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@Suppress("DEPRECATION")
class LongFormsAndCenterRecoveryTest {
    @get:Rule val compose = createComposeRule()
    private val sport = SportSummaryUiModel(1, "football", "Футбол", 0)

    @Test fun landscapeWholeDialogScrollRestoresLastFieldAndReachesSaveAction() {
        val restoration = StateRestorationTester(compose)
        var saved: AddCompletedTrainingInput? = null
        restoration.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { orientation = Configuration.ORIENTATION_LANDSCAPE }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalDensity provides Density(density.density, 2f)) { MaterialTheme {
                AddCompletedTrainingDialog(listOf(sport), LocalDate.parse("2026-10-09"),
                    listOf(SportsCenterUiModel(7, "Площадка", null, listOf(sport))), {}, { input, _ -> saved = input })
            } }
        }
        compose.onNodeWithTag("football-6").performScrollTo().performTextReplacement("60")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("football-6").performScrollTo().assertTextContains("60")
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(60, saved!!.football!!.durationMinutes) }
    }

    @Test fun pendingCenterSaveRestoresBusyStateAndRequestId() {
        val restoration = StateRestorationTester(compose)
        val pending = CompletableDeferred<Long>()
        val requests = mutableListOf<SaveSportsCenterInput>()
        var completed = 0
        restoration.setContent { MaterialTheme {
            SportsCenterDialog(null, listOf(sport), {}, { requests += it; pending.await() }, { completed++ }, 1)
        } }
        compose.onNodeWithTag("center-name").performTextInput("Центр после поворота")
        compose.onNodeWithText("Сохранить").performClick()
        compose.onNodeWithText("Сохранение…").assertIsNotEnabled()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Сохранение…").assertIsNotEnabled()
        compose.onNodeWithTag("center-name").assertTextContains("Центр после поворота")
        compose.runOnIdle {
            assertEquals(2, requests.size) // new composition reattaches to the same ViewModel request
            assertNotNull(requests.first().requestId)
            assertEquals(requests.first(), requests.last())
            pending.complete(7)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, completed) }
    }

    @Test fun footballAtDoubleFontCanReachLastFieldCorrectAndSave() {
        var saved: AddCompletedTrainingInput? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) { MaterialTheme {
                AddCompletedTrainingDialog(listOf(sport), LocalDate.parse("2026-10-09"),
                    listOf(SportsCenterUiModel(7, "Площадка с длинным названием", null, listOf(sport))), {}, { input, _ -> saved = input })
            } }
        }
        compose.onNodeWithTag("football-6").performScrollTo().performTextReplacement("60")
        compose.onNodeWithText("Сохранить").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(60, saved!!.football!!.durationMinutes) }
    }

    @Test fun climbingAtDoubleFontCanAddSecondRouteAndSaveBoth() {
        val climbing = sport.copy(id = 2, slug = "climbing", title = "Скалолазание")
        var saved: AddCompletedTrainingInput? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) { MaterialTheme {
                AddCompletedTrainingDialog(listOf(climbing), LocalDate.parse("2026-10-09"),
                    listOf(SportsCenterUiModel(7, "Скалодром", null, listOf(climbing))), {}, { input, _ -> saved = input })
            } }
        }
        compose.onNodeWithText("+ Добавить трассу").performScrollTo().performClick()
        compose.onNodeWithTag("route-1-repeat").performScrollTo().performTextReplacement("3")
        compose.onNodeWithText("Сохранить").assertIsDisplayed().performClick()
        compose.runOnIdle { val result = requireNotNull(saved); assertEquals(2, result.climbingRoutes.size); assertEquals(3, result.climbingRoutes.last().repeatCount) }
    }
}
