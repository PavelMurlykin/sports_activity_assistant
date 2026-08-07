package com.pamurlykin.sportsactivityassistant.data.repo

import com.pamurlykin.sportsactivityassistant.data.entity.SportEntity
import com.pamurlykin.sportsactivityassistant.data.entity.TrainingBundle
import com.pamurlykin.sportsactivityassistant.data.model.ClimbingDifficultyCatalog
import com.pamurlykin.sportsactivityassistant.data.model.MetricUiModel
import com.pamurlykin.sportsactivityassistant.data.model.MonthStatisticsUiModel
import com.pamurlykin.sportsactivityassistant.data.model.SportStatisticsUiModel
import com.pamurlykin.sportsactivityassistant.data.model.SportSummaryUiModel
import com.pamurlykin.sportsactivityassistant.data.model.StatisticsOverviewUiModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

object StatisticsMapper {
    fun overview(sports: List<SportEntity>, bundles: List<TrainingBundle>): StatisticsOverviewUiModel {
        val bySport = bundles.groupBy { it.sport.id }
        val summaries = sports.map { sport ->
            val items = bySport[sport.id].orEmpty()
            SportSummaryUiModel(
                id = sport.id,
                slug = sport.slug,
                title = sport.title,
                completedTrainings = items.size,
                highlights = highlights(sport.slug, items),
            )
        }
        val months = bundles
            .groupBy { YearMonth.from(it.training.trainingDate) }
            .toSortedMap(compareByDescending { it })
            .map { (month, items) ->
                val counts = items.groupingBy { it.sport.title }.eachCount()
                MonthStatisticsUiModel(
                    month = month,
                    totalTrainings = items.size,
                    countsBySport = counts.entries.sortedBy { it.key }.map { "${it.key}: ${it.value}" },
                )
            }
        return StatisticsOverviewUiModel(bundles.size, summaries, months)
    }

    fun sport(sport: SportEntity, bundles: List<TrainingBundle>): SportStatisticsUiModel {
        val metrics = when (sport.slug) {
            "football" -> footballMetrics(bundles)
            "climbing" -> climbingMetrics(bundles)
            else -> listOf(MetricUiModel("Тренировки", bundles.size.toString()))
        }
        return SportStatisticsUiModel(sport.id, sport.slug, sport.title, metrics)
    }

    private fun highlights(slug: String, items: List<TrainingBundle>): List<String> = when (slug) {
        "football" -> {
            val football = items.mapNotNull { it.football }
            listOf("${football.sumOf { it.userGoalsScored }} голов", "${football.sumOf { it.userAssists }} передач")
        }
        "climbing" -> {
            val routes = items.flatMap { it.climbing?.routes.orEmpty() }
            val attempts = routes.sumOf { it.repeatCount }
            val completed = routes.filter { it.isCompleted }.sumOf { it.repeatCount }
            listOf("$completed из $attempts трасс пройдено")
        }
        else -> emptyList()
    }

    private fun footballMetrics(items: List<TrainingBundle>): List<MetricUiModel> {
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
            if (distance > BigDecimal.ZERO) add(MetricUiModel("Учтённая дистанция", "${format(distance)} км"))
            if (duration.isNotEmpty()) add(MetricUiModel("Средняя длительность", "${duration.average().toInt()} мин"))
        }
    }

    private fun climbingMetrics(items: List<TrainingBundle>): List<MetricUiModel> {
        val routes = items.flatMap { it.climbing?.routes.orEmpty() }
        val attempts = routes.sumOf { it.repeatCount }
        val completedRoutes = routes.filter { it.isCompleted }
        val completed = completedRoutes.sumOf { it.repeatCount }
        val percent = if (attempts == 0) 0 else completed * 100 / attempts
        val hardest = ClimbingDifficultyCatalog.hardest(completedRoutes.map { it.routeDifficulty })
        return buildList {
            add(MetricUiModel("Тренировки", items.size.toString()))
            add(MetricUiModel("Трассы", attempts.toString()))
            add(MetricUiModel("Успешно пройдено", "$completed ($percent%)"))
            hardest?.let { add(MetricUiModel("Максимальная сложность", it)) }
            enumValues<com.pamurlykin.sportsactivityassistant.data.model.ClimbingWorkoutType>().forEach { type ->
                val count = routes.filter { it.workoutType == type }.sumOf { it.repeatCount }
                if (count > 0) add(MetricUiModel(type.title, count.toString()))
            }
        }
    }

    private fun format(value: BigDecimal): String = value
        .setScale(2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}
