package com.speakerroom.tap2sound.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// NOTA: la web (tap2sound-web) usa las fuentes "Space Grotesk" (titulos) y
// "Poppins" (texto) via Google Fonts. Aqui usamos las fuentes del sistema
// (FontFamily.SansSerif) con los mismos pesos y tamanos relativos, para que
// la jerarquia visual coincida sin necesitar descargar archivos .ttf.
//
// Si mas adelante quieres las tipografias exactas, se pueden anadir como
// "Downloadable Fonts" de Google Fonts (androidx.compose.ui:ui-text-google-fonts)
// o copiando los .ttf a app/src/main/res/font/ y referenciandolos con Font(R.font....).
private val DisplayFontFamily = FontFamily.SansSerif
private val BodyFontFamily = FontFamily.SansSerif

val Tap2SoundTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp
    )
)
