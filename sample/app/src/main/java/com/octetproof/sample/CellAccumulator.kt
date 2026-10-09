package com.octetproof.sample

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

/**
 * Accumulates the observed cells across scans and resolves the fully-identified
 * ones to real coordinates via OpenCelliD. Shared by [CellTheaterActivity] and
 * [LocationTheaterActivity].
 *
 * Why accumulate: a single `TelephonyManager.getAllCellInfo()` snapshot usually
 * reports only the *serving* cell with a full identity (MCC/MNC/area/CID) — the
 * only kind OpenCelliD can position — plus a few signal-only neighbours. Unioning
 * snapshots over time (and across hand-overs as you move) surfaces far more
 * distinct towers than any one call, so the triangulation has more real anchors
 * to work with. Reads platform APIs only (via [SensorMonitor]) — never the SDK.
 */
class CellAccumulator(private val io: ExecutorService, private val apiKey: String) {

    /** Stable-identity key → the latest [CellTower] seen for it. */
    private val seen = ConcurrentHashMap<String, CellTower>()
    /** cid → [lat, lon, rangeM]; an empty array means "looked up, not found". */
    private val cache = ConcurrentHashMap<Long, DoubleArray>()
    private val inFlight = ConcurrentHashMap<Long, Boolean>()

    /** Identity for de-duplication: full identity when we have it, else the
     *  air-interface fingerprint (PCI + channel) so distinct neighbours don't
     *  collapse into one. */
    private fun keyOf(c: CellTower): String =
        if (c.cid != null && c.mcc != null && c.mnc != null && c.area != null)
            "${c.tech}:${c.mcc}:${c.mnc}:${c.area}:${c.cid}"
        else "${c.tech}:p${c.pci}:c${c.channel}"

    /** Merge one scan into the accumulated set and kick off any new lookups. */
    fun observe(towers: List<CellTower>) {
        towers.forEach { c -> seen[keyOf(c)] = c; maybeLookup(c) }
    }

    /** Distinct cells observed so far. */
    fun observedCount(): Int = seen.size
    /** Cells resolved to a real OpenCelliD position. */
    fun realCount(): Int = seen.values.count { c ->
        val pos = c.cid?.let { cache[it] }
        pos != null && pos.size >= 2
    }

    private fun maybeLookup(c: CellTower) {
        if (apiKey.isBlank()) return
        val cid = c.cid ?: return
        if (c.mcc == null || c.mnc == null || c.area == null) return
        if (cache.containsKey(cid) || inFlight.putIfAbsent(cid, true) != null) return
        io.execute {
            val radio = when (c.tech) { "5G" -> "NR"; "LTE" -> "LTE"; "3G" -> "UMTS"; else -> "GSM" }
            val u = "https://opencellid.org/cell/get?key=$apiKey&mcc=${c.mcc}&mnc=${c.mnc}" +
                "&lac=${c.area}&cellid=$cid&radio=$radio&format=json"
            val res = runCatching {
                val conn = (URL(u).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000; readTimeout = 5000
                }
                conn.inputStream.bufferedReader().use { it.readText() }
            }.getOrNull()
            val arr = res?.let {
                runCatching {
                    val o = JSONObject(it)
                    if (o.has("lat") && o.has("lon")) {
                        val lat = o.getDouble("lat"); val lon = o.getDouble("lon")
                        // OpenCelliD returns (0,0) for unknown; treat as not-found.
                        if (lat == 0.0 && lon == 0.0) null
                        else doubleArrayOf(lat, lon, o.optDouble("range", 0.0))
                    } else null
                }.getOrNull()
            }
            cache[cid] = arr ?: DoubleArray(0)   // empty = looked up, not found
        }
    }

    /** The accumulated towers as the JSON array the web scenes consume, strongest
     *  signal first. `real`/`lat`/`lon` are set for OpenCelliD-resolved cells. */
    fun towersJson(): String = seen.values.sortedByDescending { it.dbm }.joinToString(",", "[", "]") { c ->
        val pos = c.cid?.let { cache[it] }
        val hasPos = pos != null && pos.size >= 2
        val lat = if (hasPos) pos!![0].toString() else "null"
        val lon = if (hasPos) pos!![1].toString() else "null"
        val rng = if (hasPos && pos!!.size >= 3 && pos[2] > 0) pos[2].toString() else "null"
        "{\"tech\":\"${c.tech}\",\"dbm\":${c.dbm},\"mcc\":${c.mcc ?: "null"}," +
            "\"mnc\":${c.mnc ?: "null"},\"area\":${c.area ?: "null"},\"cid\":${c.cid ?: "null"}," +
            "\"pci\":${c.pci ?: "null"},\"channel\":${c.channel ?: "null"},\"reg\":${c.registered}," +
            "\"real\":${if (hasPos) "true" else "false"},\"lat\":$lat,\"lon\":$lon,\"rangeM\":$rng}"
    }
}
