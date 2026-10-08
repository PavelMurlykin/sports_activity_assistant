package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SportsCenterDialog(
    center: SportsCenterUiModel?,
    sports: List<SportSummaryUiModel>,
    onDismiss: () -> Unit,
    onSave: suspend (SaveSportsCenterInput) -> Long,
    onSaved: (Long) -> Unit,
    initialSportId: Int? = null,
) {
    var name by rememberSaveable(center?.id) { mutableStateOf(center?.name.orEmpty()) }
    var city by rememberSaveable(center?.id) { mutableStateOf(center?.city.orEmpty()) }
    var selectedIds by rememberSaveable(center?.id) {
        mutableStateOf((center?.sports?.map { it.id } ?: listOfNotNull(initialSportId)).toIntArray())
    }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (center == null) "Новый спортивный центр" else "Изменить центр") },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (busy) return@TextButton
                busy = true
                scope.launch {
                    try {
                        val id = onSave(SaveSportsCenterInput(center?.id, name, city, selectedIds.toSet()))
                        onSaved(id)
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "Не удалось сохранить центр" }
                    finally { busy = false }
                }
            }) { Text(if (busy) "Сохранение…" else "Сохранить") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Отмена") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().testTag("center-name"),
                    enabled = !busy, label = { Text("Название") }, singleLine = false,
                    supportingText = { Text("Не более 200 символов") })
                OutlinedTextField(city, { city = it }, Modifier.fillMaxWidth().testTag("center-city"),
                    enabled = !busy, label = { Text("Город (необязательно)") }, singleLine = false)
                Text("Доступные виды спорта", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sports.forEach { sport ->
                        FilterChip(selected = sport.id in selectedIds, enabled = !busy,
                            onClick = {
                                selectedIds = if (sport.id in selectedIds) selectedIds.filter { it != sport.id }.toIntArray()
                                else selectedIds + sport.id
                            }, label = { Text(sport.title) })
                    }
                }
                if (center != null) Text("Изменения действуют для новых записей. Прежние результаты и планы сохраняются.")
                if (center?.isArchived == true) Text("Центр в архиве. Редактирование не возвращает его в активные.")
                error?.let { Text(it, Modifier.testTag("center-error"), color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}

/** Uses live directory state: switching sports cannot display a previous async query result. */
@Composable
fun TrainingCenterSelector(
    sports: List<SportSummaryUiModel>,
    centers: List<SportsCenterUiModel>,
    sportId: Int,
    centerId: Long,
    onSportSelected: (Int) -> Unit,
    onCenterSelected: (Long) -> Unit,
    onSaveCenter: (suspend (SaveSportsCenterInput) -> Long)?,
) {
    val available = centers.filter { !it.isArchived && it.sports.any { sport -> sport.id == sportId } }
    var creating by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(available, sportId) {
        if (available.none { it.id == centerId }) onCenterSelected(available.firstOrNull()?.id ?: 0L)
    }
    DropdownSelector("Вид спорта", sports.firstOrNull { it.id == sportId }?.title ?: "Выберите спорт",
        sports, { it.title }, { next ->
            if (centers.none { it.id == centerId && !it.isArchived && it.sports.any { sport -> sport.id == next.id } })
                onCenterSelected(0L)
            onSportSelected(next.id)
        }, Modifier.testTag("training-sport"))
    DropdownSelector("Спортивный центр", available.firstOrNull { it.id == centerId }?.fullTitle ?: "Выберите центр",
        available, { it.fullTitle }, { onCenterSelected(it.id) }, Modifier.testTag("training-center"))
    if (available.isEmpty()) {
        Text("Для этого вида спорта нет активных центров. Добавьте центр или верните подходящий из архива.")
        if (onSaveCenter != null && sportId != 0) TextButton(onClick = { creating = true }) { Text("Создать центр") }
    }
    if (creating && onSaveCenter != null) SportsCenterDialog(null, sports, { creating = false },
        onSaveCenter, { creating = false }, initialSportId = sportId)
}
