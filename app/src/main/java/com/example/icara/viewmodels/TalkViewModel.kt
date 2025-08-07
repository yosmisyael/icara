package com.example.icara.viewmodels

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.icara.helper.HandLandmarkerHelper
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

/**
 * Data class for the UI state of the talk screen, combining both speech and sign recognition states.
 */
data class TalkScreenUiState(
    val transcriptText: String = "",
    val isRecording: Boolean = false,
    val isListening: Boolean = false,
    val audioLevel: Float = 0f,
    val error: String? = null
)

/**
 * A comprehensive ViewModel that handles both speech-to-text and sign language recognition.
 * It integrates Android's SpeechRecognizer for audio input and MediaPipe's HandLandmarker
 * with a TFLite LSTM model for visual sign language interpretation.
 */
class TalkViewModel : ViewModel(), HandLandmarkerHelper.LandmarkerListener {

    // --- UI State ---
    private val _uiState = MutableStateFlow(TalkScreenUiState())
    val uiState: StateFlow<TalkScreenUiState> = _uiState.asStateFlow()

    // --- Speech Recognition ---
    private var speechRecognizer: SpeechRecognizer? = null
    private var baseTranscriptText: String = ""
    private var currentLanguage: String = "id-ID"

    // --- Threading & Coroutines for Sign Language ---
    private lateinit var cameraExecutor: ExecutorService
    private val landmarkDataChannel = Channel<List<Float>>(Channel.CONFLATED)

    // --- MediaPipe & TFLite for Sign Language ---
    private lateinit var handLandmarkerHelper: HandLandmarkerHelper
    private var liteRtInterpreter: Interpreter? = null
    private var initializationError: String? = null

    // --- Sign Language Recognition StateFlows ---
    private val _handLandmarkerResult = MutableStateFlow<HandLandmarkerHelper.ResultBundle?>(null)
    val handLandmarkerResult = _handLandmarkerResult.asStateFlow()

    private val _predictedSign = MutableStateFlow("...")
    val predictedSign = _predictedSign.asStateFlow()

    // --- LSTM Model & Data Collection ---
    private val landmarkSequence = mutableListOf<Float>()
    private val SEQUENCE_LENGTH = 30
    private val FEATURES_PER_FRAME = 126 // 2 hands * 21 landmarks * 3 coordinates
    private var frameCounter = 0
    private val PREDICTION_INTERVAL = 15 // Run prediction every 15 frames
    private val labels = listOf(
        "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
        "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"
    )

    // --- Speech Recognizer Initialization & Listener ---

