package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.data.model.AddCompletedTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.ClimbingDifficultyCatalog
import com.pamurlykin.sportsactivityassistant.data.model.ClimbingRouteInput
import com.pamurlykin.sportsactivityassistant.data.model.ClimbingWorkoutType
import com.pamurlykin.sportsactivityassistant.data.model.ComplexOptionUiModel
import com.pamurlykin.sportsactivityassistant.data.model.FootballTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.SportSummaryUiModel
import java.time.LocalDate

private data class RouteDraft(
    val workoutType: ClimbingWorkoutType = ClimbingWorkoutType.DIFFICULTY,
    val difficulty: String = "6A",
    val completed: Boolean = true,
)

@Composable
fun AddCompletedTrainingDialog(
    sports: List<SportSummaryUiModel>,
    initialDate: LocalDate,
    onLoadComplexes: suspend (Int) -> List<ComplexOptionUiModel>,
    onDismiss: () -> Unit,
    onSave: (AddCompletedTrainingInput) -> Unit,
) {
    val initialSportId = sports.firstOrNull()?.id ?: 0
    var selectedSportId by rememberSaveable { mutableIntStateOf(initialSportId) }
    var selectedComplexId by rememberSaveable { mutableLongStateOf(0L) }
    var dateText by rememberSaveable { mutableStateOf(initialDate.toString()) }
    var teamGoals by rememberSaveable { mutableStateOf("0") }
    var concededGoals by rememberSaveable { mutableStateOf("0") }
    var personalGoals by rememberSaveable { mutableStateOf("0") }
    var assists by rememberSaveable { mutableStateOf("0") }
    var distance by rememberSaveable { mutableStateOf("") }
    var players by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf("") }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    val routes = remember { mutableStateListOf(RouteDraft()) }

    LaunchedEffect(sports) {
        if (selectedSportId == 0 && sports.isNotEmpty()) selectedSportId = sports.first().id
    }
    val complexes by produceState(initialValue = emptyList<ComplexOptionUiModel>(), selectedSportId) {
        value = if (selectedSportId == 0) emptyList() else onLoadComplexes(selectedSportId)
    }
    LaunchedEffect(complexes) {
        if (complexes.isNotEmpty() && complexes.none { it.id == selectedComplexId }) selectedComplexId = complexes.first().id
    }
    val selectedSport = sports.firstOrNull { it.id == selectedSportId }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val parsedDate = runCatching { LocalDate.parse(dateText) }.getOrNull()
                if (parsedDate == null || selectedSport == null || selectedComplexId == 0L) {
                    errorText = "Проверьте дату, вид спорта и спортивный центр"
                    return@TextButton
                }
                val input = when (selectedSport.slug) {
                    "football" -> {
                        val required = listOf(teamGoals, concededGoals, personalGoals, assists).map { it.toIntOrNull() }
                        val optionalDistance = distance.replace(',', '.').takeIf(String::isNotBlank)?.toBigDecimalOrNull()
                        val optionalPlayers = players.takeIf(String::isNotBlank)?.toIntOrNull()
                        val optionalDuration = duration.takeIf(String::isNotBlank)?.toIntOrNull()
                        if (required.any { it == null || it < 0 } ||
                            (distance.isNotBlank() && (optionalDistance == null || optionalDistance.signum() < 0)) ||
                            (players.isNotBlank() && (optionalPlayers == null || optionalPlayers <= 0)) ||
                            (duration.isNotBlank() && (optionalDuration == null || optionalDuration <= 0))
                        ) {
                            errorText = "Счёт и личные показатели — неотрицательные числа; необязательные поля — положительные"
                            return@TextButton
                        }
                        AddCompletedTrainingInput(
                            selectedSportId, selectedComplexId, parsedDate,
                            football = FootballTrainingInput(
                                required[0]!!, required[1]!!, required[2]!!, required[3]!!,
                                optionalDistance, optionalPlayers, optionalDuration,
                            ),
                        )
                    }
                    "climbing" -> {
                        if (routes.isEmpty()) {
                            errorText = "Добавьте хотя бы одну трассу"
                            return@TextButton
                        }
                        AddCompletedTrainingInput(
                            selectedSportId, selectedComplexId, parsedDate,
                            climbingRoutes = routes.map { ClimbingRouteInput(it.workoutType, it.difficulty, it.completed) },
                        )
                    }
                    else -> {
                        errorText = "Для этого вида спорта форма пока не настроена"
                        return@TextButton
                    }
                }
                errorText = null
                onSave(input)
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        title = { Text("Записать тренировку") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DropdownSelector(
                    label = "Вид спорта",
                    selectedText = selectedSport?.title ?: "Выберите спорт",
                    options = sports,
                    optionLabel = { it.title },
                    onSelected = { selectedSportId = it.id },
                )
                DropdownSelector(
                    label = "Спортивный центр",
                    selectedText = complexes.firstOrNull { it.id == selectedComplexId }?.fullTitle ?: "Выберите центр",
                    options = complexes,
                    optionLabel = { it.fullTitle },
                    onSelected = { selectedComplexId = it.id },
                )
                OutlinedTextField(
                    value = dateText, onValueChange = { dateText = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Дата") }, supportingText = { Text("YYYY-MM-DD") }, singleLine = true,
                )
                HorizontalDivider()
                when (selectedSport?.slug) {
                    "football" -> FootballFields(
                        teamGoals, { teamGoals = it }, concededGoals, { concededGoals = it },
                        personalGoals, { personalGoals = it }, assists, { assists = it },
                        distance, { distance = it }, players, { players = it }, duration, { duration = it },
                    )
                    "climbing" -> ClimbingFields(routes)
                }
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
    )
}

