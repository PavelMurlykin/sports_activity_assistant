package com.pamurlykin.sportsactivityassistant.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.data.model.SaveSportsCenterInput
import com.pamurlykin.sportsactivityassistant.data.model.SportSummaryUiModel
import com.pamurlykin.sportsactivityassistant.data.model.SportsCenterUiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportsCentersScreen(viewModel: MainViewModel) {
    val centers by viewModel.sportsCenters.collectAsState()
    val statistics by viewModel.statisticsState.collectAsState()
    val operationState by viewModel.dataOperationState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var editedCenter by remember { mutableStateOf<SportsCenterUiModel?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(operationState.message, operationState.inProgress) {
        val message = operationState.message
        if (message != null && !operationState.inProgress) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearDataMessage()
        }
    }

    if (showDialog) {
        SportsCenterDialog(
            center = editedCenter,
            sports = statistics.sports,
            onDismiss = { showDialog = false },
            onSave = {
                viewModel.saveSportsCenter(it)
                showDialog = false
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("Спортивные центры", fontWeight = FontWeight.Bold)
                    Text("Места и доступные виды спорта", style = MaterialTheme.typography.bodySmall)
                }
            })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editedCenter = null; showDialog = true }) { Text("+") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 92.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (centers.isEmpty()) item { Text("Добавьте первый спортивный центр") }
            items(centers, key = { it.id }) { center ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { editedCenter = center; showDialog = true },
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(center.fullTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            center.sports.joinToString { it.title }.ifBlank { "Виды спорта не указаны" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("Нажмите, чтобы изменить", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SportsCenterDialog(
    center: SportsCenterUiModel?,
    sports: List<SportSummaryUiModel>,
    onDismiss: () -> Unit,
    onSave: (SaveSportsCenterInput) -> Unit,
) {
    var name by remember(center?.id) { mutableStateOf(center?.name.orEmpty()) }
    var city by remember(center?.id) { mutableStateOf(center?.city.orEmpty()) }
    var selectedIds by remember(center?.id) { mutableStateOf(center?.sports?.map { it.id }?.toSet().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (center == null) "Новый спортивный центр" else "Изменить центр") },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank() || selectedIds.isEmpty()) {
                    error = "Укажите название и хотя бы один вид спорта"
                } else {
                    onSave(SaveSportsCenterInput(center?.id, name, city.takeIf(String::isNotBlank), selectedIds))
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Название") }, singleLine = true)
                OutlinedTextField(city, { city = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Город (необязательно)") }, singleLine = true)
                Text("Доступные виды спорта", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sports.forEach { sport ->
                        FilterChip(
                            selected = sport.id in selectedIds,
                            onClick = {
                                selectedIds = if (sport.id in selectedIds) selectedIds - sport.id else selectedIds + sport.id
                            },
                            label = { Text(sport.title) },
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}
