package com.octetproof.sample

import android.graphics.Color as AndroidColor
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

private val GnssGreen = Color(0xFF16A34A)
private val FusedBlue = Color(0xFF2563EB)

/**
 * Location · Fix — the device's position on a map with two accuracy circles: the
 * satellite-only **GPS** fix (green) and the OS **fused** fix (blue). The circle
 * radius is the reported horizontal accuracy, so a tighter circle = a more precise
 * fix. Real platform data — the fix is genuine on both platforms.
 */
@Composable
fun FixMapScreen(nav: NavController) {
    val ctx = LocalContext.current
    val m = remember { SensorMonitor(ctx) }
    DisposableEffect(Unit) {
        m.start()
        onDispose { m.stop() }
    }

    BackScaffold("Location · Fix", { nav.popBackStack() }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FixMap(
                gps = m.coordinate, gpsAccM = m.horizontalAccuracyM,
                fused = m.fusedCoordinate, fusedAccM = m.fusedAccuracyM,
                Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(14.dp)),
            )

            SampleCard {
                LegendRow(GnssGreen, "GNSS fix", m.locationSource)
                m.coordinate?.let { (lat, lon) ->
                    FixLine("%.5f, %.5f".format(lat, lon), m.horizontalAccuracyM)
                } ?: WaitingLine()
                Spacer(Modifier.height(8.dp))
                LegendRow(FusedBlue, "Fused fix", m.fusedSource)
                m.fusedCoordinate?.let { (lat, lon) ->
                    FixLine("%.5f, %.5f".format(lat, lon), m.fusedAccuracyM)
                } ?: WaitingLine()
                Spacer(Modifier.height(8.dp))
                Text(
                    "Circle radius = reported horizontal accuracy (smaller = more precise). " +
                        "The GPS fix is satellite-only; the fused fix blends GNSS, network and sensors — " +
                        "so the two centres and circles usually differ.",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                )
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, name: String, source: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, color = Theme.accent)
        Spacer(Modifier.weight(1f))
        Text(source, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

@Composable
private fun FixLine(coords: String, accM: Float?) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(coords, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.weight(1f))
        Text(accM?.let { "±%.0f m".format(it) } ?: "±— m",
            style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, color = Color.Gray)
    }
}

@Composable
private fun WaitingLine() =
    Text("waiting for fix…", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

@Composable
private fun FixMap(
    gps: Pair<Double, Double>?,
    gpsAccM: Float?,
    fused: Pair<Double, Double>?,
    fusedAccM: Float?,
    modifier: Modifier,
) {
    val ctx = LocalContext.current
    val mapView = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(3.0)
            controller.setCenter(GeoPoint(20.0, 0.0))
            onResume()
        }
    }
    var zoomed by remember { mutableStateOf(false) }
    AndroidView(factory = { mapView }, modifier = modifier, update = { mv ->
        mv.overlays.clear()

        fun addFix(pt: GeoPoint, accM: Float?, color: Color, title: String) {
            if (accM != null && accM > 0f) {
                mv.overlays.add(Polygon(mv).apply {
                    points = Polygon.pointsAsCircle(pt, accM.toDouble())
                    fillPaint.color = AndroidColor.argb(48, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
                    outlinePaint.color = AndroidColor.argb(220, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
                    outlinePaint.strokeWidth = 4f
                })
            }
            mv.overlays.add(Marker(mv).apply {
                position = pt
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                this.title = title
            })
        }

        fused?.let { (la, lo) -> addFix(GeoPoint(la, lo), fusedAccM, FusedBlue, "Fused fix") }
        gps?.let { (la, lo) -> addFix(GeoPoint(la, lo), gpsAccM, GnssGreen, "GNSS fix") }

        // Auto-frame on the first available fix (GPS preferred, else fused).
        val focus = gps ?: fused
        if (!zoomed && focus != null) {
            mv.controller.setZoom(16.0)
            mv.controller.animateTo(GeoPoint(focus.first, focus.second))
            zoomed = true
        }
        mv.invalidate()
    })
}
