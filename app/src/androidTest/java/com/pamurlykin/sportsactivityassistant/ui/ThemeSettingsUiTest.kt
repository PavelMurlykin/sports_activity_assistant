package com.pamurlykin.sportsactivityassistant.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.StoredThemePreferences
import com.pamurlykin.sportsactivityassistant.data.model.ThemeMode
import com.pamurlykin.sportsactivityassistant.data.repo.AppRepository
import com.pamurlykin.sportsactivityassistant.ui.screen.MainViewModel
import com.pamurlykin.sportsactivityassistant.ui.theme.SportsActivityTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class ThemeSettingsUiTest {
    @get:Rule val compose = createComposeRule()
    private val systemDark = mutableStateOf(false)
    @Volatile private var renderedDark = false

    private fun fixture(test: (AppDatabase, MainViewModel, StoredThemePreferences) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val preferences = context.getSharedPreferences("theme-ui-${UUID.randomUUID()}", Context.MODE_PRIVATE)
        val themes = StoredThemePreferences(preferences)
        val repo = AppRepository(db, themes)
        runBlocking { repo.localProfileId() }
        val store = ViewModelStore()
        val visible = mutableStateOf(true)
        lateinit var vm: MainViewModel
        try {
            instrumentation.runOnMainSync { vm = MainViewModel(repo); store.put("theme", vm) }
            compose.setContent {
                if (visible.value) {
                    val mode by vm.themeMode.collectAsStateWithLifecycle()
                    val configuration = Configuration(LocalConfiguration.current).apply {
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                            if (systemDark.value) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                    }
                    CompositionLocalProvider(LocalConfiguration provides configuration) {
                        SportsActivityTheme(mode) {
                            val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                            SideEffect { renderedDark = dark }
                            SportsActivityApp(vm)
                        }
                    }
                }
            }
            compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Настройки").fetchSemanticsNodes().isNotEmpty() }
            test(db, vm, themes)
        } finally {
            compose.runOnIdle { visible.value = false; store.clear() }
            db.close()
            preferences.edit().clear().commit()
        }
    }

    @Test fun themeChoicesApplyImmediatelyAndSystemChoiceFollowsConfiguration() = fixture { _, vm, themes ->
        compose.onNodeWithContentDescription("Настройки").performClick()
        compose.onNodeWithTag("theme-mode-system").assertIsSelected()
        compose.onNodeWithTag("theme-mode-dark").performClick()
        compose.waitUntil(5000) { vm.themeMode.value == ThemeMode.DARK && renderedDark }
        compose.onNodeWithTag("theme-mode-dark").assertIsSelected()
        screenshot("theme-settings-dark.png")
        compose.onNodeWithTag("theme-mode-light").performClick()
        compose.waitUntil(5000) { vm.themeMode.value == ThemeMode.LIGHT && !renderedDark }
        screenshot("theme-settings-light.png")
        compose.onNodeWithTag("theme-mode-system").performClick()
        compose.waitUntil(5000) { themes.themeMode.value == ThemeMode.SYSTEM }
        compose.runOnIdle { systemDark.value = true }
        compose.waitUntil(5000) { renderedDark }
        compose.runOnIdle { systemDark.value = false }
        compose.waitUntil(5000) { !renderedDark }
    }

    @Test fun freshAppCanCreateItsFirstCenterInsideAWorkoutAndSaveTheResult() = fixture { db, _, _ ->
        runBlocking { assertTrue(db.referenceDao().getAllComplexes().isEmpty()) }
        compose.onNodeWithText("Статистика").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Записать тренировку: Футбол").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Записать тренировку: Футбол").performScrollTo().performClick()
        compose.onNodeWithText("Создать центр").performScrollTo().performClick()
        compose.onNodeWithTag("center-name").performTextInput("Мой первый центр")
        compose.onAllNodesWithText("Сохранить").onLast().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("center-name").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Мой первый центр").performScrollTo().assertExists()
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Всего завершённых тренировок").fetchSemanticsNodes().isNotEmpty() }
        runBlocking {
            assertEquals(listOf("Мой первый центр"), db.referenceDao().getAllComplexes().map { it.name })
            assertEquals(1, db.trainingDao().getAllTrainingBundles().size)
        }
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
