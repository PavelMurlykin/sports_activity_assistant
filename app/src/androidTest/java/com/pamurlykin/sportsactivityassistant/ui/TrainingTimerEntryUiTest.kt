package com.pamurlykin.sportsactivityassistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.repo.AppRepository
import com.pamurlykin.sportsactivityassistant.data.seed.testCenterForSport
import com.pamurlykin.sportsactivityassistant.ui.screen.MainViewModel
import com.pamurlykin.sportsactivityassistant.ui.screen.TrainingEntryScreen
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

@Suppress("DEPRECATION")
class TrainingTimerEntryUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun runningTimerAndFieldsSurviveReferenceReloadAndRestorationDuringLoading() {
        val holdQueries = AtomicBoolean(false)
        val releaseQueries = CountDownLatch(1)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, AppDatabase::class.java)
            .setQueryCallback({ _, _ ->
                if (holdQueries.get()) releaseQueries.await(20, TimeUnit.SECONDS)
            }, Executor { it.run() }).build()
        val repo = AppRepository(db)
        val sportId = runBlocking {
            repo.localProfileId()
            db.referenceDao().getSports().first { it.slug == "football" }.id.also { repo.testCenterForSport(it) }
        }
        val store = ViewModelStore()
        val visible = mutableStateOf(true)
        lateinit var viewModel: MainViewModel
        try {
            instrumentation.runOnMainSync {
                viewModel = MainViewModel(repo)
                store.put("timer-entry", viewModel)
            }
            val restoration = StateRestorationTester(compose)
            restoration.setContent { if (visible.value) MaterialTheme {
                TrainingEntryScreen(viewModel, LocalDate.now(), sportId, 0L, null, {})
            } }
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("training-timer").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("football-6").performScrollTo().performTextReplacement("12")
            compose.onNodeWithTag("football-4").performScrollTo().performTextReplacement("3.5")
            compose.onNodeWithTag("training-timer-start").performScrollTo().performClick()
            compose.onNodeWithTag("training-timer-stop").assertIsEnabled()

            // Restarting the reference collectors after a background interval emits
            // loading and temporarily removes the editor from composition.
            holdQueries.set(true)
            compose.runOnIdle { viewModel.retryReads() }
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("read-loading").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("training-timer").assertDoesNotExist()
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithTag("read-loading").assertExists()
            holdQueries.set(false)
            releaseQueries.countDown()

            compose.waitUntil(5_000) { compose.onAllNodesWithTag("training-timer").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("training-timer-stop").performScrollTo().assertIsEnabled().performClick()
            compose.onNodeWithTag("training-timer-value").assert(hasText("00:12:", substring = true))
            compose.onNodeWithTag("football-6").assertTextContains("12")
            compose.onNodeWithTag("football-4").performScrollTo().assertTextContains("3.5")
        } finally {
            holdQueries.set(false)
            releaseQueries.countDown()
            compose.runOnIdle { visible.value = false; store.clear() }
            db.close()
        }
    }
}
