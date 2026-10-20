package com.pamurlykin.sportsactivityassistant.data.model

enum class FootballMetric { TEAM_SCORED, TEAM_CONCEDED, PERSONAL_GOALS, ASSISTS, DISTANCE }

data class FootballMetricSummary(
    val metric: FootballMetric,
    val sum: String,
    val maximum: String,
    val minimum: String,
    val average: String,
)

data class FootballStatisticsUiModel(
    val games: Long,
    val wins: Long,
    val draws: Long,
    val losses: Long,
    val metrics: List<FootballMetricSummary>,
    val distanceCount: Long,
)
