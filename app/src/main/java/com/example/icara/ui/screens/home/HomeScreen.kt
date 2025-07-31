package com.example.icara.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.icara.R
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.icara.ui.components.SelectionDialog
import com.example.icara.viewmodels.HomeViewModel
import androidx.compose.runtime.getValue
import androidx.navigation.NavController

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel()
) {
    // Collect state from the ViewModel
    val showDialog by viewModel.showLangDialog.collectAsState()

    // Listen for navigation events
    LaunchedEffect(Unit) {
        viewModel.navigateToTalkScreen.collect { userName ->
            navController.navigate("talk/$userName")
        }
    }

    // Call the UI composable, passing state down and events up
    HomeScreenContent(
        showDialog = showDialog,
        onNavigateTalk = { viewModel.onDialogTrigger() },
        onDismissDialog = { viewModel.onDialogDismiss() },
        onSelectDialogOpt = { viewModel.onLangSelected(it) },
        onNavigateDictionary = { navController.navigate("dictionary") }
    )
}

@Composable
fun HomeScreenContent(
    showDialog: Boolean,
    onDismissDialog: () -> Unit,
    onSelectDialogOpt: (String) -> Unit,
    onNavigateTalk: () -> Unit,
    onNavigateDictionary: () -> Unit,
) {
    if (showDialog) {
        SelectionDialog(
            title = "Pilih Bahasa Isyarat",
            onDismissRequest = onDismissDialog,
            onOptionSelected = onSelectDialogOpt,
            options = listOf("BISINDO", "SIBI"),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 16.dp),
                ) {
                    Text(
                        text = "Selamat Datang!",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Text(
                        text = "iCara",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Bantu Teman Tuli untuk komunikasi tanpa halangan dengan Teman Dengar",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        ) {
            paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // active state card style
                val activeMenuCardColors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )

                // default state card style
                val defaultMenuCardColors = CardDefaults.cardColors(
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

                MenuCard(
                    title = "Teman Komunikasi",
                    description = "Bantu kamu mengobrol dengan Teman Dengar",
                    imageResId = R.drawable.communicate,
                    activeColors = activeMenuCardColors,
                    activeIconColors = activeIconCardColors,
                    defaultIconColors = defaultIconCardColors,
                    defaultColors = defaultMenuCardColors,
                    onClick = { onNavigateTalk() },
                )

                MenuCard(
                    title = "Kamus Isyarat",
                    description = "Bantu kamu belajar bahasa isyarat",
                    imageResId = R.drawable.dictionary,
                    activeColors = activeMenuCardColors,
                    activeIconColors = activeIconCardColors,
                    defaultIconColors = defaultIconCardColors,
                    defaultColors = defaultMenuCardColors,
                    onClick = onNavigateDictionary,
                )
            }
        }
    }
}


// card menu component
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuCard(
    title: String,
    description: String,
    imageResId: Int,
    onClick: () -> Unit,
    activeColors: CardColors,
    defaultColors: CardColors,
    activeIconColors: CardColors,
    defaultIconColors: CardColors,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressedState: State<Boolean> = interactionSource.collectIsPressedAsState()
    val currentCardColors = if (isPressedState.value) activeColors else defaultColors
    val currentIconColors = if (isPressedState.value) activeIconColors else defaultIconColors

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = currentCardColors,
        interactionSource = interactionSource,
    ) {
        Column {
            Image(
                painter = painterResource(id = imageResId),
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(color = currentIconColors.containerColor, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "Go to $title",
                        tint = currentIconColors.contentColor
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    HomeScreenContent(
        showDialog = false,
        onSelectDialogOpt = {},
        onDismissDialog = {},
        onNavigateTalk = {},
        onNavigateDictionary = {}
    )
}