package com.ironlog.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Spacing is a 4dp grid. Radius is deliberately *not* uniform: the further a
 * surface is from being tappable, the squarer it gets, so hierarchy reads by
 * shape as well as colour.
 */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val section = 40.dp
}

object Radius {
    val chip = 999.dp
    val control = 12.dp
    val card = 16.dp
    val sheet = 24.dp
    val panel = 4.dp
}

/** Minimum comfortable tap target with chalky hands and a phone on a bench. */
object Touch {
    val min = 48.dp
    val setRow = 52.dp
    val primaryButton = 56.dp
}

private val IronShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.panel),
    small = RoundedCornerShape(Radius.control),
    medium = RoundedCornerShape(Radius.card),
    large = RoundedCornerShape(Radius.sheet),
    extraLarge = RoundedCornerShape(Radius.sheet)
)

private fun ironColors(background: Color) = darkColorScheme(
    primary = Chalk,
    onPrimary = Slate900,
    primaryContainer = Slate700,
    onPrimaryContainer = Chalk,

    secondary = Info,
    onSecondary = Chalk,
    secondaryContainer = Slate700,
    onSecondaryContainer = ChalkDim,

    tertiary = PrGold,
    onTertiary = Slate900,

    background = background,
    onBackground = Chalk,

    surface = background,
    onSurface = Chalk,
    surfaceVariant = Slate800,
    onSurfaceVariant = ChalkDim,
    surfaceContainer = Slate800,
    surfaceContainerHigh = Slate700,
    surfaceContainerHighest = Slate700,
    surfaceContainerLow = Slate800,
    surfaceContainerLowest = Slate900,

    outline = Slate600,
    outlineVariant = Slate700,

    error = Danger,
    onError = Chalk,
    errorContainer = Slate700,
    onErrorContainer = Danger,

    scrim = Slate900
)

@Composable
fun IronLogTheme(
    // The app is dark-only by design: it is used in gyms, often at night, and a
    // light variant would halve the contrast of the chart palette.
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    // A background bought from the store. Every option is dark, so this changes
    // the shade without reopening the dark-only decision.
    background: Color = Slate900,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Bars are transparent under enableEdgeToEdge(); the Slate900 window
            // background shows through, so only the icon tint needs setting.
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = ironColors(background),
        typography = IronTypography,
        shapes = IronShapes,
        content = content
    )
}
