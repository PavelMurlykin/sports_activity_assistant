package com.pamurlykin.sportsactivityassistant.ui

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.repo.AppRepository
import com.pamurlykin.sportsactivityassistant.ui.screen.MainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class LandscapeNavigationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactRailKeepsAllDestinationsAccessible() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val database = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, AppDatabase::class.java).build()
        val repository = AppRepository(database)
        repository.localProfileId()
        val store = ViewModelStore()
        lateinit var viewModel: MainViewModel
        val visible = mutableStateOf(true)
        try {
            instrumentation.runOnMainSync { viewModel = MainViewModel(repository); store.put("landscape", viewModel) }
            compose.setContent {
                val configuration = Configuration(LocalConfiguration.current).apply { orientation = Configuration.ORIENTATION_LANDSCAPE }
                if (visible.value) CompositionLocalProvider(LocalConfiguration provides configuration) {
                    MaterialTheme { SportsActivityApp(viewModel) }
                }
            }
            compose.onNodeWithContentDescription("Статистика").assertHasClickAction().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("Всего завершённых тренировок").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Центры").performClick()
            compose.onNodeWithText("Спортивные центры").assertExists()
            compose.onNodeWithContentDescription("Данные").performClick()
            compose.onNodeWithText("Выбрать файл").assertExists()
            compose.onNodeWithContentDescription("Расписание").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Предыдущий месяц").fetchSemanticsNodes().isNotEmpty() }
        } finally {
            compose.runOnIdle { visible.value = false; store.clear() }
            database.close()
        }
    }
}
