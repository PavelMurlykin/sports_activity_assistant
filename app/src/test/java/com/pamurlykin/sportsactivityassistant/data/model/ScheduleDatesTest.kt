package com.pamurlykin.sportsactivityassistant.data.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ScheduleDatesTest {
    @Test fun leapFebruaryHasCompleteMondaySundayGridAndYearBoundary() {
        val days = ScheduleDates.grid(YearMonth.of(2020,2))
        assertEquals(LocalDate.parse("2020-01-27"),days.first())
        assertEquals(LocalDate.parse("2020-03-01"),days.last())
        assertEquals(35,days.size); assertTrue(LocalDate.parse("2020-02-29") in days)
        assertEquals(LocalDate.parse("2020-12-28"),ScheduleDates.grid(YearMonth.of(2021,1)).first())
    }
    @Test fun recurrenceUsesAnchorIntervalAndInclusiveEndAcrossYears() {
        val start = LocalDate.parse("2019-12-28")
        val end = LocalDate.parse("2020-02-08")
        assertEquals(listOf(start.plusWeeks(2),start.plusWeeks(4),start.plusWeeks(6)),
            ScheduleDates.expand(start,end,2,LocalDate.parse("2020-01-01"),LocalDate.parse("2020-03-01")))
        assertFalse(ScheduleDates.occurs(start,end,2,start.plusWeeks(1)))
        assertTrue(ScheduleDates.occurs(start,end,2,end))
        assertFalse(ScheduleDates.occurs(start,end,2,end.plusWeeks(2)))
    }
    @Test fun extremeIntervalAndLastSupportedYearDoNotOverflow() {
        val start = LocalDate.parse("9999-12-31")
        assertEquals(listOf(start),ScheduleDates.expand(start,null,Int.MAX_VALUE,start,start))
        assertTrue(ScheduleDates.expand(LocalDate.parse("0001-01-01"),null,Int.MAX_VALUE,start,start).isEmpty())
        assertEquals(LocalDate.of(10000,1,2),ScheduleDates.grid(YearMonth.of(9999,12)).last())
    }
    @Test fun invalidIntervalAndNonOverlappingRangeAreSafe() {
        val d = LocalDate.parse("2020-01-01")
        assertTrue(runCatching { ScheduleDates.expand(d,null,0,d,d) }.isFailure)
        assertFalse(ScheduleDates.occurs(d,null,-1,d))
        assertTrue(ScheduleDates.expand(d,d,1,d.plusDays(1),d.plusMonths(1)).isEmpty())
    }
}
