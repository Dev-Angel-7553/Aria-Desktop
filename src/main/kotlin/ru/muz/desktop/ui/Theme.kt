package ru.muz.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import ru.muz.desktop.DesktopSettings
import ru.muz.desktop.ThemeMode
import ru.muz.theme.AppPalette

val LocalDark = staticCompositionLocalOf { true }

private fun scheme(p: AppPalette, dark: Boolean): ColorScheme {
    fun c(v: Int) = Color(v)
    return if (dark) {
        darkColorScheme(
            primary = c(p.primary), onPrimary = c(p.onPrimary),
            primaryContainer = c(p.primaryContainer), onPrimaryContainer = c(p.onPrimaryContainer),
            secondary = c(p.secondary), onSecondary = c(p.onSecondary),
            secondaryContainer = c(p.secondaryContainer), onSecondaryContainer = c(p.onSecondaryContainer),
            tertiary = c(p.tertiary), onTertiary = c(p.onTertiary),
            tertiaryContainer = c(p.tertiaryContainer), onTertiaryContainer = c(p.onTertiaryContainer),
            background = c(p.background), onBackground = c(p.onBackground),
            surface = c(p.surface), onSurface = c(p.onSurface),
            surfaceVariant = c(p.surfaceVariant), onSurfaceVariant = c(p.onSurfaceVariant),
            surfaceTint = c(p.surfaceTint), inverseSurface = c(p.inverseSurface),
            inverseOnSurface = c(p.inverseOnSurface), inversePrimary = c(p.inversePrimary),
            outline = c(p.outline), outlineVariant = c(p.outlineVariant),
            surfaceDim = c(p.surfaceDim), surfaceBright = c(p.surfaceBright),
            surfaceContainerLowest = c(p.surfaceContainerLowest), surfaceContainerLow = c(p.surfaceContainerLow),
            surfaceContainer = c(p.surfaceContainer), surfaceContainerHigh = c(p.surfaceContainerHigh),
            surfaceContainerHighest = c(p.surfaceContainerHighest),
            error = c(p.error), onError = c(p.onError), errorContainer = c(p.errorContainer), onErrorContainer = c(p.onErrorContainer),
        )
    } else {
        lightColorScheme(
            primary = c(p.primary), onPrimary = c(p.onPrimary),
            primaryContainer = c(p.primaryContainer), onPrimaryContainer = c(p.onPrimaryContainer),
            secondary = c(p.secondary), onSecondary = c(p.onSecondary),
            secondaryContainer = c(p.secondaryContainer), onSecondaryContainer = c(p.onSecondaryContainer),
            tertiary = c(p.tertiary), onTertiary = c(p.onTertiary),
            tertiaryContainer = c(p.tertiaryContainer), onTertiaryContainer = c(p.onTertiaryContainer),
            background = c(p.background), onBackground = c(p.onBackground),
            surface = c(p.surface), onSurface = c(p.onSurface),
            surfaceVariant = c(p.surfaceVariant), onSurfaceVariant = c(p.onSurfaceVariant),
            surfaceTint = c(p.surfaceTint), inverseSurface = c(p.inverseSurface),
            inverseOnSurface = c(p.inverseOnSurface), inversePrimary = c(p.inversePrimary),
            outline = c(p.outline), outlineVariant = c(p.outlineVariant),
            surfaceDim = c(p.surfaceDim), surfaceBright = c(p.surfaceBright),
            surfaceContainerLowest = c(p.surfaceContainerLowest), surfaceContainerLow = c(p.surfaceContainerLow),
            surfaceContainer = c(p.surfaceContainer), surfaceContainerHigh = c(p.surfaceContainerHigh),
            surfaceContainerHighest = c(p.surfaceContainerHighest),
            error = c(p.error), onError = c(p.onError), errorContainer = c(p.errorContainer), onErrorContainer = c(p.onErrorContainer),
        )
    }
}

private val typography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun AriaTheme(settings: DesktopSettings, coverAccent: Int?, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val target = if (settings.coverAccent && coverAccent != null) coverAccent else settings.accent
    val accent by animateColorAsState(Color(target), tween(700))
    val argb = accent.toArgb()
    var colors = remember(argb, dark) { scheme(AppPalette.generate(argb, dark), dark) }
    if (settings.theme == ThemeMode.AMOLED) {
        colors = colors.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF070707),
            surfaceContainer = Color(0xFF0C0C0C),
            surfaceContainerHigh = Color(0xFF141414),
            surfaceContainerHighest = Color(0xFF1C1C1C),
        )
    }
    val density = LocalDensity.current
    val scale = settings.uiScale / 100f
    CompositionLocalProvider(
        LocalDark provides dark,
        LocalDensity provides Density(density.density * scale, density.fontScale),
    ) {
        MaterialTheme(colorScheme = colors, typography = typography, content = content)
    }
}

val SoftText: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Accent: Color @Composable get() = MaterialTheme.colorScheme.primary

fun TextStyle.tight(): TextStyle = copy(letterSpacing = (-0.2).sp)
