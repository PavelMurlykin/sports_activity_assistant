package com.pamurlykin.sportsactivityassistant.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.repo.AppRepository
import com.pamurlykin.sportsactivityassistant.ui.screen.MainViewModel
import com.pamurlykin.sportsactivityassistant.ui.theme.SportsActivityTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SportSettingsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectionSurvivesNavigationFiltersNewWorkoutAndCanBeReenabledAfterHidingEverything() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, AppDatabase::class.java).build()
        val repository = AppRepository(db); repository.localProfileId()
        val store = ViewModelStore()
        lateinit var viewModel: MainViewModel
        val visible = mutableStateOf(true)
        try {
            instrumentation.runOnMainSync { viewModel = MainViewModel(repository); store.put("settings", viewModel) }
            compose.setContent { if (visible.value) SportsActivityTheme { SportsActivityApp(viewModel) } }
            compose.onNodeWithContentDescription("Настройки").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("sport-visible-climbing").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("sport-visible-climbing").assertIsOn().performClick()
            compose.onNodeWithContentDescription("Назад").performClick()
            compose.onNodeWithText("Статистика").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Записать тренировку: Футбол").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Записать тренировку: Скалолазание").assertDoesNotExist()
            compose.onNodeWithContentDescription("Записать тренировку: Футбол").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("training-sport").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("training-sport").performScrollTo().performClick()
            compose.onNodeWithText("Скалолазание").assertDoesNotExist()
            // Dismiss the selector, then cancel the standalone entry screen.
            compose.onAllNodesWithText("Футбол").onLast().performClick()
            compose.onNodeWithText("Отмена").performClick()
            compose.onNodeWithContentDescription("Настройки").performClick()
            compose.onNodeWithTag("sport-visible-climbing").assertIsOff()
            compose.onNodeWithTag("sport-visible-football").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("sport-visible-football").fetchSemanticsNodes().single().config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState] == androidx.compose.ui.state.ToggleableState.Off }
            compose.onNodeWithContentDescription("Назад").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("Выберите виды спорта в настройках").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Выберите виды спорта в настройках").performClick()
            compose.onNodeWithTag("sport-visible-climbing").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("sport-visible-climbing").fetchSemanticsNodes().single().config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState] == androidx.compose.ui.state.ToggleableState.On }
            compose.onNodeWithContentDescription("Назад").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Записать тренировку: Скалолазание").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Записать тренировку: Футбол").assertDoesNotExist()
            assertEquals(listOf("climbing"), repository.observeSportPreferences().first().filter { it.isVisible }.map { it.sport.slug })
        } finally {
            compose.runOnIdle { visible.value = false; store.clear() }
            db.close()
        }
    }
}
