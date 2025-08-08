package com.example.icara.data.state

/**
 * Main UI state combining speech and sign language states
 */
data class TalkScreenUiState(
    val speechState: SpeechState = SpeechState(),
    val signLanguageState: SignLanguageState = SignLanguageState()
)