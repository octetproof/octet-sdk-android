package com.octetproof.sample

import android.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// On-device persistence + interchange models. SDK-free + serializable: a
// StoredProof is what the sample keeps for each proof it generated or imported
// (the SDK has no "list proofs" API). Only non-sensitive fields are stored —
// never a raw coordinate, key, token, or device identifier. Mirrors iOS Models.swift.

/** One proof the sample is holding on to. `proofB64` is the shareable serialized proof. */
@Serializable
data class StoredProof(
    val id: String,                 // local UUID (not the proof's internal id)
    val proofB64: String,           // base64 of proofBytes (shareable)
    val regionISO: String? = null,  // ISO alpha-2 for country proofs, else null
    val regionLabel: String,        // display, e.g. "Germany"
    val level: String,              // proof level, e.g. "ON_EARTH" / "COUNTRY"
    val generatedAtMs: Long,        // the proof's own signed timestamp
    val createdAtMs: Long,          // when this device stored it
    val confidenceScore: Double,    // ConfidenceSummary.overallScore
    val predicateResult: String? = null, // "inside"/"outside"/"indeterminate"; null if imported
    val imported: Boolean = false,  // arrived via share rather than generated here
    val sizeBytes: Int,
) {
    val proofBytes: ByteArray
        get() = runCatching { Base64.decode(proofB64, Base64.DEFAULT) }.getOrDefault(ByteArray(0))

    val bucket: ConfidenceBucket get() = ConfidenceBucket.of(confidenceScore)

    /** Stable content key for de-duplication (same proof bytes = same proof). */
    val contentKey: String get() = proofB64
}

/** Confidence tier — buckets `overallScore` so the UI reads at a glance. */
enum class ConfidenceBucket(val label: String, val tint: StatusKind) {
    HIGH("HIGH", StatusKind.OK),
    MEDIUM("MEDIUM", StatusKind.WARN),
    LOW("LOW", StatusKind.BAD);

    companion object {
        fun of(score: Double): ConfidenceBucket = when {
            score >= 0.8 -> HIGH
            score >= 0.5 -> MEDIUM
            else -> LOW
        }
    }
}

/**
 * The `.octetproof` interchange envelope — what travels over Nearby Share / any
 * OS share target. Versioned + self-describing, carrying ONLY the shareable
 * proof bytes plus benign metadata. Wire shape (v / proof_b64 / region /
 * generated_at) is byte-compatible with the iOS sample so proofs cross platforms.
 */
@Serializable
data class ProofEnvelope(
    val v: Int = CURRENT_VERSION,
    @SerialName("proof_b64") val proofB64: String,
    val region: String,                       // ISO alpha-2 or a display label
    @SerialName("generated_at") val generatedAtMs: Long,
) {
    companion object {
        const val CURRENT_VERSION = 1
        private val JSON = Json { prettyPrint = true; encodeDefaults = true }

        /** Decode received `.octetproof` bytes; null if not a valid current-version envelope. */
        fun decode(bytes: ByteArray): ProofEnvelope? = runCatching {
            val env = JSON.decodeFromString(serializer(), String(bytes, Charsets.UTF_8))
            if (env.v != CURRENT_VERSION) return null
            if (Base64.decode(env.proofB64, Base64.DEFAULT).isEmpty()) return null
            env
        }.getOrNull()
    }

    /** Encode to the canonical `.octetproof` file bytes. */
    fun fileBytes(): ByteArray = JSON.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)
}
