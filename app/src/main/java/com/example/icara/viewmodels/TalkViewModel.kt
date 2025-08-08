package com.example.icara.viewmodels

import android.content.Context
import android.util.Log
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.icara.data.state.TalkScreenUiState
import com.example.icara.data.repository.SignLanguageRepository
import com.example.icara.data.repository.SpeechRepository
import com.example.icara.helper.HandLandmarkerHelper
import com.example.icara.utils.CameraUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * A comprehensive ViewModel that handles both speech-to-text and sign language recognition.
 * It integrates Android's SpeechRecognizer for audio input and MediaPipe's HandLandmarker
 * with a TFLite LSTM model for visual sign language interpretation.
 */
class TalkViewModel : ViewModel() {
    // Repositories
    private val speechRepository = SpeechRepository(viewModelScope)
    private val signLanguageRepository = SignLanguageRepository(viewModelScope)

    // Combined UI State
    private val _uiState = MutableStateFlow(TalkScreenUiState())
    val uiState: StateFlow<TalkScreenUiState> = combine(
        speechRepository.speechState,
        signLanguageRepository.signLanguageState
    ) { speechState, signLanguageState ->
        TalkScreenUiState(
            speechState = speechState,
            signLanguageState = signLanguageState
        )
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = TalkScreenUiState()
    )

    // Expose specific states for backward compatibility
    val transcriptText: String get() = uiState.value.speechState.transcriptText
    val isRecording: Boolean get() = uiState.value.speechState.isRecording
    val isListening: Boolean get() = uiState.value.speechState.isListening
    val audioLevel: Float get() = uiState.value.speechState.audioLevel
    val error: String? get() = uiState.value.speechState.error
    val predictedSign: String get() = uiState.value.signLanguageState.predictedSign
    val speechState: StateFlow<com.example.icara.data.state.SpeechState> = speechRepository.speechState
    val signLanguageState: StateFlow<com.example.icara.data.state.SignLanguageState> = signLanguageRepository.signLanguageState

    // For backward compatibility - expose as StateFlow
    val handLandmarkerResult: HandLandmarkerHelper.ResultBundle?
        get() = signLanguageRepository.signLanguageState.value.handLandmarkerResult

    // --- Speech Recognition Methods ---
    fun initializeSpeechRecognizer(context: Context, language: String = "id-ID") {
        speechRepository.initializeSpeechRecognizer(context, language)
    }

    fun toggleSpeechRecording(context: Context) {
        speechRepository.toggleRecording(context)
    }

    fun clearTranscript() {
        speechRepository.clearTranscript()
    }

    fun setLanguage(language: String) {
        speechRepository.setLanguage(language)
    }

    fun clearError() {
        speechRepository.clearError()
    }

    // --- Sign Language Recognition Methods ---
    fun setupLandmarker(context: Context) {
        signLanguageRepository.initialize(context)
    }

    fun startCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ) {
        if (signLanguageRepository.signLanguageState.value.isInitialized) {
            CameraUtils.startCamera(
                context = context,
                lifecycleOwner = lifecycleOwner,
                surfaceProvider = surfaceProvider,
                handLandmarkerHelper = signLanguageRepository.getHandLandmarkerHelper(),
                cameraExecutor = signLanguageRepository.getCameraExecutor()
            ) { error ->
                Log.d("CAMERA", "Failed to initialize camera")
            }
        }
    }

    // --- ViewModel Cleanup ---
    override fun onCleared() {
        super.onCleared()
        speechRepository.cleanup()
        signLanguageRepository.cleanup()
    }
}