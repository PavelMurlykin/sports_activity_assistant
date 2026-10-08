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
    val profiles: List<ProfileBackup> = emptyList(),
    val primaryProfilePublicId: String? = null,
    val favorites: List<FavoriteBackup> = emptyList(),
    val aliases: List<AliasBackup> = emptyList(),
) {
    fun objectCount(): Long = sports.size.toLong() + centers.size + profiles.size + trainings.size +
        trainings.sumOf { it.climbingRoutes.size.toLong() } + plannedTrainings.size + recurrenceRules.size + favorites.size + aliases.size

    companion object {
        const val CURRENT_SCHEMA_VERSION = 4
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
    val publicId: String? = null,
    val createdAt: String? = null,
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
    val publicId: String? = null,
    val centerPublicId: String? = null,
    val profilePublicId: String? = null,
    val createdAt: String? = null,
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
    val publicId: String? = null,
)

@Serializable
data class PlannedTrainingBackup(
    val date: String,
    val sportSlug: String,
    val centerName: String,
    val centerCity: String? = null,
    val status: String = "planned",
    val publicId: String? = null,
    val centerPublicId: String? = null,
    val profilePublicId: String? = null,
    val createdAt: String? = null,
    val recurrenceRulePublicId: String? = null,
)

@Serializable
data class RecurrenceRuleBackup(
    val startDate: String,
    val endDate: String? = null,
    val sportSlug: String,
    val centerName: String,
    val centerCity: String? = null,
    val intervalWeeks: Int = 1,
    val publicId: String? = null,
    val centerPublicId: String? = null,
    val profilePublicId: String? = null,
    val createdAt: String? = null,
    val frequency: String = "weekly",
)

@Serializable
data class ProfileBackup(val publicId: String, val displayName: String? = null, val createdAt: String? = null)

@Serializable
data class FavoriteBackup(val profilePublicId: String, val centerPublicId: String, val createdAt: String)

@Serializable
data class AliasBackup(val kind: String, val sourceKey: String, val targetPublicId: String)

data class ImportResult(
    val importedTrainings: Int,
    val skippedTrainings: Int,
    val importedCenters: Int,
    val source: String,
    val historicalRouteAttempts: Long = 0,
    val importedPlans: Int = 0,
    val importedRules: Int = 0,
    val importedFavorites: Int = 0,
)
