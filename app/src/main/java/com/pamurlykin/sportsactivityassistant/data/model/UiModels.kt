package com.pamurlykin.sportsactivityassistant.data.model

enum class ClimbingWorkoutType(val storageValue: String) {
    DIFFICULTY("difficulty"),
    BOULDERING("bouldering");

    companion object {
        fun fromStorage(value: String): ClimbingWorkoutType {
            return entries.firstOrNull { it.storageValue == value } ?: DIFFICULTY
        }
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
    CANCELED("canceled");

    companion object {
        fun fromStorage(value: String): PlannedTrainingStatus {
            return entries.firstOrNull { it.storageValue == value } ?: PLANNED
        }
    }
}

enum class ScheduleEventState {
    COMPLETED,
    PLANNED,
}

data class SportSummaryUiModel(
    val id: Int,
    val slug: String,
    val title: String,
    val completedTrainings: Int,
)

data class StatisticsOverviewUiModel(
    val totalTrainings: Int,
    val sports: List<SportSummaryUiModel>,
)

data class ComplexOptionUiModel(
    val id: Long,
    val name: String,
    val city: String?,
) {
    val fullTitle: String = listOfNotNull(name, city).joinToString(", ")
}

data class ScheduleEventUiModel(
    val id: String,
    val date: java.time.LocalDate,
    val sportId: Int,
    val sportSlug: String,
    val sportTitle: String,
    val complexName: String,
    val state: ScheduleEventState,
    val isRecurring: Boolean,
)

data class ScheduleDayUiModel(
    val date: java.time.LocalDate,
    val inCurrentMonth: Boolean,
    val isToday: Boolean,
    val events: List<ScheduleEventUiModel>,
)

data class ScheduleMonthUiModel(
    val month: java.time.YearMonth,
    val selectedDate: java.time.LocalDate,
    val days: List<ScheduleDayUiModel>,
    val selectedDayEvents: List<ScheduleEventUiModel>,
)

data class TrainingSessionUiModel(
    val id: Long,
    val sportSlug: String,
    val sportTitle: String,
    val complexTitle: String,
    val date: java.time.LocalDate,
    val details: List<String>,
)

data class AddPlannedTrainingInput(
    val sportId: Int,
    val complexId: Long,
    val date: java.time.LocalDate,
    val repeatWeekly: Boolean,
    val intervalWeeks: Int,
    val endDate: java.time.LocalDate?,
)
