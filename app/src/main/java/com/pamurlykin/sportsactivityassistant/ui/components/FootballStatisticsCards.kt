package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.R
import com.pamurlykin.sportsactivityassistant.data.model.*
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun StatisticsCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(title, Modifier.padding(bottom = 8.dp).semantics { heading() },
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun FootballStatisticsCards(statistics: FootballStatisticsUiModel) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val percentages = listOf(statistics.wins, statistics.draws, statistics.losses).map { percentage(it, statistics.games) }
    val percentStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    val percentColumnWidth = (with(density) {
        percentages.maxOf { measurer.measure(it, percentStyle, softWrap = false).size.width }.toDp()
    } + 20.dp).coerceAtLeast(52.dp)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StatisticsCard(stringResource(R.string.football_statistics_general), Modifier.testTag("football-general")) {
            ResultRow(Icons.Rounded.SportsSoccer, stringResource(R.string.football_statistics_matches), statistics.games,
                percentColumnWidth = percentColumnWidth)
            ResultRow(Icons.Rounded.EmojiEvents, stringResource(R.string.football_statistics_win), statistics.wins,
                percentages[0],
                if (dark) Color(0xFF163F32) else Color(0xFFDDF5EC), if (dark) Color(0xFF7DE1B8) else Color(0xFF008767),
                percentColumnWidth = percentColumnWidth)
            ResultRow(Icons.Rounded.Handshake, stringResource(R.string.football_statistics_draw), statistics.draws,
                percentages[1],
                if (dark) Color(0xFF303B50) else Color(0xFFE9EDF5), if (dark) Color(0xFFCFDAF4) else Color(0xFF46516D),
                percentColumnWidth = percentColumnWidth)
            ResultRow(Icons.Rounded.Close, stringResource(R.string.football_statistics_loss), statistics.losses,
                percentages[2],
                if (dark) Color(0xFF52242B) else Color(0xFFFFE2E3), if (dark) Color(0xFFFFB8BD) else Color(0xFFB71922),
                percentColumnWidth = percentColumnWidth)
        }
        StatisticsCard(stringResource(R.string.football_statistics_detailed), Modifier.testTag("football-detailed")) {
            val titles = listOf(R.string.football_statistics_sum, R.string.football_statistics_max,
                R.string.football_statistics_min, R.string.football_statistics_average).map { stringResource(it) }
            val valueStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            val titleStyle = MaterialTheme.typography.labelSmall
            val labelStyle = MaterialTheme.typography.bodySmall
            val labels = statistics.metrics.map { stringResource(it.metric.label()) }
            val labelWidth = with(density) {
                (labels.maxOfOrNull { label -> label.lines().maxOf { line ->
                    measurer.measure(line, labelStyle, softWrap = false).size.width
                } } ?: 0).toDp()
            } + 27.dp
            val valueWidths = titles.indices.map { index ->
                with(density) {
                    (statistics.metrics.maxOfOrNull { row ->
                        measurer.measure(row.values()[index], valueStyle, softWrap = false).size.width
                    } ?: 0).toDp()
                }
            }
            // Use one set of column widths for the header and every row, including large totals.
            val columnWidths = titles.mapIndexed { index, title ->
                maxOf(with(density) { measurer.measure(title, titleStyle, softWrap = false).size.width.toDp() },
                    valueWidths[index]) + 6.dp
            }
            BoxWithConstraints(Modifier.fillMaxWidth().testTag("football-metrics-layout")) {
                val actualLabelWidth = labelWidth.coerceAtMost(maxWidth * 0.4f)
                val numericWidth = maxWidth - actualLabelWidth
                val totalColumnWidth = columnWidths.sumOf { it.value.toDouble() }.toFloat()
                // All numeric rows share a font size, fitted to the widest value in each column.
                val valueScale = titles.indices.minOf { index ->
                    val available = numericWidth * (columnWidths[index].value / totalColumnWidth) - 7.dp
                    (available.value / valueWidths[index].value.coerceAtLeast(1f)).coerceIn(0.01f, 1f)
                }
                val fittedValueStyle = valueStyle.copy(fontSize = valueStyle.fontSize * valueScale,
                    lineHeight = valueStyle.lineHeight * valueScale)
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(actualLabelWidth))
                        titles.forEachIndexed { index, title ->
                            FootballTableCell(title, Modifier.weight(columnWidths[index].value)
                                .testTag("football-header-$index"), titleStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    statistics.metrics.forEach { row ->
                        StatisticsDivider()
                        Row(Modifier.fillMaxWidth().heightIn(min = 42.dp).testTag("football-summary-${row.metric.name}"),
                            verticalAlignment = Alignment.CenterVertically) {
                            MetricLabel(row.metric, Modifier.width(actualLabelWidth))
                            row.values().forEachIndexed { index, value ->
                                val highlight = row.highlight(index) && value != "—"
                                FootballTableCell(value, Modifier.weight(columnWidths[index].value),
                                    fittedValueStyle.copy(
                                        fontWeight = if (index == 0 || highlight) FontWeight.Bold else FontWeight.Normal),
                                    highlight = highlight)
                            }
                        }
                    }
                }
            }
            if (statistics.distanceCount < statistics.games) Text(
                stringResource(R.string.football_statistics_distance_coverage, statistics.distanceCount, statistics.games),
                Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun FootballMetricSummary.values() = listOf(sum, maximum, minimum, average)
private fun FootballMetricSummary.highlight(index: Int) =
    index == if (metric == FootballMetric.TEAM_CONCEDED) 2 else 1

/** Keep complete numbers on one line without clipping or widening the table. */
@Composable
private fun FootballTableCell(value: String, modifier: Modifier, style: TextStyle,
    highlight: Boolean = false, color: Color = MaterialTheme.colorScheme.onSurface) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.padding(horizontal = 1.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        val textWidth = measurer.measure(value, style, softWrap = false, maxLines = 1).size.width.coerceAtLeast(1)
        val availableWidth = (constraints.maxWidth - with(density) { 4.dp.toPx() } - 1).coerceAtLeast(1f)
        val scale = (availableWidth / textWidth).coerceAtMost(1f)
        val pill = if (highlight) Modifier.background(
            Brush.horizontalGradient(if (dark) listOf(Color(0xFF5D3726), Color(0xFF704127))
                else listOf(Color(0xFFFFDABD), Color(0xFFFFC18B))), RoundedCornerShape(9.dp)) else Modifier
        Text(value, Modifier.fillMaxWidth().then(pill).padding(horizontal = 2.dp, vertical = 4.dp),
            style = style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale),
            maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
            color = if (highlight) { if (dark) Color(0xFFFFD5AA) else Color(0xFFAB3619) } else color)
    }
}

private fun percentage(value: Long, total: Long): String = if (total == 0L) "—" else
    "${BigDecimal.valueOf(value).multiply(BigDecimal(100)).divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP)}%"

@Composable
internal fun ResultRow(icon: ImageVector, label: String, value: Long, percent: String? = null,
    badgeColor: Color = Color.Transparent, badgeText: Color = MaterialTheme.colorScheme.onSurface,
    percentColumnWidth: Dp? = null) {
    StatisticsDivider()
    Row(Modifier.fillMaxWidth().heightIn(min = 43.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (percent != null || percentColumnWidth != null) Box(
            (if (percentColumnWidth == null) Modifier.widthIn(min = 52.dp) else Modifier.width(percentColumnWidth))
            .background(badgeColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            if (percent != null) Text(percent, color = badgeText, style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
internal fun SummaryCell(value: String, modifier: Modifier, bold: Boolean = false, highlight: Boolean = false) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Box(modifier.padding(horizontal = 2.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        val pill = if (highlight && value != "—") Modifier.widthIn(min = 34.dp).background(
            Brush.horizontalGradient(if (dark) listOf(Color(0xFF5D3726), Color(0xFF704127))
                else listOf(Color(0xFFFFDABD), Color(0xFFFFC18B))), RoundedCornerShape(9.dp)) else Modifier
        Text(value, pill.padding(horizontal = 5.dp, vertical = 4.dp), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold || highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight && value != "—") { if (dark) Color(0xFFFFD5AA) else Color(0xFFAB3619) }
                else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun MetricLabel(metric: FootballMetric, modifier: Modifier = Modifier) {
    val label = stringResource(metric.label())
    val style = MaterialTheme.typography.bodySmall
    val measurer = rememberTextMeasurer()
    Row(modifier.padding(end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(metric.icon(), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        BoxWithConstraints(Modifier.weight(1f)) {
            val textWidth = label.lines().maxOf { measurer.measure(it, style, softWrap = false).size.width }.coerceAtLeast(1)
            val scale = ((constraints.maxWidth - 1).coerceAtLeast(1).toFloat() / textWidth).coerceAtMost(1f)
            Text(label, Modifier.fillMaxWidth().testTag("football-label-${metric.name}"), maxLines = 2, softWrap = false,
                style = style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun FootballMetric.icon(): ImageVector = when (this) {
    FootballMetric.TEAM_SCORED -> Icons.Rounded.SportsSoccer
    FootballMetric.TEAM_CONCEDED -> Icons.Rounded.GridOn
    FootballMetric.PERSONAL_GOALS -> Icons.Rounded.SportsSoccer
    FootballMetric.ASSISTS -> Icons.Rounded.Sports
    FootballMetric.DISTANCE -> Icons.AutoMirrored.Rounded.DirectionsRun
}

private fun FootballMetric.label(): Int = when (this) {
    FootballMetric.TEAM_SCORED -> R.string.football_statistics_scored_label
    FootballMetric.TEAM_CONCEDED -> R.string.football_statistics_conceded_label
    FootballMetric.PERSONAL_GOALS -> R.string.football_statistics_personal_goals_label
    FootballMetric.ASSISTS -> R.string.football_statistics_assists_label
    FootballMetric.DISTANCE -> R.string.football_statistics_distance_label
}

@Composable
internal fun StatisticsDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

/** Shared by the expanded statistics history and the selected day in the schedule. */
@Composable
fun TrainingDetails(details: List<String>, football: FootballTrainingInput?, climbing: ClimbingTrainingUiModel? = null) {
    if (climbing != null) {
        ClimbingTrainingDetails(climbing)
        return
    }
    if (football == null) {
        details.forEach { detail ->
            StatisticsDivider()
            Text(detail, Modifier.fillMaxWidth().padding(vertical = 9.dp), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    Column(Modifier.fillMaxWidth().testTag("football-training-details")) {
        DetailValue(Icons.Rounded.SportsSoccer, stringResource(R.string.football_module_schyot_komand),
            "${football.teamGoalsScored}:${football.teamGoalsConceded}")
        DetailValue(Icons.Rounded.EmojiEvents, stringResource(R.string.football_statistics_result), stringResource(when {
            football.teamGoalsScored > football.teamGoalsConceded -> R.string.football_statistics_win
            football.teamGoalsScored == football.teamGoalsConceded -> R.string.football_statistics_draw
            else -> R.string.football_statistics_loss
        }))
        DetailValue(FootballMetric.PERSONAL_GOALS.icon(), stringResource(R.string.football_module_lichnye_goly), football.userGoalsScored.toString())
        DetailValue(FootballMetric.ASSISTS.icon(), stringResource(R.string.football_module_golevye_peredachi), football.userAssists.toString())
        DetailValue(Icons.AutoMirrored.Rounded.DirectionsRun, stringResource(R.string.football_statistics_distance),
            football.distanceKm?.setScale(2, RoundingMode.HALF_UP)?.toPlainString()?.replace('.', ',') ?: "—")
        football.playersPerTeam?.let { DetailValue(Icons.Rounded.Groups, stringResource(R.string.football_statistics_players), it.toString()) }
        football.durationMinutes?.let { DetailValue(Icons.Rounded.Schedule, stringResource(R.string.football_statistics_minutes), it.toString()) }
    }
}

@Composable
internal fun DetailValue(icon: ImageVector, label: String, value: String) {
    StatisticsDivider()
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(23.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.widthIn(max = 160.dp), style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
    }
}
