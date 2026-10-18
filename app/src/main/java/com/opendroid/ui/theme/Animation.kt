package com.opendroid.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntSize

val SpringFast = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)
val SpringGentle = spring<Float>(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow)
val SpringIntSize = spring<IntSize>(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow)
