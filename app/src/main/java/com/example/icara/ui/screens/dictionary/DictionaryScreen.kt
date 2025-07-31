package com.example.icara.ui.screens.dictionary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DictionaryEntry(
    val word: String,
    val signLanguage: String,
    val initialLetter: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(
    onNavigateBack: () -> Unit = {},
    onEntryClick: (String) -> Unit = {},
) {
    var searchText by remember { mutableStateOf("") }
    val dictionaryList = remember {
        listOf(
            DictionaryEntry("Apa", "BISINDO", "A"),
            DictionaryEntry("Baik", "BISINDO", "B"),
            DictionaryEntry("Menulis", "BISINDO", "M"),
            DictionaryEntry("Nama", "BISINDO", "N"),
            DictionaryEntry("Sedih", "BISINDO", "S"),
            DictionaryEntry("Terimakasih", "BISINDO", "T"),
            DictionaryEntry("Usia", "BISINDO", "U"),
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { },
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
            )
        }
    ) { paddingValues ->
        LazyColumn(
            contentPadding = paddingValues,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(dictionaryList) { entry ->
                DictionaryItem(
                    entry = entry,
                    onClick = {}
                )
            }
        }
    }
}

@Composable
fun DictionaryItem(entry: DictionaryEntry, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp), // A slightly larger radius looks nice
        colors = CardDefaults.cardColors(
            containerColor = if (entry.word == "Terimakasih") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        // This is the parent Row. It now controls the height of all its children.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // FIX: Add this modifier to make all children match the height of the tallest one.
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // This inner Row contains the text and its own padding.
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .weight(1f), // Use weight to allow the icon Box to have a fixed size
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = entry.initialLetter,
                        style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = entry.word,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = entry.signLanguage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            // This is the icon Box.
            Box(
                modifier = Modifier
                    // FIX: This will now correctly fill the height defined by IntrinsicSize.Min.
                    .fillMaxHeight()
                    .width(56.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                // Your placeholder or actual icon
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DictionaryScreenPreview() {
    DictionaryScreen()
}