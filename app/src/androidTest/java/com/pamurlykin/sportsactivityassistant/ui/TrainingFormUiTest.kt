package com.pamurlykin.sportsactivityassistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.pamurlykin.sportsactivityassistant.data.model.*
import com.pamurlykin.sportsactivityassistant.ui.components.AddCompletedTrainingDialog
import kotlinx.coroutines.CompletableDeferred
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

// The official StateRestorationTester still uses the original ComposeContentTestRule API.
@Suppress("DEPRECATION")
class TrainingFormUiTest {
    @get:Rule val compose = createComposeRule()
    private val football = SportSummaryUiModel(1, "football", "Футбол", 0)
    private val climbing = SportSummaryUiModel(2, "climbing", "Скалолазание", 0)
    private val sports = listOf(football, climbing)
    private val center = SportsCenterUiModel(7, "Общий центр", null, sports)
    private val date = LocalDate.parse("2020-01-01")

    @Test fun numericErrorsStayInFormAndCorrectedFieldsSaveWithoutReentry() {
        var saved: AddCompletedTrainingInput? = null
        compose.setContent { MaterialTheme {
            AddCompletedTrainingDialog(sports, date, listOf(center), {}, { input, _ -> saved = input })
        } }
        compose.onNodeWithTag("football-0").performScrollTo().performTextReplacement("2")
        compose.onNodeWithTag("football-2").performScrollTo().performTextReplacement("3")
        compose.onNodeWithTag("football-4").performScrollTo().performTextReplacement("7,35")
        compose.onNodeWithText("Сохранить").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Личные голы не могут превышать счёт команды").performScrollTo().assertExists()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithTag("football-2").performScrollTo().performTextReplacement("1")
        compose.onNodeWithText("Сохранить").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { val result = requireNotNull(saved); assertEquals(2, result.football!!.teamGoalsScored); assertEquals("7.35".toBigDecimal(), result.football.distanceKm) }
    }

