package com.example.icara.helper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import androidx.core.graphics.createBitmap
import java.io.ByteArrayOutputStream

class HandLandmarkerHelper(
    val context: Context,
    var currentHardware: Delegate = Delegate.GPU,
    val handLandmarkerHelperListener: LandmarkerListener,
) {
    private var handLandmarker: HandLandmarker? = null

    // FPS limiting vars
    var lastProcessTime = 0L
    val targetFPS = 30
    val frameInterval = 1000L / targetFPS

    data class ResultBundle(
        val results: List<HandLandmarkerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
    )

    interface LandmarkerListener {
        fun onError(error: String)
        fun onResults(resultBundle: ResultBundle)
    }

    init {
        setupHandLandmarker()
    }

    // setup hand landmarker model
    fun setupHandLandmarker() {
        val baseOptBuilder = BaseOptions.builder()

        when (currentHardware) {
            Delegate.CPU -> {
                baseOptBuilder.setDelegate(Delegate.CPU)
            }
            Delegate.GPU -> {
                baseOptBuilder.setDelegate(Delegate.GPU)
            }
        }

        baseOptBuilder.setModelAssetPath("hand_landmarker.task")

        val baseOpt = baseOptBuilder.build()

        val optBuilder = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOpt)
            .setMinHandDetectionConfidence(0.5F)
            .setMinTrackingConfidence(0.5F)
            .setMinHandPresenceConfidence(0.5F)
            .setNumHands(2)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, input ->
                val finishTimeMs = SystemClock.uptimeMillis()
                val inferenceTime = finishTimeMs - result.timestampMs()
                handLandmarkerHelperListener.onResults(
                    ResultBundle(
                        listOf(result),
                        inferenceTime,
                        input.height,
                        input.width
                    )
                )
            }
            .setErrorListener { e ->
                handLandmarkerHelperListener.onError(e.message ?: "Unknown error occurred")
            }

        try {
            handLandmarker = HandLandmarker.createFromOptions(context, optBuilder.build())
        } catch (e: IllegalStateException) {
            handLandmarkerHelperListener.onError(
                "Hand Landmarker failed to initialize. See error logs for " +
                        "details"
            )
            Log.e(
                "MP", "MediaPipe failed to load the task with error: " + e
                    .message
            )
        } catch (e: RuntimeException) {
            // This occurs if the model being used does not support GPU
            handLandmarkerHelperListener.onError(
                "Hand Landmarker failed to initialize. See error logs for details"
            )
            Log.e(
                "MP",
                "Image classifier failed to load model with error: " + e.message
            )
        }
    }

    // convert proxy to bitmap
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            when (imageProxy.format) {
                ImageFormat.YUV_420_888 -> {
                    // Optimized YUV conversion
                    val yBuffer = imageProxy.planes[0].buffer
                    val uBuffer = imageProxy.planes[1].buffer
                    val vBuffer = imageProxy.planes[2].buffer

                    val ySize = yBuffer.remaining()
                    val uSize = uBuffer.remaining()
                    val vSize = vBuffer.remaining()

                    val nv21 = ByteArray(ySize + uSize + vSize)

                    yBuffer.get(nv21, 0, ySize)
                    vBuffer.get(nv21, ySize, vSize)
                    uBuffer.get(nv21, ySize + vSize, uSize)

                    val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
                    val out = ByteArrayOutputStream()
                    // Reduced JPEG quality for faster conversion (still good enough for ML)
                    yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 85, out)
                    val jpegArray = out.toByteArray()
                    android.graphics.BitmapFactory.decodeByteArray(jpegArray, 0, jpegArray.size)
                }
                else -> {
                    // For other formats, try direct buffer conversion with proper size calculation
                    val buffer = imageProxy.planes[0].buffer
                    val pixelStride = imageProxy.planes[0].pixelStride
                    val rowStride = imageProxy.planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * imageProxy.width

                    val bitmapWidth = imageProxy.width + rowPadding / pixelStride
                    val bitmap = createBitmap(bitmapWidth, imageProxy.height)
                    bitmap.copyPixelsFromBuffer(buffer)

                    // Crop to actual image size if there was padding
                    if (rowPadding > 0) {
                        Bitmap.createBitmap(bitmap, 0, 0, imageProxy.width, imageProxy.height)
                    } else {
                        bitmap
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("HandLandmarkerHelper", "Error converting ImageProxy to Bitmap: ${e.message}")
            null
        }
    }

    fun detectLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        if (handLandmarker == null) {
            imageProxy.close()
            return
        }

        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastProcessTime < frameInterval) {
            imageProxy.close() // drop exceeding frames
            return
        }
        lastProcessTime = SystemClock.uptimeMillis()

        // benchmark image conversion time
        val startTime = SystemClock.uptimeMillis()
        // Convert ImageProxy to Bitmap properly
        val bitmap = imageProxyToBitmap(imageProxy)

        if (bitmap == null) {
            imageProxy.close()
            return
        }

        // benchmark conversion time
        val conversionTime = SystemClock.uptimeMillis() - startTime
        Log.d("PERFORMANCE", "Bitmap conversion time: ${conversionTime}ms")

        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

            if (isFrontCamera) {
                postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
            }
        }

        val rotatedBitmap = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        val frameTime = SystemClock.uptimeMillis()
        handLandmarker?.detectAsync(mpImage, frameTime)
        imageProxy.close()
    }

    fun clearHandLandmarker() {
        handLandmarker?.close()
        handLandmarker = null
    }
}
