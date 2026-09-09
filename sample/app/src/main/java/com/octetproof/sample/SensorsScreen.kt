package com.octetproof.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay

/** Live device-sensor diagnostics (from the hidden Dev menu). Public-framework
 *  data only. Android exposes satellites, cell towers, and Wi-Fi for real. */
@Composable
fun SensorsScreen(nav: NavController) {
    val ctx = LocalContext.current
    val m = remember { SensorMonitor(ctx) }
    DisposableEffect(Unit) {
        m.start()
        onDispose { m.stop() }
    }
    // Re-poll the snapshot-style sources (cell / Wi-Fi / battery) every few seconds.
    LaunchedEffect(Unit) {
        while (true) { delay(3000); m.refreshCellular(); m.refreshWifi(); m.refreshBattery() }
    }

    BackScaffold("Sensors", { nav.popBackStack() }) { pad ->
    LazyColumn(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SampleCard {
                CardTitle("Location & GNSS")
                Kv("Source", m.locationSource, "Source")
                Kv("Authorization", m.authStatus, "Authorization")
                m.coordinate?.let { (lat, lon) ->
                    Kv("Latitude", "%.5f".format(lat), "Coordinates")
                    Kv("Longitude", "%.5f".format(lon))
                }
                Kv("Horizontal acc.", m.horizontalAccuracyM?.let { "±%.0f m".format(it) } ?: "—", "Horizontal accuracy")
                Kv("Vertical acc.", m.verticalAccuracyM?.let { "±%.0f m".format(it) } ?: "—", "Vertical accuracy")
                Kv("Altitude", m.altitudeM?.let { "%.0f m".format(it) } ?: "—", "Altitude")
                Kv("Speed", m.speedMps?.let { "%.1f m/s".format(it) } ?: "—", "Speed")
                Kv("Course", m.courseDeg?.let { "%.0f°".format(it) } ?: "—", "Course")
                Kv("Satellites (visible)", m.satellitesTotal?.toString() ?: "…", "Satellites (visible)")
                Kv("Used in fix", m.satellitesUsedInFix.toString(), "Used in fix")
                if (m.constellations.isNotEmpty()) {
                    Kv("Constellations", m.constellations.entries.joinToString(" · ") { "${it.key} ${it.value}" }, "Constellations")
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { nav.navigate("sensorMap") }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Show on map", color = Theme.accent)
                    InfoDot("Sky dome")
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Filled.ChevronRight, null, tint = Theme.accent)
                }
            }
        }

        item {
            SampleCard {
                CardTitle("Motion & inertial")
                SensorRow("Accelerometer", m.accelAvailable, m.accel?.let { fmt3(it, "m/s²") })
                SensorRow("Gyroscope", m.gyroAvailable, m.gyro?.let { fmt3(it, "rad/s") })
                SensorRow("Magnetometer", m.magAvailable, m.mag?.let { fmt3(it, "µT") })
                SensorRow("Barometer", m.barometerAvailable, m.pressureHpa?.let { "%.1f hPa".format(it) })
            }
        }

        item {
            SampleCard {
                CardTitle("Cellular & radio")
                Kv("Radio access", m.radioTech, "Radio access")
                Kv("Cells in range", m.cellCount?.toString() ?: "…", "Cells in range")
                if (m.cells.isEmpty()) {
                    Text("No neighbouring cells reported.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                } else {
                    Spacer(Modifier.height(4.dp))
                    m.cells.forEachIndexed { i, c -> CellRow(i + 1, c) }
                    Text("Signal only — tower positions aren't exposed by the OS.",
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        item {
            SampleCard {
                CardTitle("Wi-Fi")
                Kv("Networks in range", m.wifiCount?.toString() ?: "…", "Networks in range")
                m.wifiTop.forEach { (ssid, level) -> Kv(ssid, "$level dBm") }
                if (m.wifiCount == 0) {
                    Text(
                        "No scan results — enable Wi-Fi + location, and note Android throttles background scans.",
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                    )
                }
            }
        }

        item {
            SampleCard {
                CardTitle("Battery")
                val b = m.battery
                Kv("Level", b?.levelPct?.let { "$it%" } ?: "—", "Battery level")
                Kv("Charging", b?.let { if (it.charging) "yes" else "no" } ?: "—")
                Kv("Current draw", b?.currentMa?.let { "~$it mA" } ?: "—", "Current draw")
                Kv("Charge counter", b?.chargeCounterMah?.let { "$it mAh" } ?: "—", "Charge counter")
            }
        }
    }
    }
}

@Composable
private fun CardTitle(t: String) =
    Text(t.uppercase(), style = MaterialTheme.typography.labelMedium, color = Theme.accent)

@Composable
private fun Kv(key: String, value: String, info: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        if (info != null && signalGlossary.containsKey(info)) InfoDot(info)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

/** One neighbouring cell: kind, a signal bar, and dBm. No position — see note. */
@Composable
private fun CellRow(index: Int, c: CellSignal) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${c.tech} #$index", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.width(64.dp))
        SignalBar(c.dbm, Modifier.weight(1f).padding(horizontal = 8.dp))
        Text("${c.dbm} dBm", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.width(72.dp))
    }
}

/** A horizontal signal bar. dBm ~ -120 (weak) … -40 (strong). */
@Composable
private fun SignalBar(dbm: Int, modifier: Modifier = Modifier) {
    val frac = ((dbm + 120f) / 80f).coerceIn(0f, 1f)
    val tint = when {
        frac > 0.66f -> Theme.pass
        frac > 0.33f -> Theme.warn
        else -> Theme.fail
    }
    Box(modifier.height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.Gray.copy(alpha = 0.25f))) {
        Box(Modifier.fillMaxWidth(frac).height(8.dp).clip(RoundedCornerShape(4.dp)).background(tint))
    }
}

@Composable
private fun SensorRow(name: String, available: Boolean, live: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(if (!available) StatusKind.BAD else if (live != null) StatusKind.OK else StatusKind.PENDING, 14.dp)
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        if (signalGlossary.containsKey(name)) InfoDot(name)
        Spacer(Modifier.weight(1f))
        Text(if (!available) "unavailable" else (live ?: "…"), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

private fun fmt3(t: Triple<Float, Float, Float>, unit: String) =
    "%.2f %.2f %.2f %s".format(t.first, t.second, t.third, unit)
