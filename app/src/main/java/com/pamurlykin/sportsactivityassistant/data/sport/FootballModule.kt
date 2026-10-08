package com.pamurlykin.sportsactivityassistant.data.sport

import com.pamurlykin.sportsactivityassistant.data.backup.FootballBackup
import com.pamurlykin.sportsactivityassistant.data.backup.TrainingBackup
import com.pamurlykin.sportsactivityassistant.data.dao.TrainingDao
import com.pamurlykin.sportsactivityassistant.data.entity.FootballTrainingEntity
import com.pamurlykin.sportsactivityassistant.data.entity.TrainingBundle
import com.pamurlykin.sportsactivityassistant.data.model.AddCompletedTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.FootballTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.MetricUiModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

object FootballModule : SportModule {
    override val slug = "football"
    override val title = "Футбол"

    override fun validate(input: AddCompletedTrainingInput, allowHistorical: Boolean) {
        val details = requireNotNull(input.football) { "Заполните футбольную статистику" }
        require(input.climbingRoutes.isEmpty()) { "Футбольная тренировка не может содержать трассы" }
        val errors = fieldErrors(details)
        require(errors.isEmpty()) { errors.values.first() }
    }

    /** Shared business rules for repository/import validation and inline form errors. */
    fun fieldErrors(details: FootballTrainingInput): Map<Int, String> = buildMap {
        listOf(details.teamGoalsScored, details.teamGoalsConceded, details.userGoalsScored, details.userAssists)
            .forEachIndexed { index, value -> if (value < 0) put(index, "Значение не может быть отрицательным") }
        if (details.userGoalsScored > details.teamGoalsScored) put(2, "Личные голы не могут превышать счёт команды")
        if (details.userAssists > details.teamGoalsScored) put(3, "Передачи не могут превышать число голов команды")
        details.distanceKm?.let {
            if (it < BigDecimal.ZERO) put(4, "Дистанция не может быть отрицательной")
            else if (it.precision() > 16 || it.scale() !in -6..6) put(4, "Не более 16 значащих цифр и 6 десятичных знаков")
        }
        if (details.playersPerTeam != null && details.playersPerTeam <= 0) put(5, "Число игроков должно быть положительным")
        if (details.durationMinutes != null && details.durationMinutes <= 0) put(6, "Время игры должно быть положительным")
    }

    override suspend fun insertDetails(dao: TrainingDao, trainingId: Long, input: AddCompletedTrainingInput) {
        val it = requireNotNull(input.football)
        dao.insertFootballTraining(FootballTrainingEntity(
            trainingId, it.teamGoalsScored, it.teamGoalsConceded, it.userGoalsScored,
            it.userAssists, it.distanceKm, it.playersPerTeam, it.durationMinutes,
        ))
    }

    override suspend fun updateDetails(dao: TrainingDao, bundle: TrainingBundle, input: AddCompletedTrainingInput) =
        insertDetails(dao, bundle.training.id, input)

    override fun decodeDetails(backup: TrainingBackup, sportId: Int, complexId: Long): AddCompletedTrainingInput {
        require(backup.climbingRoutes.isEmpty()) { "Футбольная запись содержит трассы" }
        val it = requireNotNull(backup.football) { "В футбольной записи отсутствует статистика" }
        return AddCompletedTrainingInput(sportId, complexId, LocalDate.parse(backup.date), football = FootballTrainingInput(
            it.teamGoalsScored, it.teamGoalsConceded, it.userGoalsScored, it.userAssists,
            it.distanceKm?.also { value -> require(value.length <= 64) { "Слишком длинная дистанция" } }?.toBigDecimal(), it.playersPerTeam, it.durationMinutes,
        ))
    }

    override fun encodeDetails(bundle: TrainingBundle, common: TrainingBackup): TrainingBackup = common.copy(
        football = bundle.football?.let {
            FootballBackup(it.teamGoalsScored, it.teamGoalsConceded, it.userGoalsScored, it.userAssists,
                it.distanceKm?.toString(), it.playersPerTeam, it.durationMinutes)
        },
    )

    override fun highlights(items: List<TrainingBundle>): List<String> {
        val football = items.mapNotNull { it.football }
        return listOf("${football.sumOf { it.userGoalsScored }} голов", "${football.sumOf { it.userAssists }} передач")
    }

    override fun metrics(items: List<TrainingBundle>): List<MetricUiModel> {
        val football = items.mapNotNull { it.football }
        val wins = football.count { it.teamGoalsScored > it.teamGoalsConceded }
        val draws = football.count { it.teamGoalsScored == it.teamGoalsConceded }
        val losses = football.count { it.teamGoalsScored < it.teamGoalsConceded }
        val distance = football.mapNotNull { it.distanceKm }.fold(BigDecimal.ZERO, BigDecimal::add)
        val duration = football.mapNotNull { it.durationMinutes }
        return buildList {
            add(MetricUiModel("Игры", football.size.toString()))
            add(MetricUiModel("Победы / ничьи / поражения", "$wins / $draws / $losses"))
            add(MetricUiModel("Счёт команд", "${football.sumOf { it.teamGoalsScored }}:${football.sumOf { it.teamGoalsConceded }}"))
            add(MetricUiModel("Личные голы", football.sumOf { it.userGoalsScored }.toString()))
            add(MetricUiModel("Голевые передачи", football.sumOf { it.userAssists }.toString()))
            if (distance > BigDecimal.ZERO) add(MetricUiModel("Учтённая дистанция", "${distance.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()} км"))
            if (duration.isNotEmpty()) add(MetricUiModel("Средняя длительность", "${duration.average().toInt()} мин"))
        }
    }

    override fun details(bundle: TrainingBundle): List<String> = buildList {
        bundle.football?.let {
            add("Счёт: ${it.teamGoalsScored}:${it.teamGoalsConceded}")
            add("Личные голы: ${it.userGoalsScored}")
            add("Голевые передачи: ${it.userAssists}")
            it.distanceKm?.let { value -> add("Дистанция: ${value.stripTrailingZeros().toPlainString()} км") }
            it.playersPerTeam?.let { value -> add("Игроков в команде: $value") }
            it.durationMinutes?.let { value -> add("Время игры: $value мин") }
        }
    }
}
