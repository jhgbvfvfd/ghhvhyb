package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

@Immutable
data class KpziExtendedColors(
    val border: Color,
    val accent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val mutedText: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val error: Color,
    val errorContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val isDark: Boolean,
)

private val LightExtendedColors = KpziExtendedColors(
    border = ParchmentBorder,
    accent = HarvestGoldAccent,
    accentContainer = HarvestGoldContainer,
    onAccentContainer = HarvestGoldOnContainer,
    mutedText = MutedBarkText,
    success = StatusSuccessLight,
    successContainer = StatusSuccessBgLight,
    warning = StatusWarningLight,
    warningContainer = StatusWarningBgLight,
    error = StatusErrorLight,
    errorContainer = StatusErrorBgLight,
    info = StatusInfoLight,
    infoContainer = StatusInfoBgLight,
    isDark = false,
)

private val DarkExtendedColors = KpziExtendedColors(
    border = DarkBarkBorder,
    accent = AmberDarkAccent,
    accentContainer = AmberDarkContainer,
    onAccentContainer = AmberDarkOnContainer,
    mutedText = MutedDarkText,
    success = StatusSuccessDark,
    successContainer = StatusSuccessBgDark,
    warning = StatusWarningDark,
    warningContainer = StatusWarningBgDark,
    error = StatusErrorDark,
    errorContainer = StatusErrorBgDark,
    info = StatusInfoDark,
    infoContainer = StatusInfoBgDark,
    isDark = true,
)

val LocalKpziColors = staticCompositionLocalOf { LightExtendedColors }

private val LightColorScheme = lightColorScheme(
    primary = ForestPrimary,
    onPrimary = Color.White,
    primaryContainer = ForestPrimaryContainer,
    onPrimaryContainer = ForestOnPrimaryContainer,
    secondary = HarvestGoldAccent,
    onSecondary = EspressoText,
    secondaryContainer = HarvestGoldContainer,
    onSecondaryContainer = HarvestGoldOnContainer,
    tertiary = StatusInfoLight,
    onTertiary = Color.White,
    background = CreamBackground,
    onBackground = EspressoText,
    surface = IvorySurface,
    onSurface = EspressoText,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = MutedBarkText,
    outline = ParchmentBorder,
    outlineVariant = ParchmentBorder,
    error = StatusErrorLight,
    onError = Color.White,
    errorContainer = StatusErrorBgLight,
    onErrorContainer = StatusErrorLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = MossPrimary,
    onPrimary = Color(0xFF102208),
    primaryContainer = MossPrimaryContainer,
    onPrimaryContainer = MossOnPrimaryContainer,
    secondary = AmberDarkAccent,
    onSecondary = Color(0xFF281904),
    secondaryContainer = AmberDarkContainer,
    onSecondaryContainer = AmberDarkOnContainer,
    tertiary = StatusInfoDark,
    onTertiary = Color(0xFF0A1F2C),
    background = DarkSoilBackground,
    onBackground = CreamDarkText,
    surface = DarkTimberSurface,
    onSurface = CreamDarkText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = MutedDarkText,
    outline = DarkBarkBorder,
    outlineVariant = DarkBarkBorder,
    error = StatusErrorDark,
    onError = Color(0xFF330C0A),
    errorContainer = StatusErrorBgDark,
    onErrorContainer = StatusErrorDark,
)

val KpziShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

@Immutable
data class KpziThemeState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isDark: Boolean = false,
    val setThemeMode: (ThemeMode) -> Unit = {},
    val toggleTheme: () -> Unit = {},
)

val LocalThemeState = staticCompositionLocalOf { KpziThemeState() }

@Composable
fun KpziTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    val themeState = remember(themeMode, darkTheme, onThemeModeChange) {
        KpziThemeState(
            themeMode = themeMode,
            isDark = darkTheme,
            setThemeMode = onThemeModeChange,
            toggleTheme = {
                onThemeModeChange(if (darkTheme) ThemeMode.LIGHT else ThemeMode.DARK)
            },
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalKpziColors provides extendedColors,
        LocalThemeState provides themeState,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KpziTypography,
            shapes = KpziShapes,
            content = content,
        )
    }
}
