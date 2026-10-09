package com.pamurlykin.sportsactivityassistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.pamurlykin.sportsactivityassistant.data.model.*
import com.pamurlykin.sportsactivityassistant.ui.components.StatisticsFilters
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@Suppress("DEPRECATION")
class StatisticsFilterUiTest {
    @get:Rule val compose = createComposeRule()
    private val date = LocalDate.parse("2020-02-29")
    private val center = SportsCenterUiModel(7,"Старый центр","Москва",emptyList(),isArchived=true)
    private fun apply() = compose.onNodeWithTag("statistics-apply").performSemanticsAction(SemanticsActions.OnClick) { it() }

    @Test fun invalidAndReversedDatesDoNotApplyAndDraftSurvivesRestore() {
        var selected: StatisticsFilter? = null
        val restore = StateRestorationTester(compose)
        restore.setContent { MaterialTheme { StatisticsFilters(StatisticsFilter(),listOf(center),{ selected = it },date) } }
        compose.onNodeWithTag("statistics-filters").performClick()
        compose.onNodeWithTag("statistics-all-time").performClick()
        compose.onNodeWithTag("statistics-start").performTextReplacement("2020-02-30")
        apply()
        compose.onNodeWithTag("statistics-filter-error").performScrollTo().assertTextContains("Несуществующая", substring = true)
        compose.runOnIdle { assertNull(selected) }
        compose.onNodeWithTag("statistics-start").performScrollTo().performTextReplacement("2020-03-01")
        compose.onNodeWithTag("statistics-end").performScrollTo().performTextReplacement("2020-02-29")
        apply()
        compose.onNodeWithTag("statistics-filter-error").performScrollTo().assertTextContains("Начало периода", substring = true)
        compose.onNodeWithTag("statistics-start").performScrollTo().performTextReplacement("2019-12-31")
        compose.onNodeWithTag("statistics-center").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Старый центр, Москва · архив").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("statistics-start").assertTextContains("2019-12-31", substring = true)
        compose.onNodeWithTag("statistics-end").performScrollTo().assertTextContains("2020-02-29", substring = true)
        compose.onNodeWithTag("statistics-center").performScrollTo().assertTextContains("архив", substring = true)
        apply()
        compose.runOnIdle { assertEquals(StatisticsFilter(LocalDate.parse("2019-12-31"),date,7),selected) }
    }

    @Test fun presetsAreInclusiveAndCancelDoesNotChangeExistingFilter() {
        var selected: StatisticsFilter? = null
        val initial = StatisticsFilter(centerId=7)
        compose.setContent { MaterialTheme { StatisticsFilters(initial,listOf(center),{ selected = it },date) } }
        compose.onNodeWithTag("active-statistics-center").assertTextContains("архив", substring = true)
        compose.onNodeWithTag("statistics-filters").performClick()
        compose.onNodeWithText("Этот месяц").performClick()
        compose.onNodeWithTag("statistics-start").assertTextContains("2020-02-01", substring = true)
        compose.onNodeWithTag("statistics-end").performScrollTo().assertTextContains("2020-02-29", substring = true)
        apply()
        compose.runOnIdle { assertEquals(StatisticsFilter(LocalDate.parse("2020-02-01"),date,7),selected); selected = null }
        compose.onNodeWithTag("statistics-filters").performClick()
        compose.onNodeWithText("Этот год").performClick()
        compose.onNodeWithTag("statistics-end").performScrollTo().assertTextContains("2020-12-31", substring = true)
        compose.onNodeWithText("Отмена").performClick()
        compose.runOnIdle { assertNull(selected) }
        compose.onNodeWithText("Сбросить").performClick()
        compose.runOnIdle { assertEquals(StatisticsFilter(),selected) }
    }
}
