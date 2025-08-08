package com.example.icara.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


sealed class DialogOption(val lang: String) {
    data class Disabled(val opt: String) : DialogOption(opt)
    data class Enabled(val opt: String) : DialogOption(opt)
}

@Composable
fun SelectionDialog(
    onDismissRequest: () -> Unit,
    onAction: (DialogOption) -> Unit,
    title: String,
    options: List<DialogOption>
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
                options.forEach { option ->
                    when (option) {
                        is DialogOption.Enabled -> {
                            Button(
                                onClick = { onAction(option) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(text = option.lang)
                            }
                        }

                        is DialogOption.Disabled -> {
                            Button(
                                onClick = { onAction(option) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                    contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            ) {
                                Text(text = option.lang)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}