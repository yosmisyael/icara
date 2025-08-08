package com.example.icara.managers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.icara.data.state.SpeechState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages speech recognition functionality
 */
class SpeechRecognitionManager(
    private val coroutineScope: CoroutineScope
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var baseTranscriptText: String = ""

    private val _speechState = MutableStateFlow(SpeechState())
    val speechState: StateFlow<SpeechState> = _speechState.asStateFlow()

    fun initializeSpeechRecognizer(context: Context, language: String = "id-ID") {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())

            _speechState.value = _speechState.value.copy(currentLanguage = language)
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                coroutineScope.launch {
                    _speechState.value = _speechState.value.copy(
                        isListening = true,
                        error = null,
                        transcriptText = ""
                    )
                }
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                coroutineScope.launch {
                    _speechState.value = _speechState.value.copy(audioLevel = rmsdB)
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                coroutineScope.launch {
                    _speechState.value = _speechState.value.copy(
                        isListening = false,
                        audioLevel = 0f
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
                    else -> "Unknown speech error"
                }
                coroutineScope.launch {
                    _speechState.value = _speechState.value.copy(
                        isRecording = false,
                        isListening = false,
                        error = errorMessage
                    )
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val transcribedText = matches?.firstOrNull() ?: ""
                coroutineScope.launch {
                    baseTranscriptText = transcribedText
                    _speechState.value = _speechState.value.copy(
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
                coroutineScope.launch {
                    _speechState.value = _speechState.value.copy(transcriptText = partialText)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    fun startRecording(context: Context) {
        if (speechRecognizer == null) {
            initializeSpeechRecognizer(context, _speechState.value.currentLanguage)
        }

        val speechLanguage = when (_speechState.value.currentLanguage.lowercase()) {
            "en" -> "en-US"
            "id" -> "id-ID"
            else -> if (_speechState.value.currentLanguage.contains("-")) {
                _speechState.value.currentLanguage
            } else {
                "${_speechState.value.currentLanguage}-${_speechState.value.currentLanguage.uppercase()}"
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLanguage)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        coroutineScope.launch {
            _speechState.value = _speechState.value.copy(isRecording = true, error = null)
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopRecording() {
        speechRecognizer?.stopListening()
        coroutineScope.launch {
            _speechState.value = _speechState.value.copy(
                isRecording = false,
                isListening = false
            )
        }
    }

    fun clearTranscript() {
        baseTranscriptText = ""
        _speechState.value = _speechState.value.copy(transcriptText = "", error = null)
    }

    fun setLanguage(language: String) {
        _speechState.value = _speechState.value.copy(currentLanguage = language)
        // Re-initialize recognizer if it's already active with a different language
        if (_speechState.value.isRecording || speechRecognizer != null) {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    fun clearError() {
        _speechState.value = _speechState.value.copy(error = null)
    }

    fun cleanup() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}