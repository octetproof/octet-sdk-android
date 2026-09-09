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

/**
 * Plain-language, one-line explanations of each signal on the Sensors screen.
 * Deliberately non-technical and leak-free: they describe what the *public OS
 * reading* means, never anything about the SDK's internal scoring or weights.
 */
val signalGlossary: Map<String, String> = mapOf(
    "Source" to "Where this location came from — the device's own GPS/sensors, the mobile network, or (a red flag) a mock/simulated provider.",
    "Authorization" to "The location permission you granted this app: precise, approximate, or denied.",
    "Coordinates" to "The device's current latitude and longitude — its position on Earth.",
    "Horizontal accuracy" to "The estimated radius of error around the position — smaller is better (e.g. 5 m is a strong fix).",
    "Vertical accuracy" to "The estimated error on altitude — how uncertain the height reading is.",
    "Altitude" to "Height above mean sea level, estimated from GPS and the barometer.",
    "Speed" to "How fast the device is moving over the ground.",
    "Course" to "The compass direction the device is travelling in (0° = North).",
    "Satellites (visible)" to "How many navigation satellites the receiver can currently see in the sky.",
    "Used in fix" to "How many of those satellites are actually being used to compute the position — more means a stronger fix.",
    "Constellations" to "Which satellite systems are in view (GPS, Galileo, GLONASS, BeiDou…). Seeing several is a sign of a genuine, healthy fix.",
    "Sky dome" to "A map of the sky: each dot is a satellite placed by its compass bearing and height above the horizon. Filled dots are used in the fix; dot size shows signal strength.",
    "Accelerometer" to "Measures acceleration on three axes — how the device is being moved or tilted, plus gravity.",
    "Gyroscope" to "Measures rotation rate on three axes — how fast the device is turning.",
    "Magnetometer" to "Measures the magnetic field on three axes — the basis of the compass.",
    "Barometer" to "Measures air pressure, which helps estimate altitude and floor level.",
    "Radio access" to "The current mobile network technology in use (5G, LTE, 3G…).",
    "Cells in range" to "How many cell towers the device can currently hear. Their positions aren't exposed by the OS, so only signal strength is shown.",
    "Strongest signal" to "The signal strength of the nearest/strongest cell, in dBm (closer to 0 is stronger).",
    "Networks in range" to "How many Wi-Fi access points the device can see nearby, with their signal strength.",
    "Battery level" to "The current battery charge, as a percentage.",
    "Current draw" to "How much current the device is drawing right now, in milliamps — higher means more power use.",
    "Charge counter" to "The battery's remaining charge in mAh, read from the hardware fuel gauge.",
    "Battery used" to "How much battery this proof run consumed, from the hardware charge counter before and after. It can only be measured off-charger — while the device is plugged in there's nothing to measure.",
)

/** A small (i) button that opens a one-line explanation for a signal. */
@Composable
fun InfoDot(key: String, modifier: Modifier = Modifier) {
    val text = signalGlossary[key] ?: return
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
