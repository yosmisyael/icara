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
    val pathProgress = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    Log.d("SplashScreen", "SplashScreen composable started")

    LaunchedEffect(Unit) {
        Log.d("SplashScreen", "LaunchedEffect started")

        // Start logo drawing animation
        pathProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 2000,
                easing = FastOutSlowInEasing
            )
        )

        // Fade in the logo fill
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 500,
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
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated Logo
            Canvas(
                modifier = Modifier.size(120.dp)
            ) {
                drawAnimatedLogo(
                    pathProgress = pathProgress.value,
                    fillAlpha = alpha.value,
                    primaryColor = Color(0xFF00796B)
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
                color = Color(0xFF00796B),
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

// Custom drawing function for animated logo
private fun DrawScope.drawAnimatedLogo(
    pathProgress: Float,
    fillAlpha: Float,
    primaryColor: Color
) {
    val centerX = size.width / 2
    val centerY = size.height / 2
    val radius = size.minDimension / 3

    // Create a simple "i" logo path (you can replace this with your actual logo path)
    val letterPath = Path().apply {
        // Draw the dot of "i"
        addOval(
            androidx.compose.ui.geometry.Rect(
                left = centerX - radius * 0.15f,
                top = centerY - radius * 1.2f,
                right = centerX + radius * 0.15f,
                bottom = centerY - radius * 0.9f
            )
        )

        // Draw the body of "i"
        addRect(
            androidx.compose.ui.geometry.Rect(
                left = centerX - radius * 0.15f,
                top = centerY - radius * 0.6f,
                right = centerX + radius * 0.15f,
                bottom = centerY + radius * 0.8f
            )
        )
    }

    // Draw the path being animated
    val pathMeasure = androidx.compose.ui.graphics.PathMeasure()
    pathMeasure.setPath(letterPath, false)
    val pathLength = pathMeasure.length
    val animatedPath = Path()

    if (pathProgress > 0f) {
        pathMeasure.getSegment(
            startDistance = 0f,
            stopDistance = pathLength * pathProgress,
            destination = animatedPath,
            startWithMoveTo = true
        )

        // Draw the stroke animation
        drawPath(
            path = animatedPath,
            color = primaryColor,
            style = Stroke(
                width = 8.dp.toPx(),
                cap = StrokeCap.Round
            )
        )
    }

    // Draw the filled logo after path animation
    if (fillAlpha > 0f) {
        drawPath(
            path = letterPath,
            color = primaryColor.copy(alpha = fillAlpha)
        )
    }

    // Optional: Add a circle background
    drawCircle(
        color = primaryColor.copy(alpha = 0.1f * fillAlpha),
        radius = radius * 1.3f,
        center = androidx.compose.ui.geometry.Offset(centerX, centerY)
    )
}