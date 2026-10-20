package com.pamurlykin.sportsactivityassistant.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.model.*
import com.pamurlykin.sportsactivityassistant.ui.components.FootballStatisticsCards
import com.pamurlykin.sportsactivityassistant.ui.components.TrainingDetails
import com.pamurlykin.sportsactivityassistant.ui.theme.SportsActivityTheme
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.math.BigDecimal

@Suppress("DEPRECATION")
class FootballStatisticsCardsUiTest {
    @get:Rule val compose = createComposeRule()
    private val statistics = FootballStatisticsUiModel(86, 45, 5, 36, listOf(
        FootballMetricSummary(FootballMetric.TEAM_SCORED, "1034", "26", "4", "12,02"),
        FootballMetricSummary(FootballMetric.TEAM_CONCEDED, "980", "23", "5", "11,40"),
        FootballMetricSummary(FootballMetric.PERSONAL_GOALS, "150", "6", "0", "1,74"),
        FootballMetricSummary(FootballMetric.ASSISTS, "157", "5", "0", "1,83"),
        FootballMetricSummary(FootballMetric.DISTANCE, "212,66", "5,20", "1,92", "3,87"),
    ), 55)

    @Test fun mockupShowsTheFiveRequestedMetricsAndOutcomePercentages() {
        compose.setContent { SportsActivityTheme {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()).padding(16.dp)) {
                FootballStatisticsCards(statistics)
            }
        } }
        compose.onNodeWithText("Сыграно матчей", substring = true).assertExists()
        listOf("52%", "6%", "42%").forEach { compose.onNodeWithText(it, substring = true).assertExists() }
        FootballMetric.entries.forEach { compose.onNodeWithTag("football-summary-${it.name}").performScrollTo().assertIsDisplayed() }
        compose.onNodeWithText("Учтённое время").assertDoesNotExist()
        compose.onNodeWithText("Общие показатели").performScrollTo()
        screenshot("football-statistics.png")
    }

    @Test fun narrowLargeFontTableCanScrollToTheAverageAndDetailsKeepOptionalValues() {
        compose.setContent { SportsActivityTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                Column(Modifier.width(320.dp).fillMaxHeight().background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState()).padding(16.dp)) {
                    FootballStatisticsCards(statistics)
                    TrainingDetails(emptyList(), FootballTrainingInput(3, 1, 2, 1, BigDecimal("7.3"), 6, 90))
                }
            }
        } }
        compose.onNodeWithText("Среднее").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("football-training-details").performScrollTo()
        compose.onNodeWithText("7,30", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("90", substring = true).performScrollTo().assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), name)
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
