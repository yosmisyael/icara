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
import androidx.lifecycle.viewModelScope
import com.example.icara.helper.HandLandmarkerHelper
import com.google.mediapipe.tasks.core.Delegate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class AudioUiState(
    val transcriptText: String = "",
    val isListening: Boolean = false
)

class TalkViewModel : ViewModel(), HandLandmarkerHelper.LandmarkerListener {

    // --- MediaPipe Helper ---
    private var handLandmarkerHelper: HandLandmarkerHelper? = null

    // --- TFLite Interpreter ---
    private var tfliteInterpreter: Interpreter? = null
    private val isInferenceRunning = AtomicBoolean(false)

    // --- Camera Provider ---
    private var cameraProvider: ProcessCameraProvider? = null
    private var isInitialized = AtomicBoolean(false)

    // --- Live Data for UI ---
    private val _handLandmarkerResult = MutableStateFlow<HandLandmarkerHelper.ResultBundle?>(null)
    val handLandmarkerResult = _handLandmarkerResult.asStateFlow()

    private val _predictedSign = MutableStateFlow("...")
    val predictedSign = _predictedSign.asStateFlow()

    private val _uiState = MutableStateFlow(AudioUiState())
    val uiState: StateFlow<AudioUiState> = _uiState.asStateFlow()

    // --- Optimized LSTM Model & Data Collection ---
    private val SEQUENCE_LENGTH = 30
    private val FEATURES_PER_FRAME = 126 // 2 hands * 21 landmarks * 3 coordinates
    private val landmarkSequence = ArrayList<Float>(SEQUENCE_LENGTH * FEATURES_PER_FRAME)

    // Pre-allocated buffers to avoid GC pressure
    private val inputBuffer = ByteBuffer.allocateDirect(1 * SEQUENCE_LENGTH * FEATURES_PER_FRAME * 4).apply {
        order(ByteOrder.nativeOrder())
    }
    private val outputBuffer = Array(1) { FloatArray(26) } // Pre-allocated output buffer
    private val emptyHandLandmarks = Array<Float>(63) { 0f } // Pre-allocated empty landmarks as List

    // Inference throttling
    private val lastInferenceTime = AtomicLong(0)
    private val INFERENCE_INTERVAL_MS = 200L

    private val labels = listOf(
        "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
        "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"
    )

