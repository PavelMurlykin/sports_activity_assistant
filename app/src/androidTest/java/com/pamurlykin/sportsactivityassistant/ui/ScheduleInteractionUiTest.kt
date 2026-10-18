package com.pamurlykin.sportsactivityassistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.model.*
import com.pamurlykin.sportsactivityassistant.data.repo.AppRepository
import com.pamurlykin.sportsactivityassistant.ui.screen.MainViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.YearMonth

@Suppress("DEPRECATION")
class ScheduleInteractionUiTest {
    @get:Rule val compose = createComposeRule()

    private fun fixture(workouts: Int = 0, block: (MainViewModel) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, AppDatabase::class.java).build()
        val repo = AppRepository(db)
        runBlocking {
            val owner = repo.localProfileId()
            val sport = db.referenceDao().getSports().first { it.slug == "football" }.id
            val center = repo.getComplexOptionsForSport(sport).first().id
            repeat(workouts) { repo.addCompletedTraining(owner, AddCompletedTrainingInput(sport, center, LocalDate.now(),
                FootballTrainingInput(0, 0, 0, 0, null, null, null))) }
        }
        lateinit var vm: MainViewModel
        val store = ViewModelStore()
        val visible = mutableStateOf(true)
        try {
            instrumentation.runOnMainSync { vm = MainViewModel(repo); store.put("schedule", vm) }
            compose.setContent { if (visible.value) MaterialTheme { SportsActivityApp(vm) } }
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("month-calendar").fetchSemanticsNodes().isNotEmpty() }
            block(vm)
        } finally {
            compose.runOnIdle { visible.value = false; store.clear() }
            db.close()
        }
    }

    @Test fun calendarSwipesMonthsAndCollapsesToHalfThenExpandsAtListStart() = fixture(10) { vm ->
        val calendar = compose.onNodeWithTag("month-calendar")
        val full = calendar.fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(full.left >= root.left && full.right <= root.right)
        calendar.performTouchInput { swipeLeft(durationMillis = 500) }
        compose.waitUntil(5_000) { vm.scheduleState.value?.month == YearMonth.now().plusMonths(1) }
        calendar.performTouchInput { swipeRight(durationMillis = 500) }
        compose.waitUntil(5_000) { vm.scheduleState.value?.month == YearMonth.now() }
        calendar.performTouchInput { swipeUp(durationMillis = 800) }
        compose.waitForIdle()
        assertEquals(full.height / 2, calendar.fetchSemanticsNode().boundsInRoot.height, 2f)
        compose.onNodeWithTag("schedule-trainings").performTouchInput { swipeDown(durationMillis = 800) }
        calendar.performTouchInput { swipeDown(durationMillis = 800) }
        compose.waitForIdle()
        assertEquals(full.height, calendar.fetchSemanticsNode().boundsInRoot.height, 2f)
    }

    @Test fun plannedTimesPrefillEditableResultDurationAndReturnToSchedule() = fixture { vm ->
        val sport = vm.statisticsState.value.sports.first { it.slug == "football" }
        val center = vm.sportsCenters.value.first { c -> !c.isArchived && c.sports.any { it.id == sport.id } }
        runBlocking { vm.addPlannedTraining(AddPlannedTrainingInput(sport.id, center.id, LocalDate.now(), false, 1, null, "23:30", "01:00"), java.util.UUID.randomUUID().toString()) }
        compose.waitUntil(5_000) { vm.scheduleState.value?.selectedDayEvents?.isNotEmpty() == true }
        compose.onNodeWithTag("month-calendar").performTouchInput { swipeUp(durationMillis = 800) }
        compose.onNodeWithText("Записать результат по плану").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("football-6").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("football-6").performScrollTo().assertTextContains("90").performTextReplacement("95")
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(5_000) { vm.scheduleState.value?.selectedDayEvents?.singleOrNull()?.state == ScheduleEventState.COMPLETED }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("month-calendar").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(vm.scheduleState.value!!.selectedDayEvents.single().details.any { it.contains("95") })
    }

    @Test fun statisticsOpensSportFormAndSaveAndCancelReturnToStatistics() = fixture { _ ->
        compose.onNodeWithText("Статистика").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Записать тренировку: Футбол").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Записать тренировку: Футбол").performScrollTo().performClick()
        compose.onNodeWithTag("football-0").assertExists()
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithText("Всего завершённых тренировок").assertExists()
        compose.onNodeWithContentDescription("Записать тренировку: Футбол").performScrollTo().performClick()
        compose.onNodeWithTag("football-0").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Всего завершённых тренировок").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Записать тренировку: Скалолазание").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("route-0-type").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("route-0-type").performScrollTo().assertExists()
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithText("Всего завершённых тренировок").assertExists()
    }
}
