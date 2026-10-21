package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.R
import com.pamurlykin.sportsactivityassistant.data.model.*

/** Both history and the selected schedule day render the same structured route rows. */
@Composable
fun ClimbingTrainingDetails(training: ClimbingTrainingUiModel) {
    Column(Modifier.fillMaxWidth().testTag("climbing-training-details")) {
        training.durationMinutes?.let {
            DetailValue(Icons.Rounded.Schedule, stringResource(R.string.duration_minutes), it.toString())
        }
        training.routes.forEachIndexed { index, route ->
            val type = route.workoutType
            val grade = ClimbingDifficultyCatalog.find(route.gradingSystem, route.gradeCode.orEmpty())?.takeIf {
                route.gradingSystem == ClimbingDifficultyCatalog.systemFor(type) &&
                    ClimbingDifficultyCatalog.find(route.gradingSystem, route.routeDifficulty)?.code == it.code
            }
            val course = SpeedCourse.entries.firstOrNull { it.code == route.speedCourse }
                ?.takeIf { route.gradingSystem == ClimbingDifficultyCatalog.NONE }
            val confirmed = grade != null || type == ClimbingWorkoutType.SPEED && course != null
            val category = course?.title ?: grade?.label ?: route.routeDifficulty.ifBlank {
                stringResource(R.string.climbing_difficulty_catalog_bez_kategorii)
            }
            StatisticsDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp).testTag("climbing-training-route-$index")
                .semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(when (type) {
                    ClimbingWorkoutType.DIFFICULTY -> Icons.Rounded.Terrain
                    ClimbingWorkoutType.BOULDERING -> Icons.Rounded.Landscape
                    ClimbingWorkoutType.SPEED -> Icons.Rounded.Bolt
                    ClimbingWorkoutType.UNKNOWN -> Icons.Rounded.History
                }, null, Modifier.padding(top = 4.dp).size(23.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(route.legacyWorkoutType ?: type.title, Modifier.align(Alignment.CenterVertically),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (grade != null) SummaryCell(category, Modifier, bold = true, highlight = true)
                        else Text(category, Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.bodyMedium)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        RouteValue(Icons.Rounded.Replay, "×${route.repeatCount}", stringResource(R.string.climbing_statistics_attempts))
                        val resultColor = if (route.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        RouteValue(if (route.isCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                            stringResource(if (route.isCompleted) R.string.sport_editors_proydena else R.string.sport_editors_ne_proydena), color = resultColor)
                        if (type == ClimbingWorkoutType.SPEED) RouteValue(Icons.Rounded.Timer,
                            route.speedTimeMillis?.let { "${formatSpeedTime(it)} ${stringResource(R.string.climbing_seconds)}" } ?: "—",
                            stringResource(R.string.climbing_time_input))
                    }
                    if (!confirmed) Text(stringResource(R.string.climbing_difficulty_catalog_istoricheskaya_shkala_ne_podtverzhdena),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RouteValue(icon: ImageVector, value: String, description: String? = null,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, description, Modifier.size(16.dp), tint = color)
        Text(value, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
