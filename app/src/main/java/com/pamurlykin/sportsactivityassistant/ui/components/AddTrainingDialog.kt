package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.data.model.AddPlannedTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.ComplexOptionUiModel
import com.pamurlykin.sportsactivityassistant.data.model.SportSummaryUiModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlannedTrainingDialog(
    sports: List<SportSummaryUiModel>,
    onLoadComplexes: suspend (Int) -> List<ComplexOptionUiModel>,
    onDismiss: () -> Unit,
    onSave: (AddPlannedTrainingInput) -> Unit,
) {
    val initialSportId = sports.firstOrNull()?.id ?: 0
    var selectedSportId by rememberSaveable { mutableIntStateOf(initialSportId) }
    var selectedComplexId by rememberSaveable { mutableLongStateOf(0L) }
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var repeatWeekly by rememberSaveable { mutableStateOf(false) }
    var intervalText by rememberSaveable { mutableStateOf("1") }
    var endDateText by rememberSaveable { mutableStateOf(LocalDate.now().plusMonths(2).toString()) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(sports) {
        if (selectedSportId == 0 && sports.isNotEmpty()) {
            selectedSportId = sports.first().id
        }
    }

    val complexes by produceState(initialValue = emptyList<ComplexOptionUiModel>(), selectedSportId) {
        value = if (selectedSportId == 0) emptyList() else onLoadComplexes(selectedSportId)
    }

    LaunchedEffect(complexes) {
        if (complexes.isNotEmpty() && complexes.none { it.id == selectedComplexId }) {
            selectedComplexId = complexes.first().id
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedDate = runCatching { LocalDate.parse(dateText) }.getOrNull()
                    val parsedEndDate = if (repeatWeekly) runCatching { LocalDate.parse(endDateText) }.getOrNull() else null
                    val interval = intervalText.toIntOrNull()?.coerceAtLeast(1)

                    if (selectedSportId == 0) {
                        errorText = "Выберите вид спорта"
                        return@TextButton
                    }
                    if (selectedComplexId == 0L) {
                        errorText = "Выберите спортивный комплекс"
                        return@TextButton
                    }
                    if (parsedDate == null) {
                        errorText = "Дата должна быть в формате YYYY-MM-DD"
                        return@TextButton
                    }
                    if (repeatWeekly && parsedEndDate == null) {
                        errorText = "Дата окончания повторов должна быть в формате YYYY-MM-DD"
                        return@TextButton
                    }
                    if (repeatWeekly && interval == null) {
                        errorText = "Интервал повторов должен быть целым числом"
                        return@TextButton
                    }
                    if (repeatWeekly && parsedEndDate != null && parsedEndDate < parsedDate) {
                        errorText = "Дата окончания не может быть раньше старта"
                        return@TextButton
                    }

                    errorText = null
                    onSave(
                        AddPlannedTrainingInput(
                            sportId = selectedSportId,
                            complexId = selectedComplexId,
                            date = parsedDate,
                            repeatWeekly = repeatWeekly,
                            intervalWeeks = interval ?: 1,
                            endDate = parsedEndDate,
                        ),
                    )
                },
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
        title = {
            Text("План тренировки")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                DropdownSelector(
                    label = "Вид спорта",
                    selectedText = sports.firstOrNull { it.id == selectedSportId }?.title ?: "Выберите спорт",
                    options = sports,
                    optionLabel = { it.title },
                    onSelected = { selectedSportId = it.id },
                )

                DropdownSelector(
                    label = "Спортивный комплекс",
                    selectedText = complexes.firstOrNull { it.id == selectedComplexId }?.fullTitle ?: "Выберите комплекс",
                    options = complexes,
                    optionLabel = { it.fullTitle },
                    onSelected = { selectedComplexId = it.id },
                )

                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Дата") },
                    supportingText = { Text("Например: 2026-03-28") },
                    singleLine = true,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !repeatWeekly,
                        onClick = { repeatWeekly = false },
                        label = { Text("Разовая") },
                    )
                    FilterChip(
                        selected = repeatWeekly,
                        onClick = { repeatWeekly = true },
                        label = { Text("Каждую неделю") },
                    )
                }

                if (repeatWeekly) {
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { intervalText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Интервал в неделях") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = endDateText,
                        onValueChange = { endDateText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Повторять до") },
                        supportingText = { Text("Например: 2026-05-29") },
                        singleLine = true,
                    )
                }

                errorText?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    )
}

@Composable
fun <T> DropdownSelector(
    label: String,
    selectedText: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { expanded = true },
            modifier = modifier.fillMaxWidth(),
        ) {
            Text(
                text = selectedText,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        DropdownMenu(
            expanded = expanded,
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
