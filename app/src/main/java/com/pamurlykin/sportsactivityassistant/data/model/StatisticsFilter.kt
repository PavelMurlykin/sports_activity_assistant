package com.pamurlykin.sportsactivityassistant.data.model

import java.time.LocalDate

/** One inclusive selection shared by overview, sport aggregates and history pages. */
data class StatisticsFilter(
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val centerId: Long? = null,
) {
    init {
        require((startDate == null) == (endDate == null)) { "Укажите обе границы периода" }
        require(startDate == null || startDate.year in 1..9999) { "Год должен быть от 1 до 9999" }
        require(endDate == null || endDate.year in 1..9999) { "Год должен быть от 1 до 9999" }
        require(startDate == null || !startDate.isAfter(endDate)) { "Начало периода позже окончания" }
        require(centerId == null || centerId > 0) { "Некорректный центр" }
    }

    val firstDate: LocalDate get() = startDate ?: LocalDate.of(1, 1, 1)
    val lastDate: LocalDate get() = endDate ?: LocalDate.of(9999, 12, 31)
}

data class StatisticsSelection(val userId: Long, val sportId: Int, val filter: StatisticsFilter)

data class TrainingPageUiModel(
    val items: List<TrainingSessionUiModel>,
    val page: Int,
    val total: Int,
    val filter: StatisticsFilter = StatisticsFilter(),
) {
    val pageCount: Int get() = ((total.toLong() + SIZE - 1) / SIZE).toInt().coerceAtLeast(1)
    companion object { const val SIZE = 20 }
}
