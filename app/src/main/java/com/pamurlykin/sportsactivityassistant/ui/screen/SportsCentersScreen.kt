package com.pamurlykin.sportsactivityassistant.ui.screen

import androidx.compose.foundation.layout.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.data.model.SportsCenterUiModel
import com.pamurlykin.sportsactivityassistant.ui.components.SportsCenterDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.res.stringResource
import com.pamurlykin.sportsactivityassistant.R
import com.pamurlykin.sportsactivityassistant.ui.components.ReadStateNotice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportsCentersScreen(viewModel: MainViewModel) {
    val centersRead by viewModel.centersReadState.collectAsStateWithLifecycle()
    val centers = centersRead.data.orEmpty()
    val statisticsRead by viewModel.statisticsReadState.collectAsStateWithLifecycle()
    val statistics by viewModel.statisticsState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var showArchive by rememberSaveable { mutableStateOf(false) }
    var archiveId by rememberSaveable { mutableStateOf<Long?>(null) }
    var archiveBusy by remember { mutableStateOf(false) }
    var archiveError by remember { mutableStateOf<String?>(null) }
    if (showDialog && (editedId == null || centers.any { it.id == editedId })) SportsCenterDialog(
        center = centers.firstOrNull { it.id == editedId }, sports = statistics.sports,
        onDismiss = { showDialog = false }, onSave = viewModel::saveSportsCenter,
        onSaved = { showDialog = false; scope.launch { snackbar.showSnackbar("Спортивный центр сохранён") } },
    )
    centers.firstOrNull { it.id == archiveId }?.let { center ->
        AlertDialog(
            onDismissRequest = { if (!archiveBusy) archiveId = null },
            title = { Text(if (center.isArchived) "Вернуть центр?" else "Архивировать центр?") },
            text = { Column {
                Text(center.fullTitle)
                Text(if (center.isArchived) "Центр снова будет доступен для новых тренировок и планов."
                    else "Новые тренировки и планы здесь будут недоступны. Прежние результаты, планы и серии сохранятся. Архивирование не отменяет события.")
                archiveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(enabled = !archiveBusy, onClick = {
                archiveBusy = true
                scope.launch {
                    try {
                        viewModel.setSportsCenterArchived(center.id, !center.isArchived)
                        archiveId = null
                        snackbar.showSnackbar(if (center.isArchived) "Центр возвращён" else "Центр в архиве")
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { archiveError = e.message ?: "Не удалось изменить центр" }
                    finally { archiveBusy = false }
                }
            }) { Text(if (center.isArchived) "Вернуть" else "В архив") } },
            dismissButton = { TextButton(enabled = !archiveBusy, onClick = { archiveId = null }) { Text("Отмена") } },
        )
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Column {
            Text("Спортивные центры", fontWeight = FontWeight.Bold)
            Text("Места и доступные виды спорта", style = MaterialTheme.typography.bodySmall)
        } }) },
        floatingActionButton = {
            if (centersRead.data != null && statisticsRead.data != null) {
                FloatingActionButton(onClick = { editedId = null; showDialog = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_center))
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 92.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!showArchive, { showArchive = false }, label = { Text("Активные") })
                FilterChip(showArchive, { showArchive = true }, label = { Text("Архив") })
            } }
            val visible = centers.filter { it.isArchived == showArchive }
            if (centersRead.failed || statisticsRead.failed) item { ReadStateNotice(true, viewModel::retryReads) }
            else if (centersRead.loading || statisticsRead.loading) item { ReadStateNotice(false, viewModel::retryReads) }
            else if (visible.isEmpty()) item { Text(if (showArchive) "Архив пуст" else "Добавьте спортивный центр") }
            items(visible, key = { it.id }) { center ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(center.fullTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(center.sports.joinToString { it.title }.ifBlank { "Виды спорта не указаны" })
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { editedId = center.id; showDialog = true }) { Text("Изменить") }
                            TextButton(onClick = { archiveError = null; archiveId = center.id }) {
                                Text(if (center.isArchived) "Вернуть" else "В архив")
                            }
                        }
                    }
                }
            }
        }
    }
}
