package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.data.model.AddCompletedTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.ComplexOptionUiModel
import com.pamurlykin.sportsactivityassistant.data.model.SportSummaryUiModel
import java.time.LocalDate

@Composable
fun AddCompletedTrainingDialog(
    sports: List<SportSummaryUiModel>,
    initialDate: LocalDate,
    onLoadComplexes: suspend (Int) -> List<ComplexOptionUiModel>,
    onDismiss: () -> Unit,
    onSave: (AddCompletedTrainingInput) -> Unit,
) {
    var selectedSportId by rememberSaveable { mutableIntStateOf(sports.firstOrNull()?.id ?: 0) }
    var selectedComplexId by rememberSaveable { mutableLongStateOf(0L) }
    var dateText by rememberSaveable { mutableStateOf(initialDate.toString()) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(sports) {
        if (selectedSportId == 0 && sports.isNotEmpty()) selectedSportId = sports.first().id
    }
    val complexes by produceState(initialValue = emptyList<ComplexOptionUiModel>(), selectedSportId) {
        value = emptyList()
        value = if (selectedSportId == 0) emptyList() else onLoadComplexes(selectedSportId)
    }
    LaunchedEffect(complexes) {
        if (complexes.none { it.id == selectedComplexId }) selectedComplexId = complexes.firstOrNull()?.id ?: 0L
    }
    val selectedSport = sports.firstOrNull { it.id == selectedSportId }
    val draft = key(selectedSportId) { SportEditors.find(selectedSport?.slug)?.rememberDraft() }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    val date = LocalDate.parse(dateText.trim())
                    require(selectedSport != null && complexes.any { it.id == selectedComplexId }) {
                        "Выберите вид спорта и спортивный центр"
                    }
                    requireNotNull(draft) { "Для этого вида спорта форма пока не настроена" }
                        .input(selectedSportId, selectedComplexId, date)
                }.onSuccess { errorText = null; onSave(it) }
                    .onFailure { errorText = it.message ?: "Проверьте данные тренировки" }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        title = { Text("Записать тренировку") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownSelector("Вид спорта", selectedSport?.title ?: "Выберите спорт", sports, { it.title }, { selectedSportId = it.id })
                DropdownSelector("Спортивный центр",
                    complexes.firstOrNull { it.id == selectedComplexId }?.fullTitle ?: "Выберите центр",
                    complexes, { it.fullTitle }, { selectedComplexId = it.id })
                OutlinedTextField(dateText, { dateText = it }, Modifier.fillMaxWidth(),
                    label = { Text("Дата") }, supportingText = { Text("YYYY-MM-DD") }, singleLine = true)
                HorizontalDivider()
                draft?.Content() ?: Text("Запись для этого вида спорта пока недоступна")
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
    )
}
