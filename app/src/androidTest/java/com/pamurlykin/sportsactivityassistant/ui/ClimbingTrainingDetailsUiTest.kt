package com.pamurlykin.sportsactivityassistant.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.model.*
import com.pamurlykin.sportsactivityassistant.ui.components.CompletedTrainingCard
import com.pamurlykin.sportsactivityassistant.ui.theme.SportsActivityTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

class ClimbingTrainingDetailsUiTest {
    @get:Rule val compose = createComposeRule()
    private val training = ClimbingTrainingUiModel(listOf(
        ClimbingRouteInput(ClimbingWorkoutType.DIFFICULTY, "6b+", true, 3),
        ClimbingRouteInput(ClimbingWorkoutType.BOULDERING, "7A", false, 2),
        ClimbingRouteInput(ClimbingWorkoutType.SPEED, "", true, speedCourse = "standard_15m", speedTimeMillis = 8530),
        ClimbingRouteInput(ClimbingWorkoutType.SPEED, "", false, 4, speedCourse = "other")), 75)

    @Test fun sharedCompletedCardShowsGradesRepeatsOutcomeAndSpeedTimesInsteadOfTextFallback() {
        compose.setContent { SportsActivityTheme(ThemeMode.LIGHT) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()).padding(16.dp)) {
                CompletedTrainingCard("22.10.2026", "climbing", "Высота", listOf("Старая подробная строка"), null, {}, {}, climbing = training)
            }
        } }
        compose.onNodeWithText("Старая подробная строка").assertDoesNotExist()
        compose.onNodeWithTag("climbing-training-route-0").assertTextContains("6b+").assertTextContains("×3").assertTextContains("Пройдена")
        compose.onNodeWithTag("climbing-training-route-1").assertTextContains("7A").assertTextContains("×2").assertTextContains("Не пройдена")
        compose.onNodeWithTag("climbing-training-route-2").assertTextContains("Эталонная 15 м").assertTextContains("8,53 с")
        compose.onNodeWithTag("climbing-training-route-3").assertTextContains("×4").assertTextContains("—")
        compose.onNodeWithText("Длительность, мин", substring = true).assertExists()
        screenshot("climbing-training-details.png")
    }

    @Test fun darkLargeFontKeepsHistoricalGradeAndUnknownTimeVisibleWithoutHorizontalScroll() {
        val historical = ClimbingRouteInput(ClimbingWorkoutType.BOULDERING, " 7B ", true, 12, gradingSystem = ClimbingDifficultyCatalog.LEGACY)
        compose.setContent { SportsActivityTheme(ThemeMode.DARK) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                Column(Modifier.width(320.dp).fillMaxHeight().background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState()).padding(16.dp)) {
                    CompletedTrainingCard("Скалолазание", "climbing", "Высота", emptyList(), null, {}, {},
                        climbing = ClimbingTrainingUiModel(listOf(historical, training.routes.last())))
                }
            }
        } }
        compose.onNodeWithTag("climbing-training-route-0").performScrollTo().assertTextContains(" 7B ").assertTextContains("×12")
            .assertTextContains("Историческая — шкала не подтверждена")
        compose.onNodeWithTag("climbing-training-route-1").performScrollTo().assertTextContains("—").assertTextContains("Не пройдена")
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)).assertCountEquals(0)
        screenshot("climbing-training-details-dark-large.png")
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), name)
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
