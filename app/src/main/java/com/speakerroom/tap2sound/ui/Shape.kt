package com.speakerroom.tap2sound.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Radios de esquina que replican el CSS de tap2sound-web:
 * --radius:20px para tarjetas/paneles y border-radius:100px (pill) para botones.
 */
val Tap2SoundShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Forma tipo "pill" para botones, igual que .btn { border-radius:100px } en la web. */
val Tap2SoundPillShape = RoundedCornerShape(100.dp)
