package com.pamurlykin.sportsactivityassistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.pamurlykin.sportsactivityassistant.data.model.*
import com.pamurlykin.sportsactivityassistant.ui.components.*
import kotlinx.coroutines.CompletableDeferred
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SportsCentersUiTest {
    @get:Rule val compose = createComposeRule()
    private val football = SportSummaryUiModel(1, "football", "Футбол", 0)
    private val climbing = SportSummaryUiModel(2, "climbing", "Скалолазание", 0)
    private val sports = listOf(football, climbing)
    private fun center(id: Long, sport: SportSummaryUiModel, archived: Boolean = false) =
        SportsCenterUiModel(id, "Центр ${sport.title}", null, listOf(sport), archived)

    @Test fun saveErrorKeepsCenterDraftAndCanBeCorrected() {
        var calls = 0
        var saved: SaveSportsCenterInput? = null
        var completed = false
        compose.setContent { MaterialTheme {
            SportsCenterDialog(null, sports, {}, { input ->
                calls++
                if (calls == 1) throw IllegalArgumentException("Центр уже существует")
                saved = input; 7L
            }, { completed = true }, initialSportId = 1)
        } }
        compose.onNodeWithTag("center-name").performTextInput("Первый центр")
        compose.onNodeWithText("Сохранить").performClick()
        compose.onNodeWithText("Центр уже существует").performScrollTo().assertExists()
        compose.onNodeWithTag("center-name").performScrollTo().assertTextContains("Первый центр")
        compose.onNodeWithTag("center-name").performTextReplacement("Второй центр")
        compose.onNodeWithText("Скалолазание").performScrollTo().performClick()
        compose.onNodeWithText("Сохранить").performClick()
        compose.runOnIdle {
            assertTrue(completed); assertEquals("Второй центр", saved!!.name); assertEquals(setOf(1,2), saved.sportIds)
        }
    }

    @Test fun centerSaveCannotBeDoubleSubmitted() {
        val pending = CompletableDeferred<Long>()
        var calls = 0
        compose.setContent { MaterialTheme {
            SportsCenterDialog(null, sports, {}, { calls++; pending.await() }, {}, initialSportId = 1)
        } }
        compose.onNodeWithTag("center-name").performTextInput("Центр")
        compose.onNodeWithText("Сохранить").performClick()
        compose.onNodeWithText("Сохранение…").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Отмена").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, calls); pending.complete(9) }
        compose.waitForIdle()
    }

    @Test fun longNamesAndManySportChipsRemainScrollableAndSaveable() {
        val many = (1..12).map { SportSummaryUiModel(it, "test$it", "Вид спорта $it с длинным названием", 0) }
        val longName = "Очень длинное название спортивного центра ".repeat(4).trim()
        var saved: SaveSportsCenterInput? = null
        compose.setContent { MaterialTheme {
            SportsCenterDialog(SportsCenterUiModel(3, longName, "Длинный город", listOf(many.first())), many,
                {}, { saved = it; 3 }, {})
        } }
        compose.onNodeWithTag("center-name").assertTextContains(longName)
        compose.onNodeWithText(many.last().title).performScrollTo().performClick()
        compose.onNodeWithText("Сохранить").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(setOf(1,12), saved!!.sportIds); assertEquals(longName, saved.name) }
    }

    @Test fun completedFormResetsIncompatibleCenterAndOffersCreationForEmptySport() {
        var saved: AddCompletedTrainingInput? = null
        compose.setContent { MaterialTheme {
            AddCompletedTrainingDialog(sports, LocalDate.parse("2026-10-08"), listOf(center(7, football), center(8, climbing, true)),
                {}, { input, _ -> saved = input }, onSaveCenter = { 9 })
        } }
        compose.onNodeWithText("Центр Футбол").assertExists()
        compose.onNodeWithTag("training-sport").performClick()
        compose.onNodeWithText("Скалолазание").performClick()
        compose.onNodeWithText("Центр Футбол").assertDoesNotExist()
        compose.onNodeWithText("Создать центр").assertExists()
        compose.onNodeWithText("Сохранить").performClick()
        compose.runOnIdle { assertNull(saved) }
    }

    @Test fun plannedFormUsesNewSportCenterAndCannotSaveStaleArchivedSelection() {
        val live = mutableStateOf(listOf(center(7, football), center(8, climbing)))
        var saved: AddPlannedTrainingInput? = null
        compose.setContent { MaterialTheme { AddPlannedTrainingDialog(sports, live.value, {}, { saved = it }) } }
        compose.onNodeWithTag("training-sport").performClick()
        compose.onNodeWithText("Скалолазание").performClick()
        compose.onNodeWithText("Центр Скалолазание").assertExists()
        compose.onNodeWithText("Сохранить").performClick()
        compose.runOnIdle { assertEquals(8L, saved!!.complexId); saved = null; live.value = listOf(center(7, football), center(8, climbing, true)) }
        compose.onNodeWithText("Центр Скалолазание").assertDoesNotExist()
        compose.onNodeWithText("Сохранить").performClick()
        compose.runOnIdle { assertNull(saved) }
    }

    @Test fun switchingSportKeepsAnAlreadyCompatibleSharedCenter() {
        var saved: AddCompletedTrainingInput? = null
        val centers = listOf(center(7, football), center(8, climbing), SportsCenterUiModel(9, "Общий центр", null, sports))
        compose.setContent { MaterialTheme {
            AddCompletedTrainingDialog(sports, LocalDate.parse("2026-10-08"), centers, {}, { input, _ -> saved = input })
        } }
        compose.onNodeWithTag("training-center").performClick()
        compose.onNodeWithText("Общий центр").performClick()
        compose.onNodeWithTag("training-sport").performClick()
        compose.onNodeWithText("Скалолазание").performClick()
        compose.onNodeWithText("Общий центр").assertExists()
        compose.onNodeWithText("Сохранить").performClick()
        compose.runOnIdle { assertEquals(9L, saved!!.complexId); assertEquals(2, saved.sportId) }
    }

    @Test fun emptyFormCreatesCompatibleCenterWithoutLosingTrainingDraft() {
        val live = mutableStateOf(emptyList<SportsCenterUiModel>())
        var saved: AddCompletedTrainingInput? = null
        compose.setContent { MaterialTheme {
            AddCompletedTrainingDialog(listOf(football), LocalDate.parse("2026-10-08"), live.value, {}, { input, _ -> saved = input },
                onSaveCenter = { input ->
                    assertEquals(setOf(1), input.sportIds)
                    live.value = listOf(SportsCenterUiModel(9, input.name, input.city, listOf(football))); 9
                })
        } }
        compose.onNodeWithText("Создать центр").performScrollTo().performClick()
        compose.onNodeWithTag("center-name").performTextInput("Новая площадка")
        // Only the top dialog participates in visible semantics.
        compose.onAllNodesWithText("Сохранить").onLast().performClick()
        compose.onNodeWithText("Новая площадка").assertExists()
        // The nested window's keyboard can still be animating; test the button action, not a moving coordinate.
        compose.onNodeWithText("Сохранить").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(9L, saved!!.complexId); assertEquals(LocalDate.parse("2026-10-08"), saved.date) }
    }
}
