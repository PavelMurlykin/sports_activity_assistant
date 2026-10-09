package com.pamurlykin.sportsactivityassistant.ui

import com.pamurlykin.sportsactivityassistant.ui.screen.ReadState
import com.pamurlykin.sportsactivityassistant.ui.screen.readStates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ReadStateTest {
    @Test fun emptyDataIsAReadyStateNotAnErrorOrLoading() = runTest {
        val states = flowOf(emptyList<Int>()).readStates().toList()
        assertTrue(states.first().loading)
        assertEquals(emptyList<Int>(), states.last().data)
        assertFalse(states.last().loading)
        assertFalse(states.last().failed)
    }

    @Test fun failedReadDoesNotExposeTechnicalExceptionOrStaleData() = runTest {
        val states = flow { emit(7); error("private database path") }.readStates().toList()
        assertEquals(ReadState(data = 7), states[1])
        assertTrue(states.last().failed)
        assertNull(states.last().data)
        assertFalse(states.last().loading)
    }

    @Test fun cancellationIsNotAnError() = runTest {
        try {
            flow<Int> { throw CancellationException("obsolete selection") }.readStates().toList()
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { /* A newer request owns the UI. */ }
    }
}
