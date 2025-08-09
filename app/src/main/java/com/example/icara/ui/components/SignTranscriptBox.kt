package com.example.icara.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Transcribe
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.icara.ui.screens.talk.MaximizedState

@Composable
fun SignTranscriptBox(
    modifier: Modifier = Modifier,
    currentMaximizedState: MaximizedState,
    onToggleMaximize: () -> Unit,
    chatBoxTitle: String,
    transcriptText: String,
    cameraPreview: @Composable () -> Unit,
    onFabClick: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val isMaximized = currentMaximizedState == MaximizedState.SIGN

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // camera preview
                Box(
                    modifier = if (isMaximized)  {
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    }
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    cameraPreview()
                    IconButton(
                        onClick = onToggleMaximize,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (isMaximized) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (isMaximized) "Minimize" else "Maximize"
                        )
                    }
                }

                // collapsable section
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
                        modifier = Modifier.verticalScroll(scrollState),
                    )
                }

            }
            // FAB
            FloatingActionButton(
                onClick = onFabClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Filled.Transcribe,
                    contentDescription = "Sign Language Action"
                )
            }
        }
    }
}