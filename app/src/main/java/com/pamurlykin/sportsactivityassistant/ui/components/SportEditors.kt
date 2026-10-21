package com.pamurlykin.sportsactivityassistant.ui.components

import com.pamurlykin.sportsactivityassistant.R

import com.pamurlykin.sportsactivityassistant.text.AppText

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
        "football" to Presentation(FootballEditor, SportVisual(Icons.Rounded.SportsSoccer, Pine, Sky, R.drawable.sport_football)),
        "climbing" to Presentation(ClimbingEditor, SportVisual(Icons.Rounded.Terrain, Mist, Clay, R.drawable.sport_climbing)),
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
                val labels = listOf(AppText.get(R.string.score_scored), AppText.get(R.string.score_conceded),
                    AppText.get(R.string.personal_goals_short), AppText.get(R.string.football_module_golevye_peredachi),
                    AppText.get(R.string.sport_editors_distantsiya_km_neobyazatelno), AppText.get(R.string.sport_editors_igrokov_v_komande_neobyazatelno),
                    AppText.get(R.string.sport_editors_vremya_igry_min_neobyazatelno))
                fun update(index: Int, next: Int) { values = values.mapIndexed { i, value -> if (i == index) next.toString() else value } }
                Text(AppText.get(R.string.score_title), Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                WorkoutCard(Modifier.testTag("football-score-card")) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ScoreWheelPicker(labels[0], values[0].toIntOrNull() ?: 0, { update(0, it) }, Modifier.weight(1f), enabled, "football-0")
                        Box(Modifier.height(102.dp).width(14.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Text(":", style = MaterialTheme.typography.headlineLarge)
                        }
                        ScoreWheelPicker(labels[1], values[1].toIntOrNull() ?: 0, { update(1, it) }, Modifier.weight(1f), enabled, "football-1")
                    }
                    if (attempted) (0..1).mapNotNull { parsed.errors[it] }.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.height(8.dp))
                Text(AppText.get(R.string.personal_statistics), Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    (2..3).forEach { index ->
                        WorkoutCard(Modifier.weight(1f).testTag("football-personal-$index")) {
                            ScoreWheelPicker(labels[index], values[index].toIntOrNull() ?: 0, { update(index, it) },
                                Modifier.fillMaxWidth(), enabled, "football-$index")
                            if (attempted) parsed.errors[index]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                labels.drop(4).forEachIndexed { offset, label ->
                    val index = offset + 4
                    StepNumberField(values[index], { text ->
                        values = values.mapIndexed { i, value -> if (i == index) text else value }
                    }, label, "football-$index", if (attempted) parsed.errors[index] else null, enabled, index == 4, increment = if (index == 6) 5 else 1)
                }
            }
            override fun input(sportId: Int, complexId: Long, date: LocalDate): AddCompletedTrainingInput {
                attempted = true
                require(parsed.errors.isEmpty()) { AppText.get(R.string.sport_editors_ispravte_otmechennye_polya_futbolnoy_statistiki) }
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
            mutableStateOf(initial?.climbingRoutes?.map(RouteDraft::from)?.takeIf { it.isNotEmpty() } ?: listOf(RouteDraft.fresh()))
        }
        var attempted by rememberSaveable { mutableStateOf(false) }
        return object : SportDraft {
            @Composable override fun Content(enabled: Boolean) =
                ClimbingFields(routes, { routes = it }, attempted, enabled)

            override fun input(sportId: Int, complexId: Long, date: LocalDate): AddCompletedTrainingInput {
                attempted = true
                val result = AddCompletedTrainingInput(sportId, complexId, date, climbingRoutes = routes.map { draft ->
                    val route = draft.route
                    val count = requireNotNull(draft.repeatText.trim().toIntOrNull()) { AppText.get(R.string.sport_editors_kolichestvo_popytok_dolzhno_byt_tselym) }
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
private fun ClimbingFields(routes: List<RouteDraft>, onChange: (List<RouteDraft>) -> Unit, attempted: Boolean, enabled: Boolean) {
    Text(AppText.get(R.string.sport_editors_trassy), style = MaterialTheme.typography.headlineMedium)
    routes.forEachIndexed { index, draft ->
        key(draft.route.publicId) {
            val route = draft.route
            fun update(next: RouteDraft) = onChange(routes.toMutableList().also { it[index] = next })
            val type = ClimbingWorkoutType.fromStorage(route.workoutType)
            val historical = route.gradingSystem == ClimbingDifficultyCatalog.LEGACY
            WorkoutCard {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(AppText.get(R.string.sport_editors_trassa, index + 1), style = MaterialTheme.typography.titleLarge)
                    if (historical) {
                        Text(AppText.get(R.string.details_pair, route.workoutType,
                            route.routeDifficulty.ifEmpty { AppText.get(R.string.climbing_module_bez_kategorii) }))
                        Text(AppText.get(R.string.sport_editors_istoricheskaya_kategoriya_shkala_ne_podtverzhdena),
                            style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(AppText.get(R.string.sport_editors_tip), style = MaterialTheme.typography.labelLarge)
                        FlowRow(Modifier.testTag("route-${index}-type"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ClimbingWorkoutType.supported.forEach { next ->
                                FilterChip(selected = type == next, enabled = enabled, label = { Text(next.title) }, onClick = {
                                    if (type != next) {
                                        val difficulty = when (next) {
                                            ClimbingWorkoutType.DIFFICULTY -> "6a"
                                            ClimbingWorkoutType.BOULDERING -> "6A"
                                            else -> ""
                                        }
                                        val system = ClimbingDifficultyCatalog.systemFor(next)
                                        update(draft.copy(route = route.copy(workoutType = next.storageValue, routeDifficulty = difficulty,
                                            gradingSystem = system, gradeCode = ClimbingDifficultyCatalog.find(system, difficulty)?.code, speedCourse = null)))
                                    }
                                })
                            }
                        }
                        if (type == ClimbingWorkoutType.SPEED) {
                            DropdownSelector(AppText.get(R.string.sport_editors_trassa_skorosti),
                                SpeedCourse.entries.firstOrNull { it.code == route.speedCourse }?.title ?: AppText.get(R.string.sport_editors_vyberite_trassu),
                                SpeedCourse.entries, { it.title }, { update(draft.copy(route = route.copy(speedCourse = it.code))) },
                                Modifier.testTag("route-${index}-course"), enabled)
                            if (attempted && route.speedCourse == null) Text(AppText.get(R.string.climbing_module_vyberite_trassu_skorosti), color = MaterialTheme.colorScheme.error)
                            Text(AppText.get(R.string.sport_editors_kategoriya_slozhnosti_ne_primenyaetsya_etalonnaya),
                                style = MaterialTheme.typography.bodySmall)
                        } else {
                            val system = ClimbingDifficultyCatalog.systemFor(type)
                            val grades = ClimbingDifficultyCatalog.grades(system)
                            WheelPicker(AppText.get(R.string.sport_editors_slozhnost, ClimbingDifficultyCatalog.title(system)),
                                grades.map { it.label }, grades.indexOfFirst { it.code == route.gradeCode },
                                { next -> update(draft.copy(route = route.copy(routeDifficulty = grades[next].label, gradeCode = grades[next].code))) },
                                Modifier.fillMaxWidth().testTag("route-${index}-grade"), enabled)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(route.completed, { update(draft.copy(route = route.copy(completed = true))) },
                            enabled = enabled, label = { Text(AppText.get(R.string.sport_editors_proydena)) })
                        FilterChip(!route.completed, { update(draft.copy(route = route.copy(completed = false))) },
                            enabled = enabled, label = { Text(AppText.get(R.string.sport_editors_ne_proydena)) })
                    }
                    val countError = if (attempted && (draft.repeatText.trim().toIntOrNull() ?: 0) <= 0)
                        AppText.get(R.string.sport_editors_vvedite_polozhitelnoe_tseloe_chislo) else null
                    StepNumberField(draft.repeatText, { update(draft.copy(repeatText = it)) },
                        AppText.get(R.string.sport_editors_kolichestvo_popytok_s_etim_rezultatom), "route-${index}-repeat", countError, enabled)
                    Text(AppText.get(R.string.sport_editors_vse_popytki_etoy_stroki_imeyut),
                        style = MaterialTheme.typography.bodySmall)
                    if (routes.size > 1) TextButton(enabled = enabled, onClick = {
                        onChange(routes.filterIndexed { i, _ -> i != index })
                    }) { Text(AppText.get(R.string.sport_editors_udalit_trassu)) }
                }
            }
        }
    }
    TextButton(enabled = enabled, onClick = { onChange(routes + RouteDraft.fresh()) }) { Text(AppText.get(R.string.sport_editors_dobavit_trassu)) }
}
