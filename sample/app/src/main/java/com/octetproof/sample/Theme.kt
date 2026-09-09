package com.octetproof.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Visual language for the sample — one Octet accent + the semantic verdict
// colours, kept separate so "good/warn/bad" never reads as "branded". Mirrors
// the iOS sample's Theme.

object Theme {
    /** The single Octet accent — a "signal" cyan. */
    val accent = Color(red = 0.02f, green = 0.66f, blue = 0.78f)

    // Semantic verdict colours.
    val pass = Color(0xFF16A34A)
    val warn = Color(0xFFD97706)
    val fail = Color(0xFFDC2626)
    val neutral = Color(0xFF8A8A8E)
}

/** The status vocabulary used everywhere (pipeline steps, check rows, verdicts). */
enum class StatusKind { OK, ACTIVE, PENDING, WARN, NOT_CHECKED, BAD }

fun StatusKind.color(): Color = when (this) {
    StatusKind.OK -> Theme.pass
    StatusKind.ACTIVE -> Theme.accent
    StatusKind.PENDING -> Theme.neutral
    StatusKind.WARN -> Theme.warn
    StatusKind.NOT_CHECKED -> Theme.neutral
    StatusKind.BAD -> Theme.fail
}

private fun StatusKind.icon(): ImageVector = when (this) {
    StatusKind.OK -> Icons.Filled.CheckCircle
    StatusKind.ACTIVE -> Icons.Filled.Circle
    StatusKind.PENDING -> Icons.Filled.RadioButtonUnchecked
    StatusKind.WARN -> Icons.Filled.Warning
    StatusKind.NOT_CHECKED -> Icons.Filled.RemoveCircle
    StatusKind.BAD -> Icons.Filled.Cancel
}

@Composable
fun OctetSampleTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Theme.accent, secondary = Theme.accent)
    } else {
        lightColorScheme(primary = Theme.accent, secondary = Theme.accent)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** A small status glyph — one shape so every list/row is consistent. */
@Composable
fun StatusDot(kind: StatusKind, size: Dp = 18.dp) {
    Icon(kind.icon(), contentDescription = null, tint = kind.color(), modifier = Modifier.size(size))
}

/** A compact rounded label (confidence bucket, "fresh", tags). */
@Composable
fun Chip(text: String, tint: Color = Theme.neutral) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        modifier = Modifier
            .background(tint.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Section card wrapper — the framed panels on Generate/Verify. */
@Composable
fun SampleCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), content = content)
    }
}
