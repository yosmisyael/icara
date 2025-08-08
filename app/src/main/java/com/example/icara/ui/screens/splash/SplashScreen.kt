package com.example.icara.ui.screens.splash

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateNext: () -> Unit
) {
    // Animation states for different logo parts
    val handPathProgress = remember { Animatable(0f) }
    val soundWave1Progress = remember { Animatable(0f) }
    val soundWave2Progress = remember { Animatable(0f) }
    val fingerProgress = remember { Animatable(0f) }
    val fillAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    Log.d("SplashScreen", "SplashScreen composable started")

    LaunchedEffect(Unit) {
        Log.d("SplashScreen", "LaunchedEffect started")

        // Start with a short delay to match native splash screen timing
        delay(300L)

        // Animate hand outline first (main gesture)
        handPathProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 800,
                easing = FastOutSlowInEasing
            )
        )

        // Animate finger details
        fingerProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 600,
                easing = FastOutSlowInEasing
            )
        )

        // Animate sound waves in sequence
        soundWave1Progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 700,
                easing = FastOutSlowInEasing
            )
        )

        delay(200L)

        soundWave2Progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 700,
                easing = FastOutSlowInEasing
            )
        )

        // Fill the logo with color
        fillAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 400,
                easing = LinearEasing
            )
        )

        // Show text after logo animation
        textAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 600,
                easing = FastOutSlowInEasing
            )
        )

        Log.d("SplashScreen", "Animations completed, waiting 1 second")
        delay(1000L)

        Log.d("SplashScreen", "About to call onNavigateNext()")
        onNavigateNext()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White // Match windowSplashScreenBackground
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated Logo - same size as native splash
            Canvas(
                modifier = Modifier.size(120.dp) // Match native splash icon size
            ) {
                drawAnimatedICaraLogo(
                    handProgress = handPathProgress.value,
                    soundWave1Progress = soundWave1Progress.value,
                    soundWave2Progress = soundWave2Progress.value,
                    fingerProgress = fingerProgress.value,
                    fillAlpha = fillAlpha.value,
                    primaryColor = Color(0xFF00677C),
                    accentColor = Color(0xFFB2EBFF)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // App Name
            Text(
                text = "iCara",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp
                ),
                color = Color(0xFF00677C),
                modifier = Modifier.alpha(textAlpha.value)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tagline
            Text(
                text = "Dunia Mendengar Bahasamu",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp
                ),
                color = Color(0xFF666666),
                modifier = Modifier.alpha(textAlpha.value)
            )
        }
    }
}

