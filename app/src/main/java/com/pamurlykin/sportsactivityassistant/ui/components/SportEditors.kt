package com.pamurlykin.sportsactivityassistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pamurlykin.sportsactivityassistant.data.backup.ClimbingRouteBackup
import com.pamurlykin.sportsactivityassistant.data.model.*
import com.pamurlykin.sportsactivityassistant.data.sport.SportModules
import com.pamurlykin.sportsactivityassistant.ui.theme.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID

interface SportEditor {
    @Composable fun rememberDraft(initial: AddCompletedTrainingInput? = null): SportDraft
}

interface SportDraft {
    @Composable fun Content(enabled: Boolean = true)
    fun input(sportId: Int, complexId: Long, date: LocalDate): AddCompletedTrainingInput
}

/** UI extension point: the common form knows nothing about sport-specific fields. */
object SportEditors {
    private data class Presentation(val editor: SportEditor, val visual: SportVisual)
    private val bySlug = mapOf(
        "football" to Presentation(FootballEditor, SportVisual(Icons.Rounded.SportsSoccer, Pine, Sky)),
        "climbing" to Presentation(ClimbingEditor, SportVisual(Icons.Rounded.Terrain, Mist, Clay)),
    )
    fun find(slug: String?): SportEditor? = bySlug[slug]?.editor
    fun visual(slug: String): SportVisual? = bySlug[slug]?.visual
}

private val footballValuesSaver = Saver<List<String>, String>(
    save = { Json.encodeToString(ListSerializer(String.serializer()), it) },
    restore = { Json.decodeFromString(ListSerializer(String.serializer()), it) },
)

private object FootballEditor : SportEditor {
    @Composable
    override fun rememberDraft(initial: AddCompletedTrainingInput?): SportDraft {
        val original = initial?.football
        var values by rememberSaveable(stateSaver = footballValuesSaver) {
            mutableStateOf(listOf(
                (original?.teamGoalsScored ?: 0).toString(), (original?.teamGoalsConceded ?: 0).toString(),
                (original?.userGoalsScored ?: 0).toString(), (original?.userAssists ?: 0).toString(),
                original?.distanceKm?.toPlainString().orEmpty(), original?.playersPerTeam?.toString().orEmpty(),
                original?.durationMinutes?.toString().orEmpty(),
            ))
        }
        var attempted by rememberSaveable { mutableStateOf(false) }
        val parsed = FootballFormValues.parse(values)
        return object : SportDraft {
            @Composable override fun Content(enabled: Boolean) {
                Text("Футбольная статистика", style = MaterialTheme.typography.titleMedium)
                val labels = listOf("Голов забито", "Голов пропущено", "Личные голы", "Голевые передачи",
                    "Дистанция, км (необязательно)", "Игроков в команде (необязательно)", "Время игры, мин (необязательно)")
                labels.forEachIndexed { index, label ->
                    NumberField(values[index], { text ->
                        values = values.mapIndexed { i, value -> if (i == index) text else value }
                    }, label, "football-$index", if (attempted) parsed.errors[index] else null, enabled, index == 4)
                }
            }
            override fun input(sportId: Int, complexId: Long, date: LocalDate): AddCompletedTrainingInput {
                attempted = true
                require(parsed.errors.isEmpty()) { "Исправьте отмеченные поля футбольной статистики" }
                return AddCompletedTrainingInput(sportId, complexId, date, football = requireNotNull(parsed.input))
                    .also { SportModules.require("football").validate(it) }
            }
        }
    }
}

@Serializable
private data class RouteDraft(val route: ClimbingRouteBackup, val repeatText: String = route.repeatCount.toString()) {
    companion object {
        fun fresh() = RouteDraft(ClimbingRouteBackup("difficulty", "6a", true,
            gradingSystem = ClimbingDifficultyCatalog.FRENCH, gradeCode = "6a", publicId = UUID.randomUUID().toString()))
        fun from(input: ClimbingRouteInput) = RouteDraft(ClimbingRouteBackup(
            input.legacyWorkoutType ?: input.workoutType.storageValue, input.routeDifficulty, input.isCompleted,
            input.repeatCount, input.gradingSystem, input.gradeCode, input.speedCourse, input.legacyWorkoutType,
            input.publicId ?: UUID.randomUUID().toString(),
        ))
    }
}

private val routeSaver = Saver<List<RouteDraft>, String>(
    save = { Json.encodeToString(ListSerializer(RouteDraft.serializer()), it) },
    restore = { Json.decodeFromString(ListSerializer(RouteDraft.serializer()), it) },
)

