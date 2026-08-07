package com.pamurlykin.sportsactivityassistant.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimbingDifficultyCatalogTest {
    @Test
    fun catalogContainsOriginalBotGradesAndExtendedScale() {
        assertTrue(ClimbingDifficultyCatalog.isValid("6a+"))
        assertTrue(ClimbingDifficultyCatalog.isValid("9C"))
        assertFalse(ClimbingDifficultyCatalog.isValid("9C+"))
        assertFalse(ClimbingDifficultyCatalog.isValid("10Z"))
    }

    @Test
    fun returnsHardestCompletedGradeByCatalogOrder() {
        assertEquals("7B+", ClimbingDifficultyCatalog.hardest(listOf("6C", "7a", "7B+", "5")))
    }
}
