package com.example.icara.viewmodels

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.example.icara.helper.HandLandmarkerHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.Executors

// A dummy data class to match the uiState property in your TalkScreen
data class AudioUiState(
    val transcriptText: String = "",
    val isListening: Boolean = false
)

class TalkViewModel : ViewModel(), HandLandmarkerHelper.LandmarkerListener {

    // --- MediaPipe Helper ---
    private lateinit var handLandmarkerHelper: HandLandmarkerHelper

    // --- TFLite Interpreter ---
    private var tfliteInterpreter: Interpreter? = null

    // --- Live Data for UI ---
    private val _handLandmarkerResult = MutableStateFlow<HandLandmarkerHelper.ResultBundle?>(null)
    val handLandmarkerResult = _handLandmarkerResult.asStateFlow()

    private val _predictedSign = MutableStateFlow("...")
    val predictedSign = _predictedSign.asStateFlow()

    // Added to match your TalkScreen's call to viewModel.uiState
    private val _uiState = MutableStateFlow(AudioUiState())
    val uiState: StateFlow<AudioUiState> = _uiState.asStateFlow()


    // --- LSTM Model & Data Collection ---
    private val landmarkSequence = mutableListOf<Float>()
    private val SEQUENCE_LENGTH = 30
    // UPDATED: Features for TWO hands (2 hands * 21 landmarks * 3 coordinates)
    private val FEATURES_PER_FRAME = 126

    private val labels = listOf(
        "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
        "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"
    )

    fun setupLandmarker(context: Context) {
        setupTfliteInterpreter(context)
        handLandmarkerHelper = HandLandmarkerHelper(
            context = context,
            handLandmarkerHelperListener = this
        )
    }

    /**
     * This function now contains all the CameraX logic, as required by your TalkScreen.
     */
    fun startCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // 1. Preview Use Case
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(surfaceProvider)
            }

            // 2. Image Analysis Use Case
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                        if (::handLandmarkerHelper.isInitialized) {
                            handLandmarkerHelper.detectLiveStream(imageProxy, isFrontCamera = true)
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            // 3. Select camera and bind to lifecycle
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                Log.e("TalkViewModel", "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }


    override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
        _handLandmarkerResult.value = resultBundle

        val handsResult = resultBundle.results.first()
        val landmarksForFrame = mutableListOf<Float>()
        val emptyHandLandmarks = FloatArray(63) { 0f }.toList() // 21 landmarks * 3 coords

        if (handsResult.landmarks().isEmpty()) {
            // If no hands are detected, add placeholders for both
            landmarksForFrame.addAll(emptyHandLandmarks)
            landmarksForFrame.addAll(emptyHandLandmarks)
        } else {
            // Process the first hand
            val firstHand = handsResult.landmarks()[0]
            landmarksForFrame.addAll(firstHand.flatMap { listOf(it.x(), it.y(), it.z()) })

            // Process the second hand if it exists, otherwise add a placeholder
            if (handsResult.landmarks().size > 1) {
                val secondHand = handsResult.landmarks()[1]
                landmarksForFrame.addAll(secondHand.flatMap { listOf(it.x(), it.y(), it.z()) })
            } else {
                landmarksForFrame.addAll(emptyHandLandmarks)
            }
        }

        // Add the combined landmarks for the frame to the sequence
        landmarkSequence.addAll(landmarksForFrame)

        // Trim the sequence if it's too long
        while (landmarkSequence.size > SEQUENCE_LENGTH * FEATURES_PER_FRAME) {
            landmarkSequence.removeAt(0)
        }

        if (landmarkSequence.size == SEQUENCE_LENGTH * FEATURES_PER_FRAME) {
            runInference()
            // Optional: Clear a portion of the sequence for non-overlapping windows
            // For a sliding window, you might remove just one frame's worth of data
            // landmarkSequence.subList(0, FEATURES_PER_FRAME).clear()
        }
    }

    private fun runInference() {
        if (tfliteInterpreter == null) {
            _predictedSign.value = "Interpreter not ready"
            return
        }
        // Input buffer size is now correctly calculated based on the doubled FEATURES_PER_FRAME
        val inputBuffer = ByteBuffer.allocateDirect(1 * SEQUENCE_LENGTH * FEATURES_PER_FRAME * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        // The sequence is already the correct size
        for (value in landmarkSequence) {
            inputBuffer.putFloat(value)
        }

        val outputBuffer = Array(1) { FloatArray(labels.size) }

        try {
            tfliteInterpreter?.run(inputBuffer, outputBuffer)
        } catch (e: Exception) {
            _predictedSign.value = "Inference Error"
            Log.e("TFLiteError", "Error during inference: ", e)
            return
        }
        val probabilities = outputBuffer[0]
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
        if (maxIndex != -1 && maxIndex < labels.size) {
            _predictedSign.value = labels[maxIndex]
        }
    }

    override fun onError(error: String) { _predictedSign.value = error }

    private fun setupTfliteInterpreter(context: Context) {
        try {
            val assetFileDescriptor = context.assets.openFd("best_model_lstm_hands.tflite")
            val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = fileInputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            val options = Interpreter.Options()
            tfliteInterpreter = Interpreter(modelBuffer, options)
            _predictedSign.value = "Ready"
        } catch (e: Exception) {
            val errorMessage = "Model load error: ${e.message}"
            Log.e("TFLiteError", errorMessage, e)
            _predictedSign.value = errorMessage
        }
    }

    override fun onCleared() {
        super.onCleared()
        handLandmarkerHelper.clearHandLandmarker()
        tfliteInterpreter?.close()
    }

    // Dummy function to prevent a crash from your UI code calling it.
    fun toggleSpeechRecording(context: Context) {
        // You can add your speech-to-text logic here later.
    }
}
