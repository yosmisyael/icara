package com.example.icara.data.state

import com.example.icara.helper.HandLandmarkerHelper

/**
 * State specific to sign language recognition functionality
 */
data class SignLanguageState(
    val predictedSign: String = "...",
    val handLandmarkerResult: HandLandmarkerHelper.ResultBundle? = null,
    val isInitialized: Boolean = false,
    val error: String? = null
)