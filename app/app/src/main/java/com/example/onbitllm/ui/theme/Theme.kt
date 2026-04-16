package com.example.onbitllm.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryPurple,
    secondary = PrimaryBlue,
    tertiary = AccentCyan,
    background = BackgroundDark,
    surface = BackgroundCard,
    surfaceVariant = BackgroundCardDark,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onTertiary = BackgroundDeepDark,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = InputBorder,
    outlineVariant = AiBubbleBorder,
    scrim = OverlayBackground
)

@Composable
fun OneBitLLMTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = BackgroundDeepDark.toArgb()
            window.navigationBarColor = BackgroundDeepDark.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
