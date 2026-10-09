package com.pamurlykin.sportsactivityassistant.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import com.pamurlykin.sportsactivityassistant.ui.components.PlanAction
import com.pamurlykin.sportsactivityassistant.ui.components.PlanActionDialog
import com.pamurlykin.sportsactivityassistant.data.model.ScheduleEventState
import com.pamurlykin.sportsactivityassistant.ui.components.TrainingActions
import com.pamurlykin.sportsactivityassistant.ui.components.TrainingActionDialog
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamurlykin.sportsactivityassistant.ui.components.AddCompletedTrainingDialog
import com.pamurlykin.sportsactivityassistant.ui.components.AddPlannedTrainingDialog
import com.pamurlykin.sportsactivityassistant.ui.components.MonthCalendar
import com.pamurlykin.sportsactivityassistant.ui.components.ReadStateNotice
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.res.stringResource
import com.pamurlykin.sportsactivityassistant.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: MainViewModel,
) {
    val readState by viewModel.scheduleReadState.collectAsStateWithLifecycle()
    val scheduleState = readState.data
    val statisticsRead by viewModel.statisticsReadState.collectAsStateWithLifecycle()
    val centersRead by viewModel.centersReadState.collectAsStateWithLifecycle()
    val statisticsState by viewModel.statisticsState.collectAsStateWithLifecycle()
    val centers by viewModel.sportsCenters.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<TrainingDialog?>(null) }
    var actionId by rememberSaveable { mutableLongStateOf(0) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var planKey by rememberSaveable { mutableStateOf<String?>(null) }
    var planRecurring by rememberSaveable { mutableStateOf(false) }
    var planAction by rememberSaveable { mutableStateOf(PlanAction.EDIT) }
    planKey?.let { androidx.compose.runtime.key(it, planAction) {
        PlanActionDialog(it, planRecurring, planAction, viewModel) { planKey = null }
    } }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { viewModel.refreshToday() }
    if (actionId != 0L) TrainingActionDialog(actionId, deleting, viewModel) { actionId = 0 }

    if (dialog == TrainingDialog.CHOICE) {
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Добавить тренировку") },
            text = { Text("Запишите результат состоявшейся тренировки или добавьте будущую в план.") },
            confirmButton = { TextButton(onClick = { dialog = TrainingDialog.COMPLETED }) { Text("Записать результат") } },
            dismissButton = { TextButton(onClick = { dialog = TrainingDialog.PLANNED }) { Text("Запланировать") } },
        )
    }
    if (dialog == TrainingDialog.PLANNED) {
        AddPlannedTrainingDialog(
            sports = statisticsState.sports,
            centers = centers,
            onSaveCenter = viewModel::saveSportsCenter,
            onDismiss = { dialog = null },
            initialDate = scheduleState?.selectedDate ?: java.time.LocalDate.now(),
            onSave = { input, requestId -> viewModel.addPlannedTraining(input, requestId) },
            onSaved = { dialog = null },
        )
    }
    if (dialog == TrainingDialog.COMPLETED) {
        AddCompletedTrainingDialog(
            sports = statisticsState.sports,
            initialDate = scheduleState?.selectedDate ?: java.time.LocalDate.now(),
            centers = centers,
            onSaveCenter = viewModel::saveSportsCenter,
            onDismiss = { dialog = null },
            onSave = { input, requestId -> viewModel.saveCompletedTraining(input, requestId) },
            onSaved = { dialog = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.nav_schedule), fontWeight = FontWeight.Bold)
                        Text(
                            "Календарь завершённых и запланированных тренировок",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!readState.loading && !readState.failed && statisticsRead.data != null && centersRead.data != null) {
                FloatingActionButton(onClick = { dialog = TrainingDialog.CHOICE }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_workout))
                }
            }
        },
    ) { innerPadding ->
        if (readState.data == null || statisticsRead.failed || centersRead.failed) {
            Column(Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                ReadStateNotice(readState.failed || statisticsRead.failed || centersRead.failed, viewModel::retryReads)
            }
        }
        scheduleState?.takeUnless { statisticsRead.failed || centersRead.failed }?.let { state ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = innerPadding.calculateTopPadding() + 12.dp, bottom = innerPadding.calculateBottomPadding() + 92.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    MonthCalendar(
                        state = state,
                        onPreviousMonth = viewModel::previousMonth,
                        onNextMonth = viewModel::nextMonth,
                        onDaySelected = viewModel::selectDate,
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "${state.selectedDate.dayOfMonth}.${state.selectedDate.monthValue}.${state.selectedDate.year}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (state.selectedDayEvents.isEmpty()) "На выбранный день тренировок пока нет" else "Тренировки на выбранный день",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (state.selectedDayEvents.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "День свободен",
                            body = "Добавьте разовую или повторяющуюся тренировку, и она сразу появится в календаре.",
                        )
                    }
                } else {
                    items(state.selectedDayEvents, key = { it.id }) { event ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = event.sportTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (event.state == com.pamurlykin.sportsactivityassistant.data.model.ScheduleEventState.COMPLETED) {
                                    TrainingActions(event.sportSlug,
                                        { deleting = false; actionId = event.id.removePrefix("completed-").toLong() },
                                        { deleting = true; actionId = event.id.removePrefix("completed-").toLong() })
                                }
                                event.planKey?.let { target ->
                                    TextButton(onClick = { planKey = target; planRecurring = event.isRecurring; planAction = PlanAction.EDIT }) { Text("Изменить план") }
                                    if (event.state == ScheduleEventState.PLANNED) {
                                        TextButton(onClick = { planKey = target; planRecurring = event.isRecurring; planAction = PlanAction.COMPLETE }) { Text("Записать результат по плану") }
                                        TextButton(onClick = { planKey = target; planRecurring = event.isRecurring; planAction = PlanAction.CANCEL }) { Text("Отменить тренировку") }
                                    }
                                }
                                if (event.linkedPlan) Text("Результат связан с планом", style = MaterialTheme.typography.bodySmall)
                                event.details.forEach { detail ->
                                    Text(
                                        text = detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = event.complexName,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = if (event.state == com.pamurlykin.sportsactivityassistant.data.model.ScheduleEventState.COMPLETED) {
                                        "Состоявшаяся тренировка"
                                    } else if (event.state == ScheduleEventState.CANCELED) {
                                        "Отменена" + if (event.isRecurring) " · серия" else ""
                                    } else if (event.isRecurring) {
                                        "Запланирована · серия"
                                    } else {
                                        "Запланирована"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class TrainingDialog {
    CHOICE,
    PLANNED,
    COMPLETED,
}

@Composable
private fun EmptyStateCard(
    title: String,
    body: String,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
