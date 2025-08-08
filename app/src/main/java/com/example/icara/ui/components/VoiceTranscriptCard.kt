package com.example.icara.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SignLanguage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.icara.ui.screens.talk.MaximizedState

@Composable
fun VoiceTranscriptCard(
    modifier: Modifier = Modifier,
    chatBoxTitle: String,
    transcriptText: String,
    isListening: Boolean = false,
    audioLevel: Float = 0f,
    currentMaximizedState: MaximizedState,
    onMicClick: () -> Unit,
    onSignLanguageClick: () -> Unit,
    onToggleMaximize: () -> Unit,
) {
    val buttonShape = if (isListening) CircleShape else RoundedCornerShape(8.dp)
    val scrollState = rememberScrollState()
    val isMaximized = currentMaximizedState == MaximizedState.VOICE

    Card(
        modifier = modifier
            .height(300.dp)
            .fillMaxWidth(),
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
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = if (currentMaximizedState == MaximizedState.NONE || isMaximized) 88.dp else 16.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // card title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = chatBoxTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onToggleMaximize) {
                        Icon(
                            imageVector = if (isMaximized) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isMaximized) "Minimize" else "Maximize"
                        )
                    }
                }

                // collapsable section: transcribe box
                if (currentMaximizedState == MaximizedState.NONE || isMaximized) {
                    // transcript text
                    Box(modifier = Modifier.weight(1f)) {
                        Text(
                            text = transcriptText,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(end = 12.dp),
                        )

                        AnimatedScrollbar(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(8.dp),
                            scrollState = scrollState,
                            thumbColor = MaterialTheme.colorScheme.onTertiary,
                            trackColor = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            // collapsable section: FAB
            if (currentMaximizedState == MaximizedState.NONE || isMaximized) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // microphone
                    Row(
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

                    // sign language avatar
                    FloatingActionButton(
                        onClick = onSignLanguageClick,
                        shape = RoundedCornerShape(8.dp),
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SignLanguage,
                            contentDescription = "Show avatar animation",
                        )
                    }
                }
            }
        }
    }
}