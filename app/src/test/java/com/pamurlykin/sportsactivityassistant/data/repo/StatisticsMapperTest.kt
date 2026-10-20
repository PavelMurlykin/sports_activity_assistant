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
import com.pamurlykin.sportsactivityassistant.data.model.FootballMetric
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsMapperTest : com.pamurlykin.sportsactivityassistant.ResourceTextTest() {
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

    @Test fun footballTableUsesPerMatchExtremaAndExcludesMissingDistanceButIncludesZero() {
        val sport = SportEntity(1, "football", "Футбол")
        val stats = requireNotNull(StatisticsMapper.sport(sport, listOf(
            footballBundle(1, sport, "2026-01-01", 5, 3, 2, 1, "10.125"),
            footballBundle(2, sport, "2026-02-01", 2, 2, 1, 2, null),
            footballBundle(3, sport, "2026-02-02", 0, 4, 0, 0, "0"),
        )).football)
        assertEquals(listOf(3L, 1L, 1L, 1L), listOf(stats.games, stats.wins, stats.draws, stats.losses))
        assertEquals(FootballMetric.entries, stats.metrics.map { it.metric })
        val scored = stats.metrics.first()
        assertEquals(listOf("7", "5", "0", "2,33"), listOf(scored.sum, scored.maximum, scored.minimum, scored.average))
        val conceded = stats.metrics[1]
        assertEquals(listOf("9", "4", "2", "3,00"), listOf(conceded.sum, conceded.maximum, conceded.minimum, conceded.average))
        val distance = stats.metrics.last()
        assertEquals(2L, stats.distanceCount)
        assertEquals(listOf("10,13", "10,13", "0,00", "5,06"), listOf(distance.sum, distance.maximum, distance.minimum, distance.average))
    }

    @Test fun emptyFootballTableHasNoInventedExtremaOrAverages() {
        val stats = requireNotNull(StatisticsMapper.sport(SportEntity(1, "football", "Футбол"), emptyList()).football)
        assertEquals(0L, stats.games)
        assertTrue(stats.metrics.all { it.maximum == "—" && it.minimum == "—" && it.average == "—" })
        assertEquals(listOf("0", "0", "0", "0", "—"), stats.metrics.map { it.sum })
    }

    @Test fun distanceExtremaCompareNumericDecimalsAndMissingDistanceIsNotZero() {
        val sport = SportEntity(1, "football", "Футбол")
        val stats = requireNotNull(StatisticsMapper.sport(sport, listOf(
            footballBundle(1, sport, "2026-01-01", 0, 0, 0, 0, "9.1"),
            footballBundle(2, sport, "2026-01-02", 0, 0, 0, 0, "10"),
            footballBundle(3, sport, "2026-01-03", 0, 0, 0, 0, null),
        )).football)
        val distance = stats.metrics.last()
        assertEquals(listOf("19,10", "10,00", "9,10", "9,55"), listOf(distance.sum, distance.maximum, distance.minimum, distance.average))
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

        assertEquals("4", values["Попытки (с повторами)"])
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
