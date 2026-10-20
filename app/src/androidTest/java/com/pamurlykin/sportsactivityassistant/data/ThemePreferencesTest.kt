package com.pamurlykin.sportsactivityassistant.data

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.pamurlykin.sportsactivityassistant.data.model.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ThemePreferencesTest {
    @Test fun everyChoiceSurvivesRecreatingThePreferencesStore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "theme-test-${UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            assertEquals(ThemeMode.SYSTEM, StoredThemePreferences(preferences).themeMode.value)
            val store = StoredThemePreferences(preferences)
            listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.SYSTEM).forEach { mode ->
                store.setThemeMode(mode)
                assertEquals(mode, store.themeMode.value)
                assertEquals(mode, StoredThemePreferences(context.getSharedPreferences(name, Context.MODE_PRIVATE)).themeMode.value)
            }
        } finally { preferences.edit().clear().commit() }
    }
}
