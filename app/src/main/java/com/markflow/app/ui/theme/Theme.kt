package com.markflow.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = MarkFlowGreen,
    onPrimary = TextOnGreen,
    primaryContainer = MarkFlowGreenSurface,
    onPrimaryContainer = MarkFlowGreenOnSurface,
    secondary = MarkFlowTeal,
    onSecondary = Color.White,
    secondaryContainer = MarkFlowTealLight,
    onSecondaryContainer = Color(0xFF00433B),
    tertiary = StatusInfo,
    onTertiary = Color.White,
    background = SurfaceWhite,
    onBackground = TextPrimary,
    surface = SurfacePure,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceGray,
    onSurfaceVariant = TextSecondary,
    outline = DividerColor,
    outlineVariant = BorderLight,
    error = StatusError,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkGreen,
    onPrimary = Color(0xFF003910),
    primaryContainer = MarkFlowGreenDark,
    onPrimaryContainer = Color(0xFFA5F5B5),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005048),
    onSecondaryContainer = MarkFlowTealLight,
    tertiary = Color(0xFF90CAF9),
    onTertiary = Color(0xFF003258),
    background = DarkBackground,
    onBackground = TextOnDark,
    surface = DarkSurface,
    onSurface = TextOnDark,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = Color(0xFFC4C7C5),
    outline = DarkDivider,
    outlineVariant = Color(0xFF444746),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun MarkFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MarkFlowTypography,
        shapes = MarkFlowShapes,
        content = content
    )
}
