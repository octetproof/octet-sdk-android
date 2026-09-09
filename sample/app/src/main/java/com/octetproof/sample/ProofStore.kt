package com.octetproof.sample

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The sample's own persistent list of proofs (the SDK exposes no proof store).
 * Backed by a single JSON file in the app's private files dir; survives relaunch.
 * Compose-observable via a snapshot-backed list. Everything it holds is
 * non-sensitive (see StoredProof). Mirrors iOS ProofStore.
 */
class ProofStore(context: Context) {
    private val file = File(context.filesDir, "proofs.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(StoredProof.serializer())

    private val _proofs = mutableStateListOf<StoredProof>()
    val proofs: List<StoredProof> get() = _proofs

    init { load() }

    /** Newest first — what every list shows. */
    val sorted: List<StoredProof> get() = _proofs.sortedByDescending { it.createdAtMs }
    val latest: StoredProof? get() = sorted.firstOrNull()

    fun contains(p: StoredProof): Boolean = _proofs.any { it.contentKey == p.contentKey }

    /** Add unless byte-identical to one already stored; returns false on duplicate. */
    fun add(p: StoredProof): Boolean {
        if (contains(p)) return false
        _proofs.add(p)
        save()
        return true
    }

    fun delete(p: StoredProof) {
        _proofs.removeAll { it.id == p.id }
        save()
    }

    fun clear() {
        _proofs.clear()
        save()
    }

    private fun load() {
        runCatching {
            if (!file.exists()) return
            val list = json.decodeFromString(serializer, file.readText())
            _proofs.clear()
            _proofs.addAll(list)
        }
    }

    private fun save() {
        runCatching { file.writeText(json.encodeToString(serializer, _proofs.toList())) }
    }
}