private object ClimbingEditor : SportEditor {
    @Composable
    override fun rememberDraft(initial: AddCompletedTrainingInput?): SportDraft {
        var routes by rememberSaveable(stateSaver = routeSaver) {
            mutableStateOf(initial?.climbingRoutes?.map(RouteDraft::from) ?: listOf(RouteDraft.fresh()))
        }
        var attempted by rememberSaveable { mutableStateOf(false) }
        return object : SportDraft {
            @Composable override fun Content(enabled: Boolean) =
                ClimbingFields(routes, { routes = it }, attempted, enabled)

            override fun input(sportId: Int, complexId: Long, date: LocalDate): AddCompletedTrainingInput {
                attempted = true
                val result = AddCompletedTrainingInput(sportId, complexId, date, climbingRoutes = routes.map { draft ->
                    val route = draft.route
                    val count = requireNotNull(draft.repeatText.trim().toIntOrNull()) { "Количество попыток должно быть целым числом" }
                    ClimbingRouteInput(ClimbingWorkoutType.fromStorage(route.workoutType), route.routeDifficulty,
                        route.completed, count, requireNotNull(route.gradingSystem), route.gradeCode,
                        route.speedCourse, route.legacyWorkoutType, route.publicId)
                })
                val module = SportModules.require("climbing")
                if (initial == null) module.validate(result) else module.validateEdit(result, initial)
                return result
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String, onValueChange: (String) -> Unit, label: String, tag: String,
    error: String?, enabled: Boolean, decimal: Boolean = false,
) {
    OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth().testTag(tag),
        enabled = enabled, label = { Text(label) }, singleLine = true, isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number))
}

@Composable
private fun ClimbingFields(routes: List<RouteDraft>, onChange: (List<RouteDraft>) -> Unit, attempted: Boolean, enabled: Boolean) {
    Text("Трассы", style = MaterialTheme.typography.titleMedium)
    routes.forEachIndexed { index, draft ->
        key(draft.route.publicId) {
            val route = draft.route
            fun update(next: RouteDraft) = onChange(routes.toMutableList().also { it[index] = next })
            val type = ClimbingWorkoutType.fromStorage(route.workoutType)
            val historical = route.gradingSystem == ClimbingDifficultyCatalog.LEGACY
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Трасса ${index + 1}", style = MaterialTheme.typography.titleSmall)
                    if (historical) {
                        Text("${route.workoutType} · ${route.routeDifficulty.ifEmpty { "без категории" }}")
                        Text("Историческая категория: шкала не подтверждена. Обозначение сохраняется без изменения.",
                            style = MaterialTheme.typography.bodySmall)
                    } else {
                        DropdownSelector("Тип", type.title, ClimbingWorkoutType.supported, { it.title }, { next ->
                            val difficulty = when (next) {
                                ClimbingWorkoutType.DIFFICULTY -> "6a"
                                ClimbingWorkoutType.BOULDERING -> "6A"
                                else -> ""
                            }
                            val system = ClimbingDifficultyCatalog.systemFor(next)
                            update(draft.copy(route = route.copy(workoutType = next.storageValue, routeDifficulty = difficulty,
                                gradingSystem = system, gradeCode = ClimbingDifficultyCatalog.find(system, difficulty)?.code, speedCourse = null)))
                        }, Modifier.testTag("route-$index-type"), enabled)
                        if (type == ClimbingWorkoutType.SPEED) {
                            DropdownSelector("Трасса скорости",
                                SpeedCourse.entries.firstOrNull { it.code == route.speedCourse }?.title ?: "Выберите трассу",
                                SpeedCourse.entries, { it.title }, { update(draft.copy(route = route.copy(speedCourse = it.code))) },
                                Modifier.testTag("route-$index-course"), enabled)
                            if (attempted && route.speedCourse == null) Text("Выберите трассу скорости", color = MaterialTheme.colorScheme.error)
                            Text("Категория сложности не применяется. Эталонная трасса должна соответствовать стандарту 15 м.",
                                style = MaterialTheme.typography.bodySmall)
                        } else {
                            val system = ClimbingDifficultyCatalog.systemFor(type)
                            DropdownSelector("Сложность (${ClimbingDifficultyCatalog.title(system)})", route.routeDifficulty,
                                ClimbingDifficultyCatalog.grades(system), { it.label },
                                { update(draft.copy(route = route.copy(routeDifficulty = it.label, gradeCode = it.code))) },
                                Modifier.testTag("route-$index-grade"), enabled)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(route.completed, { update(draft.copy(route = route.copy(completed = true))) },
                            enabled = enabled, label = { Text("Пройдена") })
                        FilterChip(!route.completed, { update(draft.copy(route = route.copy(completed = false))) },
                            enabled = enabled, label = { Text("Не пройдена") })
                    }
                    val countError = if (attempted && (draft.repeatText.trim().toIntOrNull() ?: 0) <= 0)
                        "Введите положительное целое число" else null
                    NumberField(draft.repeatText, { update(draft.copy(repeatText = it)) },
                        "Количество попыток с этим результатом", "route-$index-repeat", countError, enabled)
                    Text("Все попытки этой строки имеют одинаковый результат. Для разных результатов добавьте отдельные строки.",
                        style = MaterialTheme.typography.bodySmall)
                    if (routes.size > 1) TextButton(enabled = enabled, onClick = {
                        onChange(routes.filterIndexed { i, _ -> i != index })
                    }) { Text("Удалить трассу") }
                }
            }
        }
    }
    TextButton(enabled = enabled, onClick = { onChange(routes + RouteDraft.fresh()) }) { Text("+ Добавить трассу") }
}
