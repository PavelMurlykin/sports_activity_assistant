package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.data.model.AddPlannedTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.SportsCenterUiModel
import com.pamurlykin.sportsactivityassistant.data.model.SaveSportsCenterInput
import com.pamurlykin.sportsactivityassistant.data.model.SportSummaryUiModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlannedTrainingDialog(
    sports: List<SportSummaryUiModel>,
    centers: List<SportsCenterUiModel>,
    onDismiss: () -> Unit,
    onSave: suspend (AddPlannedTrainingInput, String) -> Unit,
    onSaveCenter: (suspend (SaveSportsCenterInput) -> Long)? = null,
    initialDate: LocalDate = LocalDate.now(),
    initial: AddPlannedTrainingInput? = null,
    onSaved: () -> Unit = {},
) {
    var selectedSportId by rememberSaveable { mutableIntStateOf(initial?.sportId ?: sports.firstOrNull()?.id ?: 0) }
    var selectedComplexId by rememberSaveable { mutableLongStateOf(initial?.complexId ?: 0L) }
    var dateText by rememberSaveable { mutableStateOf((initial?.date ?: initialDate).toString()) }
    var repeatWeekly by rememberSaveable { mutableStateOf(initial?.repeatWeekly ?: false) }
    var intervalText by rememberSaveable { mutableStateOf((initial?.intervalWeeks ?: 1).toString()) }
    var endDateText by rememberSaveable { mutableStateOf(initial?.endDate?.toString() ?: "") }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var creatingCenter by rememberSaveable { mutableStateOf(false) }
    val requestId = rememberSaveable { java.util.UUID.randomUUID().toString() }
    LaunchedEffect(sports) {
        if (selectedSportId == 0 && sports.isNotEmpty()) selectedSportId = sports.first().id
    }
    fun input(): AddPlannedTrainingInput {
        require(centers.any { it.id == selectedComplexId &&
            (initial?.sportId == selectedSportId && initial.complexId == it.id ||
                !it.isArchived && it.sports.any { sport -> sport.id == selectedSportId }) }) { "Выберите доступный центр" }
        val date = com.pamurlykin.sportsactivityassistant.data.model.TrainingValidation.parseDate(dateText.trim())
        val end = endDateText.trim().takeIf { repeatWeekly && it.isNotEmpty() }
            ?.let(com.pamurlykin.sportsactivityassistant.data.model.TrainingValidation::parseDate)
        val interval = if (repeatWeekly) requireNotNull(intervalText.toIntOrNull()) { "Введите целый положительный интервал" } else 1
        com.pamurlykin.sportsactivityassistant.data.model.TrainingValidation.recurrence(date, end, interval)
        return AddPlannedTrainingInput(selectedSportId, selectedComplexId, date, repeatWeekly, interval, end)
    }
    LaunchedEffect(saving, sports.isNotEmpty(), centers.isNotEmpty()) {
        if (saving && sports.isNotEmpty() && centers.isNotEmpty()) {
            try { onSave(input(), requestId); errorText = null; onSaved() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { errorText = e.message ?: "Не удалось сохранить план" }
            finally { saving = false }
        }
    }
    AlertDialog(
        modifier = Modifier.imePadding(),
        onDismissRequest = { if (!saving) onDismiss() },
        confirmButton = { TextButton(enabled = !saving, onClick = {
            runCatching { input() }.onSuccess { errorText = null; saving = true }.onFailure { errorText = it.message }
        }) { Text(if (saving) "Сохранение…" else "Сохранить") } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Отмена") } },
        title = { Text(if (initial == null) "План тренировки" else if (repeatWeekly) "Изменить всю серию" else "Изменить одно событие") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TrainingCenterSelector(sports, centers, selectedSportId, selectedComplexId,
                    { selectedSportId = it }, { selectedComplexId = it }, onSaveCenter, enabled = !saving,
                    retainedCenterId = initial?.complexId.takeIf { initial?.sportId == selectedSportId },
                    onCreateCenter = { creatingCenter = true })
                OutlinedTextField(dateText, { dateText = it }, Modifier.fillMaxWidth().testTag("plan-date"),
                    enabled = !saving, label = { Text(if (repeatWeekly) "Начало серии · YYYY-MM-DD" else "Дата · YYYY-MM-DD") }, singleLine = true)
                if (initial == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !repeatWeekly, enabled = !saving, onClick = { repeatWeekly = false }, label = { Text("Разовая") })
                    FilterChip(selected = repeatWeekly, enabled = !saving, onClick = { repeatWeekly = true }, label = { Text("По неделям") })
                }
                if (repeatWeekly) {
                    OutlinedTextField(intervalText, { intervalText = it }, Modifier.fillMaxWidth().testTag("plan-interval"),
                        enabled = !saving, label = { Text("Интервал в неделях") }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                    OutlinedTextField(endDateText, { endDateText = it }, Modifier.fillMaxWidth().testTag("plan-end"),
                        enabled = !saving, label = { Text("Повторять до · YYYY-MM-DD") },
                        supportingText = { Text("Включительно; пусто — без ограничения") }, singleLine = true)
                    if (initial != null) Text("Изменятся события без индивидуальных исключений. Переносы, отмены и записанные результаты сохранятся.")
                }
                errorText?.let { Text(it, Modifier.testTag("plan-error"), color = MaterialTheme.colorScheme.error) }
            }
        },
    )
    if (creatingCenter && onSaveCenter != null) SportsCenterDialog(null, sports, { creatingCenter = false },
        onSaveCenter, { selectedComplexId = it; creatingCenter = false }, initialSportId = selectedSportId)
}

@Composable
fun <T> DropdownSelector(
    label: String,
    selectedText: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { focusManager.clearFocus(); keyboard?.hide(); expanded = true },
            enabled = enabled,
            modifier = modifier.fillMaxWidth(),
        ) {
            Text(
                text = selectedText,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { item ->
                DropdownMenuItem(
                    text = { Text(optionLabel(item)) },
                    onClick = {
                        onSelected(item)
                        expanded = false
                    },
                )
            }
        }
    }
}
