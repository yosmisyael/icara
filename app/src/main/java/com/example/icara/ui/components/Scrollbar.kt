package com.example.icara.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun AnimatedScrollbar(
    modifier: Modifier = Modifier,
    scrollState: ScrollState,
    thumbColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    isVisible: Boolean = true
) {
    val maxValue = scrollState.maxValue
    val currentValue = scrollState.value
    val isScrolling by remember { derivedStateOf { scrollState.isScrollInProgress } }
    var trackHeight by remember { mutableStateOf(0f) }

    val hasScrollableContent = maxValue > 0
    val isScrolled = currentValue > 0
    val canScroll = scrollState.canScrollForward || scrollState.canScrollBackward

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isVisible && (isScrolling || hasScrollableContent || isScrolled || canScroll)) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isScrolling) 150 else 1000,
            delayMillis = if (isScrolling) 0 else 500 // Reduced delay for faster response
        ),
        label = "scrollbar_alpha"
    )

    // Don't render if no scrollable content and not scrolled
    if (!hasScrollableContent && !isScrolled && !canScroll) return

    Box(
        modifier = modifier
            .alpha(animatedAlpha)
            .background(trackColor, RoundedCornerShape(4.dp))
            .onSizeChanged { size ->
                trackHeight = size.height.toFloat()
            }
    ) {
        // Calculate thumb height based on content ratio (minimum 20%, maximum 80%)
        val contentHeight = maxValue + trackHeight // Approximate total content height
        val visibleRatio = if (contentHeight > 0) trackHeight / contentHeight else 1f
        val thumbHeight = visibleRatio.coerceIn(0.2f, 0.8f)

        // Calculate scroll progress with better precision
        val scrollProgress = if (maxValue > 0) {
            currentValue.toFloat() / maxValue.toFloat()
        } else 0f

        // Calculate thumb position - ensuring it reaches the bottom
        val availableSpace = 1f - thumbHeight
        val thumbOffset = scrollProgress * availableSpace

        val offsetY = if (trackHeight > 0) {
            (thumbOffset * trackHeight).toInt()
        } else 0

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(thumbHeight)
                .align(Alignment.TopCenter)
                .offset(y = with(LocalDensity.current) { offsetY.toDp() })
                .background(thumbColor, RoundedCornerShape(4.dp))
        )
    }
}

@Composable
fun AnimatedScrollbar(
    modifier: Modifier = Modifier,
    scrollState: ScrollState,
    thumbColor: Color,
    trackColor: Color
) {
    AnimatedScrollbar(
        modifier = modifier,
        scrollState = scrollState,
        thumbColor = thumbColor,
        trackColor = trackColor,
        isVisible = true
    )
}