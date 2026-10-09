package com.pamurlykin.sportsactivityassistant.data.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

@Serializable
data class PlanSnapshot(
    val key: String,
    val sportId: Int,
    val complexId: Long,
    val date: String,
    val repeatWeekly: Boolean,
    val intervalWeeks: Int,
    val endDate: String?,
    val revision: String,
    val canceled: Boolean,
) {
    fun input() = AddPlannedTrainingInput(sportId, complexId, LocalDate.parse(date), repeatWeekly,
        intervalWeeks, endDate?.let(LocalDate::parse))
}

enum class PlanScope { EVENT, SERIES }

object ScheduleDates {
    fun occurs(start: LocalDate, end: LocalDate?, interval: Int, date: LocalDate): Boolean =
        interval > 0 && date >= start && (end == null || date <= end) &&
            ChronoUnit.DAYS.between(start, date) % (7L * interval) == 0L

    fun expand(start: LocalDate, end: LocalDate?, interval: Int, from: LocalDate, to: LocalDate): List<LocalDate> {
        require(interval > 0)
        val last = minOf(to, end ?: to)
        val first = maxOf(start, from)
        if (first > last) return emptyList()
        val step = 7L * interval
        val gap = ChronoUnit.DAYS.between(start, first)
        val offset = ((gap + step - 1) / step) * step
        val limit = ChronoUnit.DAYS.between(start, last)
        return buildList {
            var cursor = offset
            // Check the offset before adding: very large intervals must not overflow LocalDate.
            while (cursor <= limit) { add(start.plusDays(cursor)); cursor += step }
        }
    }

    fun grid(month: YearMonth): List<LocalDate> {
        require(month.year in 1..9999)
        val first = month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value - 1).toLong())
        val last = month.atEndOfMonth().plusDays((7 - month.atEndOfMonth().dayOfWeek.value).toLong())
        return (0..ChronoUnit.DAYS.between(first, last)).map(first::plusDays)
    }
}
