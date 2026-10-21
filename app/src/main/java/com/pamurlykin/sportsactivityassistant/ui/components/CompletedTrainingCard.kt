package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.R
import com.pamurlykin.sportsactivityassistant.data.model.FootballTrainingInput

@Composable
fun CompletedTrainingCard(
    title: String,
    sportSlug: String,
    center: String,
    details: List<String>,
    football: FootballTrainingInput?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    linkedPlan: Boolean = false,
) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().testTag("completed-training-header"), verticalAlignment = Alignment.CenterVertically) {
                SportBadge(sportSlug, size = 24.dp)
                Icon(Icons.Rounded.Check, stringResource(R.string.calendar_completed), Modifier.padding(horizontal = 4.dp).size(18.dp))
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                TrainingActions(sportSlug, onEdit, onDelete)
            }
            Text(center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TrainingDetails(details, football)
            if (linkedPlan) Text(stringResource(R.string.schedule_screen_rezultat_svyazan_s_planom), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.schedule_screen_sostoyavshayasya_trenirovka), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
