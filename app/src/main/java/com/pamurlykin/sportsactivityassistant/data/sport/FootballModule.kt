package com.pamurlykin.sportsactivityassistant.data.sport

import com.pamurlykin.sportsactivityassistant.data.backup.FootballBackup
import com.pamurlykin.sportsactivityassistant.data.backup.TrainingBackup
import com.pamurlykin.sportsactivityassistant.data.dao.TrainingDao
import com.pamurlykin.sportsactivityassistant.data.entity.FootballTrainingEntity
import com.pamurlykin.sportsactivityassistant.data.entity.TrainingBundle
import com.pamurlykin.sportsactivityassistant.data.model.AddCompletedTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.FootballTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.MetricUiModel
import com.pamurlykin.sportsactivityassistant.data.model.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
            // Room stores plain TEXT: equivalent exponent/plain forms must validate equally.
            val normalized = runCatching { it.stripTrailingZeros() }.getOrNull()
            if (it < BigDecimal.ZERO) put(4, "Дистанция не может быть отрицательной")
            else if (normalized == null || normalized.precision() > 16 || normalized.scale() > 6 ||
                normalized.precision().toLong() - normalized.scale() > 22)
                put(4, "Не более 16 значащих цифр, 6 десятичных знаков и 22 цифр целой части")
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

    override suspend fun aggregate(dao: TrainingDao, selection: StatisticsSelection, includeMetrics: Boolean): SportAggregate {
        val (user, sport, filter) = selection
        val totals = dao.footballTotals(user, sport, filter.firstDate, filter.lastDate, filter.centerId)
        if (!includeMetrics) return SportAggregate(emptyList(), listOf("${totals.personalGoals} голов", "${totals.assists} передач"))
        var sum = BigDecimal.ZERO
        var afterId = 0L
        while (totals.distanceCount > 0) {
            currentCoroutineContext().ensureActive()
            val batch = dao.distanceSamples(user, sport, filter.firstDate, filter.lastDate, filter.centerId, afterId)
            if (batch.isEmpty()) break
            batch.forEach { sum = sum.add(it.distanceKm) }
            afterId = batch.last().id
        }
        return result(totals, sum)
    }

    private fun fromItems(items: List<TrainingBundle>): SportAggregate {
        val rows = items.mapNotNull { it.football }
        val distances = rows.mapNotNull { it.distanceKm }
        val durations = rows.mapNotNull { it.durationMinutes }
        val players = rows.mapNotNull { it.playersPerTeam }
        return result(FootballTotals(
            games = rows.size.toLong(),
            wins = rows.count { it.teamGoalsScored > it.teamGoalsConceded }.toLong(),
            draws = rows.count { it.teamGoalsScored == it.teamGoalsConceded }.toLong(),
            losses = rows.count { it.teamGoalsScored < it.teamGoalsConceded }.toLong(),
            teamScored = rows.sumOf { it.teamGoalsScored.toLong() },
            teamConceded = rows.sumOf { it.teamGoalsConceded.toLong() },
            personalGoals = rows.sumOf { it.userGoalsScored.toLong() },
            assists = rows.sumOf { it.userAssists.toLong() },
            distanceCount = distances.size.toLong(),
            minutes = durations.sumOf { it.toLong() }, durationCount = durations.size.toLong(),
            players = players.sumOf { it.toLong() }, playersCount = players.size.toLong(),
        ), distances.fold(BigDecimal.ZERO, BigDecimal::add))
    }

    override fun highlights(items: List<TrainingBundle>) = fromItems(items).highlights
    override fun metrics(items: List<TrainingBundle>) = fromItems(items).metrics

    private fun result(t: FootballTotals, distance: BigDecimal): SportAggregate {
        fun decimal(value: BigDecimal) = value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        fun average(value: BigDecimal, count: Long, unit: String) =
            if (count == 0L) "Нет данных" else "${decimal(value.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP))} $unit"
        return SportAggregate(listOf(
            MetricUiModel("Игры", t.games.toString()),
            MetricUiModel("Победы / ничьи / поражения", "${t.wins} / ${t.draws} / ${t.losses}"),
            MetricUiModel("Счёт команд", "${t.teamScored}:${t.teamConceded}"),
            MetricUiModel("Личные голы", t.personalGoals.toString()),
            MetricUiModel("Голевые передачи", t.assists.toString()),
            MetricUiModel("Учтённая дистанция", if (t.distanceCount == 0L) "Нет данных" else "${decimal(distance)} км"),
            MetricUiModel("Средняя дистанция", average(distance, t.distanceCount, "км")),
            MetricUiModel("Игр с дистанцией", "${t.distanceCount} из ${t.games}"),
            MetricUiModel("Учтённое время", if (t.durationCount == 0L) "Нет данных" else "${t.minutes} мин"),
            MetricUiModel("Средняя длительность", average(BigDecimal.valueOf(t.minutes), t.durationCount, "мин")),
            MetricUiModel("Игр со временем", "${t.durationCount} из ${t.games}"),
            MetricUiModel("Средний состав команды", average(BigDecimal.valueOf(t.players), t.playersCount, "игроков")),
            MetricUiModel("Игр с числом игроков", "${t.playersCount} из ${t.games}"),
        ), listOf("${t.personalGoals} голов", "${t.assists} передач"))
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
