package com.pamurlykin.sportsactivityassistant.data.model

enum class ThemeMode(val storageValue: String) {
    LIGHT("light"), DARK("dark"), SYSTEM("system");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        SYSTEM -> systemDark
    }

    companion object {
        fun fromStorage(value: String?): ThemeMode = entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}
