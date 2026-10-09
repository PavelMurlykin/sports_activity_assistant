package com.pamurlykin.sportsactivityassistant.data.model

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TrainingFormValidationTest : com.pamurlykin.sportsactivityassistant.ResourceTextTest() {
    @Test fun emptyOptionalsAreNullAndZeroScoresValid() {
        val result = FootballFormValues.parse(listOf("0","0","0","0","","",""))
        assertTrue(result.errors.isEmpty()); assertNull(result.input!!.distanceKm)
        assertNull(result.input.playersPerTeam); assertNull(result.input.durationMinutes)
    }
    @Test fun allNumericFieldsReturnInlineErrorsAndCanRecover() {
        val result = FootballFormValues.parse(listOf("","x","2147483648","1.5","x","1.5","x"))
        assertEquals((0..6).toSet(), result.errors.keys)
        val valid = FootballFormValues.parse(listOf("2","1","1","1","7,35","5","60"))
        assertTrue(valid.errors.isEmpty()); assertEquals("7.35".toBigDecimal(), valid.input!!.distanceKm)
    }
    @Test fun sharedBusinessRulesRejectNegativeOrInvalidCountsAndCrossFieldValues() {
        assertEquals((0..6).toSet(), FootballFormValues.parse(listOf("-1","-1","-1","-1","-1","0","0")).errors.keys)
        assertEquals(setOf(2,3), FootballFormValues.parse(listOf("0","0","1","1","","","")).errors.keys)
        assertTrue(FootballFormValues.parse(listOf("0","0","0","0","0","1","1")).errors.isEmpty())
    }
    @Test fun distanceLimitsPreventPathologicalParsingAndUnsupportedPrecision() {
        listOf("9".repeat(1000), "0.1234567", "12345678901234567", "1e999999", "1e99999999999").forEach { value ->
            assertTrue(FootballFormValues.parse(listOf("0","0","0","0",value,"","")).errors.containsKey(4))
        }
    }
    @Test fun completedDatePolicyPreservesHistoricalFutureButDoesNotCreateNewFuture() {
        val today = LocalDate.parse("2026-10-08")
        TrainingValidation.completedDate(today, today = today)
        TrainingValidation.completedDate(today.minusYears(20), today = today)
        assertTrue(runCatching { TrainingValidation.completedDate(today.plusDays(1), today = today) }.isFailure)
        TrainingValidation.completedDate(today.plusDays(1), today.plusDays(1), today)
        assertTrue(runCatching { TrainingValidation.completedDate(today.plusDays(2), today.plusDays(1), today) }.isFailure)
        assertTrue(runCatching { TrainingValidation.parseDate("2026-02-30") }.isFailure)
    }
}
