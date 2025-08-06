package com.example.icara.ui.screens.talk

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.icara.ui.components.AudioWaveform
import com.example.icara.viewmodels.TalkViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalkScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: TalkViewModel = viewModel(),
    lang: String,
) {
    // states of permission
    var hasCameraPermission by remember { mutableStateOf(false) }
    var hasAudioPermission by remember { mutableStateOf(false) }

    // get context and lifecycle owner for CameraX
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Collect the new predicted sign state
    val predictedSign by viewModel.predictedSign.collectAsStateWithLifecycle()

    // remember a PreviewView instance
    val previewView = remember { PreviewView(context) }

    // speech recognition ui state
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // get hand landmark result
    val handLandmarkerResult by viewModel.handLandmarkerResult.collectAsStateWithLifecycle()

    // Camera permission access
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasCameraPermission = isGranted
        }
    )

    // Audio permission access
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasAudioPermission = isGranted
        }
    )

    // Request permissions when the screen first appears
    LaunchedEffect(key1 = true) {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Request permissions and start the camera when they are granted
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            viewModel.setupLandmarker(context)
            viewModel.startCamera(context, lifecycleOwner, previewView.surfaceProvider)
        }
    }

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
                    Button(
                        onClick = {},
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Videocam,
                            contentDescription = "Camera Status",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Kamera Aktif",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
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
            SignTranscriptCard(
                chatBoxTitle = "Makna Isyaratmu",
                transcriptText = predictedSign,
                cameraPreview = {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )

                        handLandmarkerResult?.let { resultBundle ->
                            LandmarkOverlay(
                                resultBundle = resultBundle,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                    }
                }
            )
            VoiceTranscriptCard(
                chatBoxTitle = "Teks Percakapan",
                transcriptText = uiState.transcriptText.ifEmpty {
                    if (uiState.isListening) "Mendengarkan..."
                    else "Ketuk mikrofon untuk mulai merekam"
                },
                isListening = uiState.isListening,
                onMicClick = {
                    if (hasAudioPermission) {
                        viewModel.toggleSpeechRecording(context)
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )
        }
    }
}

// Sign Transcribe Component
@Composable
fun SignTranscriptCard(
    chatBoxTitle: String,
    transcriptText: String,
    cameraPreview: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                cameraPreview()
            }
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = chatBoxTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = transcriptText,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

// Voice Transcribe Component
@Composable
fun VoiceTranscriptCard(
    chatBoxTitle: String,
    transcriptText: String,
    isListening: Boolean = false,
    audioLevel: Float = 0f,
    onMicClick: () -> Unit,
) {
    val buttonShape = if (isListening) CircleShape else RoundedCornerShape(8.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = chatBoxTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = transcriptText,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isListening) {
                    AudioWaveform(
                        audioLevel = audioLevel,
                        isListening = isListening,
                        waveColor = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                FloatingActionButton(
                    onClick = onMicClick,
                    shape = buttonShape,
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Start or Stop voice transcription",
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TalkScreenPreview() {
    TalkScreen(lang = "BISINDO")
}
