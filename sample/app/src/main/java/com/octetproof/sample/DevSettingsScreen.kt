package com.octetproof.sample

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

/**
 * The hidden developer menu (unlocked by 5 taps on the title). The DEBUG-only
 * section is gated by BuildConfig.DEBUG, so a release build never shows the
 * SDK-internal toggles. Nothing here surfaces coordinates/keys/tokens/IDs.
 */
@Composable
fun DevSettingsScreen(vm: SampleViewModel, nav: NavController) {
    val s = vm.settings
    BackScaffold("Developer settings", { nav.popBackStack() }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
        SectionLabel("Display")
        SwitchRow("Show debug output", s.showDebug) { s.showDebug = it }
        SwitchRow("Device info tab", s.showDeviceTab) { s.showDeviceTab = it }

        SectionLabel("Proof upload")
        SwitchRow("Upload proofs", s.uploadsEnabled) { s.uploadsEnabled = it }
        Text("↻ Applies on next launch (the upload URL is set when the SDK starts).",
            style = MaterialTheme.typography.bodySmall, color = Color.Gray)

        SectionLabel("Diagnostics")
        Row(
            Modifier.fillMaxWidth().clickable { nav.navigate("sensors") }.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sensors")
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, null, tint = Color.Gray)
        }
        Text("A live readout of this device's own sensors (motion, location, radio, satellites, cell towers, Wi-Fi). Public-framework data only — no SDK internals.",
            style = MaterialTheme.typography.bodySmall, color = Color.Gray)

        SectionLabel("Data")
        Text("Clear stored proofs", color = Theme.fail,
            modifier = Modifier.fillMaxWidth().clickable { vm.store.clear() }.padding(vertical = 12.dp))

        if (BuildConfig.DEBUG) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionLabel("Developer (debug builds only)")
            SwitchRow("Semantic-binding v2", s.semanticV2) { s.semanticV2 = it }
            SwitchRow("Verbose SDK logs (→ logcat)", s.verboseLogs) { s.verboseLogs = it }
            Text("Verbose logs raise the SDK log level; view them with `adb logcat`. This section is compiled out of release builds.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = Theme.accent,
        modifier = Modifier.padding(top = 16.dp, bottom = 2.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
