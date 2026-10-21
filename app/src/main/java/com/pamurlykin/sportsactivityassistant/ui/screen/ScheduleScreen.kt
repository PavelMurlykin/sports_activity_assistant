package com.pamurlykin.sportsactivityassistant.ui.screen

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamurlykin.sportsactivityassistant.R
import com.pamurlykin.sportsactivityassistant.data.model.*
import com.pamurlykin.sportsactivityassistant.text.AppText
import com.pamurlykin.sportsactivityassistant.ui.components.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: MainViewModel,
    onAddResult: (LocalDate) -> Unit = {},
    onEditResult: (Long) -> Unit = {},
    onCompletePlan: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val readState by viewModel.scheduleReadState.collectAsStateWithLifecycle()
    val statisticsRead by viewModel.statisticsReadState.collectAsStateWithLifecycle()
    val centersRead by viewModel.centersReadState.collectAsStateWithLifecycle()
    val statistics by viewModel.statisticsState.collectAsStateWithLifecycle()
    val centers by viewModel.sportsCenters.collectAsStateWithLifecycle()
    var choice by rememberSaveable { mutableStateOf(false) }
    var planning by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableLongStateOf(0L) }
    var planKey by rememberSaveable { mutableStateOf<String?>(null) }
    var planRecurring by rememberSaveable { mutableStateOf(false) }
    var planAction by rememberSaveable { mutableStateOf(PlanAction.EDIT) }
    val state = readState.data
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { viewModel.refreshToday() }
    if (deleteId != 0L) TrainingActionDialog(deleteId, true, viewModel) { deleteId = 0 }
    planKey?.let { key -> key(key, planAction) { PlanActionDialog(key, planRecurring, planAction, viewModel) { planKey = null } } }
    if (choice) AdaptiveAlertDialog(onDismissRequest = { choice = false },
        title = { Text(stringResource(R.string.schedule_screen_dobavit_trenirovku)) },
        text = { Text(stringResource(R.string.schedule_screen_zapishite_rezultat_sostoyavsheysya_trenirovki_ili)) },
        confirmButton = { TextButton(onClick = { choice = false; onAddResult(state?.selectedDate ?: LocalDate.now()) }) {
            Text(stringResource(R.string.schedule_screen_zapisat_rezultat)) } },
        dismissButton = { TextButton(onClick = { choice = false; planning = true }) { Text(stringResource(R.string.schedule_screen_zaplanirovat)) } })
    if (planning) AddPlannedTrainingDialog(statistics.sports, centers, { planning = false }, viewModel::addPlannedTraining,
        viewModel::saveSportsCenter, initialDate = state?.selectedDate ?: LocalDate.now(), onSaved = { planning = false })

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_schedule), fontWeight = FontWeight.Bold) }, actions = { SettingsAction(onOpenSettings) }) },
        floatingActionButton = {
            if (state != null && !readState.failed && statisticsRead.data != null && centersRead.data != null && statistics.sports.isNotEmpty()) {
                FloatingActionButton(onClick = { choice = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.add_workout)) }
            }
        }) { padding ->
        if (state == null || statisticsRead.failed || centersRead.failed) {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                ReadStateNotice(readState.failed || statisticsRead.failed || centersRead.failed, viewModel::retryReads)
            }
        } else {
            val list = rememberLazyListState()
            var collapsed by rememberSaveable { mutableFloatStateOf(0f) }
            LaunchedEffect(state.selectedDate) { list.scrollToItem(0) }
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val fullHeight = maxHeight * 0.78f
                val collapseRange = with(LocalDensity.current) { (fullHeight / 2).toPx() }
                val connection = remember(list, collapseRange) {
                    object : NestedScrollConnection {
                        fun consume(delta: Float): Offset {
                            if (collapseRange <= 0) return Offset.Zero
                            val old = collapsed
                            collapsed = (old - delta / collapseRange).coerceIn(0f, 1f)
                            return Offset(0f, (old - collapsed) * collapseRange)
                        }
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            if (available.y < 0 || !list.canScrollBackward) return consume(available.y)
                            return Offset.Zero
                        }
                        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                            if (available.y > 0 && !list.canScrollBackward) consume(available.y) else Offset.Zero
                    }
                }
                Column(Modifier.fillMaxSize().nestedScroll(connection).padding(horizontal = 12.dp)) {
                    MonthCalendar(state, viewModel::previousMonth, viewModel::nextMonth, viewModel::selectDate,
                        modifier = Modifier.scrollable(rememberScrollableState { delta -> -list.dispatchRawDelta(-delta) }, Orientation.Vertical),
                        height = fullHeight * (1f - collapsed / 2), collapseFraction = collapsed)
                    LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("schedule-trainings"), state = list,
                        contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (statisticsRead.data != null && statistics.sports.isEmpty()) item(key = "no-sports") {
                            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings_choose_sports)) }
                        }
                        item(key = "day-heading") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${state.selectedDate.dayOfMonth}.${state.selectedDate.monthValue}.${state.selectedDate.year}",
                                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(if (state.selectedDayEvents.isEmpty()) R.string.schedule_screen_na_vybrannyy_den_trenirovok_poka
                                    else R.string.schedule_screen_trenirovki_na_vybrannyy_den), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (state.selectedDayEvents.isEmpty()) item(key = "empty-day") {
                            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                                Text(stringResource(R.string.schedule_screen_den_svoboden), style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.schedule_screen_dobavte_razovuyu_ili_povtoryayuschuyusya_trenirovku), style = MaterialTheme.typography.bodyLarge)
                            } }
                        }
                        items(state.selectedDayEvents, key = { it.id }) { event ->
                            if (event.state == ScheduleEventState.COMPLETED) {
                                val id = event.id.removePrefix("completed-").toLong()
                                CompletedTrainingCard(event.sportTitle, event.sportSlug, event.complexName, event.details, event.football,
                                    { onEditResult(id) }, { deleteId = id }, linkedPlan = event.linkedPlan, climbing = event.climbing)
                            } else Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        SportBadge(event.sportSlug, size = 24.dp)
                                        Icon(event.state.icon(), stringResource(when (event.state) {
                                            ScheduleEventState.COMPLETED -> R.string.calendar_completed
                                            ScheduleEventState.PLANNED -> R.string.calendar_planned
                                            ScheduleEventState.CANCELED -> R.string.calendar_canceled
                                        }), Modifier.padding(horizontal = 4.dp).size(18.dp))
                                        Text(event.sportTitle, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                        event.planKey?.let { target ->
                                            IconButton(onClick = { planKey = target; planRecurring = event.isRecurring; planAction = PlanAction.EDIT }) {
                                                Icon(Icons.Rounded.Edit, stringResource(R.string.schedule_screen_izmenit_plan))
                                            }
                                            if (event.state == ScheduleEventState.PLANNED) IconButton(onClick = {
                                                planKey = target; planRecurring = event.isRecurring; planAction = PlanAction.CANCEL
                                            }) { Icon(Icons.Rounded.Delete, stringResource(R.string.schedule_screen_otmenit_trenirovku)) }
                                        }
                                    }
                                    Text(event.complexName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TrainingDetails(event.details, event.football, event.climbing)
                                    if (event.linkedPlan) Text(stringResource(R.string.schedule_screen_rezultat_svyazan_s_planom), style = MaterialTheme.typography.bodyMedium)
                                    Text(stringResource(when {
                                        event.state == ScheduleEventState.COMPLETED -> R.string.schedule_screen_sostoyavshayasya_trenirovka
                                        event.state == ScheduleEventState.CANCELED -> R.string.schedule_screen_otmenena
                                        event.isRecurring -> R.string.schedule_screen_zaplanirovana_seriya
                                        else -> R.string.schedule_screen_zaplanirovana
                                    }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (event.state == ScheduleEventState.PLANNED) event.planKey?.let { target ->
                                        TextButton(onClick = { onCompletePlan(target) }) { Text(stringResource(R.string.schedule_screen_zapisat_rezultat_po_planu)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
