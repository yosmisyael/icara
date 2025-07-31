package com.example.icara.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun SelectionDialog(
    onDismissRequest: () -> Unit,
    onOptionSelected: (String) -> Unit,
    title: String,
    options: List<String>
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column (
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEach { optionText ->
                    Button(
                        onClick = { onOptionSelected(optionText) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = optionText)
                    }
                }
            }
        },
        // We don't need the default confirm/dismiss buttons for this design
        confirmButton = {},
        dismissButton = {}
    )
}

@Preview
@Composable
fun SelectionDialogPreview() {
    SelectionDialog(
        title = "Pilih Bahasa Isyarat",
        options = listOf("BISINDO", "SIBI"),
        onDismissRequest = {},
        onOptionSelected = {},
    )
}