    @Test fun storageErrorKeepsDraftAndRetryUsesSameRequestId() {
        var calls = 0
        val tokens = mutableListOf<String>()
        var saved = false
        compose.setContent { MaterialTheme {
            AddCompletedTrainingDialog(sports, date, listOf(center), {}, { _, token ->
                tokens += token; calls++
                if (calls == 1) throw IllegalStateException("Ошибка записи")
            }, onSaved = { saved = true })
        } }
        compose.onNodeWithTag("football-0").performScrollTo().performTextReplacement("3")
        compose.onNodeWithText("Сохранить").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Ошибка записи").performScrollTo().assertExists()
        compose.onNodeWithTag("football-0").performScrollTo().assertTextContains("3")
        compose.onNodeWithText("Сохранить").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(2, calls); assertEquals(tokens[0], tokens[1]); assertTrue(saved) }
    }

    @Test fun pendingWriteDisablesFormCancelAndDoubleSubmit() {
        val pending = CompletableDeferred<Unit>()
        var calls = 0
        compose.setContent { MaterialTheme {
            AddCompletedTrainingDialog(sports, date, listOf(center), {}, { _, _ -> calls++; pending.await() })
        } }
        compose.onNodeWithText("Сохранить").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Сохранение…").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("training-sport").assertIsNotEnabled()
        compose.onNodeWithTag("football-0").assertIsNotEnabled()
        compose.onNodeWithText("Отмена").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, calls); pending.complete(Unit) }
        compose.waitForIdle()
    }

    @Test fun footballAndRouteDraftsSurviveSportSwitchAndStateRestoration() {
        val restoration = StateRestorationTester(compose)
        var saved: AddCompletedTrainingInput? = null
        restoration.setContent { MaterialTheme {
            AddCompletedTrainingDialog(sports, date, listOf(center), {}, { input, _ -> saved = input })
        } }
        compose.onNodeWithTag("football-0").performScrollTo().performTextReplacement("4")
        compose.onNodeWithTag("training-sport").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Скалолазание").performClick()
        compose.onNodeWithText("+ Добавить трассу").performScrollTo().performClick()
        compose.onNodeWithTag("route-1-repeat").performScrollTo().performTextReplacement("12")
        compose.onAllNodesWithText("Не пройдена").onLast().performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("route-1-repeat").performScrollTo().assertTextContains("12")
        compose.onNodeWithText("Сохранить").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { val result = requireNotNull(saved); assertEquals(2, result.climbingRoutes.size); assertEquals(12, result.climbingRoutes[1].repeatCount); assertFalse(result.climbingRoutes[1].isCompleted) }
        compose.onNodeWithTag("training-sport").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Футбол").performClick()
        compose.onNodeWithTag("football-0").performScrollTo().assertTextContains("4")
    }

    @Test fun futureDateIsInlineErrorAndDatePickerOpensWithoutChangingDraft() {
        var saved: AddCompletedTrainingInput? = null
        compose.setContent { MaterialTheme {
            AddCompletedTrainingDialog(sports, LocalDate.now().plusDays(1), listOf(center), {}, { input, _ -> saved = input })
        } }
        compose.onNodeWithText("Сохранить").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithTag("training-date").performScrollTo().assertExists()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithTag("training-date").performTextReplacement(date.toString())
        compose.onNodeWithTag("training-date-picker").performScrollTo().performClick()
        compose.onNodeWithText("Выбрать").assertExists().performClick()
        compose.onNodeWithText("Сохранить").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(date, saved!!.date) }
    }

    @Test fun historicalEditRetainsGradeAndUuidAndArchivedCenter() {
        val original = ClimbingRouteInput(ClimbingWorkoutType.UNKNOWN, "4+", true, 12,
            gradingSystem = "legacy", gradeCode = null, legacyWorkoutType = "mystery",
            publicId = "4e3c15cb-8a81-478f-b8d9-60761987b914")
        val initial = AddCompletedTrainingInput(climbing.id, center.id, date, climbingRoutes = listOf(original))
        val restoration = StateRestorationTester(compose)
        var saved: AddCompletedTrainingInput? = null
        restoration.setContent { MaterialTheme {
            AddCompletedTrainingDialog(sports, date, listOf(center.copy(isArchived = true)), {}, { input, _ -> saved = input }, initial = initial)
        } }
        compose.onNodeWithTag("training-sport").assertIsNotEnabled()
        compose.onNodeWithText("Общий центр").assertExists()
        compose.onNodeWithTag("route-0-repeat").performScrollTo().assertTextContains("12").performTextReplacement("7")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Сохранить").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(original.copy(repeatCount = 7), saved!!.climbingRoutes.single()) }
    }

    @Test fun nestedCenterDraftSurvivesRestorationWithoutLosingWorkoutFields() {
        val live = androidx.compose.runtime.mutableStateOf(emptyList<SportsCenterUiModel>())
        val restoration = StateRestorationTester(compose)
        var saved: AddCompletedTrainingInput? = null
        restoration.setContent { MaterialTheme {
            AddCompletedTrainingDialog(sports, date, live.value, {}, { input, _ -> saved = input },
                onSaveCenter = { input ->
                    live.value = listOf(SportsCenterUiModel(8, input.name, input.city, listOf(football)))
                    8
                })
        } }
        compose.onNodeWithTag("football-0").performScrollTo().performTextReplacement("4")
        compose.onNodeWithText("Создать центр").performScrollTo().performClick()
        compose.onNodeWithTag("center-name").performTextInput("Новая площадка")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("center-name").assertTextContains("Новая площадка")
        compose.onAllNodesWithText("Сохранить").onLast().performClick()
        compose.onNodeWithTag("football-0").performScrollTo().assertTextContains("4")
        compose.onNodeWithText("Сохранить").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { val result = requireNotNull(saved); assertEquals(8L, result.complexId); assertEquals(4, result.football!!.teamGoalsScored) }
    }

    @Test fun restoredCenterSelectionIsNotResetByInitialLoadingSnapshot() {
        val second = center.copy(id = 8, name = "Другая площадка")
        val live = androidx.compose.runtime.mutableStateOf(listOf(center, second))
        val restoration = StateRestorationTester(compose)
        var saved: AddCompletedTrainingInput? = null
        restoration.setContent { MaterialTheme {
            AddCompletedTrainingDialog(sports, date, live.value, {}, { input, _ -> saved = input })
        } }
        compose.onNodeWithTag("training-center").performClick()
        compose.onNodeWithText("Другая площадка").performClick()
        compose.runOnIdle { live.value = emptyList() }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { live.value = listOf(center, second) }
        compose.onNodeWithText("Другая площадка").assertExists()
        compose.onNodeWithText("Сохранить").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(8L, saved!!.complexId) }
    }

    @Test fun cancelDoesNotSubmit() {
        var dismissed = false
        var calls = 0
        compose.setContent { MaterialTheme {
            AddCompletedTrainingDialog(sports, date, listOf(center), { dismissed = true }, { _, _ -> calls++ })
        } }
        compose.onNodeWithTag("football-0").performScrollTo().performTextReplacement("5")
        compose.onNodeWithText("Отмена").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertTrue(dismissed); assertEquals(0, calls) }
    }
}
