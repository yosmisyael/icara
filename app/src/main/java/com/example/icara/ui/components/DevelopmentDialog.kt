package com.example.icara.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DevelopmentDialog(onDismiss: () -> Unit) {
    AlertDialog(
        shape = RoundedCornerShape(12.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.Info,
                contentDescription = "Information",
                modifier = Modifier.size(48.dp)
            )
       },
        title = {
            Text(
                text = "Fitur Belum Tersedia",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            Text(
                text = "Fitur ini sedang dalam pengembangan dan akan segera hadir.",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Kembali",
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    )
}
