package com.octetproof.sample

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.octetproof.sdk.api.CheckStatus
import com.octetproof.sdk.api.ProofCheck
import com.octetproof.sdk.api.ProofVerification

/** The Verify tab: the stored-proof list + import. */
@Composable
fun VerifyScreen(vm: SampleViewModel, nav: NavController) {
    val ctx = LocalContext.current
    var toast by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        val env = bytes?.let { ProofEnvelope.decode(it) }
        toast = if (env == null) "Not a valid .octetproof file" else {
            val (_, isNew) = vm.importEnvelope(env)
            if (isNew) "Imported ${if (env.region.length == 2) countryName(env.region) else env.region}" else "Already imported"
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Proofs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item {
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.FileUpload, null)
                Spacer(Modifier.width(8.dp))
                Text("Import .octetproof")
            }
        }
        toast?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, color = Theme.accent) } }
        if (vm.store.proofs.isEmpty()) {
            item { Text("No proofs yet — generate one on the Generate tab.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium) }
        }
        items(vm.store.sorted) { proof ->
            SampleCard(modifier = Modifier.clickable { nav.navigate("verifyDetail/${proof.id}") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(proof.regionLabel, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${proof.level} · ${if (proof.imported) "imported" else (proof.predicateResult ?: "—")} · ${proof.sizeBytes} B",
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                        )
                        Text(
                            shortTimestamp(proof.createdAtMs),
                            style = MaterialTheme.typography.labelSmall, color = Color.Gray,
                        )
                    }
                    if (!proof.imported) Chip(proof.bucket.label, proof.bucket.tint.color())
                }
            }
        }
    }
}

/** When this device stored the proof, e.g. "Sep 7 · 14:52". Mirrors the iOS
 *  proof-list timestamp so the two samples read the same. */
private fun shortTimestamp(ms: Long): String =
    java.text.SimpleDateFormat("MMM d · HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(ms))

/** Verify one stored proof: pick a region to check against, see grouped checks. */
@Composable
fun VerifyDetailScreen(vm: SampleViewModel, proof: StoredProof, nav: NavController) {
    val ctx = LocalContext.current
    var expectedIso by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ProofVerification?>(null) }
    var regionSheet by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }

    // (Re)verify whenever the expected region changes.
    LaunchedEffect(expectedIso) { result = vm.verify(proof, expectedIso) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val env = ProofEnvelope(
            proofB64 = proof.proofB64,
            region = proof.regionISO ?: proof.regionLabel,
            generatedAtMs = proof.generatedAtMs,
        )
        runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(env.fileBytes()) } }
    }

    BackScaffold(proof.regionLabel, { nav.popBackStack() }) { pad ->
    LazyColumn(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Region-check selector + granularity note.
        item {
            SampleCard(modifier = Modifier.clickable { regionSheet = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Check against region")
                    Spacer(Modifier.weight(1f))
                    Text(expectedIso?.let { countryName(it) } ?: "Any region", color = Color.Gray)
                }
            }
        }
        item {
            Text(
                "This proof claims the device's location at ${proof.level.lowercase()} level — it reveals nothing finer. " +
                    "Pick a region to check that claim against; \"Any region\" leaves the three region checks unverified.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray,
            )
        }
        val v = result
        if (v == null) {
            item { Text("Verifying…", color = Color.Gray) }
        } else {
            item { VerdictHeader(v) }
            checkGroup("PASSED", v.checks.filter { it.status == CheckStatus.PASS }, StatusKind.OK)
            checkGroup("WARN", v.checks.filter { it.status == CheckStatus.WARN }, StatusKind.WARN)
            checkGroup("NOT-CHECKED", v.checks.filter { it.status == CheckStatus.NOT_CHECKED }, StatusKind.NOT_CHECKED)
            checkGroup("FAILED", v.checks.filter { it.status == CheckStatus.FAIL }, StatusKind.BAD)
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showRaw = true }) { Text("View raw") }
                    OutlinedButton(onClick = { exportLauncher.launch("${proof.regionLabel}.octetproof") }) {
                        Icon(Icons.Filled.FileDownload, null); Spacer(Modifier.width(6.dp)); Text("Export")
                    }
                }
            }
        }
    }
    }

    if (regionSheet) {
        RegionPickerSheet(
            includeAny = true,
            selectedIso = expectedIso,
            onPick = { iso -> expectedIso = iso; regionSheet = false },
            onDismiss = { regionSheet = false },
        )
    }
    if (showRaw) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRaw = false },
            confirmButton = { Button(onClick = { showRaw = false }) { Text("Close") } },
            title = { Text("Raw proof (base64)") },
            text = {
                Text(proof.proofB64, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            },
        )
    }
}

@Composable
private fun VerdictHeader(v: ProofVerification) {
    val kind = when (v.verdict) {
        ProofVerification.Verdict.VALID -> StatusKind.OK
        ProofVerification.Verdict.INVALID -> StatusKind.BAD
        ProofVerification.Verdict.INCONCLUSIVE -> StatusKind.WARN
    }
    SampleCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(kind)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(v.verdict.name, style = MaterialTheme.typography.titleMedium)
                Text("authentic: ${if (v.isAuthentic) "yes" else "no"}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.checkGroup(
    title: String,
    checks: List<ProofCheck>,
    kind: StatusKind,
) {
    if (checks.isEmpty()) return
    item { Text("$title (${checks.size})", style = MaterialTheme.typography.labelMedium, color = Color.Gray) }
    items(checks) { c -> CheckRow(c, kind) }
}

@Composable
private fun CheckRow(c: ProofCheck, kind: StatusKind) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(kind)
            Spacer(Modifier.width(8.dp))
            Text(c.name, style = MaterialTheme.typography.bodyMedium)
        }
        AnimatedVisibility(expanded) {
            val text = if (kind == StatusKind.NOT_CHECKED) notCheckedExplainer(c.name).ifEmpty { c.detail } else c.detail
            Text(text, style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(start = 26.dp, top = 2.dp))
        }
    }
}

/** Plain-language, leak-safe "why it didn't run + what to change" for the checks
 *  the on-device verifier can't perform with the given inputs. Mirrors iOS. */
private fun notCheckedExplainer(name: String): String = when (name) {
    "region-claim" -> "Checks the proof's region matches the one you expect — pick a region to run it."
    "region-type" -> "Checks the region kind (country, city, …) matches what you expect — pick a region to run it."
    "region-contains" -> "Checks the location falls inside the expected region — pick a region to run it."
    "hardware-attestation" -> "Confirms the proof came from genuine device hardware — generate one on a real device with attestation enabled to run it."
    "replay-binding" -> "The one-time replay tag travels with an uploaded proof, not the saved file, so there's nothing here to check."
    "replay-ledger" -> "Confirms this proof hasn't been reused — an online check, so it can't run on the device alone."
    "revocation" -> "Confirms the signing key hasn't been revoked — an online check, so it isn't done on the device."
    else -> ""
}
