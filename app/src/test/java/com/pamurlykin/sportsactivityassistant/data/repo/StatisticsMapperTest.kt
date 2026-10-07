package com.pamurlykin.sportsactivityassistant.data.repo

import com.pamurlykin.sportsactivityassistant.data.entity.ClimbingRouteEntity
import com.pamurlykin.sportsactivityassistant.data.entity.ClimbingTrainingEntity
import com.pamurlykin.sportsactivityassistant.data.entity.ClimbingTrainingWithRoutes
import com.pamurlykin.sportsactivityassistant.data.entity.FootballTrainingEntity
import com.pamurlykin.sportsactivityassistant.data.entity.SportEntity
import com.pamurlykin.sportsactivityassistant.data.entity.SportsComplexEntity
import com.pamurlykin.sportsactivityassistant.data.entity.TrainingBundle
import com.pamurlykin.sportsactivityassistant.data.entity.TrainingEntity
import com.pamurlykin.sportsactivityassistant.data.model.ClimbingWorkoutType
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsMapperTest {
    private val center = SportsComplexEntity(id = 1, name = "Центр", city = "Москва")

    @Test
    fun aggregatesFootballResultsAndPersonalMetrics() {
        val sport = SportEntity(1, "football", "Футбол")
        val bundles = listOf(
            footballBundle(1, sport, "2026-01-01", 5, 3, 2, 1, "7.3"),
            footballBundle(2, sport, "2026-02-01", 2, 2, 1, 2, null),
        )

        val stats = StatisticsMapper.sport(sport, bundles)
        val values = stats.metrics.associate { it.label to it.value }

        assertEquals("1 / 1 / 0", values["Победы / ничьи / поражения"])
        assertEquals("3", values["Личные голы"])
        assertEquals("3", values["Голевые передачи"])
        assertEquals("7.3 км", values["Учтённая дистанция"])
    }

    @Test
    fun climbingUsesRepeatCountAndOnlySuccessfulRoutesForHardestGrade() {
        val sport = SportEntity(2, "climbing", "Скалолазание")
        val training = TrainingEntity(1, 1, 2, 1, LocalDate.parse("2026-03-01"))
        val routes = listOf(
            ClimbingRouteEntity(1, 1, ClimbingWorkoutType.DIFFICULTY, "6B", true, 3, gradingSystem = "french", gradeCode = "6b"),
            ClimbingRouteEntity(2, 1, ClimbingWorkoutType.BOULDERING, "7A", false, 1),
        )
        val bundle = TrainingBundle(
            training, sport, center, null,
            ClimbingTrainingWithRoutes(ClimbingTrainingEntity(1), routes),
        )

        val values = StatisticsMapper.sport(sport, listOf(bundle)).metrics.associate { it.label to it.value }

        assertEquals("4", values["Трассы"])
        assertEquals("3 (75%)", values["Успешно пройдено"])
        assertEquals("6b", values["Максимум · Трудность (Французская)"])
    }

    @Test
    fun overviewGroupsTrainingsByMonth() {
        val sport = SportEntity(1, "football", "Футбол")
        val overview = StatisticsMapper.overview(
            listOf(sport),
            listOf(
                footballBundle(1, sport, "2026-01-01", 1, 0, 1, 0, null),
                footballBundle(2, sport, "2026-01-20", 1, 0, 0, 1, null),
            ),
        )
        assertEquals(2, overview.totalTrainings)
        assertEquals(2, overview.months.single().totalTrainings)
        assertTrue(overview.sports.single().highlights.contains("1 голов"))
    }

    private fun footballBundle(
        id: Long,
        sport: SportEntity,
        date: String,
        scored: Int,
        conceded: Int,
        goals: Int,
        assists: Int,
        distance: String?,
    ) = TrainingBundle(
        TrainingEntity(id, 1, sport.id, center.id, LocalDate.parse(date)),
        sport,
        center,
        FootballTrainingEntity(id, scored, conceded, goals, assists, distance?.let(::BigDecimal), 6, 60),
        null,
    )
}
