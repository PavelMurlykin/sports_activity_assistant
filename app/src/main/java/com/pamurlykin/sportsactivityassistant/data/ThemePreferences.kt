package com.pamurlykin.sportsactivityassistant.data

import android.content.SharedPreferences
import android.annotation.SuppressLint
import com.pamurlykin.sportsactivityassistant.data.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

interface ThemePreferences {
    val themeMode: StateFlow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
}

/** Used by isolated repositories without an Android preferences file. */
class InMemoryThemePreferences : ThemePreferences {
    private val selected = MutableStateFlow(ThemeMode.SYSTEM)
    override val themeMode = selected.asStateFlow()
    override suspend fun setThemeMode(mode: ThemeMode) { selected.value = mode }
}

/** The application owns one instance; publish a selection only after its disk write succeeds. */
class StoredThemePreferences(private val preferences: SharedPreferences) : ThemePreferences {
    private val selected = MutableStateFlow(ThemeMode.fromStorage(preferences.getString("theme", null)))
    private val writeLock = Mutex()
    override val themeMode = selected.asStateFlow()

    @SuppressLint("UseKtx") // commit() reports write failure; the KTX edit helper discards that result.
    override suspend fun setThemeMode(mode: ThemeMode) = withContext(Dispatchers.IO) {
        writeLock.withLock {
            if (!preferences.edit().putString("theme", mode.storageValue).commit()) throw IOException("Cannot save theme")
            selected.value = mode
        }
    }
}
