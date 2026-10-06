package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ElectricIndigo,
    onPrimary = Color.White,
    primaryContainer = IndigoDark,
    onPrimaryContainer = Color.White,
    secondary = CyberCyan,
    onSecondary = Color(0xFF002026),
    secondaryContainer = Color(0xFF004D5A),
    onSecondaryContainer = Color.White,
    tertiary = NeonEmerald,
    background = DarkBg,
    onBackground = Color(0xFFE2E8F0),
    surface = DarkSurface,
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    error = CrimsonStop,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = IndigoDark,
    onPrimary = Color.White,
    primaryContainer = IndigoLight,
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF00838F),
    onSecondary = Color.White,
    tertiary = Color(0xFF00A854),
    background = LightBg,
    onBackground = Color(0xFF0F172A),
    surface = LightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    error = CrimsonStop,
    onError = Color.White
)

@Composable
fun LiveAiReplyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep distinctive futuristic branding by default
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
