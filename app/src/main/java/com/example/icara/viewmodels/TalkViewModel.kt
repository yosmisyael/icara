package com.example.icara.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import android.util.Size
import androidx.core.content.ContextCompat
import com.example.icara.helper.HandLandmarkerHelper
import com.google.mediapipe.tasks.core.Delegate
import java.util.concurrent.Executors

class TalkViewModel : ViewModel(), HandLandmarkerHelper.LandmarkerListener {
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    // thread for image analysis executor
    private val imageAnalysisExecutor = Executors.newFixedThreadPool(2)

    // hand landmarker
    private var handLandmarkerHelper: HandLandmarkerHelper? = null
    private val _handLandmarkerResult = MutableStateFlow<HandLandmarkerHelper.ResultBundle?>(null)
    val handLandmarkerResult: StateFlow<HandLandmarkerHelper.ResultBundle?> = _handLandmarkerResult

    // start audio recording function
    fun startRecording() {
        _isRecording.update { true }
    }

    // stop audio recording function
    fun stopRecording() {
        _isRecording.update { false }
    }

    fun setupLandmarker(context: Context) {
        Log.d("DEBUG", "setup hand landmarker")
        handLandmarkerHelper?.clearHandLandmarker()
        handLandmarkerHelper = HandLandmarkerHelper(context, handLandmarkerHelperListener = this, currentHardware = Delegate.GPU)
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
    }
}