package com.speakerroom.tap2sound.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta oscura de marca: replica las variables CSS de tap2sound-web/index.html
// (:root), para que la app comparta el mismo estilo oscuro/dorado que la web.
private val Tap2SoundDarkColors = darkColorScheme(
    primary = T2SGold,
    onPrimary = T2SOnGold,
    primaryContainer = T2SGoldLight,
    onPrimaryContainer = T2SOnGold,
    secondary = T2SMuted,
    onSecondary = T2SBackground,
    secondaryContainer = T2SBackgroundAlt,
    onSecondaryContainer = T2SInk,
    tertiary = T2SGoldLight,
    onTertiary = T2SOnGold,
    background = T2SBackground,
    onBackground = T2SInk,
    surface = T2SSurface,
    onSurface = T2SInk,
    surfaceVariant = T2SBackgroundAlt,
    onSurfaceVariant = T2SMuted,
    outline = T2SOutline,
    error = Color(0xFFCF6679),
    onError = Color(0xFF1A0000)
)

// Version clara de la misma paleta, por si el dispositivo fuerza tema claro.
// Mantiene el dorado como color de marca sobre un fondo claro.
private val Tap2SoundLightColors = lightColorScheme(
    primary = T2SGold,
    onPrimary = T2SOnGold,
    primaryContainer = T2SGoldLight,
    onPrimaryContainer = T2SOnGold,
    secondary = T2SMuted,
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFBF5),
    onBackground = T2SOnGold,
    surface = Color(0xFFFFFFFF),
    onSurface = T2SOnGold,
    surfaceVariant = Color(0xFFF3EEE3),
    onSurfaceVariant = T2SMuted,
    outline = T2SOutline
)

@Composable
fun Tap2SoundTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) Tap2SoundDarkColors else Tap2SoundLightColors
    MaterialTheme(
        colorScheme = colors,
        typography = Tap2SoundTypography,
        shapes = Tap2SoundShapes,
        content = content
    )
}
