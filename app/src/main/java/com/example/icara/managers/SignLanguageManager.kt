package com.example.icara.managers

import android.content.Context
import android.util.Log
import com.example.icara.data.state.SignLanguageState
import com.example.icara.helper.HandLandmarkerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.flex.FlexDelegate
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Manages sign language recognition functionality with smart gesture detection
 */
class SignLanguageManager(
    private val coroutineScope: CoroutineScope
) : HandLandmarkerHelper.LandmarkerListener {

    private val _signLanguageState = MutableStateFlow(SignLanguageState())
    val signLanguageState: StateFlow<SignLanguageState> = _signLanguageState.asStateFlow()

    // Threading & Coroutines
    private lateinit var cameraExecutor: ExecutorService
    private val landmarkDataChannel = Channel<List<Float>>(Channel.CONFLATED)

    // MediaPipe & TFLite
    private lateinit var handLandmarkerHelper: HandLandmarkerHelper
    private var liteRtInterpreter: Interpreter? = null
    private var initializationError: String? = null

    // LSTM Model & Data Collection
    private val landmarkSequence = mutableListOf<Float>()
    private val SEQUENCE_LENGTH = 30
    private val FEATURES_PER_FRAME = 126 // 2 hands * 21 landmarks * 3 coordinates
    private var frameCounter = 0
    private val PREDICTION_INTERVAL = 30 // Reduced from 5 to 3 for even faster response
    private val labels = listOf(
        "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
        "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"
    )

    // Improved gesture detection using predictions instead of landmarks
    private var previousLandmarks: List<Float>? = null
    private var stableGestureCounter = 0
    private var handsOutOfFrameCounter = 0
    private val STABLE_GESTURE_THRESHOLD = 3 // Further reduced from 5 to 3
    private val GESTURE_CHANGE_THRESHOLD = 0.05f
    private val OUT_OF_FRAME_THRESHOLD = 10 // Reduced from 15 to 10 for faster spacing
    private var lastPredictedSign: String = ""
    private var gestureStabilized = false

    // FIXED: Improved prediction-based gesture tracking
    private var lastConfirmedPrediction: String = ""
    private var currentPredictionConsistencyCounter = 0
    private val PREDICTION_CONSISTENCY_THRESHOLD = 2 // Keep at 2 for balance of speed and accuracy
    private var tempPredictionBuffer: String = ""

    // FIXED: Better spacing state management
    private var justCompletedSpacing = false // NEW: Track if spacing just completed
    private var spacingCompletedTime = 0L
    private val POST_SPACING_WINDOW_MS = 100L // Short window after spacing where repeats are allowed

    // More strict landmark-based detection to prevent false gesture changes
    private val MAJOR_GESTURE_CHANGE_THRESHOLD = 0.45f // Increased to be much less sensitive

    // inference detection
    private var hasDetectedHandsYet = false
    private var inferenceStarted = false

    fun initialize(context: Context) {
        cameraExecutor = Executors.newSingleThreadExecutor()
        setupLiteRtInterpreter(context)
        handLandmarkerHelper = HandLandmarkerHelper(
            context = context,
            handLandmarkerHelperListener = this
        )
        startInferenceLoop()

        _signLanguageState.value = _signLanguageState.value.copy(isInitialized = true)
    }

    fun getHandLandmarkerHelper(): HandLandmarkerHelper = handLandmarkerHelper
    fun getCameraExecutor(): ExecutorService = cameraExecutor

    override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
        _signLanguageState.value = _signLanguageState.value.copy(
            handLandmarkerResult = resultBundle
        )

        val handsResult = resultBundle.results.first()
        val landmarksForFrame = mutableListOf<Float>()
        val emptyHandLandmarks = FloatArray(63) { 0f }.toList()
        val hasHandsInCurrentFrame = handsResult.landmarks().isNotEmpty()

        // Handle hands going out of frame for space detection
        if (!hasHandsInCurrentFrame) {
            handsOutOfFrameCounter++

            // If hands were out long enough, add space (only if we have existing predictions)
            if (handsOutOfFrameCounter >= OUT_OF_FRAME_THRESHOLD && !justCompletedSpacing) {
                val currentPrediction = _signLanguageState.value.predictedSign
                if (currentPrediction.isNotEmpty() &&
                    currentPrediction != "Ready" &&
                    !currentPrediction.endsWith(" ")) {
                    _signLanguageState.value = _signLanguageState.value.copy(
                        predictedSign = "$currentPrediction "
                    )
                    Log.d("SignLanguageManager", "Space added to prediction")
                }

                // FIXED: Mark spacing as completed and reset prediction tracking
                justCompletedSpacing = true
                spacingCompletedTime = System.currentTimeMillis()
                resetPredictionTrackingForSpacing()
                handsOutOfFrameCounter = 0
            }

            // Reset landmark-based detection when hands leave
            resetLandmarkDetection()
            return
        } else {
            handsOutOfFrameCounter = 0 // Reset out-of-frame counter when hands are detected
        }

        if (hasHandsInCurrentFrame && !hasDetectedHandsYet) {
            hasDetectedHandsYet = true
            inferenceStarted = true
            // Clear info text when hands are first detected
            _signLanguageState.value = _signLanguageState.value.copy(
                predictedSign = "",
                error = null
            )
        }

        // Don't process landmarks or run inference yet
        if (!hasDetectedHandsYet || !hasHandsInCurrentFrame) {
            return
        }

        if (handsResult.landmarks().isEmpty()) {
            landmarksForFrame.addAll(emptyHandLandmarks)
            landmarksForFrame.addAll(emptyHandLandmarks)
        } else {
            val firstHand = handsResult.landmarks()[0]
            landmarksForFrame.addAll(firstHand.flatMap { listOf(it.x(), it.y(), it.z()) })
            if (handsResult.landmarks().size > 1) {
                val secondHand = handsResult.landmarks()[1]
                landmarksForFrame.addAll(secondHand.flatMap { listOf(it.x(), it.y(), it.z()) })
            } else {
                landmarksForFrame.addAll(emptyHandLandmarks)
            }
        }

        // FIXED: Much more strict gesture change detection - only reset on truly major changes
        val hasMajorGestureChange = hasMajorGestureChange(landmarksForFrame)

        if (hasMajorGestureChange) {
            // Only reset on truly major landmark changes
            resetLandmarkDetection()
            resetPredictionTrackingForGestureChange()
            Log.d("SignLanguageManager", "Major gesture change detected - full reset")
        } else {
            stableGestureCounter++
            if (stableGestureCounter >= STABLE_GESTURE_THRESHOLD) {
                gestureStabilized = true
            }
        }

        // Update previous landmarks for next comparison
        previousLandmarks = landmarksForFrame.toList()

        landmarkSequence.addAll(landmarksForFrame)
        while (landmarkSequence.size > SEQUENCE_LENGTH * FEATURES_PER_FRAME) {
            landmarkSequence.subList(0, FEATURES_PER_FRAME).clear()
        }

        frameCounter++
        if (frameCounter >= PREDICTION_INTERVAL &&
            gestureStabilized &&
            landmarkSequence.size == SEQUENCE_LENGTH * FEATURES_PER_FRAME
        ) {
            frameCounter = 0
            if (landmarkSequence.size == SEQUENCE_LENGTH * FEATURES_PER_FRAME) {
                landmarkDataChannel.trySend(landmarkSequence.toList())
            }
        }
    }

    private fun resetLandmarkDetection() {
        previousLandmarks = null
        stableGestureCounter = 0
        gestureStabilized = false
        Log.d("SignLanguageManager", "Landmark detection reset")
    }

    // FIXED: Different reset functions for different scenarios
    private fun resetPredictionTrackingForSpacing() {
        currentPredictionConsistencyCounter = 0
        tempPredictionBuffer = ""
        // DON'T reset lastConfirmedPrediction - this allows same gesture after spacing
        Log.d("SignLanguageManager", "Prediction tracking reset for spacing")
    }

    private fun resetPredictionTrackingForGestureChange() {
        currentPredictionConsistencyCounter = 0
        tempPredictionBuffer = ""
        lastConfirmedPrediction = "" // Reset this for gesture changes
        Log.d("SignLanguageManager", "Prediction tracking reset for gesture change")
    }

    private fun hasMajorGestureChange(currentLandmarks: List<Float>): Boolean {
        val previous = previousLandmarks ?: return true // First frame is always considered changed

        if (previous.size != currentLandmarks.size) return true

        // FIXED: More lenient gesture change detection
        var totalDistance = 0f
        var significantChanges = 0

        for (i in currentLandmarks.indices step 3) {
            val dx = abs(currentLandmarks[i] - previous[i])
            val dy = abs(currentLandmarks[i + 1] - previous[i + 1])
            val dz = abs(currentLandmarks[i + 2] - previous[i + 2])
            val distance = (dx + dy + dz) / 3f

            totalDistance += distance

            // Count landmarks with significant individual changes
            if (distance > MAJOR_GESTURE_CHANGE_THRESHOLD) {
                significantChanges++
            }
        }

        val averageDistance = totalDistance / (currentLandmarks.size / 3)

        // FIXED: Much more strict thresholds to prevent false gesture changes
        return averageDistance > MAJOR_GESTURE_CHANGE_THRESHOLD && significantChanges > 12 // Increased from 8 to 12
    }

    override fun onError(error: String) {
        _signLanguageState.value = _signLanguageState.value.copy(
            predictedSign = error,
            error = error
        )
        Log.e("SignLanguageManager", "Hand Landmarker Error: $error")
    }

    private fun startInferenceLoop() {
        coroutineScope.launch(Dispatchers.Default) {
            landmarkDataChannel.receiveAsFlow().collect { sequence ->
                runInference(sequence)
            }
        }
    }

    private fun runInference(sequenceToProcess: List<Float>) {
        if (initializationError != null) {
            _signLanguageState.value = _signLanguageState.value.copy(
                predictedSign = initializationError!!,
                error = initializationError
            )
            return
        }
        if (liteRtInterpreter == null) {
            val errorMsg = "Interpreter not ready"
            _signLanguageState.value = _signLanguageState.value.copy(
                predictedSign = errorMsg,
                error = errorMsg
            )
            return
        }

        val inputBuffer = ByteBuffer.allocateDirect(1 * SEQUENCE_LENGTH * FEATURES_PER_FRAME * 4).apply {
            order(ByteOrder.nativeOrder())
            for (value in sequenceToProcess) {
                putFloat(value)
            }
        }
        val outputBuffer = Array(1) { FloatArray(labels.size) }

        try {
            liteRtInterpreter?.run(inputBuffer, outputBuffer)
        } catch (e: Exception) {
            val errorMsg = "Inference Error"
            _signLanguageState.value = _signLanguageState.value.copy(
                predictedSign = errorMsg,
                error = errorMsg
            )
            Log.e("LiteRTError", "Error during inference: ", e)
            return
        }

        val probabilities = outputBuffer[0]
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
        if (maxIndex != -1 && maxIndex < labels.size) {
            val newPrediction = labels[maxIndex]

            // FIXED: Improved prediction handling
            handleNewPrediction(newPrediction)
        }
    }

    private fun handleNewPrediction(newPrediction: String) {
        // Check if this prediction is the same as our temporary buffer
        if (newPrediction == tempPredictionBuffer) {
            currentPredictionConsistencyCounter++
        } else {
            // Different prediction, reset consistency tracking
            tempPredictionBuffer = newPrediction
            currentPredictionConsistencyCounter = 1
        }

        // Check if we're in post-spacing window
        val timeSinceSpacing = System.currentTimeMillis() - spacingCompletedTime
        val inPostSpacingWindow = justCompletedSpacing && timeSinceSpacing <= POST_SPACING_WINDOW_MS

        Log.d("SignLanguageManager", "Prediction: $newPrediction, Consistency: $currentPredictionConsistencyCounter/$PREDICTION_CONSISTENCY_THRESHOLD, JustSpaced: $justCompletedSpacing, InWindow: $inPostSpacingWindow, LastConfirmed: $lastConfirmedPrediction")

        // Only accept the prediction if it's been consistent for enough frames
        if (currentPredictionConsistencyCounter >= PREDICTION_CONSISTENCY_THRESHOLD) {
            // FIXED: Strict duplicate prevention - only allow repeats immediately after spacing
            val canAcceptPrediction = if (inPostSpacingWindow) {
                // In post-spacing window: allow any prediction (including repeats)
                true
            } else {
                // Normal operation: STRICT - no repeats allowed
                newPrediction != lastConfirmedPrediction
            }

            if (canAcceptPrediction) {
                val currentPrediction = _signLanguageState.value.predictedSign
                val updatedPrediction = if (currentPrediction.isEmpty() || currentPrediction == "Ready") {
                    newPrediction
                } else {
                    "$currentPrediction$newPrediction" // No space between letters unless hands go out of frame
                }

                _signLanguageState.value = _signLanguageState.value.copy(
                    predictedSign = updatedPrediction,
                    error = null
                )

                // ALWAYS update lastConfirmedPrediction after successful acceptance
                lastConfirmedPrediction = newPrediction

                Log.d("SignLanguageManager", "New prediction confirmed: $newPrediction -> lastConfirmed updated to: $lastConfirmedPrediction")

                // Exit post-spacing state if we were in it
                if (inPostSpacingWindow) {
                    justCompletedSpacing = false
                    Log.d("SignLanguageManager", "Exited post-spacing window after successful prediction")
                }

                // IMPORTANT: Reset consistency counter to prevent immediate re-acceptance
                currentPredictionConsistencyCounter = 0
                tempPredictionBuffer = ""

            } else {
                Log.d("SignLanguageManager", "Prediction $newPrediction BLOCKED - same as last confirmed ($lastConfirmedPrediction) and not in post-spacing window")
            }
        }

        // Clean up old spacing state if window expired
        if (justCompletedSpacing && timeSinceSpacing > POST_SPACING_WINDOW_MS) {
            justCompletedSpacing = false
            Log.d("SignLanguageManager", "Post-spacing window expired")
        }
    }

    private fun setupLiteRtInterpreter(context: Context) {
        try {
            val assetFileDescriptor = context.assets.openFd("best_model_lstm_hands.tflite")
            val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = fileInputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val options = Interpreter.Options()
            val compatList = CompatibilityList()
            options.addDelegate(FlexDelegate())
            Log.i("SignLanguageManager", "Flex delegate added.")

            var gpuDelegate: GpuDelegate? = null
            if (compatList.isDelegateSupportedOnThisDevice) {
                val delegateOptions = compatList.bestOptionsForThisDevice
                gpuDelegate = GpuDelegate(delegateOptions)
                options.addDelegate(gpuDelegate)
                Log.i("SignLanguageManager", "GPU delegate is supported, attempting to apply.")
            } else {
                options.setNumThreads(4)
                Log.w("SignLanguageManager", "GPU delegate not supported. Using CPU with 4 threads.")
            }

            try {
                liteRtInterpreter = Interpreter(modelBuffer, options)
            } catch (e: Exception) {
                if (gpuDelegate != null) {
                    Log.w("SignLanguageManager", "Failed to initialize with GPU delegate. Retrying without it. Error: ${e.message}")
                    val fallbackOptions = Interpreter.Options().apply {
                        addDelegate(FlexDelegate())
                        setNumThreads(4)
                    }
                    liteRtInterpreter = Interpreter(modelBuffer, fallbackOptions)
                    gpuDelegate.close()
                } else {
                    throw e
                }
            }
            _signLanguageState.value = _signLanguageState.value.copy(
                predictedSign = "Ready",
                error = null
            )
            initializationError = null
        } catch (e: Exception) {
            val errorMessage = "Model load error: ${e.message}"
            Log.e("LiteRTError", errorMessage, e)
            _signLanguageState.value = _signLanguageState.value.copy(
                predictedSign = errorMessage,
                error = errorMessage
            )
            initializationError = errorMessage
        }
    }

    fun cleanup() {
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
        if (::handLandmarkerHelper.isInitialized) {
            handLandmarkerHelper.clearHandLandmarker()
        }
        liteRtInterpreter?.close()
        landmarkDataChannel.close()
        Log.d("SignLanguageManager", "SignLanguageManager cleaned up and all resources released.")
    }
}