    // Dedicated executor for ML processing
    private val mlExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ML-Processing").apply {
            priority = Thread.NORM_PRIORITY - 1
        }
    }

    fun setupLandmarker(context: Context) {
        if (isInitialized.get()) {
            Log.d("TalkViewModel", "Already initialized, skipping setup")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Setup TFLite first
                setupTfliteInterpreter(context)

                // Then setup MediaPipe
                withContext(Dispatchers.Main) {
                    handLandmarkerHelper?.clearHandLandmarker()
                    handLandmarkerHelper = HandLandmarkerHelper(
                        context = context,
                        handLandmarkerHelperListener = this@TalkViewModel,
                        currentHardware = Delegate.GPU
                    )
                    isInitialized.set(true)
                    Log.d("TalkViewModel", "Landmarker setup completed")
                }
            } catch (e: Exception) {
                Log.e("TalkViewModel", "Error setting up landmarker", e)
                withContext(Dispatchers.Main) {
                    _predictedSign.value = "Setup Error: ${e.message}"
                }
            }
        }
    }

    fun startCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ) {
        // Ensure we have a clean start
        stopCamera()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCamera(lifecycleOwner, surfaceProvider)
            } catch (e: Exception) {
                Log.e("TalkViewModel", "Camera provider initialization failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCamera(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        val cameraProvider = this.cameraProvider ?: return

        try {
            // Unbind all previous use cases
            cameraProvider.unbindAll()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(mlExecutor) { imageProxy ->
                        handLandmarkerHelper?.let { helper ->
                            helper.detectLiveStream(imageProxy, isFrontCamera = true)
                        } ?: imageProxy.close()
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            Log.d("TalkViewModel", "Camera binding successful")
        } catch (e: Exception) {
            Log.e("TalkViewModel", "Camera binding failed", e)
        }
    }

    fun stopCamera() {
        cameraProvider?.unbindAll()
    }

    fun resetDetection() {
        viewModelScope.launch(Dispatchers.Default) {
            synchronized(landmarkSequence) {
                landmarkSequence.clear()
            }
            isInferenceRunning.set(false)
            lastInferenceTime.set(0)

            withContext(Dispatchers.Main) {
                _handLandmarkerResult.value = null
                _predictedSign.value = "Reset"
            }
        }
    }

    override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
        // update UI
        _handLandmarkerResult.value = resultBundle

        // process landmarks for ML inference on background thread
        processLandmarksForInference(resultBundle)
    }

    private fun processLandmarksForInference(resultBundle: HandLandmarkerHelper.ResultBundle) {
        // skip processing if still running inference
        if (isInferenceRunning.get()) {
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val handsResult = resultBundle.results.first()
                val landmarksForFrame = mutableListOf<Float>()

                if (handsResult.landmarks().isEmpty()) {
                    // Add empty landmarks for both hands
                    landmarksForFrame.addAll(emptyHandLandmarks)
                    landmarksForFrame.addAll(emptyHandLandmarks)
                } else {
                    // Process the first hand
                    val firstHand = handsResult.landmarks()[0]
                    landmarksForFrame.addAll(firstHand.flatMap { listOf(it.x(), it.y(), it.z()) })

                    // Process the second hand or add placeholder
                    if (handsResult.landmarks().size > 1) {
                        val secondHand = handsResult.landmarks()[1]
                        landmarksForFrame.addAll(secondHand.flatMap { listOf(it.x(), it.y(), it.z()) })
                    } else {
                        landmarksForFrame.addAll(emptyHandLandmarks)
                    }
                }

                // Add to sequence with proper synchronization
                synchronized(landmarkSequence) {
                    landmarkSequence.addAll(landmarksForFrame)

                    // Trim sequence efficiently
                    while (landmarkSequence.size > SEQUENCE_LENGTH * FEATURES_PER_FRAME) {
                        // Remove a full frame at once for better performance
                        repeat(FEATURES_PER_FRAME) {
                            if (landmarkSequence.isNotEmpty()) {
                                landmarkSequence.removeAt(0)
                            }
                        }
                    }

                    // Check if ready for inference and throttle
                    if (landmarkSequence.size == SEQUENCE_LENGTH * FEATURES_PER_FRAME) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastInferenceTime.get() >= INFERENCE_INTERVAL_MS) {
                            if (isInferenceRunning.compareAndSet(false, true)) {
                                lastInferenceTime.set(currentTime)
                                runInference()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TalkViewModel", "Error processing landmarks", e)
            }
        }
    }

    private fun runInference() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                if (tfliteInterpreter == null) {
                    withContext(Dispatchers.Main) {
                        _predictedSign.value = "Interpreter not ready"
                    }
                    return@launch
                }

                // Clear and populate input buffer
                inputBuffer.clear()
                synchronized(landmarkSequence) {
                    landmarkSequence.forEach { value ->
                        inputBuffer.putFloat(value)
                    }
                }

                // Reset buffer position for reading
                inputBuffer.rewind()

                // Run inference
                tfliteInterpreter?.run(inputBuffer, outputBuffer)

                // Process results
                val probabilities = outputBuffer[0]
                val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1

                val prediction = if (maxIndex != -1 && maxIndex < labels.size) {
                    labels[maxIndex]
                } else {
                    "Unknown"
                }

                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    _predictedSign.value = prediction
                }

            } catch (e: Exception) {
                Log.e("TFLiteError", "Error during inference: ", e)
                withContext(Dispatchers.Main) {
                    _predictedSign.value = "Inference Error"
                }
            } finally {
                isInferenceRunning.set(false)
            }
        }
    }

    override fun onError(error: String) {
        _predictedSign.value = error
        Log.e("TalkViewModel", "MediaPipe error: $error")
    }

    private fun setupTfliteInterpreter(context: Context) {
        try {
            val assetFileDescriptor = context.assets.openFd("best_model_lstm_hands.tflite")
            val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = fileInputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val options = Interpreter.Options().apply {
                setNumThreads(2)
                setUseXNNPACK(true)
            }

            tfliteInterpreter = Interpreter(modelBuffer, options)
            Log.d("TalkViewModel", "TFLite interpreter setup completed")

        } catch (e: Exception) {
            val errorMessage = "Model load error: ${e.message}"
            Log.e("TFLiteError", errorMessage, e)
            _predictedSign.value = errorMessage
        }
    }

    // Call this when navigating back to the screen
    fun onResume(context: Context) {
        if (!isInitialized.get()) {
            setupLandmarker(context)
        }
    }

    // Call this when navigating away from the screen
    fun onPause() {
        stopCamera()
        resetDetection()
    }

    override fun onCleared() {
        super.onCleared()
        stopCamera()
        handLandmarkerHelper?.clearHandLandmarker()
        handLandmarkerHelper = null
        tfliteInterpreter?.close()
        tfliteInterpreter = null
        mlExecutor.shutdown()
        isInitialized.set(false)
        Log.d("TalkViewModel", "ViewModel cleared")
    }

    fun toggleSpeechRecording(context: Context) {
        // Speech-to-text logic here
    }
}