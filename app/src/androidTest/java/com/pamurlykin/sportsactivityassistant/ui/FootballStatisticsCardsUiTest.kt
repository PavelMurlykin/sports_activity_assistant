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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.model.*
import com.pamurlykin.sportsactivityassistant.ui.components.FootballStatisticsCards
import com.pamurlykin.sportsactivityassistant.ui.components.TrainingDetails
import com.pamurlykin.sportsactivityassistant.ui.theme.SportsActivityTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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
        assertTableFits(statistics)
        compose.onNodeWithText("Учтённое время").assertDoesNotExist()
        compose.onNodeWithText("Общие показатели").performScrollTo()
        screenshot("football-statistics.png")
    }

    @Test fun narrowLargeFontTableWrapsLabelsAndKeepsNumbersOnOneLine() {
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
        assertTableFits(statistics)
        val label = compose.onNodeWithTag("football-label-${FootballMetric.DISTANCE.name}")
        assertTrue("The first column must wrap long labels", textLayout(label).lineCount > 1)
        screenshot("football-statistics-narrow-large.png")
        compose.onNodeWithTag("football-training-details").performScrollTo()
        compose.onNodeWithText("7,30", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("90", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun phoneWidthKeepsEveryColumnAndLargeTotalsInsideTheCard() {
        val large = statistics.copy(metrics = listOf(FootballMetricSummary(FootballMetric.TEAM_SCORED,
            "9223372036854775807", "2147483647", "0", "2147483647,00")))
        compose.setContent { SportsActivityTheme {
            Column(Modifier.width(320.dp).fillMaxHeight().background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()).padding(16.dp)) { FootballStatisticsCards(large) }
        } }
        assertTableFits(large)
        screenshot("football-statistics-phone-large-totals.png")
    }

    private fun assertTableFits(data: FootballStatisticsUiModel) {
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)).assertCountEquals(0)
        (0..3).forEach { compose.onAllNodesWithTag("football-header-$it").assertCountEquals(1) }
        data.metrics.forEach { metric ->
            val parent = hasTestTag("football-summary-${metric.metric.name}")
            compose.onNode(parent).performScrollTo().assertIsDisplayed()
            val row = compose.onNode(parent).fetchSemanticsNode().boundsInRoot
            val layout = compose.onNodeWithTag("football-metrics-layout").fetchSemanticsNode().boundsInRoot
            val label = compose.onNodeWithTag("football-label-${metric.metric.name}").fetchSemanticsNode().boundsInRoot
            var previousRight = label.right
            listOf(metric.sum, metric.maximum, metric.minimum, metric.average).forEachIndexed { index, value ->
                val node = compose.onNode(hasText(value) and hasAnyAncestor(parent))
                node.assertIsDisplayed()
                val bounds = node.fetchSemanticsNode().boundsInRoot
                val text = textLayout(node)
                assertEquals("Value $value must remain on one line", 1, text.lineCount)
                assertTrue("Value $value must be complete: size=${text.size}, paragraph=${text.multiParagraph.width}, " +
                    "line=${text.getLineLeft(0)}..${text.getLineRight(0)}, font=${text.layoutInput.style.fontSize}",
                    !text.didOverflowWidth && !text.isLineEllipsized(0))
                assertTrue("Value $value must fit horizontally", bounds.left >= layout.left && bounds.right <= layout.right)
                assertTrue("Columns must not overlap", bounds.left >= previousRight)
                assertEquals("All four values must share a row", row.center.y, bounds.center.y, 1f)
                val header = compose.onNodeWithTag("football-header-$index").fetchSemanticsNode().boundsInRoot
                assertEquals("Values must align with the column header", header.center.x, bounds.center.x, 1f)
                previousRight = bounds.right
            }
        }
    }

    private fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), name)
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
