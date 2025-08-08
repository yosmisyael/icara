package com.example.icara.data.repository

import android.content.Context
import com.example.icara.data.state.SpeechState
import com.example.icara.managers.SpeechRecognitionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository pattern for speech recognition
 */
class SpeechRepository(coroutineScope: CoroutineScope) {
    private val speechManager = SpeechRecognitionManager(coroutineScope)

    val speechState: StateFlow<SpeechState> = speechManager.speechState

    fun toggleRecording(context: Context) {
        if (speechState.value.isRecording) {
            speechManager.stopRecording()
        } else {
            speechManager.startRecording(context)
        }
    }

    fun initializeSpeechRecognizer(context: Context, language: String) {
        speechManager.initializeSpeechRecognizer(context, language)
    }

    fun clearTranscript() = speechManager.clearTranscript()
    fun setLanguage(language: String) = speechManager.setLanguage(language)
    fun clearError() = speechManager.clearError()
    fun cleanup() = speechManager.cleanup()
}