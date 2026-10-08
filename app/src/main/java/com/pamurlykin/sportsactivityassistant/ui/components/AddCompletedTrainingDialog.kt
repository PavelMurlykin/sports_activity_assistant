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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.data.model.AddCompletedTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.SportsCenterUiModel
import com.pamurlykin.sportsactivityassistant.data.model.SaveSportsCenterInput
import com.pamurlykin.sportsactivityassistant.data.model.SportSummaryUiModel
import java.time.LocalDate

@Composable
fun AddCompletedTrainingDialog(
    sports: List<SportSummaryUiModel>,
    initialDate: LocalDate,
    centers: List<SportsCenterUiModel>,
    onDismiss: () -> Unit,
    onSave: (AddCompletedTrainingInput) -> Unit,
    onSaveCenter: (suspend (SaveSportsCenterInput) -> Long)? = null,
) {
    var selectedSportId by rememberSaveable { mutableIntStateOf(sports.firstOrNull()?.id ?: 0) }
    var selectedComplexId by rememberSaveable { mutableLongStateOf(0L) }
    var dateText by rememberSaveable { mutableStateOf(initialDate.toString()) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(sports) {
        if (selectedSportId == 0 && sports.isNotEmpty()) selectedSportId = sports.first().id
    }
    val complexes = centers.filter { !it.isArchived && it.sports.any { sport -> sport.id == selectedSportId } }
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
                TrainingCenterSelector(sports, centers, selectedSportId, selectedComplexId,
                    { selectedSportId = it }, { selectedComplexId = it }, onSaveCenter)
                OutlinedTextField(dateText, { dateText = it }, Modifier.fillMaxWidth(),
                    label = { Text("Дата") }, supportingText = { Text("YYYY-MM-DD") }, singleLine = true)
                HorizontalDivider()
                draft?.Content() ?: Text("Запись для этого вида спорта пока недоступна")
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
    )
}
