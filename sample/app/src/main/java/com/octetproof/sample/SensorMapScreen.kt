package com.octetproof.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay

/**
 * The "Show on map" destination: the device's own position on a geographic map,
 * stacked over a 2.5D sky dome of the satellites in reach. Cell towers are NOT
 * plotted — Android exposes their signal but not their geographic position — so
 * they live as a signal-strength readout on the Sensors screen instead.
 */
@Composable
fun SensorMapScreen(nav: NavController) {
    val ctx = LocalContext.current
    val m = remember { SensorMonitor(ctx) }
    DisposableEffect(Unit) {
        m.start()
        onDispose { m.stop() }
    }
    LaunchedEffect(Unit) { while (true) { delay(4000); m.refreshCellular() } }

    BackScaffold("Location & sky", { nav.popBackStack() }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("YOUR POSITION", style = MaterialTheme.typography.labelMedium, color = Theme.accent)
            OsmMap(m.coordinate, Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(14.dp)))
            m.coordinate?.let { (lat, lon) ->
                Text(
                    "%.5f, %.5f".format(lat, lon) + (m.horizontalAccuracyM?.let { "  ·  ±%.0f m".format(it) } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                )
            }

            Text("SATELLITES IN VIEW", style = MaterialTheme.typography.labelMedium, color = Theme.accent,
                modifier = Modifier.padding(top = 8.dp))
            SampleCard {
                if (m.satellites.isEmpty()) {
                    Text("Waiting for satellite positions… (go outside or near a window for a fix)",
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                } else {
                    SkyDome(m.satellites, Modifier.fillMaxWidth().aspectRatio(1.35f))
                    SkyDomeLegend(m.satellites, Modifier.padding(top = 8.dp))
                    Text(
                        "${m.satellites.size} placed · ${m.satellitesUsedInFix} used in fix. " +
                            "Overhead is the top of the dome; the rim is the horizon. Filled = used in fix, size = signal.",
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            Text(
                "Nearby cell towers can't be shown here — Android exposes their signal but not their position. " +
                    "See the Cellular card on the Sensors screen for the per-cell signal readout.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray,
            )
        }
    }
}
