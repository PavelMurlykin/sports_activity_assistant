package com.pamurlykin.sportsactivityassistant.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.model.*
import com.pamurlykin.sportsactivityassistant.ui.components.StatisticsFilters
import com.pamurlykin.sportsactivityassistant.ui.theme.SportsActivityTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

@Suppress("DEPRECATION")
class StatisticsFilterUiTest {
    @get:Rule val compose = createComposeRule()
    private val date = LocalDate.parse("2020-02-29")
    private val football = SportSummaryUiModel(1, "football", "Футбол", 0)
    private val climbing = SportSummaryUiModel(2, "climbing", "Скалолазание", 0)
    private val centers = listOf(
        SportsCenterUiModel(7, "Старый центр", "Москва", listOf(football), isArchived = true),
        SportsCenterUiModel(8, "Футбольный центр", null, listOf(football)),
        SportsCenterUiModel(9, "Скалодром", null, listOf(climbing)),
    )
    private fun chooseDay() = compose.onNode(hasText("Выбрать") and hasAnyAncestor(isDialog())).performClick()

    @Test fun quickPeriodsPreserveCentersAndOpenTheCalendarWithoutAnEditor() {
        val selected = mutableStateOf(StatisticsFilter(centerId = 7))
        compose.setContent { SportsActivityTheme {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                StatisticsFilters(selected.value, centers, { selected.value = it }, date, sportId = 1)
            }
        } }
        compose.onNodeWithText("Период").assertDoesNotExist()
        compose.onNodeWithText("Даты").assertDoesNotExist()
        compose.onNodeWithText("Месяц").performClick()
        compose.runOnIdle { assertEquals(StatisticsFilter(LocalDate.parse("2020-02-01"), date, 7), selected.value) }
        compose.onNodeWithText("Год").performClick()
        compose.runOnIdle { assertEquals(StatisticsFilter(LocalDate.parse("2020-01-01"), LocalDate.parse("2020-12-31"), 7), selected.value) }
        compose.onNodeWithText("Всё время").performClick()
        compose.onNodeWithTag("statistics-start").assertHasClickAction().assert(!hasSetTextAction()).performClick()
        compose.onNode(isDialog()).assertExists()
        compose.onNodeWithText("Фильтры").assertDoesNotExist()
        chooseDay()
        compose.runOnIdle { assertEquals(StatisticsFilter(startDate = date, centerId = 7), selected.value) }
        screenshot("compact-statistics-filters.png")
    }

    @Test fun centerListSupportsMultipleSelectionsRestrictsSportAndRestoresItsState() {
        val selected = mutableStateOf(StatisticsFilter(date, date))
        val restore = StateRestorationTester(compose)
        restore.setContent { SportsActivityTheme { StatisticsFilters(selected.value, centers, { selected.value = it }, date, sportId = 1) } }
        compose.onNodeWithTag("statistics-choose-centers").performClick()
        compose.onNodeWithTag("statistics-center-9").assertDoesNotExist()
        compose.onNodeWithTag("statistics-center-7").performClick()
        compose.onNodeWithTag("statistics-center-8").performClick()
        compose.runOnIdle { assertEquals(setOf(7L, 8L), selected.value.centerIds) }
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("statistics-center-7").assertIsSelected()
        compose.onNodeWithTag("statistics-center-8").assertIsSelected().performClick()
        compose.runOnIdle { assertEquals(setOf(7L), selected.value.centerIds) }
        Espresso.pressBack()
        compose.onNodeWithTag("statistics-all-centers").performClick()
        compose.runOnIdle { assertEquals(StatisticsFilter(date, date), selected.value) }
    }

    @Test fun independentDatesAndOpenCalendarSurviveRestorationAndCancelKeepsSelection() {
        val selected = mutableStateOf(StatisticsFilter())
        val restore = StateRestorationTester(compose)
        restore.setContent { SportsActivityTheme { StatisticsFilters(selected.value, centers, { selected.value = it }, date) } }
        compose.onNodeWithTag("statistics-end").performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNode(isDialog()).assertExists()
        chooseDay()
        compose.runOnIdle { assertEquals(StatisticsFilter(endDate = date), selected.value) }
        compose.onNodeWithTag("statistics-start").performClick()
        compose.onNode(hasText("Отмена") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { assertEquals(StatisticsFilter(endDate = date), selected.value) }
        compose.onNodeWithTag("statistics-start").performClick()
        chooseDay()
        compose.runOnIdle { assertEquals(StatisticsFilter(date, date), selected.value) }
        compose.onNodeWithTag("statistics-start").assertTextContains("29.02.2020", substring = true)
    }

    private fun screenshot(name: String) {
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
