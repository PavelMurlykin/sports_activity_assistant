package com.pamurlykin.sportsactivityassistant.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.model.ScheduleDayUiModel
import com.pamurlykin.sportsactivityassistant.data.model.ScheduleMonthUiModel
import com.pamurlykin.sportsactivityassistant.ui.components.MonthCalendar
import com.pamurlykin.sportsactivityassistant.ui.components.ReadStateNotice
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

@Suppress("DEPRECATION")
class LifecycleAndCalendarUiTest {
    @get:Rule val compose = createComposeRule()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test fun stoppedScreenDoesNotCollectUntilStartedAgain() {
        lateinit var owner: Owner
        val source = MutableStateFlow(0)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            owner = Owner()
            owner.registry.currentState = Lifecycle.State.STARTED
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                val value by source.collectAsStateWithLifecycle()
                Text("value=$value")
            }
        }
        compose.onNodeWithText("value=0").assertExists()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.waitUntil(5000) { source.subscriptionCount.value == 0 }
        compose.runOnIdle { source.value = 99 }
        compose.onNodeWithText("value=0").assertExists()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.waitUntil(5000) { compose.onAllNodesWithText("value=99").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun errorHasAnActionAndLoadingIsNotAnEmptyHistory() {
        val failed = androidx.compose.runtime.mutableStateOf(false)
        var retries = 0
        compose.setContent { MaterialTheme { ReadStateNotice(failed.value, { retries++ }) } }
        compose.onNodeWithTag("read-loading").assertExists()
        compose.onNodeWithText("Повторить").assertDoesNotExist()
        compose.runOnIdle { failed.value = true }
        compose.onNodeWithTag("read-error").assertExists()
        compose.onNodeWithText("Повторить").assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithTag("read-loading").assertDoesNotExist()
    }

    @Test fun compactCalendarKeepsDayTargetsAndSelectionAtDoubleFontScale() {
        val first = LocalDate.parse("2020-09-28")
        val selected = androidx.compose.runtime.mutableStateOf(first)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                MaterialTheme {
                    Column(Modifier.requiredWidth(300.dp).verticalScroll(rememberScrollState())) {
                        val days = List(35) { offset ->
                            val day = first.plusDays(offset.toLong())
                            ScheduleDayUiModel(day, day.monthValue == 10, day == first, emptyList())
                        }
                        MonthCalendar(ScheduleMonthUiModel(YearMonth.of(2020, 10), selected.value, days, emptyList()),
                            {}, {}, { selected.value = it })
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Предыдущий месяц").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("2020-09-28", substring = true)
            .performScrollTo().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(172.dp).assertIsSelected()
        compose.onNodeWithContentDescription("2020-10-04", substring = true).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(LocalDate.parse("2020-10-04"), selected.value) }
        compose.onNodeWithContentDescription("2020-10-04", substring = true).assertIsSelected()
    }
}
