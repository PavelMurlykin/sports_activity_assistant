package com.pamurlykin.sportsactivityassistant.data.model

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class CenterNamesTest {
    @Test fun whitespaceCaseAndMissingCityShareOneKey() {
        assertEquals(CenterNames.key("  Большая\tАрена  ", null), CenterNames.key("большая\u00a0  арена", "  "))
        assertEquals("Большая Арена", CenterNames.clean("  Большая\n\u00a0Арена  "))
        assertNotEquals(CenterNames.key("Арена", "Москва"), CenterNames.key("Арена", "Казань"))
    }
    @Test fun unicodeNormalizationAndLocaleAreDeterministic() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            assertEquals(CenterNames.key("I CAFÉ", ""), CenterNames.key("i cafe\u0301", null))
        } finally { Locale.setDefault(old) }
    }
    @Test fun manualFieldsHaveLimitsAndRejectControls() {
        CenterNames.validate("А".repeat(200), null)
        listOf("", "А".repeat(201), "А\u0000").forEach { assertTrue(runCatching { CenterNames.validate(it, null) }.isFailure) }
        assertTrue(runCatching { CenterNames.validate("Арена", "А".repeat(201)) }.isFailure)
    }
}
