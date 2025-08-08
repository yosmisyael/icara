package com.example.icara.managers

import android.content.Context
import android.util.Log
import com.example.icara.data.state.SignLanguageState
import com.example.icara.helper.HandLandmarkerHelper
import kotlinx.coroutines.CoroutineScope
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
 * Manages sign language recognition functionality
 */
class SignLanguageManager(
    private val coroutineScope: CoroutineScope
) : HandLandmarkerHelper.LandmarkerListener {

    private val _signLanguageState = MutableStateFlow(SignLanguageState())
    val signLanguageState: StateFlow<SignLanguageState> = _signLanguageState.asStateFlow()

    // Threading & Coroutines
    private lateinit var cameraExecutor: ExecutorService
    private val landmarkDataChannel = Channel<List<Float>>(Channel.CONFLATED)

    // MediaPipe & TFLite
    private lateinit var handLandmarkerHelper: HandLandmarkerHelper
    private var liteRtInterpreter: Interpreter? = null
    private var initializationError: String? = null

    // LSTM Model & Data Collection
    private val landmarkSequence = mutableListOf<Float>()
    private val SEQUENCE_LENGTH = 30
    private val FEATURES_PER_FRAME = 126 // 2 hands * 21 landmarks * 3 coordinates
    private var frameCounter = 0
    private val PREDICTION_INTERVAL = 15 // Run prediction every 15 frames
    private val labels = listOf(
        "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
        "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"
    )

    fun initialize(context: Context) {
        cameraExecutor = Executors.newSingleThreadExecutor()
        setupLiteRtInterpreter(context)
        handLandmarkerHelper = HandLandmarkerHelper(
            context = context,
            handLandmarkerHelperListener = this
        )
        startInferenceLoop()

        _signLanguageState.value = _signLanguageState.value.copy(isInitialized = true)
    }

    fun getHandLandmarkerHelper(): HandLandmarkerHelper = handLandmarkerHelper
    fun getCameraExecutor(): ExecutorService = cameraExecutor

    override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
        _signLanguageState.value = _signLanguageState.value.copy(
            handLandmarkerResult = resultBundle
        )

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

    override fun onError(error: String) {
        _signLanguageState.value = _signLanguageState.value.copy(
            predictedSign = error,
            error = error
        )
        Log.e("SignLanguageManager", "Hand Landmarker Error: $error")
    }

    private fun startInferenceLoop() {
        coroutineScope.launch(Dispatchers.Default) {
            landmarkDataChannel.receiveAsFlow().collect { sequence ->
                runInference(sequence)
            }
        }
    }

    private fun runInference(sequenceToProcess: List<Float>) {
        if (initializationError != null) {
            _signLanguageState.value = _signLanguageState.value.copy(
                predictedSign = initializationError!!,
                error = initializationError
            )
            return
        }
        if (liteRtInterpreter == null) {
            val errorMsg = "Interpreter not ready"
            _signLanguageState.value = _signLanguageState.value.copy(
                predictedSign = errorMsg,
                error = errorMsg
            )
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
            val errorMsg = "Inference Error"
            _signLanguageState.value = _signLanguageState.value.copy(
                predictedSign = errorMsg,
                error = errorMsg
            )
            Log.e("LiteRTError", "Error during inference: ", e)
            return
        }

        val probabilities = outputBuffer[0]
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
        if (maxIndex != -1 && maxIndex < labels.size) {
            _signLanguageState.value = _signLanguageState.value.copy(
                predictedSign = labels[maxIndex],
                error = null
            )
        }
    }

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
            Log.i("SignLanguageManager", "Flex delegate added.")

            var gpuDelegate: GpuDelegate? = null
            if (compatList.isDelegateSupportedOnThisDevice) {
                val delegateOptions = compatList.bestOptionsForThisDevice
                gpuDelegate = GpuDelegate(delegateOptions)
                options.addDelegate(gpuDelegate)
                Log.i("SignLanguageManager", "GPU delegate is supported, attempting to apply.")
            } else {
                options.setNumThreads(4)
                Log.w("SignLanguageManager", "GPU delegate not supported. Using CPU with 4 threads.")
            }

            try {
                liteRtInterpreter = Interpreter(modelBuffer, options)
            } catch (e: Exception) {
                if (gpuDelegate != null) {
                    Log.w("SignLanguageManager", "Failed to initialize with GPU delegate. Retrying without it. Error: ${e.message}")
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
            _signLanguageState.value = _signLanguageState.value.copy(
                predictedSign = "Ready",
                error = null
            )
            initializationError = null
        } catch (e: Exception) {
            val errorMessage = "Model load error: ${e.message}"
            Log.e("LiteRTError", errorMessage, e)
            _signLanguageState.value = _signLanguageState.value.copy(
                predictedSign = errorMessage,
                error = errorMessage
            )
            initializationError = errorMessage
        }
    }

    fun cleanup() {
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
        if (::handLandmarkerHelper.isInitialized) {
            handLandmarkerHelper.clearHandLandmarker()
        }
        liteRtInterpreter?.close()
        landmarkDataChannel.close()
        Log.d("SignLanguageManager", "SignLanguageManager cleaned up and all resources released.")
    }
}