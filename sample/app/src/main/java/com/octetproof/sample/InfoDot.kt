package com.octetproof.sample

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** One-line explanations for the (i) buttons. */
val coreGlossary: Map<String, String> = mapOf(
    "Battery used" to "How much battery this proof run consumed, from the hardware charge counter before and after. It can only be measured off-charger — while the device is plugged in there's nothing to measure.",
)

/** A small (i) button that opens a one-line explanation for a signal. */
@Composable
fun InfoDot(key: String, modifier: Modifier = Modifier) {
    val text = coreGlossary[key] ?: channelGlossary[key] ?: return
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier.size(22.dp)) {
        Icon(Icons.Outlined.Info, contentDescription = "About $key", tint = Color.Gray, modifier = Modifier.size(15.dp))
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Got it") } },
            title = { Text(key) },
            text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        )
    }
}
