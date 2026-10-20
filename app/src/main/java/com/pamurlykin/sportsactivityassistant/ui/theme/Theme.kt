package com.pamurlykin.sportsactivityassistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import com.pamurlykin.sportsactivityassistant.data.model.ThemeMode
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

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF70DBB0),
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF15513D),
    onPrimaryContainer = Color(0xFFB0F2D1),
    secondary = Color(0xFFB5C5CE),
    onSecondary = Color(0xFF203039),
    secondaryContainer = Color(0xFF30423F),
    onSecondaryContainer = Color(0xFFD4E8E0),
    tertiary = Color(0xFFE8B887),
    onTertiary = Color(0xFF452B0D),
    tertiaryContainer = Color(0xFF614121),
    onTertiaryContainer = Color(0xFFFFDCB9),
    background = Color(0xFF101719),
    onBackground = Color(0xFFE0E7E9),
    surface = Color(0xFF182124),
    onSurface = Color(0xFFE0E7E9),
    surfaceVariant = Color(0xFF283337),
    onSurfaceVariant = Color(0xFFBAC7CD),
    outline = Color(0xFF8B9A9F),
    outlineVariant = Color(0xFF3D4C51),
    surfaceContainer = Color(0xFF1C272B),
    surfaceContainerLow = Color(0xFF182124),
    surfaceContainerHigh = Color(0xFF283337),
    surfaceContainerHighest = Color(0xFF334045),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun SportsActivityTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (themeMode.isDark(isSystemInDarkTheme())) DarkScheme else LightScheme,
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
