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
    primary = GoldenSun,                  // Premium Slate-White / Platinum Silver highlight (#E2E8F0)
    onPrimary = Color(0xFF090D16),        // Obsidian Dark text on primary elements
    secondary = CrispBlue,                // Sleek Muted Dark Slate (#1E293B)
    tertiary = GoldenSun,
    background = SmoothBackgroundWhite,   // Deep slate backdrops
    surface = Color(0xFF151D2A),          // Premium Obsidian Slate surfaces
    onPrimaryContainer = Color.White,
    onBackground = Color(0xFFF8FAFC),     // Bright pristine text
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1E293B),   // Slate dark cards
    onSurfaceVariant = Color(0xFF94A3B8), // Sleek grey text
    outline = Color(0xFF334155)           // Dark subtle border outline
)

private val LightColorScheme = lightColorScheme(
    primary = GoldenSun,                  // Premium Slate-White / Platinum Silver highlight (#E2E8F0)
    onPrimary = Color(0xFF090D16),        // Obsidian Dark text on primary elements
    secondary = CrispBlue,                // Sleek Muted Dark Slate (#1E293B)
    tertiary = GoldenSun,
    background = SmoothBackgroundWhite,   // Deep dark canvas
    surface = Color(0xFF151D2A),          // Dark cards
    onPrimaryContainer = Color.White,
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1E293B),   // Dark slate panels
    onSurfaceVariant = Color(0xFF94A3B8), // Muted grey text
    outline = Color(0xFF334155)           // Subtle border outline
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Allow turning off dynamic system colors to preserve brand cohesive identity
    dynamicColor: Boolean = false,
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
