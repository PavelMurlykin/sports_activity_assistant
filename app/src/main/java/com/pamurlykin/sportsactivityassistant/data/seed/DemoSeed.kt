package com.pamurlykin.sportsactivityassistant.data.seed

import com.pamurlykin.sportsactivityassistant.data.AppDatabase
import com.pamurlykin.sportsactivityassistant.data.entity.ClimbingRouteEntity
import com.pamurlykin.sportsactivityassistant.data.entity.ClimbingTrainingEntity
import com.pamurlykin.sportsactivityassistant.data.entity.FootballTrainingEntity
import com.pamurlykin.sportsactivityassistant.data.entity.PlannedTrainingEntity
import com.pamurlykin.sportsactivityassistant.data.entity.RecurrenceRuleEntity
import com.pamurlykin.sportsactivityassistant.data.entity.SportEntity
import com.pamurlykin.sportsactivityassistant.data.entity.SportsComplexEntity
import com.pamurlykin.sportsactivityassistant.data.entity.SportsComplexSportEntity
import com.pamurlykin.sportsactivityassistant.data.entity.TrainingEntity
import com.pamurlykin.sportsactivityassistant.data.entity.UserEntity
import com.pamurlykin.sportsactivityassistant.data.entity.UserFavoriteComplexEntity
import com.pamurlykin.sportsactivityassistant.data.model.ClimbingWorkoutType
import com.pamurlykin.sportsactivityassistant.data.model.PlannedTrainingStatus
import com.pamurlykin.sportsactivityassistant.data.model.RecurrenceFrequency
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

object DemoSeed {
    suspend fun seed(
        database: AppDatabase,
        includeSampleTrainings: Boolean = true,
    ) {
        if (database.referenceDao().getSports().isNotEmpty()) return

        val createdAt = Instant.parse("2026-03-01T08:00:00Z")

        database.referenceDao().insertUsers(
            listOf(
                UserEntity(
                    id = 1,
                    legacyExternalId = 1,
                    username = null,
                    firstName = null,
                    lastName = null,
                    createdAt = createdAt,
                ),
            ),
        )

        database.referenceDao().insertSports(
            listOf(
                SportEntity(id = 1, slug = "football", title = "Футбол"),
                SportEntity(id = 2, slug = "climbing", title = "Скалолазание"),
            ),
        )

        database.referenceDao().insertComplexes(
            listOf(
                SportsComplexEntity(
                    id = 1,
                    name = "Энергия Высоты",
                    city = "Санкт-Петербург",
                    createdAt = createdAt,
                ),
                SportsComplexEntity(
                    id = 2,
                    name = "Фабрика Футбола",
                    city = "Санкт-Петербург",
                    createdAt = createdAt,
                ),
                SportsComplexEntity(
                    id = 3,
                    name = "Арена на горе",
                    city = "Анталья",
                    createdAt = createdAt,
                ),
            ),
        )

        database.referenceDao().insertComplexSports(
            listOf(
                SportsComplexSportEntity(id = 1, sportsComplexId = 1, sportId = 2),
                SportsComplexSportEntity(id = 2, sportsComplexId = 2, sportId = 1),
                SportsComplexSportEntity(id = 3, sportsComplexId = 3, sportId = 1),
            ),
        )

        database.referenceDao().insertFavoriteComplexes(
            listOf(
                UserFavoriteComplexEntity(id = 1, userId = 1, sportsComplexId = 1, createdAt = createdAt),
                UserFavoriteComplexEntity(id = 2, userId = 1, sportsComplexId = 2, createdAt = createdAt),
            ),
        )

        if (!includeSampleTrainings) return

        database.trainingDao().insertTrainings(
            listOf(
                TrainingEntity(
                    id = 1,
                    userId = 1,
                    sportId = 1,
                    sportsComplexId = 2,
                    trainingDate = LocalDate.parse("2026-03-05"),
                    createdAt = createdAt,
                ),
                TrainingEntity(
                    id = 2,
                    userId = 1,
                    sportId = 2,
                    sportsComplexId = 1,
                    trainingDate = LocalDate.parse("2026-03-09"),
                    createdAt = createdAt,
                ),
                TrainingEntity(
                    id = 3,
                    userId = 1,
                    sportId = 1,
                    sportsComplexId = 3,
                    trainingDate = LocalDate.parse("2026-03-18"),
                    createdAt = createdAt,
                ),
            ),
        )

        database.trainingDao().insertFootballTrainings(
            listOf(
                FootballTrainingEntity(
                    trainingId = 1,
                    teamGoalsScored = 5,
                    teamGoalsConceded = 3,
                    userGoalsScored = 2,
                    userAssists = 1,
                    distanceKm = BigDecimal("7.30"),
                    playersPerTeam = 6,
                    durationMinutes = 60,
                ),
                FootballTrainingEntity(
                    trainingId = 3,
                    teamGoalsScored = 4,
                    teamGoalsConceded = 4,
                    userGoalsScored = 1,
                    userAssists = 2,
                    distanceKm = BigDecimal("6.10"),
                    playersPerTeam = null,
                    durationMinutes = 70,
                ),
            ),
        )

        database.trainingDao().insertClimbingTrainings(
            listOf(
                ClimbingTrainingEntity(trainingId = 2),
            ),
        )

        database.trainingDao().insertClimbingRoutes(
            listOf(
                ClimbingRouteEntity(
                    id = 1,
                    climbingTrainingId = 2,
                    workoutType = ClimbingWorkoutType.DIFFICULTY,
                    routeDifficulty = "6A+",
                    isCompleted = true,
                    repeatCount = 4,
                ),
                ClimbingRouteEntity(
                    id = 2,
                    climbingTrainingId = 2,
                    workoutType = ClimbingWorkoutType.BOULDERING,
                    routeDifficulty = "6B",
                    isCompleted = true,
                    repeatCount = 3,
                ),
            ),
        )

        database.planningDao().insertPlannedTrainings(
            listOf(
                PlannedTrainingEntity(
                    id = 1,
                    userId = 1,
                    sportId = 1,
                    sportsComplexId = 2,
                    plannedDate = LocalDate.parse("2026-03-28"),
                    recurrenceRuleId = null,
                    status = PlannedTrainingStatus.PLANNED,
                    createdAt = createdAt,
                ),
            ),
        )

        database.planningDao().insertRecurrenceRules(
            listOf(
                RecurrenceRuleEntity(
                    id = 1,
                    userId = 1,
                    sportId = 2,
                    sportsComplexId = 1,
                    startDate = LocalDate.parse("2026-03-27"),
                    endDate = LocalDate.parse("2026-05-29"),
                    frequency = RecurrenceFrequency.WEEKLY,
                    intervalWeeks = 1,
                    createdAt = createdAt,
                ),
            ),
        )
    }
}
