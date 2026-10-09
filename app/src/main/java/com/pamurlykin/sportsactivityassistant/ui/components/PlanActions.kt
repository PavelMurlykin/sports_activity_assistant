package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.pamurlykin.sportsactivityassistant.data.model.*
import com.pamurlykin.sportsactivityassistant.ui.screen.MainViewModel
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import java.util.UUID

enum class PlanAction { EDIT, CANCEL, COMPLETE }

@Composable
fun PlanActionDialog(key: String, recurring: Boolean, action: PlanAction, viewModel: MainViewModel, onDismiss: () -> Unit) {
    var scope by rememberSaveable { mutableStateOf<PlanScope?>(if (recurring && action != PlanAction.COMPLETE) null else PlanScope.EVENT) }
    var encoded by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    val requestId = rememberSaveable { UUID.randomUUID().toString() }
    val statistics by viewModel.statisticsState.collectAsState()
    val centers by viewModel.sportsCenters.collectAsState()
    if (scope == null) {
        AlertDialog(onDismissRequest = onDismiss, title = { Text("Повторяющаяся тренировка") },
            text = { Text(if (action == PlanAction.EDIT)
                "Изменить только это событие или всю серию? Индивидуальные исключения и результаты при редактировании серии сохраняются."
                else "Отменить только это событие или всю серию? Завершённые результаты не удаляются.") },
            confirmButton = { TextButton(onClick = { scope = PlanScope.EVENT }) { Text("Только это событие") } },
            dismissButton = { Column {
                TextButton(onClick = { scope = PlanScope.SERIES }) { Text("Вся серия") }
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            } })
        return
    }
    LaunchedEffect(key, scope) {
        if (encoded == null && error == null) {
            try { encoded = Json.encodeToString(PlanSnapshot.serializer(), viewModel.loadPlan(key, requireNotNull(scope))) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
        }
    }
    val snapshot = encoded?.let { Json.decodeFromString(PlanSnapshot.serializer(), it) }
    if (snapshot == null) {
        AlertDialog(onDismissRequest = onDismiss, title = { Text("План тренировки") },
            text = { Text(error ?: "Загрузка…") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } })
        return
    }
    when (action) {
        PlanAction.EDIT -> AddPlannedTrainingDialog(statistics.sports, centers, onDismiss,
            { input, token -> viewModel.updatePlan(snapshot, requireNotNull(scope), input, token) },
            viewModel::saveSportsCenter, initial = snapshot.input(), onSaved = onDismiss)
        PlanAction.COMPLETE -> AddCompletedTrainingDialog(statistics.sports, java.time.LocalDate.parse(snapshot.date),
            centers, onDismiss, { input, token -> viewModel.completePlan(snapshot, input, token) },
            viewModel::saveSportsCenter,
            initial = AddCompletedTrainingInput(snapshot.sportId, snapshot.complexId, java.time.LocalDate.parse(snapshot.date)),
            onSaved = onDismiss, fromPlan = true)
        PlanAction.CANCEL -> {
            LaunchedEffect(saving) {
                if (saving) {
                    try { viewModel.cancelPlan(snapshot, requireNotNull(scope), requestId); onDismiss() }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message }
                    finally { saving = false }
                }
            }
            AlertDialog(onDismissRequest = { if (!saving) onDismiss() },
                title = { Text(if (scope == PlanScope.SERIES) "Отменить всю серию?" else "Отменить тренировку?") },
                text = { Column {
                    Text(if (scope == PlanScope.SERIES) "Все незавершённые события серии, включая переносы, будут отмечены отменёнными. Результаты и индивидуальные исключения сохранятся."
                        else "Событие ${snapshot.date} останется в календаре с отметкой «Отменена». Результаты не удаляются.")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                } },
                confirmButton = { TextButton(enabled = !saving, onClick = { saving = true }) { Text(if (saving) "Отмена…" else "Подтвердить отмену") } },
                dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Оставить план") } })
        }
    }
}
