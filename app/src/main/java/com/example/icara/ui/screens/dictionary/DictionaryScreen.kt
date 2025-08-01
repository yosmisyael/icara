package com.example.icara.ui.screens.dictionary

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.icara.ui.components.SearchBar
import com.example.icara.viewmodels.DictionaryViewModel
import com.google.gson.Gson
import kotlinx.parcelize.Parcelize

@Parcelize
data class DictionaryEntry(
    val word: String,
    val signLanguage: String,
    val initialLetter: String
): Parcelable

@Composable
fun DictionaryScreen(
    navController: NavController,
    onNavigateBack: () -> Unit,
    viewModel: DictionaryViewModel = viewModel(),
) {
    // Listen for navigation events from the ViewModel
    LaunchedEffect(Unit) {
        viewModel.navigationChannel.collect { entry ->
            // Execute navigation when an event is received
            val entryJson = Gson().toJson(entry)
            navController.navigate("dictionary_detail/$entryJson")
        }
    }

    // Call the UI composable
    DictionaryScreenContent(
        onNavigateBack = onNavigateBack,
        onEntryClick = { entry ->
            viewModel.navigateToDictionaryDetailScreen(entry)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreenContent(
    onNavigateBack: () -> Unit,
    onEntryClick: (DictionaryEntry) -> Unit,
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
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // active state card style
            val activeEntryCard = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )

            // default state card style
            val defaultEntryCard = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )

            // active state icon style
            val activeIconCardColors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            // default state icon style
            val defaultIconCardColors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface
            )

            // Screen headline
            Text(
                text = "Kamus Isyarat",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Search bar
            SearchBar(
                searchText = searchText,
                onSearchTextChanged = { searchText = it },
                modifier = Modifier.padding(top = 16.dp),
            )

            // Dictionary entries
            LazyColumn(
                contentPadding = PaddingValues(top = 16.dp),
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(dictionaryList) { entry ->
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

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.word == "Terimakasih") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
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
                        text = entry.initialLetter,
                        style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        color = currentIconColors.contentColor,
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = entry.word,
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

            // This is the icon box
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
    DictionaryScreenContent(
        onNavigateBack = { },
        onEntryClick = { },
    )
}