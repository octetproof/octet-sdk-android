package com.octetproof.sample

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** Colours per GNSS constellation — shared by the dome and its legend. */
private val constellationColors = mapOf(
    "GPS" to Color(0xFF4F9DFF),
    "GLONASS" to Color(0xFFFF6B6B),
    "Galileo" to Color(0xFF37C871),
    "BeiDou" to Color(0xFFFFA23A),
    "QZSS" to Color(0xFFB07BFF),
    "SBAS" to Color(0xFF00C2C2),
    "IRNSS" to Color(0xFFE85AC0),
)

private fun colorFor(constellation: String) = constellationColors[constellation] ?: Color(0xFF9AA0A6)

/**
 * A 2.5D perspective "sky dome": each satellite is placed by azimuth (compass
 * bearing) and elevation (angle above the horizon) on a tilted hemisphere seen
 * from slightly above. Overhead (zenith) rises to the top of the dome; the
 * horizon is the tilted ellipse at the base. North is at the back, South at the
 * front. Dot colour = constellation, dot size = signal (C/N₀), filled = used in
 * the current fix. Real GnssStatus data — Android only.
 */
@Composable
fun SkyDome(satellites: List<SatInfo>, modifier: Modifier = Modifier) {
    val grid = Color(0xFF5A6068)
    val labelColor = Color(0xFF9AA0A6)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h * 0.60f
        val rx = (w * 0.42f).coerceAtMost(h * 0.72f)
        val ry = rx * 0.42f
        val domeH = rx * 0.58f

        fun project(azDeg: Float, elDeg: Float): Offset {
            val a = Math.toRadians(azDeg.toDouble())
            val el = elDeg.coerceIn(0f, 90f)
            val elR = Math.toRadians(el.toDouble())
            val g = cos(elR).toFloat() // 1 at horizon, 0 at zenith
            val x = cx + rx * g * sin(a).toFloat()
            val y = cy - ry * g * cos(a).toFloat() - domeH * sin(elR).toFloat()
            return Offset(x, y)
        }

        // Elevation rings: horizon (0°) + 30° + 60°.
        for (el in intArrayOf(0, 30, 60)) {
            val elR = Math.toRadians(el.toDouble())
            val g = cos(elR).toFloat()
            val ringRx = rx * g
            val ringRy = ry * g
            val ringCy = cy - domeH * sin(elR).toFloat()
            drawOval(
                color = grid.copy(alpha = if (el == 0) 0.9f else 0.4f),
                topLeft = Offset(cx - ringRx, ringCy - ringRy),
                size = Size(ringRx * 2f, ringRy * 2f),
                style = Stroke(width = if (el == 0) 2.5f else 1.2f),
            )
        }

        // Azimuth spokes to the four cardinals + zenith cross.
        val zenith = project(0f, 90f)
        for (az in intArrayOf(0, 90, 180, 270)) {
            val edge = project(az.toFloat(), 0f)
            drawLine(grid.copy(alpha = 0.35f), zenith, edge, strokeWidth = 1f)
        }
        drawLine(grid, Offset(zenith.x - 7f, zenith.y), Offset(zenith.x + 7f, zenith.y), strokeWidth = 1.5f)
        drawLine(grid, Offset(zenith.x, zenith.y - 7f), Offset(zenith.x, zenith.y + 7f), strokeWidth = 1.5f)

        // Cardinal labels at the horizon.
        val paint = Paint().apply {
            color = android.graphics.Color.rgb(154, 160, 166)
            textSize = 30f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.apply {
            fun label(text: String, az: Int, dy: Float) {
                val p = project(az.toFloat(), 0f)
                drawText(text, p.x, p.y + dy, paint)
            }
            label("N", 0, -8f)
            label("S", 180, 26f)
            label("E", 90, 10f)
            label("W", 270, 10f)
        }

        // Satellites (draw high-elevation last so they sit on top).
        satellites.sortedBy { it.elevationDeg }.forEach { s ->
            val p = project(s.azimuthDeg, s.elevationDeg)
            val r = (3f + (s.cn0.coerceIn(0f, 50f) / 50f) * 6f)
            val c = colorFor(s.constellation)
            if (s.usedInFix) {
                drawCircle(c, radius = r, center = p)
                drawCircle(Color.White.copy(alpha = 0.85f), radius = r, center = p, style = Stroke(width = 1.2f))
            } else {
                drawCircle(c.copy(alpha = 0.55f), radius = r, center = p, style = Stroke(width = 1.6f))
            }
        }

        // The device (observer) — a red X at the centre of the horizon: you stand
        // here, looking up, with the satellites above. Approximate, for orientation.
        val red = Color(0xFFFF3B30)
        val s = 10f
        drawLine(red, Offset(cx - s, cy - s), Offset(cx + s, cy + s), strokeWidth = 3.5f)
        drawLine(red, Offset(cx - s, cy + s), Offset(cx + s, cy - s), strokeWidth = 3.5f)
        drawContext.canvas.nativeCanvas.drawText(
            "you",
            cx,
            cy + 28f,
            Paint().apply {
                color = android.graphics.Color.rgb(255, 59, 48)
                textSize = 26f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            },
        )
    }
}

/** A colour-keyed legend for the constellations actually present. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkyDomeLegend(satellites: List<SatInfo>, modifier: Modifier = Modifier) {
    val present = satellites.map { it.constellation }.distinct().sorted()
    if (present.isEmpty()) return
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        present.forEach { name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colorFor(name)))
                Spacer(Modifier.width(4.dp))
                Text(name, style = MaterialTheme.typography.labelSmall, color = labelGray)
            }
        }
    }
}

private val labelGray = Color(0xFF9AA0A6)
