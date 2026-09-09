package com.octetproof.sample

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/** The Generate tab: pipeline + region picker + map + generate + result + debug. */
@Composable
fun GenerateScreen(vm: SampleViewModel, nav: NavController) {
    var regionSheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Header(vm, nav) }
        item { PipelineCard(vm) }
        vm.licenseNotice?.let { notice ->
            item {
                Text(
                    notice,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Theme.warn.copy(alpha = 0.15f))
                        .padding(8.dp),
                )
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Region", color = Color.Gray)
                Spacer(Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { regionSheet = true },
                ) {
                    Text("${vm.selectedCountry.name} (${vm.selectedCountry.iso})", color = Theme.accent)
                    Icon(Icons.Filled.ChevronRight, null, tint = Theme.accent)
                }
            }
        }
        item { OsmMap(vm.userLocation, Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(14.dp))) }
        item {
            Button(
                onClick = { vm.generate() },
                enabled = vm.isReady && !vm.isGenerating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (vm.isGenerating) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (vm.isGenerating) "Generating…" else "Generate Proof", fontWeight = FontWeight.Bold)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Force fresh proof", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Bypass the cache and mint a brand-new proof each time",
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                    )
                }
                Switch(
                    checked = vm.forceFresh,
                    onCheckedChange = { vm.forceFresh = it },
                    enabled = !vm.isGenerating,
                )
            }
        }
        vm.lastResult?.let { item { ResultCard(it, nav) } }
        if (vm.settings.showDebug) item { DebugCard(vm) }
    }

    if (regionSheet) {
        RegionPickerSheet(
            includeAny = false,
            selectedIso = vm.selectedCountry.iso,
            onPick = { iso -> iso?.let { code -> demoCountries.firstOrNull { it.iso == code }?.let(vm::userSelectedCountry) }; regionSheet = false },
            onDismiss = { regionSheet = false },
        )
    }
}

@Composable
private fun Header(vm: SampleViewModel, nav: NavController) {
    var taps by remember { mutableStateOf(0) }
    Column(
        modifier = Modifier.fillMaxWidth().clickable {
            taps += 1
            if (taps >= 5) { taps = 0; vm.settings.devMenuUnlocked = true; nav.navigate("devSettings") }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Octetproof", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("  · Sample", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
        }
        Text(
            "SDK v${com.octetproof.sdk.api.Octet.SDK_VERSION} · " +
                (vm.licenseLine.ifEmpty { vm.sdkState.label }),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
        )
    }
}

@Composable
private fun PipelineCard(vm: SampleViewModel) {
    SampleCard {
        Text("PIPELINE", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        vm.steps.forEach { step ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                StatusDot(step.kind)
                Spacer(Modifier.width(8.dp))
                Text(
                    step.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (step.state == StepState.PENDING) Color.Gray else Color.Unspecified,
                )
                Spacer(Modifier.weight(1f))
                step.seconds?.let { Text("%.1fs".format(it), fontFamily = FontFamily.Monospace, color = Color.Gray) }
            }
        }
    }
}

