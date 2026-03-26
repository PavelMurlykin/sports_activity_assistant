package com.pamurlykin.sportsactivityassistant.data.repo

import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.entity.PlannedTrainingEntity
import com.pamurlykin.sportsactivityassistant.data.entity.RecurrenceRuleEntity
import com.pamurlykin.sportsactivityassistant.data.entity.TrainingBundle
import com.pamurlykin.sportsactivityassistant.data.model.AddPlannedTrainingInput
import com.pamurlykin.sportsactivityassistant.data.model.ComplexOptionUiModel
import com.pamurlykin.sportsactivityassistant.data.model.PlannedTrainingStatus
import com.pamurlykin.sportsactivityassistant.data.model.RecurrenceFrequency
import com.pamurlykin.sportsactivityassistant.data.model.ScheduleDayUiModel
import com.pamurlykin.sportsactivityassistant.data.model.ScheduleEventState
import com.pamurlykin.sportsactivityassistant.data.model.ScheduleEventUiModel
import com.pamurlykin.sportsactivityassistant.data.model.ScheduleMonthUiModel
import com.pamurlykin.sportsactivityassistant.data.model.SportSummaryUiModel
import com.pamurlykin.sportsactivityassistant.data.model.StatisticsOverviewUiModel
import com.pamurlykin.sportsactivityassistant.data.model.TrainingSessionUiModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class AppRepository(
    private val database: AppDatabase,
) {
    fun observeStatisticsOverview(): Flow<StatisticsOverviewUiModel> {
        return combine(
            database.referenceDao().observeSports(),
            database.trainingDao().observeAllTrainingBundles(),
        ) { sports, bundles ->
            val counts = bundles.groupingBy { it.sport.id }.eachCount()
            val sportItems = sports.map { sport ->
                SportSummaryUiModel(
                    id = sport.id,
                    slug = sport.slug,
                    title = sport.title,
                    completedTrainings = counts[sport.id] ?: 0,
                )
            }
            StatisticsOverviewUiModel(
                totalTrainings = bundles.size,
                sports = sportItems,
            )
        }
    }

    fun observeTrainingsForSport(sportId: Int): Flow<List<TrainingSessionUiModel>> {
        return database.trainingDao().observeTrainingBundlesBySport(sportId).map { bundles ->
            bundles.map(::mapTrainingBundle)
        }
    }

    suspend fun getComplexOptionsForSport(sportId: Int): List<ComplexOptionUiModel> {
        return database.referenceDao().getComplexesForSport(sportId).map { complex ->
            ComplexOptionUiModel(
                id = complex.id,
                name = complex.name,
                city = complex.city,
            )
        }
    }

    suspend fun getScheduleMonth(
        userId: Long,
        month: YearMonth,
        selectedDate: LocalDate,
        today: LocalDate,
    ): ScheduleMonthUiModel {
        val gridStart = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val gridEnd = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

        val sportsById = database.referenceDao().getSports().associateBy { it.id }
        val complexesById = database.referenceDao().getAllComplexes().associateBy { it.id }
        val completed = database.trainingDao().getTrainingBundlesBetween(userId, gridStart, gridEnd)
        val planned = database.planningDao().getPlannedTrainingsBetween(userId, gridStart, gridEnd)
        val recurring = database.planningDao().getRecurrenceRulesOverlapping(userId, gridStart, gridEnd)

        val eventsByDate = buildMap<LocalDate, MutableList<ScheduleEventUiModel>> {
            completed.forEach { bundle ->
                getOrPut(bundle.training.trainingDate, ::mutableListOf).add(
                    ScheduleEventUiModel(
                        id = "completed-${bundle.training.id}",
                        date = bundle.training.trainingDate,
                        sportId = bundle.sport.id,
                        sportSlug = bundle.sport.slug,
                        sportTitle = bundle.sport.title,
                        complexName = bundle.complex.name,
                        state = ScheduleEventState.COMPLETED,
                        isRecurring = false,
                    ),
                )
            }

            planned.forEach { item ->
                val sport = sportsById.getValue(item.sportId)
                val complex = complexesById.getValue(item.sportsComplexId)
                getOrPut(item.plannedDate, ::mutableListOf).add(
                    ScheduleEventUiModel(
                        id = "planned-${item.id}",
                        date = item.plannedDate,
                        sportId = sport.id,
                        sportSlug = sport.slug,
                        sportTitle = sport.title,
                        complexName = complex.name,
                        state = ScheduleEventState.PLANNED,
                        isRecurring = item.recurrenceRuleId != null,
                    ),
                )
            }

            recurring.forEach { rule ->
                expandRecurringDates(rule, gridStart, gridEnd).forEach { date ->
                    val sport = sportsById.getValue(rule.sportId)
                    val complex = complexesById.getValue(rule.sportsComplexId)
                    getOrPut(date, ::mutableListOf).add(
                        ScheduleEventUiModel(
                            id = "rule-${rule.id}-$date",
                            date = date,
                            sportId = sport.id,
                            sportSlug = sport.slug,
                            sportTitle = sport.title,
                            complexName = complex.name,
                            state = ScheduleEventState.PLANNED,
                            isRecurring = true,
                        ),
                    )
                }
            }
        }

        val selectedEvents = eventsByDate[selectedDate].orEmpty().sortedWith(calendarEventComparator)
        val days = generateSequence(gridStart) { current ->
            current.takeIf { it < gridEnd }?.plusDays(1)
        }
            .takeWhile { it <= gridEnd }
            .map { date ->
                ScheduleDayUiModel(
                    date = date,
                    inCurrentMonth = date.month == month.month,
                    isToday = date == today,
                    events = eventsByDate[date].orEmpty().sortedWith(calendarEventComparator),
                )
            }
            .toList()

        return ScheduleMonthUiModel(
            month = month,
            selectedDate = selectedDate,
            days = days,
            selectedDayEvents = selectedEvents,
        )
    }

    suspend fun addPlannedTraining(userId: Long, input: AddPlannedTrainingInput) {
        if (input.repeatWeekly) {
            database.planningDao().insertRecurrenceRule(
                RecurrenceRuleEntity(
                    userId = userId,
                    sportId = input.sportId,
                    sportsComplexId = input.complexId,
                    startDate = input.date,
                    endDate = input.endDate,
                    frequency = RecurrenceFrequency.WEEKLY,
                    intervalWeeks = input.intervalWeeks.coerceAtLeast(1),
                ),
            )
            return
        }

        database.planningDao().insertPlannedTraining(
            PlannedTrainingEntity(
                userId = userId,
                sportId = input.sportId,
                sportsComplexId = input.complexId,
                plannedDate = input.date,
                recurrenceRuleId = null,
                status = PlannedTrainingStatus.PLANNED,
            ),
        )
    }

    private fun mapTrainingBundle(bundle: TrainingBundle): TrainingSessionUiModel {
        val details = buildList {
            bundle.football?.let { football ->
                add("Счёт команды: ${football.teamGoalsScored}:${football.teamGoalsConceded}")
                add("Мои голы: ${football.userGoalsScored}")
                add("Мои ассисты: ${football.userAssists}")
                football.distanceKm?.let { distance ->
                    add("Дистанция: ${distance.stripTrailingZeros().toPlainString()} км")
                }
            }

            bundle.climbing?.routes?.forEach { route ->
                val workoutTitle = when (route.workoutType) {
                    com.pamurlykin.sportsactivityassistant.data.model.ClimbingWorkoutType.DIFFICULTY -> "Трудность"
                    com.pamurlykin.sportsactivityassistant.data.model.ClimbingWorkoutType.BOULDERING -> "Боулдеринг"
                }
                add("$workoutTitle ${route.routeDifficulty}: ${route.routesCompleted} маршрутов")
            }
        }

        return TrainingSessionUiModel(
            id = bundle.training.id,
            sportSlug = bundle.sport.slug,
            sportTitle = bundle.sport.title,
            complexTitle = listOfNotNull(bundle.complex.name, bundle.complex.city).joinToString(", "),
            date = bundle.training.trainingDate,
            details = details,
        )
    }

    private fun expandRecurringDates(
        rule: RecurrenceRuleEntity,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
    ): List<LocalDate> {
        if (rule.frequency != RecurrenceFrequency.WEEKLY) return emptyList()

        val hardEnd = minOf(rangeEnd, rule.endDate ?: rangeEnd)
        if (hardEnd < rangeStart) return emptyList()

        var cursor = rule.startDate
        if (cursor < rangeStart) {
            val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(cursor, rangeStart)
            val stepDays = 7L * rule.intervalWeeks.coerceAtLeast(1)
            val jumps = daysBetween / stepDays
            cursor = cursor.plusDays(jumps * stepDays)
            while (cursor < rangeStart) {
                cursor = cursor.plusDays(stepDays)
            }
        }

        val dates = mutableListOf<LocalDate>()
        val stepDays = 7L * rule.intervalWeeks.coerceAtLeast(1)
        while (cursor <= hardEnd) {
            if (cursor >= rangeStart) dates += cursor
            cursor = cursor.plusDays(stepDays)
        }
        return dates
    }

    private companion object {
        val calendarEventComparator = compareBy<ScheduleEventUiModel>({ it.state }, { it.sportTitle }, { it.complexName })
    }
}
