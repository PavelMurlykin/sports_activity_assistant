package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.data.model.*
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCompletedTrainingDialog(
    sports: List<SportSummaryUiModel>,
    initialDate: LocalDate,
    centers: List<SportsCenterUiModel>,
    onDismiss: () -> Unit,
    onSave: suspend (AddCompletedTrainingInput, String) -> Unit,
    onSaveCenter: (suspend (SaveSportsCenterInput) -> Long)? = null,
    initial: AddCompletedTrainingInput? = null,
    onSaved: () -> Unit = {},
    fromPlan: Boolean = false,
) {
    var selectedSportId by rememberSaveable { mutableIntStateOf(initial?.sportId ?: sports.firstOrNull()?.id ?: 0) }
    var selectedComplexId by rememberSaveable { mutableLongStateOf(initial?.complexId ?: 0L) }
    var dateText by rememberSaveable { mutableStateOf((initial?.date ?: initialDate).toString()) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var datePickerOpen by rememberSaveable { mutableStateOf(false) }
    var creatingCenter by rememberSaveable { mutableStateOf(false) }
    val requestId = rememberSaveable { UUID.randomUUID().toString() }
    val holders = rememberSaveableStateHolder()
    LaunchedEffect(sports) {
        if (selectedSportId == 0 && sports.isNotEmpty()) selectedSportId = sports.first().id
    }
    val selectedSport = sports.firstOrNull { it.id == selectedSportId }
    val dateResult = runCatching {
        TrainingValidation.parseDate(dateText.trim()).also { TrainingValidation.completedDate(it, initial?.date.takeUnless { fromPlan }) }
    }
    val dateError = if (!attempted || dateResult.isSuccess) null else {
        if (runCatching { TrainingValidation.parseDate(dateText.trim()) }.isFailure) "Введите дату YYYY-MM-DD (год 0001–9999)"
        else dateResult.exceptionOrNull()?.message
    }
    holders.SaveableStateProvider(selectedSportId) {
        val draft = SportEditors.find(selectedSport?.slug)?.rememberDraft(initial.takeUnless { fromPlan })
        fun input(): AddCompletedTrainingInput {
            val date = dateResult.getOrElse { throw IllegalArgumentException(dateError ?: "Проверьте дату") }
            require(selectedSport != null && centers.any { center ->
                center.id == selectedComplexId && (!fromPlan && center.id == initial?.complexId ||
                    !center.isArchived && center.sports.any { it.id == selectedSportId })
            }) { "Выберите вид спорта и спортивный центр" }
            return requireNotNull(draft) { "Для этого вида спорта форма пока не настроена" }
                .input(selectedSportId, selectedComplexId, date)
        }
        LaunchedEffect(saving, selectedSport?.id, centers.isNotEmpty()) {
            if (saving && selectedSport != null && centers.isNotEmpty()) {
                try {
                    onSave(input(), requestId)
                    errorText = null
                    onSaved()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { errorText = e.message ?: "Не удалось сохранить тренировку" }
                finally { saving = false }
            }
        }
        AlertDialog(
            modifier = Modifier.imePadding(),
            onDismissRequest = { if (!saving) onDismiss() },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    if (!saving) {
                        attempted = true
                        runCatching { input() }.onSuccess { errorText = null; saving = true }
                            .onFailure { errorText = it.message ?: "Проверьте данные тренировки" }
                    }
                }) { Text(if (saving) "Сохранение…" else "Сохранить") }
            },
            dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Отмена") } },
            title = { Text(if (fromPlan) "Результат по плану" else if (initial == null) "Записать тренировку" else "Изменить тренировку") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TrainingCenterSelector(sports, centers, selectedSportId, selectedComplexId,
                        { selectedSportId = it }, { selectedComplexId = it }, onSaveCenter,
                        enabled = !saving, sportLocked = initial != null, retainedCenterId = initial?.complexId.takeUnless { fromPlan },
                        onCreateCenter = { creatingCenter = true })
                    if (initial != null) Text(if (fromPlan) "Вид спорта из плана. Укажите фактическую дату и доступный центр; будущий результат записать нельзя." else "Вид спорта менять нельзя. Прежний центр можно оставить, даже если он в архиве.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(dateText, { dateText = it }, Modifier.fillMaxWidth().testTag("training-date"),
                        enabled = !saving, label = { Text("Дата") },
                        supportingText = { Text(dateError ?: "YYYY-MM-DD · будущие тренировки добавляйте в план") },
                        isError = dateError != null, singleLine = true)
                    TextButton(enabled = !saving, onClick = { datePickerOpen = true },
                        modifier = Modifier.testTag("training-date-picker")) { Text("Выбрать дату") }
                    HorizontalDivider()
                    draft?.Content(enabled = !saving) ?: Text("Запись для этого вида спорта пока недоступна")
                    errorText?.let { Text(it, Modifier.testTag("training-error"),
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
        )
    }
    if (creatingCenter && onSaveCenter != null) {
        SportsCenterDialog(null, sports, { creatingCenter = false }, onSaveCenter,
            { id -> selectedComplexId = id; creatingCenter = false }, initialSportId = selectedSportId)
    }
    if (datePickerOpen) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = dateResult.getOrNull()?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            yearRange = 1..9999,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return date <= LocalDate.now() || !fromPlan && date == initial?.date
                }
            },
        )
        DatePickerDialog(onDismissRequest = { datePickerOpen = false },
            confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                dateText = Instant.ofEpochMilli(requireNotNull(picker.selectedDateMillis)).atZone(ZoneOffset.UTC).toLocalDate().toString()
                datePickerOpen = false
            }) { Text("Выбрать") } },
            dismissButton = { TextButton(onClick = { datePickerOpen = false }) { Text("Отмена") } }) {
            DatePicker(picker)
        }
    }
}
