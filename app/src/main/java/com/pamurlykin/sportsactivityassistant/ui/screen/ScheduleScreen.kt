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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.pamurlykin.sportsactivityassistant.ui.components.AddTrainingDialog
import com.pamurlykin.sportsactivityassistant.ui.components.MonthCalendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: MainViewModel,
) {
    val scheduleState by viewModel.scheduleState.collectAsState()
    val statisticsState by viewModel.statisticsState.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AddTrainingDialog(
            sports = statisticsState.sports,
            onLoadComplexes = viewModel::loadComplexesForSport,
            onDismiss = { showDialog = false },
            onSave = {
                viewModel.addPlannedTraining(it)
                showDialog = false
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Расписание", fontWeight = FontWeight.Bold)
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
            FloatingActionButton(onClick = { showDialog = true }) {
                Text("+")
            }
        },
    ) { innerPadding ->
        scheduleState?.let { state ->
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
                                Text(
                                    text = event.complexName,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = if (event.state == com.pamurlykin.sportsactivityassistant.data.model.ScheduleEventState.COMPLETED) {
                                        "Состоявшаяся тренировка"
                                    } else if (event.isRecurring) {
                                        "Запланирована, повторяется каждую неделю"
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
