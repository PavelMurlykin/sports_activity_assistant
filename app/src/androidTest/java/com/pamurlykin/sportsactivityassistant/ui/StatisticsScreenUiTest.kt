package com.pamurlykin.sportsactivityassistant.ui

import com.pamurlykin.sportsactivityassistant.data.seed.testCenterForSport

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.model.*
import com.pamurlykin.sportsactivityassistant.data.repo.AppRepository
import com.pamurlykin.sportsactivityassistant.ui.screen.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import java.time.LocalDate

@Suppress("DEPRECATION")
class StatisticsScreenUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun overviewAndSportShareFilterAndNextPageShowsRemainingRecordsNotWholeHistory() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext,AppDatabase::class.java).build()
        val repo = AppRepository(db)
        val store = ViewModelStore()
        lateinit var vm: MainViewModel
        val visible = mutableStateOf(true)
        var editedId = 0L
        try {
            val user = repo.localProfileId()
            val sport = db.referenceDao().getSportBySlug("football")!!.id
            val center = db.testCenterForSport(sport).id
            val day = LocalDate.parse("2020-02-29")
            val ids = (1..21).map { repo.addCompletedTraining(user,AddCompletedTrainingInput(sport,center,day,FootballTrainingInput(0,0,0,0,null,null,null))) }
            repo.addCompletedTraining(user,AddCompletedTrainingInput(sport,center,day.minusDays(1),FootballTrainingInput(0,0,0,0,null,null,null)))
            instrumentation.runOnMainSync {
                vm = MainViewModel(repo); store.put("statistics",vm)
                vm.setStatisticsFilter(StatisticsFilter(day,day,center))
            }
            compose.setContent { if (visible.value) MaterialTheme {
                var opened by remember { mutableIntStateOf(0) }
                if (opened == 0) StatisticsScreen(vm,{ opened = it })
                else StatisticsDetailScreen(opened,vm,{ opened = 0 }, onEditTraining = { editedId = it })
            } }
            compose.waitUntil(5000) { compose.onAllNodesWithText("21 тренировок").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Футбол").performScrollTo().performClick()
            compose.onNodeWithTag("statistics-start").assertTextContains("29.02.2020", substring = true)
            compose.waitUntil(5000) { compose.onAllNodesWithText("Общие показатели").fetchSemanticsNodes().isNotEmpty() }
            val firstCard = hasTestTag("training-card-" + ids.last())
            compose.onNodeWithTag("statistics-history").performScrollToNode(firstCard)
            val header = compose.onNode(hasTestTag("completed-training-header") and hasAnyAncestor(firstCard)).fetchSemanticsNode().boundsInRoot
            val edit = compose.onNode(hasContentDescription("Изменить тренировку") and hasAnyAncestor(firstCard))
            val delete = compose.onNode(hasContentDescription("Удалить тренировку") and hasAnyAncestor(firstCard))
            assertTrue(header.contains(edit.fetchSemanticsNode().boundsInRoot.center))
            assertTrue(header.contains(delete.fetchSemanticsNode().boundsInRoot.center))
            edit.performClick()
            compose.runOnIdle { assertEquals(ids.last(), editedId) }
            File(instrumentation.targetContext.getExternalFilesDir(null), "compact-training-card.png").outputStream().use {
                compose.onNode(firstCard).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.onNodeWithTag("statistics-history").performScrollToNode(hasTestTag("statistics-pages"))
            compose.onNodeWithText("Показано", substring = true).assertDoesNotExist()
            compose.onAllNodesWithContentDescription("Следующая").assertCountEquals(1)
            compose.onNodeWithContentDescription("Следующая").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("2 / 2").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Следующая").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Предыдущая").performClick()
            compose.waitUntil(5000) { compose.onAllNodes(firstCard).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Назад к общей статистике").performClick()
            compose.onNodeWithTag("statistics-start").assertTextContains("29.02.2020", substring = true)
            compose.onNodeWithText("Всё время").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("22 тренировок").fetchSemanticsNodes().isNotEmpty() }
        } finally {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            instrumentation.runOnMainSync { store.clear() }
            db.close()
        }
    }
}
