package com.pamurlykin.sportsactivityassistant.data.model

import com.pamurlykin.sportsactivityassistant.data.model.ClimbingDifficultyCatalog as Catalog
import org.junit.Assert.*
import org.junit.Test

class ClimbingDifficultyCatalogTest {
    @Test fun catalogHasDistinctCodesDisplayAndRanges() {
        assertEquals("6a+", Catalog.find(Catalog.FRENCH, " 6A+ ")!!.code)
        assertEquals("6a+", Catalog.find(Catalog.FRENCH, "6A+")!!.label)
        assertEquals("6A+", Catalog.find(Catalog.FONTAINEBLEAU, "6a+")!!.label)
        assertNotNull(Catalog.find(Catalog.FRENCH, "3a"))
        assertNotNull(Catalog.find(Catalog.FRENCH, "9C"))
        assertNull(Catalog.find(Catalog.FRENCH, "9C+"))
        assertNotNull(Catalog.find(Catalog.FONTAINEBLEAU, "1b"))
        assertNotNull(Catalog.find(Catalog.FONTAINEBLEAU, "9A"))
        assertNull(Catalog.find(Catalog.FONTAINEBLEAU, "9A+"))
        assertNull(Catalog.find(Catalog.FONTAINEBLEAU, "9C"))
        assertNull(Catalog.find(Catalog.FRENCH, "10Z"))
    }

    @Test fun plusGradesHaveStrictUniqueOrderWithinEachScale() {
        listOf(Catalog.FRENCH, Catalog.FONTAINEBLEAU).forEach { system ->
            val grades = Catalog.grades(system)
            assertEquals(grades.size, grades.map { it.code }.distinct().size)
            assertEquals(grades.size, grades.map { it.rank }.distinct().size)
            assertTrue(Catalog.find(system, "6a")!!.rank < Catalog.find(system, "6a+")!!.rank)
            assertTrue(Catalog.find(system, "6c+")!!.rank < Catalog.find(system, "7a")!!.rank)
        }
        assertEquals("7b+", Catalog.hardest(Catalog.FRENCH, listOf("6C", "7a", "7B+", "5"))!!.code)
        assertTrue(Catalog.grades(Catalog.NONE).isEmpty())
        assertNull(Catalog.hardest(Catalog.LEGACY, listOf("9c")))
    }

    @Test fun historicalLeadIsMappedWithoutGuessingOtherDisciplines() {
        assertEquals(HistoricalGrade("french", "6a+"), HistoricalClimbingGrades.classify("difficulty", " 6A+ "))
        listOf("3", "4", "4+", "5", "10Z", "9C+").forEach {
            assertEquals(HistoricalGrade("legacy", null), HistoricalClimbingGrades.classify("difficulty", it))
        }
        listOf("bouldering", "speed", "strange").forEach {
            assertEquals(HistoricalGrade("legacy", null), HistoricalClimbingGrades.classify(it, "6A"))
        }
        assertEquals(ClimbingWorkoutType.UNKNOWN, ClimbingWorkoutType.fromStorage("strange"))
        assertFalse(ClimbingWorkoutType.UNKNOWN in ClimbingWorkoutType.supported)
    }
}