@Composable
private fun ResultCard(result: GenerateResult, nav: NavController) {
    val kind = when (result.result) {
        com.octetproof.sdk.api.OctetVerdict.Result.YES -> StatusKind.OK
        com.octetproof.sdk.api.OctetVerdict.Result.NO -> StatusKind.BAD
        com.octetproof.sdk.api.OctetVerdict.Result.INDETERMINATE -> StatusKind.WARN
    }
    val headline = when (result.result) {
        com.octetproof.sdk.api.OctetVerdict.Result.YES -> "Inside ${result.regionLabel}"
        com.octetproof.sdk.api.OctetVerdict.Result.NO -> "Outside ${result.regionLabel}"
        com.octetproof.sdk.api.OctetVerdict.Result.INDETERMINATE -> "Indeterminate"
    }
    // Fade + slight scale-in on every new result so a fresh run is unmistakable
    // even when it's fast (keyed on `result`, so each Generate re-animates).
    val appear = remember(result) { Animatable(0f) }
    LaunchedEffect(result) { appear.animateTo(1f, tween(350)) }
    SampleCard(
        modifier = Modifier.graphicsLayer {
            alpha = appear.value
            val s = 0.97f + 0.03f * appear.value
            scaleX = s; scaleY = s
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(kind)
            Spacer(Modifier.width(8.dp))
            Text(headline, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(result.bucket.label, result.bucket.tint.color())
            Text("%.2f".format(result.score), fontFamily = FontFamily.Monospace, color = Color.Gray)
            if (result.stored != null) {
                if (result.fresh) Chip("✓ fresh", Theme.pass) else Chip("cached")
            }
        }
        result.battery?.let { b ->
            Spacer(Modifier.height(6.dp))
            BatteryLine(b)
        }
        val stored = result.stored
        if (stored != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Verify ▸",
                color = Theme.accent,
                modifier = Modifier.clickable { nav.navigate("verifyDetail/${stored.id}") },
            )
        } else {
            Text("No proof produced under current conditions.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

/** Per-proof battery consumption, measured from the hardware charge counter. */
@Composable
private fun BatteryLine(b: BatteryCost) {
    val text = when {
        b.charging -> "Battery: shown only off-charger — unplug the charger to measure a proof's consumption"
        b.mAh != null -> {
            val pct = b.pct?.takeIf { it > 0 }?.let { " ($it%)" } ?: ""
            val avg = b.avgMa?.let { " · ~$it mA avg" } ?: ""
            "Battery: %.2f mAh%s%s · %.1fs".format(b.mAh, pct, avg, b.durationSec)
        }
        else -> "Battery: below the charge counter's resolution this run (%.1fs)".format(b.durationSec)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        InfoDot("Battery used")
    }
}

@Composable
private fun DebugCard(vm: SampleViewModel) {
    SampleCard {
        Text("DEBUG (${vm.debug.size})", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Spacer(Modifier.height(4.dp))
        vm.debug.takeLast(50).forEach { line ->
            Text(line.text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = Color.Gray)
        }
        Text("no coordinates · keys · tokens · IDs shown", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@Composable
fun OsmMap(location: Pair<Double, Double>?, modifier: Modifier) {
    val ctx = LocalContext.current
    val mapView = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(4.0)
            controller.setCenter(GeoPoint(47.5, 14.5))
            onResume()
        }
    }
    var zoomed by remember { mutableStateOf(false) }
    AndroidView(factory = { mapView }, modifier = modifier, update = { mv ->
        location?.let { (lat, lon) ->
            val pt = GeoPoint(lat, lon)
            mv.overlays.clear()
            mv.overlays.add(Marker(mv).apply {
                position = pt
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "You"
            })
            if (!zoomed) { mv.controller.setZoom(14.0); mv.controller.animateTo(pt); zoomed = true }
            mv.invalidate()
        }
    })
}

/**
 * Reusable searchable region picker (Generate + Verify). A bottom sheet with a
 * LazyColumn — smooth scrolling, no menu rebuild. `includeAny` adds an
 * "Any region" row (Verify) that clears the selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionPickerSheet(
    includeAny: Boolean,
    selectedIso: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) demoCountries
        else demoCountries.filter { it.name.contains(q, true) || it.iso.contains(q, true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default,
            placeholder = { Text("Search country") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
            if (includeAny && query.isBlank()) {
                item {
                    ListItem(
                        headlineContent = { Text("Any region — don't check") },
                        trailingContent = { if (selectedIso == null) Icon(Icons.Filled.ChevronRight, null, tint = Theme.accent) },
                        modifier = Modifier.clickable { onPick(null) },
                    )
                    HorizontalDivider()
                }
            }
            items(filtered) { c ->
                ListItem(
                    headlineContent = { Text("${c.name} (${c.iso})") },
                    trailingContent = { if (selectedIso == c.iso) Icon(Icons.Filled.ChevronRight, null, tint = Theme.accent) },
                    modifier = Modifier.clickable { onPick(c.iso) },
                )
            }
        }
    }
}
