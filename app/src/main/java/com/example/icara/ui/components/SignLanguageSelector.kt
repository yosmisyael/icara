package com.example.icara.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun SignLanguageSelector(modifier: Modifier = Modifier) {
    // state for managing whether the dropdown is expanded or not
    var expanded by remember { mutableStateOf(false) }

    // list of options and state for the currently selected one
    val options = listOf("BISINDO", "SIBI")
    var selectedOptionText by remember { mutableStateOf(options[0]) }

    // track parent width
    var buttonWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    Box(
        modifier = modifier.wrapContentSize(Alignment.TopStart)
    ) {
        Button(
            onClick = { expanded = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            modifier = Modifier.onSizeChanged {
                buttonWidth = with(density) { it.width.toDp() }
            },
        ) {
            Icon(
                imageVector = Icons.Filled.Translate,
                contentDescription = "Pilih Bahasa Isyarat",
                modifier = Modifier.size(18.dp),
            )

            Spacer(modifier = Modifier.width(8.dp))

            // display the currently selected option
            Text(
                text = selectedOptionText,
                style = MaterialTheme.typography.titleMedium,
            )

            // dropdown icon
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Buka Menu",
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(buttonWidth)
        ) {
            DropdownMenuItem(
                text = { Text("BISINDO") },
                onClick = {
                    selectedOptionText = "BISINDO"
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Translate,
                        contentDescription = "BISINDO"
                    )
                }
            )

            DropdownMenuItem(
                text = { Text("SIBI") },
                onClick = { },
                enabled = false,
                leadingIcon = {
                    Icon(
                        Icons.Filled.Translate,
                        contentDescription = "SIBI"
                    )
                }
            )
        }
    }
}
