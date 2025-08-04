package com.example.icara.viewmodels

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import android.util.Size
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import com.example.icara.helper.HandLandmarkerHelper
import com.google.mediapipe.tasks.core.Delegate
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

data class TalkScreenUiState(
    val transcriptText: String = "",
    val isRecording: Boolean = false,
    val isListening: Boolean = false,
    val audioLevel: Float = 0f,
    val error: String? = null
)

class TalkViewModel : ViewModel(), HandLandmarkerHelper.LandmarkerListener {
    // speech recording state
    private val _uiState = MutableStateFlow(TalkScreenUiState())
    val uiState: StateFlow<TalkScreenUiState> = _uiState.asStateFlow()
    private var speechRecognizer: SpeechRecognizer? = null
    private var baseTranscriptText: String = ""
    private var currentLanguage: String = "id-ID"

    // function to initialize speech recognizer
    fun initializeSpeechRecognizer(context: Context, language: String = "id-ID") {
        currentLanguage = language
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
        }
    }

    // function to create speech listener
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(
                        isListening = true,
                        error = null
                    )
                }
            }

            override fun onBeginningOfSpeech() { }

            override fun onRmsChanged(rmsdB: Float) {
                Log.d("AUDIO", "audio level: $rmsdB")
                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(
                        audioLevel = rmsdB
                    )
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) { }

            override fun onEndOfSpeech() {
                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(
                        isListening = false,
                        audioLevel = 0f,
                    )
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
                    else -> "Unknown error"
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
                    val fullTextTranscribe = if (baseTranscriptText.isEmpty()) {
                        transcribedText
                    } else {
                        "$baseTranscriptText $transcribedText"
                    }

                    baseTranscriptText = fullTextTranscribe

                    _uiState.value = _uiState.value.copy(
                        transcriptText = fullTextTranscribe,
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
                    val displayText = if (baseTranscriptText.isEmpty()) {
                        partialText
                    } else {
                        "$baseTranscriptText $partialText"
                    }

                    _uiState.value = _uiState.value.copy(
                        transcriptText = displayText
                    )
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Handle other events if needed
            }
        }
    }

    // thread for image analysis executor
    private val imageAnalysisExecutor = Executors.newFixedThreadPool(2)

    // hand landmarker
    private var handLandmarkerHelper: HandLandmarkerHelper? = null
    private val _handLandmarkerResult = MutableStateFlow<HandLandmarkerHelper.ResultBundle?>(null)
    val handLandmarkerResult: StateFlow<HandLandmarkerHelper.ResultBundle?> = _handLandmarkerResult

    // function to toggle speech recording
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
            _uiState.value = _uiState.value.copy(
                isRecording = true,
                error = null
            )
        }

        speechRecognizer?.startListening(intent)
    }

    private fun stopSpeechRecording() {
        speechRecognizer?.stopListening()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRecording = false,
                isListening = false
            )
        }
    }

    fun clearTranscript() {
        _uiState.value = _uiState.value.copy(
            transcriptText = "",
            error = null
        )
    }

    fun setLanguage(language: String) {
        currentLanguage = language
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun setupLandmarker(context: Context) {
        Log.d("DEBUG", "setup hand landmarker")
        handLandmarkerHelper?.clearHandLandmarker()
        handLandmarkerHelper = HandLandmarkerHelper(context, handLandmarkerHelperListener = this)
    }

    // start camera function
    fun startCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // camera preview use case
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(surfaceProvider)
                }

            // imageAnalysis use case, for getting frames for MediaPipe
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(640, 480),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                            )
                        )
                        .build()
                )
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also {
                    it.setAnalyzer(imageAnalysisExecutor) { imageProxy ->
                        val isFrontCamera = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                        handLandmarkerHelper?.detectLiveStream(imageProxy, isFrontCamera)
                    }
                }

            // select the front camera
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind everything before rebinding
                cameraProvider.unbindAll()

                // Bind the use cases to the camera
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageAnalyzer
                )
            } catch (e: Exception) {
                Log.e("CameraX", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
        _handLandmarkerResult.value = resultBundle
    }

    override fun onError(error: String) {
        Log.e("TalkViewModel", "Hand Landmarker Error: $error")
    }

    // clear ViedModel function
    override fun onCleared() {
        super.onCleared()
        handLandmarkerHelper?.clearHandLandmarker()
        speechRecognizer?.destroy()
    }
}