package com.octetproof.sample

import android.app.Application
import android.content.Context
import android.location.Geocoder
import android.os.BatteryManager
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.octetproof.sample.BuildConfig
import com.octetproof.sdk.api.AdvancedConfig
import com.octetproof.sdk.api.LicenseState
import com.octetproof.sdk.api.LocationProof
import com.octetproof.sdk.api.Octet
import com.octetproof.sdk.api.OctetConfig
import com.octetproof.sdk.api.OctetRegion
import com.octetproof.sdk.api.OctetSdk
import com.octetproof.sdk.api.OctetVerdict
import com.octetproof.sdk.api.ProofLevel
import com.octetproof.sdk.api.ProofRegion
import com.octetproof.sdk.api.ProofVerification
import com.octetproof.sdk.api.VerifyOptions
import com.octetproof.sdk.logging.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.Locale
import java.util.UUID

// Pipeline model (mirrors iOS PipelineStep / StepState).
enum class StepState { PENDING, ACTIVE, DONE }

data class PipelineStep(
    val id: String,
    val label: String,
    val state: StepState = StepState.PENDING,
    val seconds: Double? = null,
    val activeAtMs: Long? = null,
) {
    val kind: StatusKind
        get() = when (state) {
            StepState.PENDING -> StatusKind.PENDING
            StepState.ACTIVE -> StatusKind.ACTIVE
            StepState.DONE -> StatusKind.OK
        }
}

/** One line of the curated, sanitized debug feed — public verdict fields + the
 *  sample's own timings only; never a coordinate/key/token/id. */
data class DebugLine(val id: Long, val timeMs: Long, val text: String)

/** The Generate result-card payload (the predicate answer, not a verification). */
data class GenerateResult(
    val result: OctetVerdict.Result,
    val regionLabel: String,
    val bucket: ConfidenceBucket,
    val score: Double,
    val fresh: Boolean,
    val stored: StoredProof?,
    val battery: BatteryCost? = null,
)

/** Battery consumed across one proof run, measured from the hardware charge
 *  counter. `mAh`/`avgMa` are null when the device was charging or the run was
 *  shorter than the counter's update resolution (a common, honest outcome). */
data class BatteryCost(
    val mAh: Double?,
    val pct: Int?,
    val avgMa: Int?,
    val durationSec: Double,
    val charging: Boolean,
)

sealed interface SdkState {
    val label: String
    data object Idle : SdkState { override val label = "Waiting for permission…" }
    data object Starting : SdkState { override val label = "SDK initializing…" }
    data object Ready : SdkState { override val label = "SDK ready" }
    data class Failed(val message: String) : SdkState { override val label = "SDK failed: $message" }
}

/**
 * The Android sample's app model — SDK lifecycle, generate pipeline, verify, the
 * curated debug feed, region selection, and live location for the map. Mirrors
 * the iOS AppModel. Compose-observable via mutableState.
 */
class SampleViewModel(app: Application) : AndroidViewModel(app) {

    val settings = AppSettings(app)
    val store = ProofStore(app)

    var sdkState by mutableStateOf<SdkState>(SdkState.Idle)
        private set
    var licenseLine by mutableStateOf("")
        private set
    var licenseNotice by mutableStateOf<String?>(null)
        private set
    var selectedCountry by mutableStateOf(defaultCountry)
        private set
    val steps = mutableStateListOf<PipelineStep>().apply { addAll(baseSteps()) }
    var isGenerating by mutableStateOf(false)
        private set
    var lastResult by mutableStateOf<GenerateResult?>(null)
        private set
    /** When on, Generate bypasses the freshness cache and mints a brand-new proof
     *  (SDK `isWithin(forceFresh = true)`) instead of possibly reusing a still-valid
     *  cached fix — handy to prove a *fresh* run really happened. Set by the UI toggle. */
    var forceFresh by mutableStateOf(false)
    val debug = mutableStateListOf<DebugLine>()