// Custom drawing function for animated iCara logo based on the SVG
private fun DrawScope.drawAnimatedICaraLogo(
    handProgress: Float,
    soundWave1Progress: Float,
    soundWave2Progress: Float,
    fingerProgress: Float,
    fillAlpha: Float,
    primaryColor: Color,
    accentColor: Color
) {
    val scale = size.minDimension / 200f // Scale to fit canvas
    val centerX = size.width / 2
    val centerY = size.height / 2

    // Create paths based on SVG coordinates, scaled and centered
    fun scaleX(x: Float) = centerX + (x - 23.5f) * scale
    fun scaleY(y: Float) = centerY + (y - 22.5f) * scale

    // Main hand gesture path
    val handPath = Path().apply {
        // Main hand body (simplified from SVG path)
        moveTo(scaleX(11f), scaleY(39.2f))
        cubicTo(scaleX(9f), scaleY(39.2f), scaleX(7.4f), scaleY(38.5f), scaleX(6f), scaleY(37.2f))
        cubicTo(scaleX(4.7f), scaleY(35.8f), scaleX(4f), scaleY(34.1f), scaleX(4f), scaleY(32.1f))
        lineTo(scaleX(4f), scaleY(13.2f))
        cubicTo(scaleX(4f), scaleY(11.6f), scaleX(5.1f), scaleY(10.9f), scaleX(6.3f), scaleY(10.9f))
        cubicTo(scaleX(7.5f), scaleY(10.9f), scaleX(8.7f), scaleY(11.6f), scaleX(8.7f), scaleY(13.2f))
        lineTo(scaleX(8.7f), scaleY(32.1f))
        cubicTo(scaleX(8.7f), scaleY(33.4f), scaleX(9.8f), scaleY(34.5f), scaleX(11f), scaleY(34.5f))
        lineTo(scaleX(20f), scaleY(34.5f))
        lineTo(scaleX(28.8f), scaleY(31.2f))
        lineTo(scaleX(26.1f), scaleY(35.7f))
        cubicTo(scaleX(23.5f), scaleY(38.3f), scaleX(20f), scaleY(39.2f), scaleX(20f), scaleY(39.2f))
        close()
    }

    // Finger paths
    val finger1Path = Path().apply {
        moveTo(scaleX(13.2f), scaleY(21.5f))
        cubicTo(scaleX(11.9f), scaleY(21.5f), scaleX(10.8f), scaleY(20.4f), scaleX(10.8f), scaleY(19.1f))
        lineTo(scaleX(10.8f), scaleY(8.7f))
        cubicTo(scaleX(10.8f), scaleY(6.9f), scaleX(12f), scaleY(6.2f), scaleX(13.2f), scaleY(6.2f))
        cubicTo(scaleX(14.4f), scaleY(6.2f), scaleX(15.7f), scaleY(6.9f), scaleX(15.7f), scaleY(8.7f))
        lineTo(scaleX(15.7f), scaleY(19.1f))
        cubicTo(scaleX(15.7f), scaleY(20.4f), scaleX(14.6f), scaleY(21.5f), scaleX(13.2f), scaleY(21.5f))
        close()
    }

    val finger2Path = Path().apply {
        moveTo(scaleX(20.3f), scaleY(16.9f))
        cubicTo(scaleX(18.1f), scaleY(16.9f), scaleX(18.1f), scaleY(15.9f), scaleX(18.1f), scaleY(15.9f))
        lineTo(scaleX(18.1f), scaleY(6.6f))
        cubicTo(scaleX(18.1f), scaleY(4.8f), scaleX(19.8f), scaleY(3.8f), scaleX(20.5f), scaleY(3.8f))
        cubicTo(scaleX(21.2f), scaleY(3.8f), scaleX(22.9f), scaleY(4.8f), scaleX(22.9f), scaleY(6.6f))
        lineTo(scaleX(22.9f), scaleY(12.6f))
        cubicTo(scaleX(22.9f), scaleY(15.9f), scaleX(20.3f), scaleY(16.9f), scaleX(20.3f), scaleY(16.9f))
        close()
    }

    // Central circle/button
    val centerCirclePath = Path().apply {
        addOval(
            androidx.compose.ui.geometry.Rect(
                center = androidx.compose.ui.geometry.Offset(scaleX(25.6f), scaleY(24.3f)),
                radius = 3.6f * scale
            )
        )
    }

    // Sound wave paths
    val soundWave1Path = Path().apply {
        // First sound wave arc
        addArc(
            oval = androidx.compose.ui.geometry.Rect(
                center = androidx.compose.ui.geometry.Offset(scaleX(25.6f), scaleY(24.3f)),
                radius = 8f * scale
            ),
            startAngleDegrees = -45f,
            sweepAngleDegrees = 90f
        )
    }

    val soundWave2Path = Path().apply {
        // Second sound wave arc
        addArc(
            oval = androidx.compose.ui.geometry.Rect(
                center = androidx.compose.ui.geometry.Offset(scaleX(25.6f), scaleY(24.3f)),
                radius = 14f * scale
            ),
            startAngleDegrees = -60f,
            sweepAngleDegrees = 120f
        )
    }

    // Animate hand path
    if (handProgress > 0f) {
        drawAnimatedPath(
            path = handPath,
            progress = handProgress,
            color = primaryColor,
            strokeWidth = 3.dp.toPx()
        )
    }

    // Animate finger paths
    if (fingerProgress > 0f) {
        drawAnimatedPath(
            path = finger1Path,
            progress = fingerProgress,
            color = primaryColor,
            strokeWidth = 2.dp.toPx()
        )

        drawAnimatedPath(
            path = finger2Path,
            progress = fingerProgress,
            color = primaryColor,
            strokeWidth = 2.dp.toPx()
        )
    }

    // Animate sound waves
    if (soundWave1Progress > 0f) {
        drawAnimatedPath(
            path = soundWave1Path,
            progress = soundWave1Progress,
            color = primaryColor,
            strokeWidth = 4.dp.toPx()
        )
    }

    if (soundWave2Progress > 0f) {
        drawAnimatedPath(
            path = soundWave2Path,
            progress = soundWave2Progress,
            color = primaryColor,
            strokeWidth = 4.dp.toPx()
        )
    }

    // Fill the paths when animation is complete
    if (fillAlpha > 0f) {
        drawPath(
            path = handPath,
            color = primaryColor.copy(alpha = fillAlpha)
        )

        drawPath(
            path = finger1Path,
            color = primaryColor.copy(alpha = fillAlpha)
        )

        drawPath(
            path = finger2Path,
            color = primaryColor.copy(alpha = fillAlpha)
        )

        // Center circle with accent color
        drawPath(
            path = centerCirclePath,
            color = accentColor.copy(alpha = fillAlpha)
        )
    }
}

// Helper function to draw animated paths
private fun DrawScope.drawAnimatedPath(
    path: Path,
    progress: Float,
    color: Color,
    strokeWidth: Float
) {
    if (progress <= 0f) return

    val pathMeasure = PathMeasure()
    pathMeasure.setPath(path, false)
    val pathLength = pathMeasure.length
    val animatedPath = Path()

    if (pathLength > 0f) {
        pathMeasure.getSegment(
            startDistance = 0f,
            stopDistance = pathLength * progress,
            destination = animatedPath,
            startWithMoveTo = true
        )

        drawPath(
            path = animatedPath,
            color = color,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round
            )
        )
    }
}