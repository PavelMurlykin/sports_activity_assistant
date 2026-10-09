package com.pamurlykin.sportsactivityassistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.pamurlykin.sportsactivityassistant.data.model.*
import com.pamurlykin.sportsactivityassistant.ui.components.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

@Suppress("DEPRECATION")
class PlanningFormUiTest {
    @get:Rule val compose = createComposeRule()
    private val sport = SportSummaryUiModel(1,"football","Футбол",0)
    private val center = SportsCenterUiModel(7,"Арена",null,listOf(sport))
    private val date = LocalDate.parse("2020-02-29")
    private fun save() = compose.onNodeWithText("Сохранить").performSemanticsAction(SemanticsActions.OnClick) { it() }

    @Test fun selectedDateAndWeeklyDraftSurviveRestoreAndZeroIntervalIsNotCoerced() {
        val restoration = StateRestorationTester(compose)
        var saved: AddPlannedTrainingInput? = null
        restoration.setContent { MaterialTheme {
            AddPlannedTrainingDialog(listOf(sport),listOf(center),{}, { input,_ -> saved = input }, initialDate = date)
        } }
        compose.onNodeWithTag("plan-date").assertTextContains(date.toString())
        compose.onNodeWithText("По неделям").performClick()
        compose.onNodeWithTag("plan-interval").performScrollTo().performTextReplacement("0")
        save()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithTag("plan-interval").performScrollTo().performTextReplacement("2")
        compose.onNodeWithTag("plan-end").performScrollTo().performTextReplacement("2020-05-01")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("plan-interval").assertTextContains("2")
        compose.onNodeWithTag("plan-end").performScrollTo().assertTextContains("2020-05-01")
        save()
        compose.runOnIdle { val value = requireNotNull(saved); assertEquals(date,value.date); assertEquals(2,value.intervalWeeks); assertEquals(LocalDate.parse("2020-05-01"),value.endDate) }
    }
    @Test fun failedWriteKeepsPlanAndStableTokenAndPendingSaveCannotRepeat() {
        var calls = 0
        val tokens = mutableListOf<String>()
        val pending = CompletableDeferred<Unit>()
        compose.setContent { MaterialTheme {
            AddPlannedTrainingDialog(listOf(sport),listOf(center),{}, { _,token ->
                tokens += token; calls++
                if (calls == 1) throw IllegalStateException("Ошибка сохранения плана")
                pending.await()
            }, initialDate = date)
        } }
        save()
        compose.onNodeWithTag("plan-error").performScrollTo().assertTextContains("Ошибка сохранения плана")
        save()
        compose.onNodeWithText("Сохранение…").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Отмена").assertIsNotEnabled()
        compose.onNodeWithTag("plan-date").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(2,calls); assertEquals(tokens[0],tokens[1]); pending.complete(Unit) }
        compose.waitForIdle()
    }
    @Test fun resultPrefillLocksSportButDoesNotPermitFutureDateOrArchivedCenter() {
        var saved: AddCompletedTrainingInput? = null
        val future = LocalDate.now().plusDays(1)
        val archived = center.copy(isArchived = true)
        val active = center.copy(id = 8,name = "Открытая арена")
        compose.setContent { MaterialTheme {
            AddCompletedTrainingDialog(listOf(sport),future,listOf(archived,active),{}, { input,_ -> saved = input },
                initial = AddCompletedTrainingInput(1,7,future), fromPlan = true)
        } }
        compose.onNodeWithTag("training-sport").assertIsNotEnabled()
        save()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithTag("training-date").performScrollTo().performTextReplacement(date.toString())
        save()
        compose.runOnIdle { val value = requireNotNull(saved); assertEquals(date,value.date); assertEquals(8L,value.complexId) }
    }
    @Test fun calendarHasLegendNavigationLabelsAndNonColorStateDescriptions() {
        val planned = ScheduleEventUiModel("p",date,1,"football","Футбол","Арена",ScheduleEventState.PLANNED,false)
        val canceled = planned.copy(id = "c",state = ScheduleEventState.CANCELED)
        val days = listOf(ScheduleDayUiModel(date,true,true,listOf(planned,canceled)))
        compose.setContent { MaterialTheme {
            MonthCalendar(ScheduleMonthUiModel(YearMonth.from(date),date,days,listOf(planned,canceled)),{},{},{})
        } }
        compose.onNodeWithText("✓ Состоялась · ○ План · × Отменена").assertExists()
        compose.onNodeWithContentDescription("Предыдущий месяц").assertHasClickAction()
        compose.onNodeWithContentDescription("Следующий месяц").assertHasClickAction()
        compose.onNodeWithContentDescription("$date, сегодня, Футбол: запланирована, Футбол: отменена").assertIsSelected().assertHasClickAction()
    }
}
