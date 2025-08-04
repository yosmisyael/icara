package com.example.icara.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.sin

@Composable
fun AudioWaveform(
    audioLevel: Float,
    isListening: Boolean,
    modifier: Modifier = Modifier,
    waveColor: Color = MaterialTheme.colorScheme.primary,
    barCount: Int = 10
) {
    var animationPhase by remember { mutableStateOf(0f) }

    // Animate the waveform
    LaunchedEffect(isListening) {
        while (isListening) {
            animationPhase += 0.2f
            delay(100) // Update every
        }
    }

    Canvas(
        modifier = modifier
            .width(100.dp)
            .height(24.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val barWidth = canvasWidth / barCount
        val centerY = canvasHeight / 2

        repeat(barCount) { index ->
            val x = index * barWidth + barWidth / 2

            // Calculate bar height based on audio level and animation
            val baseHeight = if (isListening) {
                // Normalize audio level (?)
                val normalizedLevel = ((audioLevel + 40) / 40).coerceIn(0f, 1f)

                if (normalizedLevel > 0.1f) {
                    // Voice detected - animate based on audio level
                    val animatedMultiplier = 1f + sin(animationPhase + index * 0.8f) * 0.3f
                    val height = normalizedLevel * animatedMultiplier * canvasHeight * 0.8f
                    height.coerceAtLeast(3.dp.toPx())
                } else {
                    // No significant voice - subtle flat animation
                    val subtleHeight = 2.dp.toPx() + sin(animationPhase + index * 0.3f) * 1.dp.toPx()
                    subtleHeight.coerceAtLeast(2.dp.toPx())
                }
            } else {
                // Not listening - flat
                2.dp.toPx()
            }

            val barHeight = baseHeight.coerceAtMost(canvasHeight * 0.9f)

            // Draw the bar
            drawLine(
                color = waveColor,
                start = Offset(x, centerY - barHeight / 2),
                end = Offset(x, centerY + barHeight / 2),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}