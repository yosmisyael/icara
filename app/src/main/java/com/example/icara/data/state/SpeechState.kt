package com.example.icara.data.state

/**
 * State specific to speech recognition functionality
 */
data class SpeechState(
    val transcriptText: String = "",
    val isRecording: Boolean = false,
    val isListening: Boolean = false,
    val audioLevel: Float = 0f,
    val error: String? = null,
    val currentLanguage: String = "id-ID"
)