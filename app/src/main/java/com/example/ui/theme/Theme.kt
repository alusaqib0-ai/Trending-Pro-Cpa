package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryEmerald,
    onPrimary = BgDark,
    secondary = DarkEmerald,
    onSecondary = TextLight,
    background = BgDark,
    surface = CardBg,
    onBackground = TextLight,
    onSurface = TextLight,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TextMuted,
    outline = FieldBorder
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryEmerald,
    onPrimary = BgDark,
    secondary = DarkEmerald,
    onSecondary = TextLight,
    background = BgDark,
    surface = CardBg,
    onBackground = TextLight,
    onSurface = TextLight,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TextMuted,
    outline = FieldBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force Dark theme for Professional Polish
    dynamicColor: Boolean = false, // Disable dynamic color to enforce our branding
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
