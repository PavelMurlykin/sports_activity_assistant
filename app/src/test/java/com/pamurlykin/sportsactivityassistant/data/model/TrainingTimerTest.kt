package com.pamurlykin.sportsactivityassistant.data.model

import org.junit.Assert.*
import org.junit.Test

class TrainingTimerTest {
    @Test fun minutesRoundAtThirtySecondsWithoutSavingAnInvalidZero() {
        assertEquals("", TrainingTimer.durationText(0L))
        assertEquals("", TrainingTimer.durationText(29_999L))
        assertEquals("1", TrainingTimer.durationText(30_000L))
        assertEquals("1", TrainingTimer.durationText(89_999L))
        assertEquals("2", TrainingTimer.durationText(90_000L))
    }

    @Test fun stoppingExcludesThePauseAndStartingAgainPreservesElapsedSeconds() {
        val running = TrainingTimer().start(1_000L)
        assertEquals(running, running.start(2_000L))
        val stopped = running.stop(11_500L)
        assertFalse(stopped.isRunning)
        assertEquals(10_500L, stopped.elapsedMillis(100_000L))
        val resumed = stopped.start(100_000L)
        assertEquals(31_000L, resumed.elapsedMillis(120_500L))
        assertEquals("1", TrainingTimer.durationText(resumed.elapsedMillis(120_500L)))
    }

    @Test fun elapsedTimeCatchesUpWithoutReceivingIntermediateTicks() {
        val timer = TrainingTimer.fromDuration("5").start(5_000L)
        assertEquals(3_905_000L, timer.elapsedMillis(3_610_000L))
        assertEquals("01:05:05", TrainingTimer.display(timer.elapsedMillis(3_610_000L)))
        assertEquals("65", TrainingTimer.durationText(timer.elapsedMillis(3_610_000L)))
    }

    @Test fun manualMinutesCreateAPausedCounterAndResetClearsIt() {
        val manual = TrainingTimer.fromDuration(" 65 ")
        assertFalse(manual.isRunning)
        assertEquals("01:05:00", TrainingTimer.display(manual.elapsedMillis(9_000L)))
        assertEquals("00:00:00", TrainingTimer.display(TrainingTimer().elapsedMillis(9_000L)))
        listOf("", "0", "-1", "1.5", "2147483648", "wrong").forEach {
            assertEquals(0L, TrainingTimer.fromDuration(it).elapsedMillis(9_000L))
        }
    }

    @Test fun maximumDurationDoesNotOverflowAndBackwardClockDoesNotSubtractTime() {
        val maximum = TrainingTimer.fromDuration(Int.MAX_VALUE.toString()).start(1L)
        assertEquals(Int.MAX_VALUE.toString(), TrainingTimer.durationText(maximum.elapsedMillis(Long.MAX_VALUE)))
        val timer = TrainingTimer.fromDuration("1").start(10_000L)
        assertEquals(60_000L, timer.elapsedMillis(5_000L))
        assertEquals("00:01:00", TrainingTimer.display(timer.stop(5_000L).elapsedMillis(15_000L)))
    }
}
