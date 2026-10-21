package com.pamurlykin.sportsactivityassistant.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pamurlykin.sportsactivityassistant.data.model.*
import com.pamurlykin.sportsactivityassistant.ui.components.AddCompletedTrainingDialog
import com.pamurlykin.sportsactivityassistant.ui.theme.SportsActivityTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@Suppress("DEPRECATION")
class VisualRefreshUiTest {
    @get:Rule val compose = createComposeRule()
    private val football = SportSummaryUiModel(1, "football", "Футбол", 0)
    private val climbing = SportSummaryUiModel(2, "climbing", "Скалолазание", 0)
    private val sports = listOf(football, climbing)
    private val center = SportsCenterUiModel(7, "Спортивный центр", null, sports)

    @Test fun scoreAndPersonalCardsFollowMockupAndDigitSwipesKeepTheOtherDigit() {
        var saved: AddCompletedTrainingInput? = null
        compose.setContent { SportsActivityTheme {
            AddCompletedTrainingDialog(sports, LocalDate.now(), listOf(center), {}, { input, _ -> saved = input })
        } }
        compose.onNodeWithTag("football-0").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(13f) }
        compose.onNodeWithTag("football-0").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "13"))
        compose.onNodeWithTag("football-0-units", useUnmergedTree = true).performTouchInput {
            swipe(start = androidx.compose.ui.geometry.Offset(width / 2f, height * 0.75f), end = androidx.compose.ui.geometry.Offset(width / 2f, height * 0.38f), durationMillis = 700)
        }
        compose.waitForIdle()
        val selected = compose.onNodeWithTag("football-0").fetchSemanticsNode().config[SemanticsProperties.StateDescription].toInt()
        assertTrue("Units wheel must advance while the tens stay at 1: $selected", selected in 14..19)
        compose.onNodeWithTag("football-personal-2").performScrollTo()
        val left = compose.onNodeWithTag("football-personal-2").fetchSemanticsNode().positionInRoot
        val right = compose.onNodeWithTag("football-personal-3").fetchSemanticsNode().positionInRoot
        assertTrue(left.x < right.x); assertEquals(left.y, right.y, 1f)
        compose.onNodeWithText("Личная статистика").assertExists()
        compose.onNodeWithText("Счёт").performScrollTo()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "compact-football-form.png")
            .outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithText("Сохранить").performClick()
        compose.runOnIdle { assertEquals(selected, requireNotNull(saved).football!!.teamGoalsScored) }
    }

    @Test fun minutesStepByFiveAndManualValuesRemainAvailableForBothSports() {
        var saved: AddCompletedTrainingInput? = null
        compose.setContent { SportsActivityTheme {
            AddCompletedTrainingDialog(sports, LocalDate.now(), listOf(center), {}, { input, _ -> saved = input })
        } }
        compose.onNodeWithTag("football-6").performScrollTo()
        compose.onNodeWithContentDescription("Увеличить: Время игры, мин").performClick().performClick()
        compose.onNodeWithTag("football-6").assertTextContains("10").performTextReplacement("17")
        compose.onNodeWithContentDescription("Увеличить: Время игры, мин").performClick()
        compose.onNodeWithTag("football-6").assertTextContains("22")
        compose.onNodeWithContentDescription("Уменьшить: Время игры, мин").performClick()
        compose.onNodeWithTag("football-6").assertTextContains("17")
        compose.onNodeWithTag("training-sport").performScrollTo().performClick()
        compose.onNodeWithText("Скалолазание").performClick()
        compose.onNodeWithTag("training-duration").performScrollTo()
        compose.onNodeWithContentDescription("Увеличить: Длительность, мин").performClick()
        compose.onNodeWithTag("training-duration").assertTextContains("5").performTextReplacement("17")
        compose.onNodeWithContentDescription("Увеличить: Длительность, мин").performClick()
        compose.onNodeWithTag("training-duration").assertTextContains("22")
        compose.onNodeWithText("Сохранить").performClick()
        compose.runOnIdle { assertEquals(22, requireNotNull(saved).durationMinutes) }
    }

    @Test fun dateIsACalendarFieldWithoutAnEditableTextAction() {
        compose.setContent { SportsActivityTheme {
            AddCompletedTrainingDialog(sports, LocalDate.now(), listOf(center), {}, { _, _ -> })
        } }
        compose.onNodeWithTag("training-date").performScrollTo().assertHasClickAction().assert(!hasSetTextAction())
        compose.onNodeWithTag("training-date-picker").assertContentDescriptionEquals("Выбрать дату").performClick()
        compose.onNodeWithText("Выбрать").assertExists().performClick()
        compose.onNodeWithTag("training-date").assertTextContains(LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.uuuu")))
        compose.onNodeWithTag("training-sport").performScrollTo().performClick()
        compose.onNodeWithText("Скалолазание").performClick()
        compose.onNodeWithTag("training-date").assertHasClickAction().assert(!hasSetTextAction())
    }
}
