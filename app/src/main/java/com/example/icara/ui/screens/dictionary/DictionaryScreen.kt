package com.example.icara.ui.screens.dictionary

import android.annotation.SuppressLint
import android.os.Parcelable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.icara.data.model.DictionaryEntry
import com.example.icara.data.model.EntryType
import com.example.icara.ui.components.SearchBar
import com.example.icara.viewmodels.DictionaryUiState
import com.example.icara.viewmodels.DictionaryViewModel
import com.google.gson.Gson

@Composable
fun DictionaryScreen(
    navController: NavController,
    onNavigateBack: () -> Unit,
    viewModel: DictionaryViewModel = viewModel(),
    sharedViewModel: SharedDictionaryViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()

    // Listen for navigation events from the ViewModel
    LaunchedEffect(Unit) {
        viewModel.navigationChannel.collect { entry ->
            // Execute navigation when an event is received
            sharedViewModel.setSelectedEntry(entry)
            navController.navigate("dictionary_detail")
        }
    }

    // Call the UI composable
    DictionaryScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onEntryClick = { entry ->
            viewModel.navigateToDictionaryDetailScreen(entry)
        },
        onSearchQueryChanged = { query ->
            viewModel.updateSearchQuery(query)
        },
        onRetry = {
            viewModel.retry()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreenContent(
    uiState: DictionaryUiState,
    onNavigateBack: () -> Unit,
    onEntryClick: (DictionaryEntry) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onRetry: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { },
                navigationIcon = {
                    TextButton(
                        onClick = onNavigateBack,
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
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        ) {
            // Screen headline
            Text(
                text = "Kamus Isyarat",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Search bar
            SearchBar(
                searchText = uiState.searchQuery,
                onSearchTextChanged = onSearchQueryChanged,
                modifier = Modifier.padding(top = 16.dp),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp)
            ) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    uiState.error != null && uiState.allEntries.isEmpty() -> {
                        // Show error only if we have no cached data at all
                        ErrorContent(
                            error = uiState.error,
                            isOffline = uiState.isOffline,
                            onRetry = onRetry,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    uiState.entries.isEmpty() && uiState.allEntries.isNotEmpty() -> {
                        // Show empty search results (data exists but search filtered everything out)
                        EmptyContent(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    uiState.entries.isEmpty() && uiState.allEntries.isEmpty() -> {
                        // Show empty state when no data at all
                        EmptyContent(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    else -> {
                        DictionaryList(
                            entries = uiState.entries,
                            onEntryClick = onEntryClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorContent(
    error: String,
    isOffline: Boolean = false,
    onRetry: () -> Unit,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (isOffline) Icons.Default.WifiOff else Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isOffline) "Tidak Ada Koneksi" else "Terjadi Kesalahan",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )

        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )

        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isOffline) "Coba Koneksi Lagi" else "Coba Lagi")
        }
    }
}

@Composable
fun EmptyContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Tidak ada data ditemukan",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Coba ubah kata kunci pencarian",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun DictionaryList(
    entries: List<DictionaryEntry>,
    onEntryClick: (DictionaryEntry) -> Unit
) {
    // Card colors for active and default states
    val activeEntryCard = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    )

    val defaultEntryCard = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )

    val activeIconCardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    val defaultIconCardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(entries) { entry ->
            DictionaryItem(
                entry = entry,
                onClick = { onEntryClick(entry) },
                activeIconColors = activeIconCardColors,
                defaultIconColors = defaultIconCardColors,
                defaultCardColors = defaultEntryCard,
                activeCardColors = activeEntryCard,
            )
        }
    }
}

@Composable
fun DictionaryItem(
    entry: DictionaryEntry,
    onClick: () -> Unit,
    activeCardColors: CardColors,
    defaultCardColors: CardColors,
    activeIconColors: CardColors,
    defaultIconColors: CardColors
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressedState: State<Boolean> = interactionSource.collectIsPressedAsState()
    val currentCardColors = if (isPressedState.value) activeCardColors else defaultCardColors
    val currentIconColors = if (isPressedState.value) activeIconColors else defaultIconColors

    val initialLetter = entry.name.firstOrNull()?.uppercaseChar()?.toString() ?: ""

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = defaultCardColors,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(currentCardColors.containerColor),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Text section
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(currentIconColors.containerColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initialLetter,
                        style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        color = currentIconColors.contentColor,
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = currentCardColors.contentColor,
                    )
                    Text(
                        text = entry.signLanguage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = currentCardColors.contentColor,
                    )
                }
            }

            // Icon box
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(80.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                // Icon entry
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
    val sampleState = DictionaryUiState(
        entries = listOf(
            DictionaryEntry(
                id = "1",
                name = "Apa",
                signLanguage = "BISINDO",
                streamUrl = "https://example.com/apa.m3u8",
                type = EntryType.WORD,
                aliases = listOf()
            ),
            DictionaryEntry(
                id = "2",
                name = "Baik",
                signLanguage = "BISINDO",
                streamUrl = "https://example.com/baik.m3u8",
                type = EntryType.WORD,
                aliases = listOf()
            ),
            DictionaryEntry(
                id = "3",
                name = "Terimakasih",
                signLanguage = "BISINDO",
                streamUrl = "https://example.com/terimakasih.m3u8",
                type = EntryType.WORD,
                aliases = listOf("terima kasih", "thanks")
            )
        )
    )

    DictionaryScreenContent(
        uiState = sampleState,
        onNavigateBack = { },
        onEntryClick = { },
        onSearchQueryChanged = { },
        onRetry = { }
    )
}