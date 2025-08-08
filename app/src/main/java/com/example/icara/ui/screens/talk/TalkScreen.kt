package com.example.icara.ui.screens.talk

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
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
import com.example.icara.ui.components.SignLanguageSelector
import com.example.icara.ui.components.SignTranscriptCard
import com.example.icara.ui.components.VoiceTranscriptCard
import com.example.icara.viewmodels.TalkViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalkScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: TalkViewModel = viewModel(),
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
                    SignLanguageSelector(
                        modifier = Modifier.padding(end = 16.dp)
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

@Preview(showBackground = true)
@Composable
fun TalkScreenPreview() {
    TalkScreen()
}
