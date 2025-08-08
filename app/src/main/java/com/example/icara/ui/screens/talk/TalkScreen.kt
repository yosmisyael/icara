package com.example.icara.ui.screens.talk

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.icara.ui.components.SignLanguageSwitcher
import com.example.icara.ui.components.SignTranscriptBox
import com.example.icara.ui.components.DevelopmentDialog
import com.example.icara.ui.components.VoiceTranscriptBox
import com.example.icara.viewmodels.TalkViewModel

enum class MaximizedState {
    SIGN, VOICE, NONE
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun TalkScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: TalkViewModel = viewModel(),
) {
    var maximizedCard by remember { mutableStateOf(MaximizedState.NONE) }

    // states of permission
    var hasCameraPermission by remember { mutableStateOf(false) }
    var hasAudioPermission by remember { mutableStateOf(false) }

    // get context and lifecycle owner for CameraX
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // collect states from ViewModel
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val signLanguageState by viewModel.signLanguageState.collectAsStateWithLifecycle()

    // remember a PreviewView instance
    val previewView = remember { PreviewView(context) }

    // camera permission access
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasCameraPermission = isGranted
        }
    )

    // audio permission access
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasAudioPermission = isGranted
        }
    )

    // request permissions when the screen first appears
    LaunchedEffect(key1 = true) {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // request permissions and start the camera when they are granted
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            viewModel.setupLandmarker(context)
            viewModel.startCamera(context, lifecycleOwner, previewView.surfaceProvider)
        }
    }

    // dialog state
    var showUnderDevelopmentDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {},
                navigationIcon = {
                    TextButton(
                        onClick = { onNavigateBack() },
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ChevronLeft,
                                contentDescription = "kembali",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "Kembali",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                },
                actions = {
                    SignLanguageSwitcher(
                        modifier = Modifier.padding(end = 16.dp),
                        onSelectDisabledItem = { showUnderDevelopmentDialog = true }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Teman Komunikasi",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            when (maximizedCard) {
                MaximizedState.SIGN -> {
                    SignTranscriptBox(
                        modifier = Modifier.weight(1f),
                        currentMaximizedState = maximizedCard,
                        onToggleMaximize = { maximizedCard = MaximizedState.NONE },
                        chatBoxTitle = "Makna Isyaratmu",
                        transcriptText = signLanguageState.predictedSign,
                        cameraPreview = {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AndroidView(
                                    factory = { previewView },
                                    modifier = Modifier.fillMaxSize(),
                                    update = { view ->
                                        view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                        view.scaleType = PreviewView.ScaleType.FILL_CENTER
                                    }
                                )

                                signLanguageState.handLandmarkerResult?.let { resultBundle ->
                                    LandmarkOverlay(
                                        resultBundle = resultBundle,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    )

                    VoiceTranscriptBox(
                        modifier = Modifier.height(80.dp),
                        currentMaximizedState = maximizedCard,
                        onToggleMaximize = { maximizedCard = MaximizedState.VOICE },
                        chatBoxTitle = "Teks Percakapan",
                        transcriptText = uiState.speechState.transcriptText.ifEmpty {
                            if (uiState.speechState.isListening) "Mendengarkan..."
                            else "Ketuk mikrofon untuk mulai merekam"
                        },
                        isListening = uiState.speechState.isListening,
                        onMicClick = {
                            if (hasAudioPermission) {
                                viewModel.toggleSpeechRecording(context)
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onSignLanguageClick = { showUnderDevelopmentDialog = true },
                    )
                }

                MaximizedState.VOICE -> {
                    SignTranscriptBox(
                        modifier = Modifier.height(80.dp),
                        currentMaximizedState = maximizedCard,
                        onToggleMaximize = { maximizedCard = MaximizedState.SIGN },
                        chatBoxTitle = "Makna Isyaratmu",
                        transcriptText = signLanguageState.predictedSign,
                        cameraPreview = {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AndroidView(
                                    factory = { previewView },
                                    modifier = Modifier.fillMaxSize(),
                                    update = { view ->
                                        view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                        view.scaleType = PreviewView.ScaleType.FILL_CENTER
                                    }
                                )

                                signLanguageState.handLandmarkerResult?.let { resultBundle ->
                                    LandmarkOverlay(
                                        resultBundle = resultBundle,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    )

                    VoiceTranscriptBox(
                        modifier = Modifier.weight(1f),
                        currentMaximizedState = maximizedCard,
                        onToggleMaximize = { maximizedCard = MaximizedState.NONE },
                        chatBoxTitle = "Teks Percakapan",
                        transcriptText = uiState.speechState.transcriptText.ifEmpty {
                            if (uiState.speechState.isListening) "Mendengarkan..."
                            else "Ketuk mikrofon untuk mulai merekam"
                        },
                        isListening = uiState.speechState.isListening,
                        onMicClick = {
                            if (hasAudioPermission) {
                                viewModel.toggleSpeechRecording(context)
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onSignLanguageClick = { showUnderDevelopmentDialog = true },
                    )
                }

                MaximizedState.NONE -> {
                    SignTranscriptBox(
                        modifier = Modifier.weight(1f),
                        currentMaximizedState = maximizedCard,
                        onToggleMaximize = { maximizedCard = MaximizedState.SIGN },
                        chatBoxTitle = "Makna Isyaratmu",
                        transcriptText = signLanguageState.predictedSign,
                        cameraPreview = {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AndroidView(
                                    factory = { previewView },
                                    modifier = Modifier.fillMaxSize(),
                                    update = { view ->
                                        view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                        view.scaleType = PreviewView.ScaleType.FILL_CENTER
                                    }
                                )

                                signLanguageState.handLandmarkerResult?.let { resultBundle ->
                                    LandmarkOverlay(
                                        resultBundle = resultBundle,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    )

                    VoiceTranscriptBox(
                        modifier = Modifier.weight(1f),
                        currentMaximizedState = maximizedCard,
                        onToggleMaximize = { maximizedCard = MaximizedState.VOICE },
                        chatBoxTitle = "Teks Percakapan",
                        transcriptText = uiState.speechState.transcriptText.ifEmpty {
                            if (uiState.speechState.isListening) "Mendengarkan..."
                            else "Ketuk mikrofon untuk mulai merekam"
                        },
                        isListening = uiState.speechState.isListening,
                        onMicClick = {
                            if (hasAudioPermission) {
                                viewModel.toggleSpeechRecording(context)
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onSignLanguageClick = { showUnderDevelopmentDialog = true },
                    )
                }
            }
        }
    }

    if (showUnderDevelopmentDialog) {
        DevelopmentDialog(onDismiss = { showUnderDevelopmentDialog = false })
    }
}

@Preview(showBackground = true)
@Composable
fun TalkScreenPreview() {
    TalkScreen()
}