package com.pamurlykin.sportsactivityassistant.data.repo

import com.pamurlykin.sportsactivityassistant.R

import com.pamurlykin.sportsactivityassistant.text.AppText

import com.pamurlykin.sportsactivityassistant.data.entity.SportEntity
import com.pamurlykin.sportsactivityassistant.data.entity.TrainingBundle
import com.pamurlykin.sportsactivityassistant.data.model.MetricUiModel
import com.pamurlykin.sportsactivityassistant.data.model.MonthStatisticsUiModel
import com.pamurlykin.sportsactivityassistant.data.model.SportStatisticsUiModel
import com.pamurlykin.sportsactivityassistant.data.model.SportSummaryUiModel
import com.pamurlykin.sportsactivityassistant.data.model.StatisticsOverviewUiModel
import com.pamurlykin.sportsactivityassistant.data.sport.SportModules
import com.pamurlykin.sportsactivityassistant.data.model.*
import java.time.YearMonth
import java.time.temporal.ChronoUnit

object StatisticsMapper {
    fun aggregatedOverview(
        sports: List<SportEntity>,
        counts: List<SportCount>,
        months: List<MonthSportCount>,
        highlights: Map<Int, List<String>>,
        filter: StatisticsFilter,
    ): StatisticsOverviewUiModel {
        val countsById = counts.associate { it.sportId to it.count }
        val summaries = sports.map { sport ->
            SportSummaryUiModel(sport.id, sport.slug, sport.title, countsById[sport.id] ?: 0,
                highlights[sport.id].orEmpty())
        }
        val byMonth = months.groupBy { YearMonth.parse(it.month) }
        val first = filter.startDate?.let(YearMonth::from) ?: byMonth.keys.minOrNull()
        val last = filter.endDate?.let(YearMonth::from) ?: byMonth.keys.maxOrNull()
        val titles = sports.associate { it.id to it.title }
        // Zero months are computed on access, not allocated for a potentially 9999-year interval.
        val monthRows = if (first == null || last == null) emptyList() else object : AbstractList<MonthStatisticsUiModel>() {
            override val size = (ChronoUnit.MONTHS.between(first, last) + 1).toInt()
            override fun get(index: Int): MonthStatisticsUiModel {
                if (index !in 0 until size) throw IndexOutOfBoundsException(index.toString())
                val month = last.minusMonths(index.toLong())
                val rows = byMonth[month].orEmpty()
                return MonthStatisticsUiModel(month, rows.sumOf { it.count },
                    rows.sortedBy { titles[it.sportId] }.map { "${titles[it.sportId]}: ${it.count}" })
            }
        }
        return StatisticsOverviewUiModel(counts.sumOf { it.count }, summaries, monthRows, filter)
    }

    /** Small in-memory control calculation for tests; production screens use SQL snapshots. */
    fun overview(sports: List<SportEntity>, bundles: List<TrainingBundle>): StatisticsOverviewUiModel {
        val bySport = bundles.groupBy { it.sport.id }
        return aggregatedOverview(sports,
            bySport.map { (id, items) -> SportCount(id, items.size) },
            bundles.groupBy { YearMonth.from(it.training.trainingDate) to it.sport.id }
                .map { (key, items) -> MonthSportCount(key.first.toString(), key.second, items.size) },
            sports.associate { it.id to SportModules.find(it.slug)?.highlights(bySport[it.id].orEmpty()).orEmpty() },
            StatisticsFilter())
    }

    fun sport(sport: SportEntity, bundles: List<TrainingBundle>): SportStatisticsUiModel =
        SportStatisticsUiModel(sport.id, sport.slug, sport.title,
            SportModules.find(sport.slug)?.metrics(bundles)
                ?: listOf(MetricUiModel(AppText.get(R.string.app_repository_istoricheskie_trenirovki), bundles.size.toString()),
                    MetricUiModel(AppText.get(R.string.app_repository_statistika), AppText.get(R.string.app_repository_vid_sporta_ne_podderzhivaetsya))))
}