    /**
     * Initializes the SpeechRecognizer for the specified language.
     */
    fun initializeSpeechRecognizer(context: Context, language: String = "id-ID") {
        currentLanguage = language
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
        }
    }

    /**
     * Creates a RecognitionListener to handle speech recognition events.
     */
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(
                        isListening = true,
                        error = null,
                        transcriptText = "",
                    )
                }
            }

            override fun onBeginningOfSpeech() {

            }

            override fun onRmsChanged(rmsdB: Float) {
                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(audioLevel = rmsdB)
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(isListening = false, audioLevel = 0f)
                }
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown speech error"
                }
                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(
                        isRecording = false,
                        isListening = false,
                        error = errorMessage
                    )
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val transcribedText = matches?.firstOrNull() ?: ""
                viewModelScope.launch {
                    baseTranscriptText = transcribedText
                    _uiState.value = _uiState.value.copy(
                        transcriptText = transcribedText,
                        isRecording = false,
                        isListening = false,
                        audioLevel = 0f,
                        error = null
                    )
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partialText = matches?.firstOrNull() ?: ""
                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(transcriptText = partialText)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    // --- Speech Recording Control ---

    /**
     * Toggles the speech recording on or off.
     */
    fun toggleSpeechRecording(context: Context) {
        if (_uiState.value.isRecording) {
            stopSpeechRecording()
        } else {
            startSpeechRecording(context)
        }
    }

    private fun startSpeechRecording(context: Context) {
        if (speechRecognizer == null) {
            initializeSpeechRecognizer(context, currentLanguage)
        }
        val speechLanguage = when (currentLanguage.lowercase()) {
            "en" -> "en-US"
            "id" -> "id-ID"
            else -> if (currentLanguage.contains("-")) currentLanguage else "$currentLanguage-${currentLanguage.uppercase()}"
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLanguage)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRecording = true, error = null)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun stopSpeechRecording() {
        speechRecognizer?.stopListening()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRecording = false, isListening = false)
        }
    }

    fun clearTranscript() {
        baseTranscriptText = ""
        _uiState.value = _uiState.value.copy(transcriptText = "", error = null)
    }

    fun setLanguage(language: String) {
        currentLanguage = language
        // Re-initialize recognizer if it's already active with a different language
        if (_uiState.value.isRecording || speechRecognizer != null) {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // --- Sign Language Recognition Setup & Camera ---

    /**
     * Initializes all necessary components for sign language recognition.
     */
    fun setupLandmarker(context: Context) {
        cameraExecutor = Executors.newSingleThreadExecutor()
        setupLiteRtInterpreter(context)
        handLandmarkerHelper = HandLandmarkerHelper(
            context = context,
            handLandmarkerHelperListener = this
        )
        startInferenceLoop()
    }

    /**
     * Sets up and starts the CameraX pipeline for hand landmark detection.
     */
    fun startCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(surfaceProvider)
            }
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (::handLandmarkerHelper.isInitialized) {
                            handLandmarkerHelper.detectLiveStream(imageProxy, isFrontCamera = true)
                        } else {
                            imageProxy.close()
                        }
                    }
                }
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
                _predictedSign.value = "Camera Error"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // --- Sign Language Processing & Inference ---

    /**
     * Callback from HandLandmarkerHelper. Processes landmarks and triggers inference at intervals.
     */
    override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
        _handLandmarkerResult.value = resultBundle

        val handsResult = resultBundle.results.first()
        val landmarksForFrame = mutableListOf<Float>()
        val emptyHandLandmarks = FloatArray(63) { 0f }.toList()

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

        landmarkSequence.addAll(landmarksForFrame)
        while (landmarkSequence.size > SEQUENCE_LENGTH * FEATURES_PER_FRAME) {
            landmarkSequence.subList(0, FEATURES_PER_FRAME).clear()
        }

        frameCounter++
        if (frameCounter >= PREDICTION_INTERVAL) {
            frameCounter = 0
            if (landmarkSequence.size == SEQUENCE_LENGTH * FEATURES_PER_FRAME) {
                landmarkDataChannel.trySend(landmarkSequence.toList())
            }
        }
    }

    /**
     * Launches a long-running coroutine to handle inference, decoupling it from the landmark detection callback.
     */
    private fun startInferenceLoop() {
        viewModelScope.launch(Dispatchers.Default) {
            landmarkDataChannel.receiveAsFlow().collect { sequence ->
                runInference(sequence)
            }
        }
    }

    /**
     * Runs inference on the TFLite model using the provided landmark sequence.
     */
    private fun runInference(sequenceToProcess: List<Float>) {
        if (initializationError != null) {
            _predictedSign.value = initializationError!!
            return
        }
        if (liteRtInterpreter == null) {
            _predictedSign.value = "Interpreter not ready"
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
            _predictedSign.value = "Inference Error"
            Log.e("LiteRTError", "Error during inference: ", e)
            return
        }

        val probabilities = outputBuffer[0]
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
        if (maxIndex != -1 && maxIndex < labels.size) {
            _predictedSign.value = labels[maxIndex]
        }
    }

    /**
     * HandLandmarkerHelper error callback.
     */
    override fun onError(error: String) {
        _predictedSign.value = error
        Log.e("TalkViewModel", "Hand Landmarker Error: $error")
    }

    /**
     * Initializes the TFLite interpreter, attempting to use GPU delegate with a CPU fallback.
     */
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
            Log.i("TalkViewModel", "Flex delegate added.")

            var gpuDelegate: GpuDelegate? = null
            if (compatList.isDelegateSupportedOnThisDevice) {
                val delegateOptions = compatList.bestOptionsForThisDevice
                gpuDelegate = GpuDelegate(delegateOptions)
                options.addDelegate(gpuDelegate)
                Log.i("TalkViewModel", "GPU delegate is supported, attempting to apply.")
            } else {
                options.setNumThreads(4)
                Log.w("TalkViewModel", "GPU delegate not supported. Using CPU with 4 threads.")
            }

            try {
                liteRtInterpreter = Interpreter(modelBuffer, options)
            } catch (e: Exception) {
                if (gpuDelegate != null) {
                    Log.w("TalkViewModel", "Failed to initialize with GPU delegate. Retrying without it. Error: ${e.message}")
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
            _predictedSign.value = "Ready"
            initializationError = null
        } catch (e: Exception) {
            val errorMessage = "Model load error: ${e.message}"
            Log.e("LiteRTError", errorMessage, e)
            _predictedSign.value = errorMessage
            initializationError = errorMessage
        }
    }

    // --- ViewModel Cleanup ---

    /**
     * Cleans up all resources when the ViewModel is destroyed.
     */
    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
        if (::handLandmarkerHelper.isInitialized) {
            handLandmarkerHelper.clearHandLandmarker()
        }
        liteRtInterpreter?.close()
        landmarkDataChannel.close()
        Log.d("TalkViewModel", "ViewModel cleared and all resources released.")
    }
}