package com.octetproof.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.octetproof.sdk.api.Octet

/** The (opt-in) Device tab — capabilities + states, grouped. No identifiers. */
@Composable
fun DeviceInfoScreen(vm: SampleViewModel) {
    val ctx = LocalContext.current
    val categories = DeviceInfo.categories(ctx)
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Device", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        categories.forEach { cat ->
            item {
                SampleCard {
                    Text(cat.title.uppercase(), style = MaterialTheme.typography.labelMedium, color = Theme.accent)
                    cat.rows.forEach { Kv(it.key, it.value) }
                }
            }
        }
        item {
            SampleCard {
                Text("SDK / LICENSE", style = MaterialTheme.typography.labelMedium, color = Theme.accent)
                Kv("SDK", "v${Octet.SDK_VERSION}")
                Kv("license", vm.licenseLine.ifEmpty { "—" })
            }
        }
    }
}

@Composable
private fun Kv(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Spacer(Modifier.padding(horizontal = 4.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
