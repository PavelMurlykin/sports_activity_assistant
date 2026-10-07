package com.pamurlykin.sportsactivityassistant.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupDocument(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAt: String,
    val sports: List<SportBackup>,
    val centers: List<CenterBackup>,
    val trainings: List<TrainingBackup>,
    val plannedTrainings: List<PlannedTrainingBackup> = emptyList(),
    val recurrenceRules: List<RecurrenceRuleBackup> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 3
    }
}

@Serializable
data class SportBackup(
    val slug: String,
    val title: String,
)

@Serializable
data class CenterBackup(
    val legacyId: Long? = null,
    val name: String,
    val city: String? = null,
    val sportSlugs: List<String>,
)

@Serializable
data class TrainingBackup(
    val legacyId: Long? = null,
    val date: String,
    val sportSlug: String,
    val centerLegacyId: Long? = null,
    val centerName: String,
    val centerCity: String? = null,
    val football: FootballBackup? = null,
    val climbingRoutes: List<ClimbingRouteBackup> = emptyList(),
)

@Serializable
data class FootballBackup(
    val teamGoalsScored: Int,
    val teamGoalsConceded: Int,
    val userGoalsScored: Int,
    val userAssists: Int,
    val distanceKm: String? = null,
    val playersPerTeam: Int? = null,
    val durationMinutes: Int? = null,
)

@Serializable
data class ClimbingRouteBackup(
    val workoutType: String,
    val routeDifficulty: String,
    val completed: Boolean,
    val repeatCount: Int = 1,
    val gradingSystem: String? = null,
    val gradeCode: String? = null,
    val speedCourse: String? = null,
    val legacyWorkoutType: String? = null,
)

@Serializable
data class PlannedTrainingBackup(
    val date: String,
    val sportSlug: String,
    val centerName: String,
    val centerCity: String? = null,
    val status: String = "planned",
)

@Serializable
data class RecurrenceRuleBackup(
    val startDate: String,
    val endDate: String? = null,
    val sportSlug: String,
    val centerName: String,
    val centerCity: String? = null,
    val intervalWeeks: Int = 1,
)

data class ImportResult(
    val importedTrainings: Int,
    val skippedTrainings: Int,
    val importedCenters: Int,
    val source: String,
    val historicalRouteAttempts: Long = 0,
)
