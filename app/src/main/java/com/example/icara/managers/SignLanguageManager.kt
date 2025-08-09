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

    // IMPROVED: Adaptive prediction intervals
    private val PREDICTION_INTERVAL_DYNAMIC = 30 // Every frame for dynamic gestures (words)
    private val PREDICTION_INTERVAL_STATIC = 10 // Every 3 frames for static gestures (letters)
    private var currentPredictionInterval = PREDICTION_INTERVAL_STATIC

    private val labels = listOf(
        "A", "Apa", "B", "C", "D", "E", "F", "I", "J", "K", "L", "M",
        "N", "Nama", "O", "P", "Q", "R", "S", "Saya", "Terima Kasih",
        "U", "V", "W", "X", "Y", "Z"
    )

    // IMPROVED: Adaptive gesture detection
    private var previousLandmarks: List<Float>? = null
    private var stableGestureCounter = 0
    private var handsOutOfFrameCounter = 0
    private var gestureStabilized = false

    // IMPROVED: Movement-based detection
    private var isGestureDynamic = false
    private var movementHistory = mutableListOf<Float>()
    private val MOVEMENT_HISTORY_SIZE = 10
    private val DYNAMIC_MOVEMENT_THRESHOLD = 0.15f // Threshold to detect if gesture is dynamic
    private val STATIC_STABILITY_THRESHOLD = 2 // Reduced for faster static gestures
    private var dynamicProcessingCounter = 0

    private val OUT_OF_FRAME_THRESHOLD = 10

    // IMPROVED: Adaptive consistency requirements
    private var lastConfirmedPrediction: String = ""
    private var currentPredictionConsistencyCounter = 0
    private val PREDICTION_CONSISTENCY_STATIC = 2 // For static gestures (letters)
    private val PREDICTION_CONSISTENCY_DYNAMIC = 1 // For dynamic gestures (words) - faster
    private var currentConsistencyThreshold = PREDICTION_CONSISTENCY_STATIC
    private var tempPredictionBuffer: String = ""

    // Post-spacing window logic
    private var justCompletedSpacing = false
    private var spacingCompletedTime = 0L
    private val POST_SPACING_WINDOW_MS = 100L

    // IMPROVED: Adaptive gesture change detection
    private val MAJOR_GESTURE_CHANGE_STATIC = 0.45f // For detecting actual gesture changes
    private val MAJOR_GESTURE_CHANGE_DYNAMIC = 0.25f // More sensitive for dynamic gestures
    private var currentGestureChangeThreshold = MAJOR_GESTURE_CHANGE_STATIC

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

                justCompletedSpacing = true
                spacingCompletedTime = System.currentTimeMillis()
                resetPredictionTrackingForSpacing()
                handsOutOfFrameCounter = 0
            }

            resetLandmarkDetection()
            return
        } else {
            handsOutOfFrameCounter = 0
        }

        if (hasHandsInCurrentFrame && !hasDetectedHandsYet) {
            hasDetectedHandsYet = true
            inferenceStarted = true
            _signLanguageState.value = _signLanguageState.value.copy(
                predictedSign = "",
                error = null
            )
        }

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

        // IMPROVED: Detect if gesture is dynamic or static
        detectGestureType(landmarksForFrame)

        // IMPROVED: Adaptive processing based on gesture type
        val hasMajorGestureChange = hasMajorGestureChange(landmarksForFrame)

        if (hasMajorGestureChange && !isGestureDynamic) {
            // Only reset for static gestures on major changes
            resetLandmarkDetection()
            resetPredictionTrackingForGestureChange()
            Log.d("SignLanguageManager", "Major gesture change detected - full reset")
        } else if (isGestureDynamic) {
            // For dynamic gestures, don't reset - keep processing
            dynamicProcessingCounter++
            gestureStabilized = true // Always consider dynamic gestures as "ready"
            Log.d("SignLanguageManager", "Dynamic gesture processing: $dynamicProcessingCounter")
        } else {
            // Static gesture processing
            stableGestureCounter++
            if (stableGestureCounter >= STATIC_STABILITY_THRESHOLD) {
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

        // IMPROVED: Adaptive processing - always process if we have enough data
        val shouldProcess = if (isGestureDynamic) {
            // Dynamic gestures: process every frame if we have enough sequence data
            frameCounter >= currentPredictionInterval && landmarkSequence.size >= SEQUENCE_LENGTH * FEATURES_PER_FRAME
        } else {
            // Static gestures: require stability
            frameCounter >= currentPredictionInterval && gestureStabilized && landmarkSequence.size == SEQUENCE_LENGTH * FEATURES_PER_FRAME
        }

        if (shouldProcess) {
            frameCounter = 0
            landmarkDataChannel.trySend(landmarkSequence.toList())
        }
    }

    // NEW: Detect if current gesture is dynamic (movement-based) or static
    private fun detectGestureType(currentLandmarks: List<Float>) {
        val previous = previousLandmarks
        if (previous == null || previous.size != currentLandmarks.size) return

        // Calculate movement magnitude
        var totalMovement = 0f
        for (i in currentLandmarks.indices step 3) {
            val dx = abs(currentLandmarks[i] - previous[i])
            val dy = abs(currentLandmarks[i + 1] - previous[i + 1])
            val dz = abs(currentLandmarks[i + 2] - previous[i + 2])
            totalMovement += (dx + dy + dz) / 3f
        }
        val averageMovement = totalMovement / (currentLandmarks.size / 3)

        // Add to movement history
        movementHistory.add(averageMovement)
        while (movementHistory.size > MOVEMENT_HISTORY_SIZE) {
            movementHistory.removeAt(0)
        }

        // Determine if gesture is dynamic based on recent movement
        val recentAverageMovement = movementHistory.average().toFloat()
        val wasDynamic = isGestureDynamic
        isGestureDynamic = recentAverageMovement > DYNAMIC_MOVEMENT_THRESHOLD

        // Update processing parameters based on gesture type
        if (isGestureDynamic != wasDynamic) {
            if (isGestureDynamic) {
                // Switched to dynamic
                currentPredictionInterval = PREDICTION_INTERVAL_DYNAMIC
                currentConsistencyThreshold = PREDICTION_CONSISTENCY_DYNAMIC
                currentGestureChangeThreshold = MAJOR_GESTURE_CHANGE_DYNAMIC
                dynamicProcessingCounter = 0
                Log.d("SignLanguageManager", "Switched to DYNAMIC processing (movement: $recentAverageMovement)")
            } else {
                // Switched to static
                currentPredictionInterval = PREDICTION_INTERVAL_STATIC
                currentConsistencyThreshold = PREDICTION_CONSISTENCY_STATIC
                currentGestureChangeThreshold = MAJOR_GESTURE_CHANGE_STATIC
                Log.d("SignLanguageManager", "Switched to STATIC processing (movement: $recentAverageMovement)")
            }
        }
    }

    private fun resetLandmarkDetection() {
        previousLandmarks = null
        stableGestureCounter = 0
        gestureStabilized = false
        dynamicProcessingCounter = 0
        movementHistory.clear()
        isGestureDynamic = false
        // Reset to default static parameters
        currentPredictionInterval = PREDICTION_INTERVAL_STATIC
        currentConsistencyThreshold = PREDICTION_CONSISTENCY_STATIC
        currentGestureChangeThreshold = MAJOR_GESTURE_CHANGE_STATIC
        Log.d("SignLanguageManager", "Landmark detection reset")
    }

    private fun resetPredictionTrackingForSpacing() {
        currentPredictionConsistencyCounter = 0
        tempPredictionBuffer = ""
        Log.d("SignLanguageManager", "Prediction tracking reset for spacing")
    }

    private fun resetPredictionTrackingForGestureChange() {
        currentPredictionConsistencyCounter = 0
        tempPredictionBuffer = ""
        lastConfirmedPrediction = ""
        Log.d("SignLanguageManager", "Prediction tracking reset for gesture change")
    }

    private fun hasMajorGestureChange(currentLandmarks: List<Float>): Boolean {
        val previous = previousLandmarks ?: return true

        if (previous.size != currentLandmarks.size) return true

        var totalDistance = 0f
        var significantChanges = 0

        for (i in currentLandmarks.indices step 3) {
            val dx = abs(currentLandmarks[i] - previous[i])
            val dy = abs(currentLandmarks[i + 1] - previous[i + 1])
            val dz = abs(currentLandmarks[i + 2] - previous[i + 2])
            val distance = (dx + dy + dz) / 3f

            totalDistance += distance

            if (distance > currentGestureChangeThreshold) {
                significantChanges++
            }
        }

        val averageDistance = totalDistance / (currentLandmarks.size / 3)

        // IMPROVED: Use adaptive thresholds based on gesture type
        val requiredChanges = if (isGestureDynamic) 8 else 12
        return averageDistance > currentGestureChangeThreshold && significantChanges > requiredChanges
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

        Log.d("SignLanguageManager", "Prediction: $newPrediction, Consistency: $currentPredictionConsistencyCounter/$currentConsistencyThreshold, Dynamic: $isGestureDynamic, JustSpaced: $justCompletedSpacing, InWindow: $inPostSpacingWindow, LastConfirmed: $lastConfirmedPrediction")

        // IMPROVED: Use adaptive consistency threshold
        if (currentPredictionConsistencyCounter >= currentConsistencyThreshold) {
            val canAcceptPrediction = if (inPostSpacingWindow) {
                true
            } else {
                newPrediction != lastConfirmedPrediction
            }

            if (canAcceptPrediction) {
                val currentPrediction = _signLanguageState.value.predictedSign
                val updatedPrediction = if (currentPrediction.isEmpty() || currentPrediction == "Ready") {
                    newPrediction
                } else {
                    val separator = if (newPrediction.length > 1) " " else ""
                    "$currentPrediction$separator$newPrediction"
                }

                _signLanguageState.value = _signLanguageState.value.copy(
                    predictedSign = updatedPrediction,
                    error = null
                )

                lastConfirmedPrediction = newPrediction

                Log.d("SignLanguageManager", "New prediction confirmed: $newPrediction -> lastConfirmed updated to: $lastConfirmedPrediction")

                if (inPostSpacingWindow) {
                    justCompletedSpacing = false
                    Log.d("SignLanguageManager", "Exited post-spacing window after successful prediction")
                }

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
            val assetFileDescriptor = context.assets.openFd("best_model_lstm_hands_v2.tflite")
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