package com.pamurlykin.sportsactivityassistant.data.model

import java.math.BigDecimal
import java.math.RoundingMode

data class ClimbingAttemptCounts(val attempts: Long = 0, val completed: Long = 0) {
    val percentage: String get() = if (attempts == 0L) "—" else
        "${BigDecimal.valueOf(completed).multiply(BigDecimal(100)).divide(BigDecimal.valueOf(attempts), 0, RoundingMode.HALF_UP)}%"
}

data class ClimbingGradeStatistics(val label: String, val counts: ClimbingAttemptCounts)

data class ClimbingDisciplineStatistics(
    val type: ClimbingWorkoutType,
    val counts: ClimbingAttemptCounts,
    val grades: List<ClimbingGradeStatistics>,
    val ungraded: ClimbingAttemptCounts = ClimbingAttemptCounts(),
)

data class ClimbingSpeedStatistics(
    val course: SpeedCourse?,
    val counts: ClimbingAttemptCounts,
    val timedAttempts: Long,
    val bestTimeMillis: Long?,
    val averageTimeMillis: Double?,
)

data class ClimbingStatisticsUiModel(
    val speed: List<ClimbingSpeedStatistics>,
    val difficulty: ClimbingDisciplineStatistics,
    val bouldering: ClimbingDisciplineStatistics,
    val unknown: ClimbingAttemptCounts = ClimbingAttemptCounts(),
)

/** Blank means unrecorded; fractions finer than a millisecond are rejected. */
fun parseSpeedTimeMillis(seconds: String): Long? {
    if (seconds.isBlank()) return null
    val value = seconds.trim().replace(',', '.').toBigDecimalOrNull()
    require(value != null && value > BigDecimal.ZERO) { "speed-time" }
    return value.movePointRight(3).longValueExact().also { require(it > 0) { "speed-time" } }
}

fun formatSpeedTime(millis: Number?): String {
    if (millis == null) return "—"
    val seconds = millis.toString().toBigDecimal().movePointLeft(3).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros()
    return seconds.setScale(maxOf(2, seconds.scale())).toPlainString().replace('.', ',')
}