    /** Live device location for the map (lat, lon); null until the first fix. */
    var userLocation by mutableStateOf<Pair<Double, Double>?>(null)
        private set
    /** A `.octetproof` awaiting the import-preview dialog. */
    var pendingImport by mutableStateOf<ProofEnvelope?>(null)

    private var sdk: OctetSdk? = null
    private var started = false
    private var didAutoSelectCountry = false
    private var userOverrodeCountry = false
    private var logSeq = 0L

    val isReady: Boolean get() = sdkState is SdkState.Ready

    // MARK: - lifecycle

    /** Called by the Activity once ACCESS_FINE_LOCATION is granted. */
    fun onPermissionGranted() {
        if (started) return
        started = true
        sdkState = SdkState.Starting
        markActive("init")
        log("sdk.start requested")
        viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            try {
                val config = OctetConfig(
                    licenseKey = BuildConfig.OCTET_LICENSE_KEY,
                    proofUploadUrl = if (settings.uploadsEnabled) BuildConfig.OCTET_ACTIVATION_SERVER_URL else null,
                    advanced = AdvancedConfig(
                        activationServerUrl = BuildConfig.OCTET_ACTIVATION_SERVER_URL,
                        logLevel = if (settings.verboseLogs) LogLevel.VERBOSE else LogLevel.INFO,
                        playIntegrityCloudProjectNumber =
                            BuildConfig.OCTET_PI_CLOUD_PROJECT.takeIf { it != 0L }),
                )
                val s = Octet.start(getApplication(), config)
                sdk = s
                complete("init", sinceMs = t0)
                sdkState = SdkState.Ready
                log("sdk.start ok")
                log("activation ✓")
                s.licenseStatus?.let { st ->
                    licenseLine = "license ${st.state}"
                    if (st.state == LicenseState.GRACE_PERIOD && st.daysUntilHardStop != null) {
                        val d = st.daysUntilHardStop
                        licenseNotice = "Your license expires in $d day${if (d == 1) "" else "s"} — please renew."
                    }
                }
            } catch (e: Exception) {
                sdkState = SdkState.Failed(e.toString())
                markFailed("init")
                log("sdk.start failed")
            }
        }
    }

    /** Feed the map + (once) default the region picker to the GPS country. */
    fun onLocation(lat: Double, lon: Double) {
        val first = userLocation == null
        userLocation = lat to lon
        if (first) autoSelectCountry(lat, lon)
    }

    // MARK: - region selection

    fun userSelectedCountry(c: Country) {
        selectedCountry = c
        userOverrodeCountry = true
    }

    private fun autoSelectCountry(lat: Double, lon: Double) {
        if (didAutoSelectCountry || userOverrodeCountry) return
        didAutoSelectCountry = true
        viewModelScope.launch {
            val iso = withContext(Dispatchers.IO) {
                runCatching {
                    @Suppress("DEPRECATION")
                    Geocoder(getApplication(), Locale.getDefault())
                        .getFromLocation(lat, lon, 1)?.firstOrNull()?.countryCode
                }.getOrNull()
            } ?: return@launch
            if (!userOverrodeCountry) {
                demoCountries.firstOrNull { it.iso == iso }?.let { selectedCountry = it }
            }
        }
    }

    // MARK: - generate

    fun generate() {
        val activeSdk = sdk ?: return
        if (isGenerating) return
        isGenerating = true
        lastResult = null
        resetForGenerate()
        log(if (forceFresh) "generate: region ${selectedCountry.iso} (force-fresh)" else "generate: region ${selectedCountry.iso}")
        viewModelScope.launch {
            val battStart = readBattery()
            val t0 = System.currentTimeMillis()
            val animator = launch { animateSteps() }
            val verdict = activeSdk.loc.isWithin(
                OctetRegion.country(selectedCountry.iso), Instant.now(), forceFresh = forceFresh)
            // Let the pipeline animation finish its minimum run so a fast (cached)
            // proof is still a visibly NEW run, not a silent flash of the old marks.
            animator.join()
            finishSteps()
            val battery = computeBatteryCost(battStart, t0)

            // forceFresh always mints a new proof, so a produced proof is fresh;
            // otherwise trust the SDK's honest "freshly generated" message.
            val fresh = forceFresh || verdict.message.contains("fresh", ignoreCase = true)
            log("fix quality: ${bucketLabel(verdict.confidence.overallScore)}")

            var stored: StoredProof? = null
            val proof = verdict.proof
            if (proof != null) {
                val sp = makeStoredProof(proof, verdict.result)
                val isNew = store.add(sp)
                stored = sp
                log(if (isNew) "proof built (${proof.proofBytes.size} B) · stored" else "proof already stored")
                if (settings.uploadsEnabled) log("upload queued")
            } else {
                log("no proof produced (${verdict.reason})")
            }

            lastResult = GenerateResult(
                result = verdict.result,
                regionLabel = selectedCountry.name,
                bucket = ConfidenceBucket.of(verdict.confidence.overallScore),
                score = verdict.confidence.overallScore,
                fresh = fresh,
                stored = stored,
                battery = battery,
            )
            battery?.mAh?.let { log("battery: %.2f mAh this run".format(it)) }
            isGenerating = false
        }
    }

    private fun makeStoredProof(proof: LocationProof, result: OctetVerdict.Result) = StoredProof(
        id = UUID.randomUUID().toString(),
        proofB64 = Base64.encodeToString(proof.proofBytes, Base64.NO_WRAP),
        regionISO = selectedCountry.iso,
        regionLabel = selectedCountry.name,
        level = levelLabel(proof.level),
        generatedAtMs = proof.timestampMs,
        createdAtMs = System.currentTimeMillis(),
        confidenceScore = proof.confidence.overallScore,
        predicateResult = resultLabel(result),
        imported = false,
        sizeBytes = proof.proofBytes.size,
    )

    // MARK: - verify

    /** Verify a stored proof. `expectedISO` (a country the user picks) drives the
     *  region-claim / region-type / region-contains checks — NOT the isWithin query. */
    fun verify(proof: StoredProof, expectedISO: String?): ProofVerification {
        val options = if (expectedISO != null && expectedISO.length == 2) {
            VerifyOptions(expectedRegion = ProofRegion.Country(expectedISO))
        } else {
            VerifyOptions()
        }
        return Octet.verify(proof.proofBytes, options)
    }

    // MARK: - import

    /** Verify+store an incoming `.octetproof` envelope. Returns (proof, isNew). */
    fun importEnvelope(env: ProofEnvelope): Pair<StoredProof, Boolean> {
        val bytes = runCatching { Base64.decode(env.proofB64, Base64.DEFAULT) }.getOrDefault(ByteArray(0))
        val label = if (env.region.length == 2) countryName(env.region) else env.region
        val sp = StoredProof(
            id = UUID.randomUUID().toString(),
            proofB64 = env.proofB64,
            regionISO = if (env.region.length == 2) env.region else null,
            regionLabel = label,
            level = "—",
            generatedAtMs = env.generatedAtMs,
            createdAtMs = System.currentTimeMillis(),
            confidenceScore = 0.0,
            predicateResult = null,
            imported = true,
            sizeBytes = bytes.size,
        )
        return sp to store.add(sp)
    }

    // MARK: - battery cost (hardware charge counter; no permission needed)

    private data class BattReading(val chargeUah: Int, val levelPct: Int, val charging: Boolean)

    private val batteryManager by lazy {
        getApplication<Application>().getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    }

    private fun readBattery(): BattReading? {
        val bm = batteryManager ?: return null
        return BattReading(
            chargeUah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
            levelPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            charging = bm.isCharging,
        )
    }

    private fun computeBatteryCost(start: BattReading?, t0: Long): BatteryCost? {
        val end = readBattery() ?: return null
        val durationSec = (System.currentTimeMillis() - t0) / 1000.0
        val charging = start?.charging == true || end.charging
        if (start == null || start.chargeUah <= 0 || end.chargeUah <= 0) {
            return BatteryCost(null, null, null, durationSec, charging)
        }
        val deltaUah = start.chargeUah - end.chargeUah // positive = consumed
        val pct = (start.levelPct - end.levelPct).takeIf { it in 0..100 }
        if (charging || deltaUah <= 0) {
            return BatteryCost(null, pct, null, durationSec, charging)
        }
        val mAh = deltaUah / 1000.0
        val avgMa = if (durationSec > 0) (mAh / (durationSec / 3600.0)).toInt() else null
        return BatteryCost(mAh, pct, avgMa, durationSec, charging)
    }

    // MARK: - pipeline helpers

    private fun baseSteps() = listOf(
        PipelineStep("init", "SDK initialized"),
        PipelineStep("warmup", "Sensors warmed up"),
        PipelineStep("locate", "Location fixed"),
        PipelineStep("generate", "Proof generated"),
    )

    private fun resetForGenerate() {
        val init = steps.firstOrNull { it.id == "init" }?.copy()
            ?: PipelineStep("init", "SDK initialized", StepState.DONE)
        val fresh = mutableListOf(
            init.copy(state = StepState.DONE),
            PipelineStep("warmup", "Sensors warmed up"),
            PipelineStep("locate", "Location fixed"),
            PipelineStep("generate", "Proof generated"),
        )
        if (settings.uploadsEnabled) fresh.add(PipelineStep("upload", "Upload queued"))
        steps.clear(); steps.addAll(fresh)
    }

    private suspend fun animateSteps() {
        markActive("warmup")
        delay(500); if (!viewModelScope.isActive) return
        complete("warmup"); markActive("locate")
        delay(700); if (!viewModelScope.isActive) return
        complete("locate"); markActive("generate")
    }

    private fun finishSteps() {
        for (id in listOf("warmup", "locate", "generate", "upload")) {
            val i = steps.indexOfFirst { it.id == id }
            if (i >= 0 && steps[i].state != StepState.DONE) complete(id)
        }
    }

    private fun markActive(id: String) {
        val i = steps.indexOfFirst { it.id == id }
        if (i >= 0) steps[i] = steps[i].copy(state = StepState.ACTIVE, activeAtMs = System.currentTimeMillis())
    }

    private fun complete(id: String, sinceMs: Long? = null) {
        val i = steps.indexOfFirst { it.id == id }
        if (i < 0) return
        // Duration from when the step went active (or an explicit start, e.g. init).
        val from = sinceMs ?: steps[i].activeAtMs ?: System.currentTimeMillis()
        val secs = (System.currentTimeMillis() - from) / 1000.0
        steps[i] = steps[i].copy(state = StepState.DONE, seconds = secs)
    }

    private fun markFailed(id: String) {
        val i = steps.indexOfFirst { it.id == id }
        if (i >= 0) steps[i] = steps[i].copy(state = StepState.PENDING)
    }

    // MARK: - curated debug log (sanitized)

    fun log(text: String) {
        debug.add(DebugLine(logSeq++, System.currentTimeMillis(), text))
        if (debug.size > 200) debug.removeAt(0)
    }

    fun clearDebug() = debug.clear()

    // MARK: - labels (public, non-sensitive)

    fun bucketLabel(score: Double) = when (ConfidenceBucket.of(score)) {
        ConfidenceBucket.HIGH -> "strong"
        ConfidenceBucket.MEDIUM -> "ok"
        ConfidenceBucket.LOW -> "weak"
    }

    fun resultLabel(r: OctetVerdict.Result) = when (r) {
        OctetVerdict.Result.YES -> "inside"
        OctetVerdict.Result.NO -> "outside"
        OctetVerdict.Result.INDETERMINATE -> "indeterminate"
    }

    private fun levelLabel(l: ProofLevel) = l.name
}
