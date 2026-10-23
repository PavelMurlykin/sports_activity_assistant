package com.pamurlykin.sportsactivityassistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import com.pamurlykin.sportsactivityassistant.data.model.*
import com.pamurlykin.sportsactivityassistant.ui.components.AddCompletedTrainingDialog
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@Suppress("DEPRECATION")
class TrainingTimerUiTest {
    @get:Rule val compose = createComposeRule()
    private val football = SportSummaryUiModel(1, "football", "Футбол", 0)
    private val climbing = SportSummaryUiModel(2, "climbing", "Скалолазание", 0)
    private val sports = listOf(football, climbing)
    private val center = SportsCenterUiModel(7, "Центр", null, sports)
    private val now = AtomicLong(10_000L)

    private fun advance(millis: Long) {
        compose.runOnIdle { now.addAndGet(millis) }
        compose.mainClock.advanceTimeBy(300L)
        compose.waitForIdle()
    }

    @Test fun footballTimerRoundsResumesAndSavesTheDisplayedDuration() {
        var saved: AddCompletedTrainingInput? = null
        compose.setContent { MaterialTheme {
            AddCompletedTrainingDialog(sports, LocalDate.now(), listOf(center), {}, { input, _ -> saved = input }, timerClock = now::get)
        } }
        compose.onNodeWithTag("training-timer-start").performScrollTo().performClick()
        advance(29_999L)
        compose.onNodeWithTag("football-6").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithTag("training-timer-value").assertTextEquals("00:00:29")
        advance(1L)
        compose.onNodeWithTag("football-6").assertTextContains("1")
        compose.onNodeWithTag("training-timer-stop").performClick()
        advance(90_000L)
        compose.onNodeWithTag("training-timer-value").assertTextEquals("00:00:30")
        compose.onNodeWithTag("training-timer-start").performClick()
        advance(60_000L)
        compose.onNodeWithTag("football-6").assertTextContains("2")
        compose.onNodeWithText("Сохранить").performClick()
        compose.runOnIdle {
            val result = requireNotNull(saved)
            assertEquals(2, result.football!!.durationMinutes)
            assertNull(result.durationMinutes)
        }
    }

    @Test fun manualDurationPausesAndEachSportRetainsItsTimerAcrossRestoration() {
        val restoration = StateRestorationTester(compose)
        var saved: AddCompletedTrainingInput? = null
        restoration.setContent { MaterialTheme {
            AddCompletedTrainingDialog(sports, LocalDate.now(), listOf(center), {}, { input, _ -> saved = input }, timerClock = now::get)
        } }
        compose.onNodeWithTag("football-6").performTextReplacement("12")
        compose.onNodeWithTag("training-timer-value").assertTextEquals("00:12:00")
        compose.onNodeWithTag("training-timer-start").performClick()
        advance(61_000L)
        compose.onNodeWithTag("football-6").performTextReplacement("7")
        compose.onNodeWithTag("training-timer-stop").assertIsNotEnabled()
        compose.onNodeWithTag("training-timer-value").assertTextEquals("00:07:00")
        compose.onNodeWithTag("training-sport").performScrollTo().performClick()
        compose.onNodeWithText("Скалолазание").performClick()
        compose.onNodeWithTag("training-timer-value").assertTextEquals("00:00:00")
        compose.onNodeWithTag("training-duration").performTextReplacement("5")
        compose.onNodeWithTag("training-timer-start").performClick()
        advance(90_000L)
        compose.onNodeWithTag("training-duration").assertTextContains("7")
        compose.runOnIdle { now.addAndGet(30_000L) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("training-timer-value").assertTextEquals("00:07:00")
        compose.onNodeWithText("Сохранить").performClick()
        compose.runOnIdle {
            val result = requireNotNull(saved)
            assertEquals(7, result.durationMinutes)
            assertNull(result.football)
        }
        compose.onNodeWithTag("training-sport").performScrollTo().performClick()
        compose.onNodeWithText("Футбол").performClick()
        compose.onNodeWithTag("football-6").assertTextContains("7")
        compose.onNodeWithTag("training-timer-reset").performClick()
        compose.onNodeWithTag("training-timer-value").assertTextEquals("00:00:00")
        compose.onNodeWithText("Сохранить").performClick()
        compose.runOnIdle { assertNull(requireNotNull(saved).football!!.durationMinutes) }
    }

    @Test fun invalidManualDurationCannotBeSilentlyReplacedOrSavedByTheTimer() {
        var saved = false
        compose.setContent { MaterialTheme {
            AddCompletedTrainingDialog(sports, LocalDate.now(), listOf(center), {}, { _, _ -> saved = true }, timerClock = now::get)
        } }
        compose.onNodeWithTag("football-6").performTextReplacement("0")
        compose.onNodeWithTag("training-timer-start").assertIsNotEnabled()
        compose.onNodeWithText("Сохранить").performClick()
        compose.runOnIdle { assertFalse(saved) }
        compose.onNodeWithTag("football-6").assertTextContains("0")
        compose.onNodeWithTag("training-timer-reset").performClick()
        compose.onNodeWithText("Сохранить").performClick()
        compose.runOnIdle { assertTrue(saved) }
    }
}
