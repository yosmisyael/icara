package com.example.icara.ui.screens.talk

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Offset
import com.example.icara.helper.HandLandmarkerHelper
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker

@Composable
fun LandmarkOverlay(
    resultBundle: HandLandmarkerHelper.ResultBundle,
    modifier: Modifier = Modifier
) {
    val handLandmarkerResult = resultBundle.results.firstOrNull() ?: return
    val imageHeight = resultBundle.inputImageHeight
    val imageWidth = resultBundle.inputImageWidth

    Canvas(modifier = modifier) {
        // PreviewView typically uses CENTER_CROP scaling, so we match that behavior
        val scaleX = size.width / imageWidth
        val scaleY = size.height / imageHeight

        // Use the larger scale factor to fill the entire preview area (CENTER_CROP behavior)
        val scale = kotlin.math.max(scaleX, scaleY)

        // Calculate the scaled image dimensions
        val scaledImageWidth = imageWidth * scale
        val scaledImageHeight = imageHeight * scale

        // Calculate offsets to center the scaled image
        val offsetX = (size.width - scaledImageWidth) / 2f
        val offsetY = (size.height - scaledImageHeight) / 2f

        val handConnections = HandLandmarker.HAND_CONNECTIONS.map { connection ->
            Pair(connection.start(), connection.end())
        }

        handLandmarkerResult.landmarks().forEach { landmarkList ->
            drawConnections(
                landmarkList,
                handConnections,
                Color.Yellow,
                scale,
                offsetX,
                offsetY,
                scaledImageWidth,
                scaledImageHeight
            )
            drawLandmarks(
                landmarkList,
                Color.Yellow,
                scale,
                offsetX,
                offsetY,
                scaledImageWidth,
                scaledImageHeight
            )
        }
    }
}

// Helper function to draw connections between landmarks
private fun DrawScope.drawConnections(
    landmarkList: List<NormalizedLandmark>,
    connections: List<Pair<Int, Int>>,
    color: Color,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    scaledImageWidth: Float,
    scaledImageHeight: Float
) {
    connections.forEach { (startIdx, endIdx) ->
        if (startIdx < landmarkList.size && endIdx < landmarkList.size) {
            val start = landmarkList[startIdx].toOffset(scale, offsetX, offsetY, scaledImageWidth, scaledImageHeight)
            val end = landmarkList[endIdx].toOffset(scale, offsetX, offsetY, scaledImageWidth, scaledImageHeight)

            drawLine(
                color = color,
                start = start,
                end = end,
                strokeWidth = 3.0f
            )
        }
    }
}

// Helper function to draw the landmark points
private fun DrawScope.drawLandmarks(
    landmarkList: List<NormalizedLandmark>,
    color: Color,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    scaledImageWidth: Float,
    scaledImageHeight: Float
) {
    landmarkList.forEach { landmark ->
        drawCircle(
            color = color,
            radius = 6.0f,
            center = landmark.toOffset(scale, offsetX, offsetY, scaledImageWidth, scaledImageHeight)
        )
    }
}

// Helper function to convert normalized landmark coordinates to screen coordinates
private fun NormalizedLandmark.toOffset(
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    scaledImageWidth: Float,
    scaledImageHeight: Float
): Offset {
    val x = offsetX + (this.x() * scaledImageWidth)
    val y = offsetY + (this.y() * scaledImageHeight)

    return Offset(x = x, y = y)
}

