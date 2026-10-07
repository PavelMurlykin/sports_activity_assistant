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
        require(listOf(details.teamGoalsScored, details.teamGoalsConceded, details.userGoalsScored, details.userAssists).all { it >= 0 }) {
            "Счёт, личные голы и передачи не могут быть отрицательными"
        }
        require(details.userGoalsScored <= details.teamGoalsScored) { "Личные голы не могут превышать счёт команды" }
        require(details.userAssists <= details.teamGoalsScored) { "Передачи не могут превышать число голов команды" }
        require(details.distanceKm == null || details.distanceKm >= BigDecimal.ZERO) { "Дистанция не может быть отрицательной" }
        require(details.playersPerTeam == null || details.playersPerTeam > 0) { "Число игроков должно быть положительным" }
        require(details.durationMinutes == null || details.durationMinutes > 0) { "Время игры должно быть положительным" }
    }

    override suspend fun insertDetails(dao: TrainingDao, trainingId: Long, input: AddCompletedTrainingInput) {
        val it = requireNotNull(input.football)
        dao.insertFootballTraining(FootballTrainingEntity(
            trainingId, it.teamGoalsScored, it.teamGoalsConceded, it.userGoalsScored,
            it.userAssists, it.distanceKm, it.playersPerTeam, it.durationMinutes,
        ))
    }

    override fun decodeDetails(backup: TrainingBackup, sportId: Int, complexId: Long): AddCompletedTrainingInput {
        require(backup.climbingRoutes.isEmpty()) { "Футбольная запись содержит трассы" }
        val it = requireNotNull(backup.football) { "В футбольной записи отсутствует статистика" }
        return AddCompletedTrainingInput(sportId, complexId, LocalDate.parse(backup.date), football = FootballTrainingInput(
            it.teamGoalsScored, it.teamGoalsConceded, it.userGoalsScored, it.userAssists,
            it.distanceKm?.toBigDecimal(), it.playersPerTeam, it.durationMinutes,
        ))
    }

    override fun encodeDetails(bundle: TrainingBundle, common: TrainingBackup): TrainingBackup = common.copy(
        football = bundle.football?.let {
            FootballBackup(it.teamGoalsScored, it.teamGoalsConceded, it.userGoalsScored, it.userAssists,
                it.distanceKm?.toPlainString(), it.playersPerTeam, it.durationMinutes)
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
