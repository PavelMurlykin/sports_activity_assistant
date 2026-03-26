package com.pamurlykin.sportsactivityassistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightScheme = lightColorScheme(
    primary = Pine,
    onPrimary = Mist,
    secondary = Clay,
    onSecondary = Mist,
    tertiary = Sky,
    background = Sand,
    onBackground = Ink,
    surface = Mist,
    onSurface = Ink,
    surfaceVariant = SandDark,
    onSurfaceVariant = PineSoft,
    outline = PineSoft,
)

@Composable
fun SportsActivityTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightScheme,
        typography = Typography(),
        content = content,
    )
}
