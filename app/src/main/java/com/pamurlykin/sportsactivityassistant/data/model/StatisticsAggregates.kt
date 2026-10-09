package com.pamurlykin.sportsactivityassistant.data.model

import java.math.BigDecimal

data class SportCount(val sportId: Int, val count: Int)
data class MonthSportCount(val month: String, val sportId: Int, val count: Int)
data class DistanceSample(val id: Long, val distanceKm: BigDecimal)

data class FootballTotals(
    val games: Long = 0,
    val wins: Long = 0,
    val draws: Long = 0,
    val losses: Long = 0,
    val teamScored: Long = 0,
    val teamConceded: Long = 0,
    val personalGoals: Long = 0,
    val assists: Long = 0,
    val distanceCount: Long = 0,
    val minutes: Long = 0,
    val durationCount: Long = 0,
    val players: Long = 0,
    val playersCount: Long = 0,
)

/** A bounded aggregate group, not a loaded route or training. */
data class ClimbingBucket(
    val workoutType: ClimbingWorkoutType,
    val gradingSystem: String,
    val gradeCode: String?,
    val speedCourse: String?,
    val gradeMatches: Boolean,
    val rowCount: Long,
    val attempts: Long,
    val completed: Long,
)

data class SportAggregate(val metrics: List<MetricUiModel>, val highlights: List<String>)

/** Match the same Unicode trim predicate as the offline grade catalog. */
val gradeWhitespace: String = buildString {
    for (character in Char.MIN_VALUE..Char.MAX_VALUE) if (character.isWhitespace()) append(character)
}
