package com.inventoria.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shape definitions for the Inventoria app.
 * Aligned with Material 3 system tokens and reverse-engineered styles.
 */
val Shapes = Shapes(
    // Buttons and small chips (aligned with ShapeAppearance.Material3.SmallComponent)
    extraSmall = RoundedCornerShape(4.dp),
    
    // Most standard components (aligned with ShapeAppearance.Material3.MediumComponent)
    small = RoundedCornerShape(8.dp),
    
    // Cards and standard dialogs (aligned with ShapeAppearance.M3.Sys.Shape.Corner.Medium)
    medium = RoundedCornerShape(12.dp),
    
    // Large surfaces and FABs (aligned with ShapeAppearance.M3.Sys.Shape.Corner.Large)
    large = RoundedCornerShape(16.dp),
    
    // Modal Bottom Sheets and Full-screen dialogs (aligned with ShapeAppearance.M3.Sys.Shape.Corner.ExtraLarge)
    extraLarge = RoundedCornerShape(28.dp)
)
