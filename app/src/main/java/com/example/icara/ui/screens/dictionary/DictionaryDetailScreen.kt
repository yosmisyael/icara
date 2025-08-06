package com.example.icara.ui.screens.dictionary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.example.icara.ui.components.VideoPlayerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryDetailScreen(
    sharedViewModel: SharedDictionaryViewModel,
    onNavigateBack: () -> Unit
) {
    val selectedState by sharedViewModel.selectedEntry.collectAsState()
    val entry = selectedState!!

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    TextButton(
                        onClick = { onNavigateBack() },
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp)
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Main word title
            Text(
                text = entry.name,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )

            // Sign language type
            Text(
                text = entry.signLanguage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Video Player Card
            VideoPlayerCard(
                streamUrl = entry.streamUrl,
                modifier = Modifier.fillMaxWidth()
            )

            // Additional Information (if available)
            if (entry.aliases.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Kata Lain:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = entry.aliases.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Source information (if available)
            entry.source?.let { source ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Sumber: $source",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerCard(
    streamUrl: String,
    modifier: Modifier = Modifier
) {
    var playerState by remember { mutableStateOf(VideoPlayerState.LOADING) }
    var errorMessage by remember { mutableStateOf("") }
    val context = LocalContext.current

    // create custom data source factory
    val dataSourceFactory = remember {
        DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
            .setConnectTimeoutMs(30000)
            .setReadTimeoutMs(30000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "Accept" to "*/*",
                    "Accept-Encoding" to "gzip, deflate, br",
                    "Accept-Language" to "en-US,en;q=0.9,id;q=0.8",
                    "Cache-Control" to "no-cache",
                    "Connection" to "keep-alive",
                    "Pragma" to "no-cache",
                    "Sec-Fetch-Dest" to "video",
                    "Sec-Fetch-Mode" to "cors",
                    "Sec-Fetch-Site" to "cross-site"
                )
            )
    }

    // create ExoPlayer instance
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(dataSourceFactory)
            )
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 3000,
                        /* maxBufferMs = */ 20000,
                        /* bufferForPlaybackMs = */ 3000,
                        /* bufferForPlaybackAfterRebufferMs = */ 2000
                    )
                    .build()
            )
            .build()
            .apply {
                // Add listener for player state changes
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        playerState = when (playbackState) {
                            Player.STATE_BUFFERING -> VideoPlayerState.BUFFERING
                            Player.STATE_READY -> VideoPlayerState.READY
                            Player.STATE_ENDED -> VideoPlayerState.READY
                            else -> playerState
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        playerState = VideoPlayerState.ERROR
                        errorMessage = when (error.errorCode) {
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                                "Tidak dapat terhubung ke server video. Periksa koneksi internet Anda."
                            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                                // Get more specific error info
                                val cause = error.cause
                                when {
                                    cause is HttpDataSource.InvalidResponseCodeException -> {
                                        "Server menolak permintaan (${cause.responseCode}): ${cause.responseMessage ?: "Video tidak tersedia"}"
                                    }
                                    else -> "Video tidak ditemukan atau server tidak dapat diakses."
                                }
                            }
                            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                                "Format video tidak didukung atau rusak."
                            PlaybackException.ERROR_CODE_IO_UNSPECIFIED ->
                                "Terjadi kesalahan jaringan. Periksa koneksi internet dan coba lagi."
                            else -> "Terjadi kesalahan saat memuat video: ${error.message}"
                        }

                        // Log the full error for debugging
                        println("ExoPlayer Error: ${error.message}")
                        println("Error cause: ${error.cause}")
                    }
                })
            }
    }

    // setup video source
    LaunchedEffect(streamUrl) {
        if (streamUrl.isNotBlank()) {
            try {
                println("Loading video URL: $streamUrl") // Debug log
                val mediaItem = MediaItem.Builder()
                    .setUri(streamUrl)
                    .build()

                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                playerState = VideoPlayerState.LOADING
            } catch (e: Exception) {
                playerState = VideoPlayerState.ERROR
                errorMessage = "Gagal memuat video: ${e.message}"
                println("Error setting up video: ${e.message}")
            }
        } else {
            playerState = VideoPlayerState.ERROR
            errorMessage = "URL video tidak valid"
        }
    }

    // clean up player when composable is disposed
    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        ) {
            when (playerState) {
                VideoPlayerState.LOADING -> {
                    LoadingVideoContent()
                }

                VideoPlayerState.READY, VideoPlayerState.BUFFERING -> {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                controllerShowTimeoutMs = 700
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (playerState == VideoPlayerState.BUFFERING) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                VideoPlayerState.ERROR -> {
                    ErrorVideoContent(
                        errorMessage = errorMessage,
                        onRetry = {
                            playerState = VideoPlayerState.LOADING
                            try {
                                val mediaItem = MediaItem.Builder()
                                    .setUri(streamUrl)
                                    .build()
                                exoPlayer.setMediaItem(mediaItem)
                                exoPlayer.prepare()
                            } catch (e: Exception) {
                                playerState = VideoPlayerState.ERROR
                                errorMessage = "Gagal memuat video: ${e.message}"
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingVideoContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Memuat video...",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun ErrorVideoContent(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Gagal Memuat Video",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Coba Lagi")
            }
        }
    }
}