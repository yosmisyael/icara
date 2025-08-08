package com.example.icara.data.repository


import android.content.Context
import com.example.icara.data.state.SignLanguageState
import com.example.icara.managers.SignLanguageManager
import com.example.icara.helper.HandLandmarkerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ExecutorService

/**
 * Repository pattern for sign language recognition
 */
class SignLanguageRepository(coroutineScope: CoroutineScope) {
    private val signLanguageManager = SignLanguageManager(coroutineScope)

    val signLanguageState: StateFlow<SignLanguageState> = signLanguageManager.signLanguageState

    fun initialize(context: Context) {
        signLanguageManager.initialize(context)
    }

    fun getHandLandmarkerHelper(): HandLandmarkerHelper =
        signLanguageManager.getHandLandmarkerHelper()

    fun getCameraExecutor(): ExecutorService =
        signLanguageManager.getCameraExecutor()

    fun cleanup() = signLanguageManager.cleanup()
}