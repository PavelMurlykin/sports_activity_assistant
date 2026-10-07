package com.pamurlykin.sportsactivityassistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.pamurlykin.sportsactivityassistant.data.model.AddCompletedTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.ComplexOptionUiModel
import com.pamurlykin.sportsactivityassistant.data.model.SportSummaryUiModel
import com.pamurlykin.sportsactivityassistant.data.sport.SportModules
import com.pamurlykin.sportsactivityassistant.ui.components.AddCompletedTrainingDialog
import com.pamurlykin.sportsactivityassistant.ui.components.SportEditors
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SportEditorsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun everySupportedSportHasAnEditor() {
        SportModules.all.forEach { assertNotNull(SportEditors.find(it.slug)) }
        assertNull(SportEditors.find("unsupported"))
    }

    @Test fun footballZeroScoreSavesWithoutOptionalFields() {
        var saved: AddCompletedTrainingInput? = null
        compose.setContent { MaterialTheme {
            AddCompletedTrainingDialog(listOf(SportSummaryUiModel(5, "football", "Футбол", 0)),
                LocalDate.parse("2026-10-01"), { listOf(ComplexOptionUiModel(7, "Центр", null)) }, {}, { saved = it })
        } }
        compose.waitForIdle()
        compose.onNodeWithText("Сохранить").performClick()
        compose.runOnIdle {
            val result = requireNotNull(saved)
            assertEquals(5, result.sportId)
            assertEquals(7L, result.complexId)
            assertEquals(0, result.football!!.teamGoalsScored)
            assertNull(result.football.distanceKm)
            assertTrue(result.climbingRoutes.isEmpty())
        }
    }

    @Test fun climbingEditorSavesTwoRoutesInOneTraining() {
        var saved: AddCompletedTrainingInput? = null
        compose.setContent { MaterialTheme {
            AddCompletedTrainingDialog(listOf(SportSummaryUiModel(6, "climbing", "Скалолазание", 0)),
                LocalDate.parse("2026-10-01"), { listOf(ComplexOptionUiModel(8, "Скалодром", null)) }, {}, { saved = it })
        } }
        compose.waitForIdle()
        compose.onNodeWithText("+ Добавить трассу").performScrollTo().performClick()
        compose.onNodeWithText("Сохранить").performClick()
        compose.runOnIdle {
            val result = requireNotNull(saved)
            assertEquals(2, result.climbingRoutes.size)
            assertNull(result.football)
        }
    }

    @Test fun changingDisciplineUsesItsOwnScaleAndSpeedHasNoGrade() {
        var saved: AddCompletedTrainingInput? = null
        compose.setContent { MaterialTheme {
            AddCompletedTrainingDialog(listOf(SportSummaryUiModel(6, "climbing", "Скалолазание", 0)),
                LocalDate.parse("2026-10-07"), { listOf(ComplexOptionUiModel(8, "Скалодром", null)) }, {}, { saved = it })
        } }
        compose.waitForIdle()
        compose.onNodeWithTag("route-0-type").performScrollTo().performClick()
        compose.onNodeWithText("Болдер").performClick()
        compose.onNodeWithText("Сложность (Fontainebleau)").assertExists()
        compose.onNodeWithText("+ Добавить трассу").performScrollTo().performClick()
        compose.onNodeWithTag("route-1-type").performScrollTo().performClick()
        compose.onNodeWithText("Скорость").performClick()
        compose.onNodeWithTag("route-1-course").performScrollTo().performClick()
        compose.onNodeWithText("Эталонная 15 м").performClick()
        compose.onNodeWithText("Сохранить").performClick()
        compose.runOnIdle {
            val routes = requireNotNull(saved).climbingRoutes
            assertEquals("fontainebleau", routes[0].gradingSystem)
            assertEquals("6a", routes[0].gradeCode)
            assertEquals("none", routes[1].gradingSystem)
            assertNull(routes[1].gradeCode)
            assertEquals("", routes[1].routeDifficulty)
            assertEquals("standard_15m", routes[1].speedCourse)
        }
    }

    @Test fun speedDoesNotSaveUntilCourseIsSelectedThenCanRecover() {
        var saved: AddCompletedTrainingInput? = null
        compose.setContent { MaterialTheme {
            AddCompletedTrainingDialog(listOf(SportSummaryUiModel(6, "climbing", "Скалолазание", 0)),
                LocalDate.parse("2026-10-07"), { listOf(ComplexOptionUiModel(8, "Скалодром", null)) }, {}, { saved = it })
        } }
        compose.waitForIdle()
        compose.onNodeWithTag("route-0-type").performScrollTo().performClick()
        compose.onNodeWithText("Скорость").performClick()
        compose.onNodeWithText("Сохранить").performClick()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithText("Выберите трассу скорости").performScrollTo().assertExists()
        compose.onNodeWithTag("route-0-course").performScrollTo().performClick()
        compose.onNodeWithText("Иная трасса").performClick()
        compose.onNodeWithText("Сохранить").performClick()
        compose.runOnIdle { assertEquals("other", requireNotNull(saved).climbingRoutes.single().speedCourse) }
    }
}
