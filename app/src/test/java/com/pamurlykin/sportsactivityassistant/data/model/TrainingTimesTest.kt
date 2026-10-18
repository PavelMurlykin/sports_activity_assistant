package com.pamurlykin.sportsactivityassistant.data.model

import com.pamurlykin.sportsactivityassistant.ResourceTextTest
import org.junit.Assert.*
import org.junit.Test

class TrainingTimesTest : ResourceTextTest() {
    @Test fun optionalTimesDoNotInventADuration() {
        assertNull(TrainingTimes.parse("  "))
        assertNull(TrainingTimes.duration(null, null))
        assertNull(TrainingTimes.duration("10:30", null))
        assertNull(TrainingTimes.duration(null, "12:00"))
    }

    @Test fun durationIncludesMidnightAndMinutePrecision() {
        assertEquals(90, TrainingTimes.duration("10:30", "12:00"))
        assertEquals(90, TrainingTimes.duration("23:30", "01:00"))
        assertEquals(1, TrainingTimes.duration("23:59", "00:00"))
        assertEquals(1439, TrainingTimes.duration("00:00", "23:59"))
    }

    @Test fun invalidTimesAndZeroDurationAreRejected() {
        listOf("24:00", "12:60", "1:30", "aa:bb", "10:30:00").forEach {
            assertTrue(it, runCatching { TrainingTimes.parse(it) }.isFailure)
            assertTrue(it, runCatching { TrainingTimes.duration(null, it) }.isFailure)
        }
        assertTrue(runCatching { TrainingTimes.duration("10:30", "10:30") }.isFailure)
    }
}
