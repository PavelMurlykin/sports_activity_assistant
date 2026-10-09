package com.pamurlykin.sportsactivityassistant.ui.screen

import com.pamurlykin.sportsactivityassistant.ui.components.AdaptiveAlertDialog

import com.pamurlykin.sportsactivityassistant.text.AppText

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
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
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
        AdaptiveAlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(AppText.get(R.string.schedule_screen_dobavit_trenirovku)) },
            text = { Text(AppText.get(R.string.schedule_screen_zapishite_rezultat_sostoyavsheysya_trenirovki_ili)) },
            confirmButton = { TextButton(onClick = { dialog = TrainingDialog.COMPLETED }) { Text(AppText.get(R.string.schedule_screen_zapisat_rezultat)) } },
            dismissButton = { TextButton(onClick = { dialog = TrainingDialog.PLANNED }) { Text(AppText.get(R.string.schedule_screen_zaplanirovat)) } },
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
                        if (LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE) Text(
                            AppText.get(R.string.schedule_screen_kalendar_zavershyonnyh_i_zaplanirovannyh_trenirovok),
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
                            text = if (state.selectedDayEvents.isEmpty()) AppText.get(R.string.schedule_screen_na_vybrannyy_den_trenirovok_poka) else AppText.get(R.string.schedule_screen_trenirovki_na_vybrannyy_den),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (state.selectedDayEvents.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = AppText.get(R.string.schedule_screen_den_svoboden),
                            body = AppText.get(R.string.schedule_screen_dobavte_razovuyu_ili_povtoryayuschuyusya_trenirovku),
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
                                    TextButton(onClick = { planKey = target; planRecurring = event.isRecurring; planAction = PlanAction.EDIT }) { Text(AppText.get(R.string.schedule_screen_izmenit_plan)) }
                                    if (event.state == ScheduleEventState.PLANNED) {
                                        TextButton(onClick = { planKey = target; planRecurring = event.isRecurring; planAction = PlanAction.COMPLETE }) { Text(AppText.get(R.string.schedule_screen_zapisat_rezultat_po_planu)) }
                                        TextButton(onClick = { planKey = target; planRecurring = event.isRecurring; planAction = PlanAction.CANCEL }) { Text(AppText.get(R.string.schedule_screen_otmenit_trenirovku)) }
                                    }
                                }
                                if (event.linkedPlan) Text(AppText.get(R.string.schedule_screen_rezultat_svyazan_s_planom), style = MaterialTheme.typography.bodySmall)
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
                                        AppText.get(R.string.schedule_screen_sostoyavshayasya_trenirovka)
                                    } else if (event.state == ScheduleEventState.CANCELED) {
                                        AppText.get(R.string.schedule_screen_otmenena) + if (event.isRecurring) AppText.get(R.string.schedule_screen_seriya) else ""
                                    } else if (event.isRecurring) {
                                        AppText.get(R.string.schedule_screen_zaplanirovana_seriya)
                                    } else {
                                        AppText.get(R.string.schedule_screen_zaplanirovana)
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
