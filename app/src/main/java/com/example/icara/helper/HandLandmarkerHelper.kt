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
    var currentHardware: Delegate = Delegate.CPU,
    val handLandmarkerHelperListener: LandmarkerListener,
) {
    private var handLandmarker: HandLandmarker? = null

    // FPS limiting vars
    var lastProcessTime = 0L
    val targetFPS = 30
    val frameInterval = 1000L / targetFPS

    // pre-allocated transformation matrix to avoid garbage collection
    private val transformMatrix = Matrix()

    // frame skipping counter for additional performance boost
    private var frameCounter = 0
    private val PROCESS_EVERY_N_FRAMES = 2 // Process every 2nd frame

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
            .setMinHandDetectionConfidence(0.6F)
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
                    convertYuvToBitmap(imageProxy)
                }
                else -> {
                    // For other formats, try direct buffer conversion with proper size calculation
                    val buffer = imageProxy.planes[0].buffer
                    val pixelStride = imageProxy.planes[0].pixelStride
                    val rowStride = imageProxy.planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * imageProxy.width

                    val bitmap = if (rowPadding == 0) {
                        // No padding, direct conversion
                        createBitmap(imageProxy.width, imageProxy.height).apply {
                            copyPixelsFromBuffer(buffer)
                        }
                    } else {
                        // Handle row padding
                        val bitmapWidth = imageProxy.width + rowPadding / pixelStride
                        val paddedBitmap = createBitmap(bitmapWidth, imageProxy.height).apply {
                            copyPixelsFromBuffer(buffer)
                        }
                        Bitmap.createBitmap(paddedBitmap, 0, 0, imageProxy.width, imageProxy.height)
                    }
                    bitmap
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

        // Frame skipping for additional performance boost
        /* frameCounter++
        if (frameCounter % PROCESS_EVERY_N_FRAMES != 0) {
            imageProxy.close()
            return
        } */

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

        // reuse transformation matrix
        transformMatrix.reset()
        transformMatrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

        if (isFrontCamera) {
            transformMatrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
        }

        val rotatedBitmap = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, transformMatrix, false // Set to false for better performance
        )

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        val frameTime = SystemClock.uptimeMillis()

        try {
            handLandmarker?.detectAsync(mpImage, frameTime)
        } catch (e: Exception) {
            Log.e("HandLandmarkerHelper", "Error during detection: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    fun clearHandLandmarker() {
        handLandmarker?.close()
        handLandmarker = null
    }

    /**
     * Function to perform direct YUV to Bitmap conversion without JPEG intermediate step
     */
    private fun convertYuvToBitmap(imageProxy: ImageProxy): Bitmap? {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        // Convert YUV to RGB directly
        val yuvBytes = ByteArray(ySize + uSize + vSize)
        yBuffer.get(yuvBytes, 0, ySize)
        uBuffer.get(yuvBytes, ySize, uSize)
        vBuffer.get(yuvBytes, ySize + uSize, vSize)

        val width = imageProxy.width
        val height = imageProxy.height
        val pixels = IntArray(width * height)

        // Simple YUV to RGB conversion (faster than JPEG route)
        convertYuvToRgb(yuvBytes, pixels, width, height)

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Function to convert YUV420 to RGB conversion
     */
    private fun convertYuvToRgb(yuv: ByteArray, rgb: IntArray, width: Int, height: Int) {
        val frameSize = width * height
        var j = 0
        var yp = 0

        for (y in 0 until height) {
            var uvp = frameSize + (y shr 1) * width
            var u = 0
            var v = 0

            for (x in 0 until width) {
                val yValue = (0xff and yuv[yp].toInt()) - 16
                if (yValue < 0) continue

                if ((x and 1) == 0) {
                    v = (0xff and yuv[uvp++].toInt()) - 128
                    u = (0xff and yuv[uvp++].toInt()) - 128
                }

                val y1192 = 1192 * yValue
                var r = (y1192 + 1634 * v)
                var g = (y1192 - 833 * v - 400 * u)
                var b = (y1192 + 2066 * u)

                r = if (r < 0) 0 else if (r > 262143) 262143 else r
                g = if (g < 0) 0 else if (g > 262143) 262143 else g
                b = if (b < 0) 0 else if (b > 262143) 262143 else b

                rgb[j++] = (-0x1000000 or ((r shl 6) and 0xff0000) or
                        ((g shr 2) and 0xff00) or ((b shr 10) and 0xff))
                yp++
            }
        }
    }

}
