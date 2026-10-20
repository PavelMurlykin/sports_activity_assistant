package com.pamurlykin.sportsactivityassistant.data.model

import org.junit.Assert.*
import org.junit.Test

class ThemeModeTest {
    @Test fun explicitChoicesOverrideSystemAndSystemFollowsBothStates() {
        listOf(false, true).forEach { systemDark ->
            assertFalse(ThemeMode.LIGHT.isDark(systemDark))
            assertTrue(ThemeMode.DARK.isDark(systemDark))
            assertEquals(systemDark, ThemeMode.SYSTEM.isDark(systemDark))
        }
    }

    @Test fun storedChoicesRoundTripAndUnknownOrMissingChoiceUsesSystem() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromStorage(it.storageValue)) }
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("future-choice"))
    }
}
