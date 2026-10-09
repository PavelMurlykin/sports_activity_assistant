package com.pamurlykin.sportsactivityassistant.data.repo

import com.pamurlykin.sportsactivityassistant.data.entity.*
import com.pamurlykin.sportsactivityassistant.data.model.*
import com.pamurlykin.sportsactivityassistant.data.sport.FootballModule
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class StatisticsSelectionTest {
    private val football = SportEntity(1, "football", "Футбол")
    private fun date(s: String) = LocalDate.parse(s)

    @Test fun inclusiveRangeRejectsPartialReversedOrUnsupportedBounds() {
        assertEquals(date("0001-01-01"), StatisticsFilter().firstDate)
        assertEquals(date("9999-12-31"), StatisticsFilter().lastDate)
        val day = date("2020-02-29")
        assertEquals(day, StatisticsFilter(day, day).lastDate)
        listOf(
            { StatisticsFilter(day, null) }, { StatisticsFilter(null, day) },
            { StatisticsFilter(day, day.minusDays(1)) }, { StatisticsFilter(centerId = 0) },
            { StatisticsFilter(LocalDate.of(0,1,1), day) },
            { StatisticsFilter(day, LocalDate.of(10000,1,1)) },
        ).forEach { assertTrue(runCatching(it).isFailure) }
    }

    @Test fun selectedIntervalContainsZeroMonthsAndRespectsPartialMonths() {
        val filter = StatisticsFilter(date("2019-12-15"), date("2020-03-02"))
        val overview = StatisticsMapper.aggregatedOverview(listOf(football), listOf(SportCount(1,2)),
            listOf(MonthSportCount("2019-12",1,1), MonthSportCount("2020-03",1,1)), emptyMap(), filter)
        assertEquals(2, overview.totalTrainings)
        assertEquals(listOf("2020-03","2020-02","2020-01","2019-12"), overview.months.map { it.month.toString() })
        assertEquals(listOf(1,0,0,1), overview.months.map { it.totalTrainings })
        assertEquals(filter, overview.filter)
        assertEquals(0, overview.months[1].countsBySport.size)
    }

    @Test fun emptyAllTimeIsEmptyButEmptySelectedIntervalIncludesZeroMonths() {
        assertTrue(StatisticsMapper.aggregatedOverview(listOf(football),emptyList(),emptyList(),emptyMap(),StatisticsFilter()).months.isEmpty())
        val filter = StatisticsFilter(date("0001-01-01"), date("9999-12-31"))
        val months = StatisticsMapper.aggregatedOverview(listOf(football),emptyList(),emptyList(),emptyMap(),filter).months
        assertEquals(119988, months.size)
        assertEquals(YearMonth.of(9999,12), months[0].month)
        assertEquals(YearMonth.of(1,1), months[months.lastIndex].month)
        assertEquals(0,months[60000].totalTrainings)
        assertTrue(runCatching { months[-1] }.isFailure)
        assertTrue(runCatching { months[months.size] }.isFailure)
    }

    @Test fun allTimeAlsoShowsGapsBetweenFirstAndLastWorkout() {
        val months = StatisticsMapper.aggregatedOverview(listOf(football),listOf(SportCount(1,2)),
            listOf(MonthSportCount("2018-12",1,1),MonthSportCount("2020-01",1,1)),emptyMap(),StatisticsFilter()).months
        assertEquals(14,months.size)
        assertEquals(0,months[5].totalTrainings)
    }

    @Test fun footballUsesLongTotalsZeroDistanceAndOnlySuppliedDenominators() {
        fun bundle(id: Long, distance: String?, minutes: Int?) = TrainingBundle(
            TrainingEntity(id,1,1,1,date("2020-01-01")), football, SportsComplexEntity(1,"Центр",null),
            FootballTrainingEntity(id,Int.MAX_VALUE,0,Int.MAX_VALUE,Int.MAX_VALUE,distance?.let(::BigDecimal),null,minutes),null)
        val values = FootballModule.metrics(listOf(bundle(1,null,null),bundle(2,"0",40),bundle(3,"0.3",41))).associate { it.label to it.value }
        assertEquals("6442450941",values["Личные голы"])
        assertEquals("6442450941:0",values["Счёт команд"])
        assertEquals("0.3 км",values["Учтённая дистанция"])
        assertEquals("0.15 км",values["Средняя дистанция"])
        assertEquals("2 из 3",values["Игр с дистанцией"])
        assertEquals("81 мин",values["Учтённое время"])
        assertEquals("40.5 мин",values["Средняя длительность"])
        assertEquals("2 из 3",values["Игр со временем"])
        val missing = FootballModule.metrics(listOf(bundle(1,null,null))).associate { it.label to it.value }
        val zero = FootballModule.metrics(listOf(bundle(2,"0",null))).associate { it.label to it.value }
        assertEquals("Нет данных",missing["Средняя дистанция"])
        assertEquals("0 км",zero["Средняя дистанция"])
        assertEquals("1 из 1",zero["Игр с дистанцией"])
    }
    @Test fun equivalentScientificAndPlainDistancesValidateAfterStorage() {
        val original = BigDecimal("1000000000000000E+6")
        val stored = BigDecimal(original.toPlainString())
        val input = FootballTrainingInput(0,0,0,0,original,null,null)
        assertTrue(FootballModule.fieldErrors(input).isEmpty())
        assertTrue(FootballModule.fieldErrors(input.copy(distanceKm=stored)).isEmpty())
        val backup = com.pamurlykin.sportsactivityassistant.data.backup.TrainingBackup(
            date="2020-01-01",sportSlug="football",centerName="Центр",
            football=com.pamurlykin.sportsactivityassistant.data.backup.FootballBackup(0,0,0,0,stored.toString()))
        FootballModule.validate(FootballModule.decodeDetails(backup,1,1))
        assertEquals(0, original.compareTo(stored))
        listOf("12345678901234567","0.0000001","1E+22","10E+2147483647").forEach { value ->
            assertFalse(FootballModule.fieldErrors(input.copy(distanceKm=BigDecimal(value))).isEmpty())
        }
    }
}
