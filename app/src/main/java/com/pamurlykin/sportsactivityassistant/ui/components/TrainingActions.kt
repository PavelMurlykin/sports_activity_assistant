package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.data.model.TrainingEditSnapshot
import com.pamurlykin.sportsactivityassistant.ui.screen.MainViewModel
import kotlinx.coroutines.CancellationException
import java.util.UUID

@Composable
fun TrainingActions(sportSlug: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    if (SportEditors.find(sportSlug) != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onEdit) { Text("Изменить тренировку") }
        TextButton(onClick = onDelete) { Text("Удалить тренировку") }
    }
}

/** The original revision survives restoration along with the form, preventing stale overwrites. */
@Composable
fun TrainingActionDialog(id: Long, deleting: Boolean, viewModel: MainViewModel, onDismiss: () -> Unit) {
    val sports by viewModel.statisticsState.collectAsState()
    val centers by viewModel.sportsCenters.collectAsState()
    var revision by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var sportId by rememberSaveable(id) { mutableIntStateOf(0) }
    var centerId by rememberSaveable(id) { mutableLongStateOf(0) }
    var error by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var deletingNow by rememberSaveable(id) { mutableStateOf(false) }
    val requestId = rememberSaveable(id) { UUID.randomUUID().toString() }
    LaunchedEffect(id) {
        if (revision == null) {
            try {
                val snapshot = viewModel.loadTrainingForEdit(id)
                sportId = snapshot.input.sportId
                centerId = snapshot.input.complexId
                revision = snapshot.revision
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Не удалось открыть тренировку" }
        }
    }
    val snapshot = revision?.let { TrainingEditSnapshot.restore(id, sportId, centerId, it) }
    if (snapshot == null || sports.sports.isEmpty() || centers.isEmpty()) {
        AlertDialog(onDismissRequest = onDismiss, title = { Text("Тренировка") },
            text = { Text(error ?: "Загрузка…") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } })
    } else if (!deleting) {
        AddCompletedTrainingDialog(sports.sports, snapshot.input.date, centers, onDismiss,
            onSave = { input, token -> viewModel.saveCompletedTraining(input, token, snapshot) },
            onSaveCenter = viewModel::saveSportsCenter, initial = snapshot.input, onSaved = onDismiss)
    } else {
        LaunchedEffect(deletingNow) {
            if (deletingNow) {
                try {
                    viewModel.deleteCompletedTraining(snapshot, requestId)
                    onDismiss()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message ?: "Не удалось удалить тренировку" }
                finally { deletingNow = false }
            }
        }
        AlertDialog(onDismissRequest = { if (!deletingNow) onDismiss() },
            title = { Text("Удалить тренировку?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${sports.sports.firstOrNull { it.id == sportId }?.title.orEmpty()} · ${snapshot.input.date}")
                    Text(centers.firstOrNull { it.id == centerId }?.fullTitle.orEmpty())
                    Text("Результат и вся его спортивная статистика будут удалены. Центр, планы и серии останутся. Отменить удаление нельзя; восстановление возможно из прежней копии файла.")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(enabled = !deletingNow, onClick = { if (!deletingNow) deletingNow = true }) {
                Text(if (deletingNow) "Удаление…" else "Удалить")
            } },
            dismissButton = { TextButton(enabled = !deletingNow, onClick = onDismiss) { Text("Отмена") } })
    }
}
