package com.example.ui.screens.ai

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * غلاف تفاعلي موحد يوجه مباشرة نحو مجسم فهيم ثلاثي الأبعاد الواقعي [FahimMascot3D].
 */
@Composable
fun FahimInteractiveMascot(
    modifier: Modifier = Modifier,
    compactMode: Boolean = false
) {
    FahimMascot3D(
        modifier = modifier,
        compactMode = compactMode
    )
}
