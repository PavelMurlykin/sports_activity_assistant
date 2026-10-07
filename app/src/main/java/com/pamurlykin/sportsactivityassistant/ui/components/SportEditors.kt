package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.data.model.AddCompletedTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.ClimbingDifficultyCatalog
import com.pamurlykin.sportsactivityassistant.data.model.ClimbingRouteInput
import com.pamurlykin.sportsactivityassistant.data.model.ClimbingWorkoutType
import com.pamurlykin.sportsactivityassistant.data.model.FootballTrainingInput
import java.time.LocalDate

import com.pamurlykin.sportsactivityassistant.data.sport.SportModules
import com.pamurlykin.sportsactivityassistant.ui.theme.Clay
import com.pamurlykin.sportsactivityassistant.ui.theme.Mist
import com.pamurlykin.sportsactivityassistant.ui.theme.Pine
import com.pamurlykin.sportsactivityassistant.ui.theme.Sky

interface SportEditor {
    @Composable fun rememberDraft(): SportDraft
}

interface SportDraft {
    @Composable fun Content()
    fun input(sportId: Int, complexId: Long, date: LocalDate): AddCompletedTrainingInput
}

/** UI extension point: adding a sport does not require editing the common dialog. */
object SportEditors {
    private data class Presentation(val editor: SportEditor, val visual: SportVisual)
    private val bySlug = mapOf(
        "football" to Presentation(FootballEditor, SportVisual(Icons.Rounded.SportsSoccer, Pine, Sky)),
        "climbing" to Presentation(ClimbingEditor, SportVisual(Icons.Rounded.Terrain, Mist, Clay)),
    )
    fun find(slug: String?): SportEditor? = bySlug[slug]?.editor
    fun visual(slug: String): SportVisual? = bySlug[slug]?.visual
}

private object FootballEditor : SportEditor {
    @Composable
    override fun rememberDraft(): SportDraft {
        var teamGoals by rememberSaveable { mutableStateOf("0") }
        var concededGoals by rememberSaveable { mutableStateOf("0") }
        var personalGoals by rememberSaveable { mutableStateOf("0") }
        var assists by rememberSaveable { mutableStateOf("0") }
        var distance by rememberSaveable { mutableStateOf("") }
        var players by rememberSaveable { mutableStateOf("") }
        var duration by rememberSaveable { mutableStateOf("") }
        return object : SportDraft {
            @Composable override fun Content() = FootballFields(
                teamGoals, { teamGoals = it }, concededGoals, { concededGoals = it },
                personalGoals, { personalGoals = it }, assists, { assists = it },
                distance, { distance = it }, players, { players = it }, duration, { duration = it },
            )
            override fun input(sportId: Int, complexId: Long, date: LocalDate): AddCompletedTrainingInput {
                fun required(value: String) = requireNotNull(value.trim().toIntOrNull()) { "Введите целые числа для счёта, голов и передач" }
                fun optional(value: String) = value.trim().takeIf(String::isNotBlank)?.let {
                    requireNotNull(it.toIntOrNull()) { "Число игроков и время игры должны быть целыми числами" }
                }
                val km = distance.trim().takeIf(String::isNotBlank)?.replace(',', '.')?.let {
                    requireNotNull(it.toBigDecimalOrNull()) { "Проверьте дистанцию" }
                }
                return AddCompletedTrainingInput(sportId, complexId, date, football = FootballTrainingInput(
                    required(teamGoals), required(concededGoals), required(personalGoals), required(assists),
                    km, optional(players), optional(duration),
                )).also { SportModules.require("football").validate(it) }
            }
        }
    }
}

private data class RouteDraft(
    val workoutType: ClimbingWorkoutType = ClimbingWorkoutType.DIFFICULTY,
    val difficulty: String = "6A",
    val completed: Boolean = true,
)

private object ClimbingEditor : SportEditor {
    @Composable
    override fun rememberDraft(): SportDraft {
        val routes = remember { mutableStateListOf(RouteDraft()) }
        return object : SportDraft {
            @Composable override fun Content() = ClimbingFields(routes)
            override fun input(sportId: Int, complexId: Long, date: LocalDate): AddCompletedTrainingInput =
                AddCompletedTrainingInput(sportId, complexId, date, climbingRoutes = routes.map {
                    ClimbingRouteInput(it.workoutType, it.difficulty, it.completed)
                }).also { SportModules.require("climbing").validate(it) }
        }
    }
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
