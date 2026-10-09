package com.octetproof.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

/**
 * GNSS · Sky — a live sky map of the satellites overhead (the 2.5D dome), with a
 * sortable satellite table and a tap-through detail card per satellite. Real
 * `GnssStatus` data — Android only. The richer 3D orbit globe (TLE-driven) is a
 * separate "Orbits" screen.
 */
@Composable
fun GnssSkyScreen(nav: NavController) {
    val ctx = LocalContext.current
    val m = remember { SensorMonitor(ctx) }
    DisposableEffect(Unit) {
        m.start()
        onDispose { m.stop() }
    }
    var selected by remember { mutableStateOf<SatInfo?>(null) }

    BackScaffold("GNSS · Sky", { nav.popBackStack() }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SampleCard {
                    Text("SKY DOME", style = MaterialTheme.typography.labelMedium, color = Theme.accent)
                    if (m.satellites.isEmpty()) {
                        Text(
                            "Waiting for satellite positions… go outside or near a window for a fix.",
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        SkyDome(m.satellites, Modifier.fillMaxWidth().aspectRatio(1.35f)) { selected = it }
                        SkyDomeLegend(m.satellites, Modifier.padding(top = 8.dp))
                        Text(
                            "${m.satellites.size} placed · ${m.satellitesUsedInFix} used in fix · " +
                                "strongest ${m.satellites.maxOf { it.cn0 }.toInt()} dBHz. " +
                                "Overhead = top of the dome, rim = horizon. Filled = used in fix, size = signal.",
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            if (m.satellites.isNotEmpty()) {
                item {
                    SampleCard {
                        Text("SATELLITES", style = MaterialTheme.typography.labelMedium, color = Theme.accent)
                        Spacer(Modifier.height(6.dp))
                        SatHeaderRow()
                        m.satellites.sortedByDescending { it.cn0 }.forEach { s ->
                            SatRow(s) { selected = s }
                        }
                        Text(
                            "Tap a satellite for detail. Eph/Alm = broadcast ephemeris / almanac cached.",
                            style = MaterialTheme.typography.labelSmall, color = Color.Gray,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }

    selected?.let { SatelliteDetailDialog(it) { selected = null } }
}

@Composable
private fun SatHeaderRow() {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        HCell("Sat", 56.dp)
        HCell("Band", 72.dp)
        HCell("C/N0", 0.dp, weight = 1f)
        HCell("Elev", 48.dp)
        HCell("Fix", 36.dp)
        HCell("Eph", 40.dp)
    }
}

@Composable
private fun SatRow(s: SatInfo, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.width(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(colorFor(s.constellation)))
            Spacer(Modifier.width(6.dp))
            Text(s.label, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
        Text(s.band, Modifier.width(72.dp), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Row(Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cn0Bar(s.cn0, Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            Text("${s.cn0.toInt()}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
        Text("${s.elevationDeg.toInt()}°", Modifier.width(48.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        Text(if (s.usedInFix) "✓" else "·", Modifier.width(36.dp),
            color = if (s.usedInFix) Theme.pass else Color.Gray, fontWeight = FontWeight.Bold)
        Text(if (s.hasEphemeris) "E" else if (s.hasAlmanac) "a" else "·", Modifier.width(40.dp),
            style = MaterialTheme.typography.bodySmall, color = if (s.hasEphemeris) Theme.accent else Color.Gray)
    }
}

@Composable
private fun RowScope.HCell(t: String, w: Dp, weight: Float = 0f) {
    Text(
        t,
        if (weight > 0f) Modifier.weight(weight) else Modifier.width(w),
        style = MaterialTheme.typography.labelSmall, color = Color.Gray,
    )
}

/** C/N0 bar, ~0…50 dBHz; green strong, amber ok, red weak. */
@Composable
private fun Cn0Bar(cn0: Float, modifier: Modifier = Modifier) {
    val frac = (cn0 / 50f).coerceIn(0f, 1f)
    val tint = when {
        frac > 0.66f -> Theme.pass
        frac > 0.33f -> Theme.warn
        else -> Theme.fail
    }
    Box(modifier.height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.Gray.copy(alpha = 0.25f))) {
        Box(Modifier.fillMaxWidth(frac).height(6.dp).clip(RoundedCornerShape(3.dp)).background(tint))
    }
}

@Composable
private fun SatelliteDetailDialog(s: SatInfo, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(colorFor(s.constellation)))
                Spacer(Modifier.width(8.dp))
                Text("${s.constellation} ${s.label}")
            }
        },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DetailRow("Constellation", s.constellation)
                DetailRow("SVID / PRN", s.svid.toString())
                DetailRow("Band", s.band)
                DetailRow("C/N0", "${s.cn0.toInt()} dBHz")
                DetailRow("Azimuth", "${s.azimuthDeg.toInt()}°")
                DetailRow("Elevation", "${s.elevationDeg.toInt()}°")
                DetailRow("Used in fix", if (s.usedInFix) "yes" else "no")
                DetailRow("Ephemeris", if (s.hasEphemeris) "cached" else "no")
                DetailRow("Almanac", if (s.hasAlmanac) "cached" else "no")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Orbit params (altitude · velocity · period) arrive with the 3D Orbits view, " +
                        "computed from published TLEs.",
                    style = MaterialTheme.typography.labelSmall, color = Color.Gray,
                )
            }
        },
    )
}

@Composable
private fun DetailRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(k, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        if (signalGlossary.containsKey(k)) InfoDot(k)
        Spacer(Modifier.weight(1f))
        Text(v, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}
