package com.pamurlykin.sportsactivityassistant.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.model.SaveSportsCenterInput
import com.pamurlykin.sportsactivityassistant.data.model.ThemeMode
import com.pamurlykin.sportsactivityassistant.data.repo.AppRepository
import com.pamurlykin.sportsactivityassistant.ui.screen.*
import com.pamurlykin.sportsactivityassistant.ui.theme.SportsActivityTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class CentersSettingsUiTest {
    @get:Rule val compose = createComposeRule()

    private fun fixture(settings: Boolean, test: (AppRepository, MainViewModel, Long) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, AppDatabase::class.java).build()
        val repo = AppRepository(db)
        val id = runBlocking {
            repo.localProfileId()
            val sports = db.referenceDao().getSports()
            repo.saveSportsCenter(SaveSportsCenterInput(name = "Высота", city = "Москва", sportIds = sports.map { it.id }.toSet()))
        }
        val store = ViewModelStore()
        lateinit var vm: MainViewModel
        val visible = mutableStateOf(true)
        try {
            instrumentation.runOnMainSync { vm = MainViewModel(repo); store.put("centers-settings", vm) }
            compose.setContent { if (visible.value) SportsActivityTheme(ThemeMode.LIGHT) {
                if (settings) SettingsScreen(vm) {} else SportsCentersScreen(vm)
            } }
            test(repo, vm, id)
        } finally {
            compose.runOnIdle { visible.value = false; store.clear() }
            db.close()
        }
    }

    @Test fun centerActionsStayInHeaderAndArchiveAndRestoreKeepTheCenter() {
        fixture(false) { repo, _, id ->
            compose.waitUntil(5000) { compose.onAllNodesWithTag("center-$id").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("centers-list").performScrollToNode(hasTestTag("center-$id"))
            val header = compose.onNodeWithTag("center-header-$id").fetchSemanticsNode().boundsInRoot
            val edit = compose.onNodeWithContentDescription("Изменить центр: Высота, Москва").fetchSemanticsNode().boundsInRoot
            assertTrue(edit.top >= header.top && edit.bottom <= header.bottom)
            screenshot("centers-redesign.png")
            compose.onNodeWithContentDescription("В архив: Высота, Москва").performClick()
            compose.onNodeWithText("В архив").performClick()
            compose.waitUntil(5000) { runBlocking { repo.observeSportsCenters().first().single().isArchived } }
            compose.onNodeWithText("Архив").performClick()
            compose.onNodeWithTag("centers-list").performScrollToNode(hasTestTag("center-$id"))
            compose.onNodeWithContentDescription("Вернуть центр: Высота, Москва").performClick()
            compose.onNodeWithText("Вернуть").performClick()
            compose.waitUntil(5000) { runBlocking { !repo.observeSportsCenters().first().single().isArchived } }
        }
    }

    @Test fun settingsBackupActionsAndRestoreOpenTheExistingPreview() {
        fixture(true) { repo, vm, _ ->
            compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("restore-backup"))
            compose.onNodeWithText("Сохранить резервную копию").assertIsDisplayed()
            compose.onNodeWithText("Восстановить данные из файла").assertIsDisplayed()
            compose.onNodeWithTag("settings-backup").onChildren().filter(hasClickAction()).assertCountEquals(2)
            screenshot("settings-backup.png")
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val file = File(context.cacheDir, "settings-restore-ui.json")
            try {
                file.writeText(runBlocking { repo.createBackup() })
                compose.runOnIdle {
                    assertTrue(vm.beginImportSelection())
                    vm.importSelected(context.contentResolver, Uri.fromFile(file))
                }
                compose.waitUntil(10000) { compose.onAllNodesWithText("Проверка импорта").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Отмена").performClick()
                compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("restore-backup"))
                compose.onNodeWithText("Восстановить данные из файла").assertIsDisplayed()
            } finally { file.delete() }
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), name)
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
