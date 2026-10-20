package com.pamurlykin.sportsactivityassistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightScheme = lightColorScheme(
    primary = Pine,
    onPrimary = Color.White,
    primaryContainer = Sky,
    onPrimaryContainer = Pine,
    secondary = PineSoft,
    onSecondary = Mist,
    tertiary = Sky,
    background = Sand,
    onBackground = Ink,
    surface = Mist,
    onSurface = Ink,
    surfaceVariant = SandDark,
    onSurfaceVariant = PineSoft,
    outline = PineSoft,
    outlineVariant = SandDark,
    surfaceContainer = Mist,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = SandDark,
    secondaryContainer = Sky,
    onSecondaryContainer = Pine,
)

@Composable
fun SportsActivityTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightScheme,
        typography = Typography().let { type -> type.copy(
            headlineLarge = type.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
            headlineMedium = type.headlineMedium.copy(fontWeight = FontWeight.Bold),
            headlineSmall = type.headlineSmall.copy(fontWeight = FontWeight.Bold),
            titleLarge = type.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        ) },
        shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp),
        ),
        content = content,
    )
}