@Composable
private fun FootballFields(
    teamGoals: String, onTeamGoals: (String) -> Unit,
    conceded: String, onConceded: (String) -> Unit,
    personalGoals: String, onPersonalGoals: (String) -> Unit,
    assists: String, onAssists: (String) -> Unit,
    distance: String, onDistance: (String) -> Unit,
    players: String, onPlayers: (String) -> Unit,
    duration: String, onDuration: (String) -> Unit,
) {
    Text("Футбольная статистика", style = MaterialTheme.typography.titleMedium)
    NumberField(teamGoals, onTeamGoals, "Голов забито")
    NumberField(conceded, onConceded, "Голов пропущено")
    NumberField(personalGoals, onPersonalGoals, "Личные голы")
    NumberField(assists, onAssists, "Голевые передачи")
    NumberField(distance, onDistance, "Дистанция, км (необязательно)")
    NumberField(players, onPlayers, "Игроков в команде (необязательно)")
    NumberField(duration, onDuration, "Время игры, мин (необязательно)")
}

@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
        label = { Text(label) }, singleLine = true,
    )
}

@Composable
private fun ClimbingFields(routes: MutableList<RouteDraft>) {
    Text("Трассы", style = MaterialTheme.typography.titleMedium)
    routes.forEachIndexed { index, route ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Трасса ${index + 1}", style = MaterialTheme.typography.titleSmall)
                    DropdownSelector(
                        label = "Тип",
                        selectedText = route.workoutType.title,
                        options = ClimbingWorkoutType.entries,
                        optionLabel = { it.title },
                        onSelected = { routes[index] = route.copy(workoutType = it) },
                    )
                    DropdownSelector(
                        label = "Сложность (французская шкала)",
                        selectedText = route.difficulty,
                        options = ClimbingDifficultyCatalog.values,
                        optionLabel = { it },
                        onSelected = { routes[index] = route.copy(difficulty = it) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = route.completed,
                            onClick = { routes[index] = route.copy(completed = true) },
                            label = { Text("Пройдена") },
                        )
                        FilterChip(
                            selected = !route.completed,
                            onClick = { routes[index] = route.copy(completed = false) },
                            label = { Text("Не пройдена") },
                        )
                    }
                    if (routes.size > 1) TextButton(onClick = { routes.removeAt(index) }) { Text("Удалить трассу") }
                }
            }
        }
    }
    TextButton(onClick = { routes.add(RouteDraft()) }) { Text("+ Добавить трассу") }
}
