package com.pamurlykin.sportsactivityassistant.data.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

enum class ClimbingWorkoutType(val storageValue: String, val title: String) {
    DIFFICULTY("difficulty", "Трудность"),
    SPEED("speed", "Скорость"),
    BOULDERING("bouldering", "Болдер"),
    UNKNOWN("unknown", "Историческая дисциплина");

    companion object {
        fun fromStorage(value: String): ClimbingWorkoutType {
            return entries.firstOrNull { it.storageValue == value } ?: UNKNOWN
        }
        val supported = listOf(DIFFICULTY, SPEED, BOULDERING)
    }
}

enum class RecurrenceFrequency(val storageValue: String) {
    NONE("none"),
    WEEKLY("weekly");

    companion object {
        fun fromStorage(value: String): RecurrenceFrequency {
            return entries.firstOrNull { it.storageValue == value } ?: NONE
        }
    }
}

enum class PlannedTrainingStatus(val storageValue: String) {
    PLANNED("planned"),
    CANCELED("canceled"),
    COMPLETED("completed");

    companion object {
        fun fromStorage(value: String): PlannedTrainingStatus {
            return entries.firstOrNull { it.storageValue == value } ?: PLANNED
        }
    }
}

enum class ScheduleEventState {
    COMPLETED,
    PLANNED,
    CANCELED,
}

data class SportSummaryUiModel(
    val id: Int,
    val slug: String,
    val title: String,
    val completedTrainings: Int,
    val highlights: List<String> = emptyList(),
)

data class MonthStatisticsUiModel(
    val month: YearMonth,
    val totalTrainings: Int,
    val countsBySport: List<String>,
)

data class StatisticsOverviewUiModel(
    val totalTrainings: Int,
    val sports: List<SportSummaryUiModel>,
    val months: List<MonthStatisticsUiModel> = emptyList(),
    val filter: StatisticsFilter = StatisticsFilter(),
)

data class MetricUiModel(
    val label: String,
    val value: String,
)

data class SportStatisticsUiModel(
    val sportId: Int,
    val sportSlug: String,
    val sportTitle: String,
    val metrics: List<MetricUiModel>,
    val filter: StatisticsFilter = StatisticsFilter(),
)

data class ComplexOptionUiModel(
    val id: Long,
    val name: String,
    val city: String?,
) {
    val fullTitle: String = listOfNotNull(name, city?.takeIf(String::isNotBlank)).joinToString(", ")
}

data class SportsCenterUiModel(
    val id: Long,
    val name: String,
    val city: String?,
    val sports: List<SportSummaryUiModel>,
    val isArchived: Boolean = false,
) {
    val fullTitle: String = listOfNotNull(name, city?.takeIf(String::isNotBlank)).joinToString(", ")
}

data class ScheduleEventUiModel(
    val id: String,
    val date: LocalDate,
    val sportId: Int,
    val sportSlug: String,
    val sportTitle: String,
    val complexName: String,
    val state: ScheduleEventState,
    val isRecurring: Boolean,
    val details: List<String> = emptyList(),
    val planKey: String? = null,
    val linkedPlan: Boolean = false,
)

data class ScheduleDayUiModel(
    val date: LocalDate,
    val inCurrentMonth: Boolean,
    val isToday: Boolean,
    val events: List<ScheduleEventUiModel>,
)

data class ScheduleMonthUiModel(
    val month: YearMonth,
    val selectedDate: LocalDate,
    val days: List<ScheduleDayUiModel>,
    val selectedDayEvents: List<ScheduleEventUiModel>,
)

data class TrainingSessionUiModel(
    val id: Long,
    val sportSlug: String,
    val sportTitle: String,
    val complexTitle: String,
    val date: LocalDate,
    val details: List<String>,
)

data class AddPlannedTrainingInput(
    val sportId: Int,
    val complexId: Long,
    val date: LocalDate,
    val repeatWeekly: Boolean,
    val intervalWeeks: Int,
    val endDate: LocalDate?,
)

data class FootballTrainingInput(
    val teamGoalsScored: Int,
    val teamGoalsConceded: Int,
    val userGoalsScored: Int,
    val userAssists: Int,
    val distanceKm: BigDecimal?,
    val playersPerTeam: Int?,
    val durationMinutes: Int?,
)

data class ClimbingRouteInput(
    val workoutType: ClimbingWorkoutType,
    val routeDifficulty: String,
    val isCompleted: Boolean,
    val repeatCount: Int = 1,
    val gradingSystem: String = ClimbingDifficultyCatalog.systemFor(workoutType),
    val gradeCode: String? = ClimbingDifficultyCatalog.find(gradingSystem, routeDifficulty)?.code,
    val speedCourse: String? = null,
    val legacyWorkoutType: String? = null,
    val publicId: String? = null,
)

data class AddCompletedTrainingInput(
    val sportId: Int,
    val complexId: Long,
    val date: LocalDate,
    val football: FootballTrainingInput? = null,
    val climbingRoutes: List<ClimbingRouteInput> = emptyList(),
)

data class SaveSportsCenterInput(
    val id: Long? = null,
    val name: String,
    val city: String?,
    val sportIds: Set<Int>,
)

data class DataOperationUiState(
    val inProgress: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)
