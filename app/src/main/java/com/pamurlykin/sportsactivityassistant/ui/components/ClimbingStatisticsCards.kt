package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.R
import com.pamurlykin.sportsactivityassistant.data.model.*

@Composable
fun ClimbingStatisticsCards(statistics: ClimbingStatisticsUiModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StatisticsCard(ClimbingWorkoutType.SPEED.title, Modifier.testTag("climbing-speed")) {
            val rows = statistics.speed.ifEmpty {
                listOf(ClimbingSpeedStatistics(null, ClimbingAttemptCounts(), 0, null, null))
            }
            rows.forEachIndexed { index, speed ->
                if (index > 0) Spacer(Modifier.height(16.dp))
                if (statistics.speed.isNotEmpty()) Text(speed.course?.title ?: stringResource(R.string.climbing_statistics_unknown_course),
                    Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                AttemptRows(speed.counts)
                DetailValue(Icons.Rounded.Timer, stringResource(R.string.climbing_statistics_best), formatSpeedTime(speed.bestTimeMillis))
                DetailValue(Icons.Rounded.AvTimer, stringResource(R.string.climbing_statistics_average), formatSpeedTime(speed.averageTimeMillis))
                if (speed.timedAttempts < speed.counts.completed) Text(
                    stringResource(R.string.climbing_statistics_time_coverage, speed.timedAttempts, speed.counts.completed),
                    Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        GradeStatisticsCard(statistics.difficulty)
        GradeStatisticsCard(statistics.bouldering)
        if (statistics.unknown.attempts > 0) StatisticsCard(stringResource(R.string.climbing_statistics_unknown)) {
            AttemptRows(statistics.unknown)
        }
    }
}

@Composable
private fun AttemptRows(counts: ClimbingAttemptCounts) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    ResultRow(Icons.Rounded.Replay, stringResource(R.string.climbing_statistics_attempts), counts.attempts)
    ResultRow(Icons.Rounded.CheckCircle, stringResource(R.string.climbing_statistics_completed), counts.completed, counts.percentage,
        if (dark) Color(0xFF163F32) else Color(0xFFDDF5EC), if (dark) Color(0xFF7DE1B8) else Color(0xFF008767))
}

@Composable
private fun GradeStatisticsCard(statistics: ClimbingDisciplineStatistics) {
    StatisticsCard(statistics.type.title, Modifier.testTag("climbing-${statistics.type.storageValue}")) {
        val fontScale = LocalDensity.current.fontScale
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val tableWidth = maxOf(maxWidth, 310.dp * fontScale)
            Column(Modifier.horizontalScroll(rememberScrollState()).width(tableWidth)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(R.string.climbing_statistics_grade, R.string.climbing_statistics_attempts,
                        R.string.climbing_statistics_completed, R.string.climbing_statistics_percent).forEachIndexed { index, label ->
                        Text(stringResource(label), Modifier.weight(if (index == 0) 1.5f else 1f),
                            style = MaterialTheme.typography.labelSmall, textAlign = if (index == 0) TextAlign.Start else TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                statistics.grades.forEach { grade -> GradeRow(grade.label, grade.counts) }
                if (statistics.ungraded.attempts > 0) GradeRow(stringResource(R.string.climbing_statistics_ungraded), statistics.ungraded)
                GradeRow(stringResource(R.string.climbing_statistics_total), statistics.counts, total = true)
            }
        }
    }
}

@Composable
private fun GradeRow(label: String, counts: ClimbingAttemptCounts, total: Boolean = false) {
    StatisticsDivider()
    Row(Modifier.fillMaxWidth().heightIn(min = 43.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1.5f).padding(vertical = 8.dp, horizontal = 2.dp), style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (total) FontWeight.Bold else FontWeight.Normal,
            color = if (total) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        SummaryCell(counts.attempts.toString(), Modifier.weight(1f), bold = total)
        SummaryCell(counts.completed.toString(), Modifier.weight(1f), bold = total, highlight = !total && counts.completed > 0)
        SummaryCell(counts.percentage, Modifier.weight(1f), bold = total)
    }
}
