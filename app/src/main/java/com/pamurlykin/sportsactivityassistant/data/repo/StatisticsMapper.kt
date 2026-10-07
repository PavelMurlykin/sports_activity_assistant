package com.pamurlykin.sportsactivityassistant.data.repo

import com.pamurlykin.sportsactivityassistant.data.entity.SportEntity
import com.pamurlykin.sportsactivityassistant.data.entity.TrainingBundle
import com.pamurlykin.sportsactivityassistant.data.model.MetricUiModel
import com.pamurlykin.sportsactivityassistant.data.model.MonthStatisticsUiModel
import com.pamurlykin.sportsactivityassistant.data.model.SportStatisticsUiModel
import com.pamurlykin.sportsactivityassistant.data.model.SportSummaryUiModel
import com.pamurlykin.sportsactivityassistant.data.model.StatisticsOverviewUiModel
import com.pamurlykin.sportsactivityassistant.data.sport.SportModules
import java.time.YearMonth

object StatisticsMapper {
    fun overview(sports: List<SportEntity>, bundles: List<TrainingBundle>): StatisticsOverviewUiModel {
        val bySport = bundles.groupBy { it.sport.id }
        val summaries = sports.map { sport ->
            val items = bySport[sport.id].orEmpty()
            SportSummaryUiModel(
                id = sport.id, slug = sport.slug, title = sport.title,
                completedTrainings = items.size,
                highlights = SportModules.find(sport.slug)?.highlights(items).orEmpty(),
            )
        }
        val months = bundles.groupBy { YearMonth.from(it.training.trainingDate) }
            .toSortedMap(compareByDescending { it }).map { (month, items) ->
                val counts = items.groupingBy { it.sport.title }.eachCount()
                MonthStatisticsUiModel(month, items.size,
                    counts.entries.sortedBy { it.key }.map { "${it.key}: ${it.value}" })
            }
        return StatisticsOverviewUiModel(bundles.size, summaries, months)
    }

    fun sport(sport: SportEntity, bundles: List<TrainingBundle>): SportStatisticsUiModel =
        SportStatisticsUiModel(sport.id, sport.slug, sport.title,
            SportModules.find(sport.slug)?.metrics(bundles)
                ?: listOf(MetricUiModel("Исторические тренировки", bundles.size.toString()),
                    MetricUiModel("Статистика", "Вид спорта не поддерживается")))
}
