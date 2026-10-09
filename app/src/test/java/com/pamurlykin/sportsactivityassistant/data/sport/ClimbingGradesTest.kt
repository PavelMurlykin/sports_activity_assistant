package com.pamurlykin.sportsactivityassistant.data.sport

import com.pamurlykin.sportsactivityassistant.data.backup.*
import com.pamurlykin.sportsactivityassistant.data.entity.*
import com.pamurlykin.sportsactivityassistant.data.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ClimbingGradesTest : com.pamurlykin.sportsactivityassistant.ResourceTextTest() {
    private val date = LocalDate.parse("2026-10-07")
    private fun input(route: ClimbingRouteInput) = AddCompletedTrainingInput(2, 1, date, climbingRoutes = listOf(route))

    @Test fun validatesApplicableScaleAndCanonicalCode() {
        val lead = ClimbingRouteInput(ClimbingWorkoutType.DIFFICULTY, "6A+", true)
        val boulder = ClimbingRouteInput(ClimbingWorkoutType.BOULDERING, "6A+", false)
        ClimbingModule.validate(input(lead))
        ClimbingModule.validate(input(boulder))
        listOf(lead.copy(gradingSystem = "fontainebleau"), boulder.copy(gradingSystem = "french"),
            lead.copy(gradeCode = "6b"), lead.copy(gradeCode = "6A+"), lead.copy(speedCourse = "standard_15m"),
            boulder.copy(routeDifficulty = "9C", gradeCode = "9c"), lead.copy(gradingSystem = "unknown")).forEach {
            assertTrue(runCatching { ClimbingModule.validate(input(it)) }.isFailure)
        }
    }

    @Test fun speedRequiresCourseButHasNoGrade() {
        val speed = ClimbingRouteInput(ClimbingWorkoutType.SPEED, "", true)
        assertTrue(runCatching { ClimbingModule.validate(input(speed)) }.isFailure)
        SpeedCourse.entries.forEach { ClimbingModule.validate(input(speed.copy(speedCourse = it.code))) }
        assertTrue(runCatching { ClimbingModule.validate(input(speed.copy(speedCourse = "unknown"))) }.isFailure)
        assertTrue(runCatching { ClimbingModule.validate(input(speed.copy(routeDifficulty = "6A", gradeCode = "6a", speedCourse = "standard_15m"))) }.isFailure)
    }

    @Test fun historicalInputIsNotAllowedInNewManualWorkouts() {
        val historical = ClimbingRouteInput(ClimbingWorkoutType.BOULDERING, "very old label", true, 4, "legacy", null)
        assertTrue(runCatching { ClimbingModule.validate(input(historical)) }.isFailure)
        ClimbingModule.validate(input(historical), allowHistorical = true)
        assertTrue(runCatching { ClimbingModule.validate(input(historical.copy(gradeCode = "6a")), true) }.isFailure)
    }

    @Test fun confirmedMaximaAreSeparateAndHistoricalSpeedIsExcluded() {
        val routes = listOf(
            ClimbingRouteEntity(1, 1, ClimbingWorkoutType.DIFFICULTY, "6b+", true, 3, gradingSystem = "french", gradeCode = "6b+"),
            ClimbingRouteEntity(2, 1, ClimbingWorkoutType.DIFFICULTY, "9c", false, gradingSystem = "french", gradeCode = "9c"),
            ClimbingRouteEntity(3, 1, ClimbingWorkoutType.BOULDERING, "7A", true, gradingSystem = "fontainebleau", gradeCode = "7a"),
            ClimbingRouteEntity(4, 1, ClimbingWorkoutType.SPEED, "9C", true, 2),
            ClimbingRouteEntity(5, 1, ClimbingWorkoutType.BOULDERING, "8C", true, 4),
            ClimbingRouteEntity(6, 1, ClimbingWorkoutType.SPEED, "", true, gradingSystem = "none", speedCourse = "standard_15m"),
        )
        val bundle = TrainingBundle(TrainingEntity(1, 1, 2, 1, date), SportEntity(2, "climbing", "Скалолазание"),
            SportsComplexEntity(1, "Центр", null), null, ClimbingTrainingWithRoutes(ClimbingTrainingEntity(1), routes))
        val metrics = ClimbingModule.metrics(listOf(bundle)).associate { it.label to it.value }
        assertEquals("12", metrics["Попытки (с повторами)"])
        assertEquals("11 (91%)", metrics["Успешно пройдено"])
        assertEquals("6b+", metrics["Максимум · Трудность (Французская)"])
        assertEquals("7A", metrics["Максимум · Болдер (Fontainebleau)"])
        assertEquals("6", metrics["Исторические категории без сравнения"])
        assertTrue(ClimbingModule.details(bundle).any { it.contains("8C (историческая") })
        assertTrue(ClimbingModule.details(bundle).any { it.contains("Эталонная 15 м") })
    }

    @Test fun routeCountersDoNotOverflowInt() {
        val routes = (1L..2L).map { ClimbingRouteEntity(it, 1, ClimbingWorkoutType.SPEED, "old", true, Int.MAX_VALUE) }
        val bundle = TrainingBundle(TrainingEntity(1, 1, 2, 1, date), SportEntity(2, "climbing", "Скалолазание"),
            SportsComplexEntity(1, "Центр", null), null, ClimbingTrainingWithRoutes(ClimbingTrainingEntity(1), routes))
        val metrics = ClimbingModule.metrics(listOf(bundle)).associate { it.label to it.value }
        assertEquals("4294967294", metrics["Попытки (с повторами)"])
        assertEquals("4294967294 (100%)", metrics["Успешно пройдено"])
    }
}
