package com.octetproof.sample

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Looper
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** One satellite as reported by GnssStatus — position on the sky (azimuth +
 *  elevation), signal, constellation, identity, band, ephemeris/almanac state,
 *  and whether it contributed to the fix. Real GnssStatus data — Android only. */
data class SatInfo(
    val constellation: String,
    val svid: Int,
    val azimuthDeg: Float,
    val elevationDeg: Float,
    val cn0: Float,
    val usedInFix: Boolean,
    val band: String,
    val hasEphemeris: Boolean,
    val hasAlmanac: Boolean,
) {
    /** A stable per-satellite label, e.g. "G14" (GPS 14), "E07" (Galileo), "R22"
     *  (GLONASS) — the standard single-letter constellation prefix + SVID. */
    val label: String get() {
        val prefix = when (constellation) {
            "GPS" -> "G"; "GLONASS" -> "R"; "Galileo" -> "E"; "BeiDou" -> "C"
            "QZSS" -> "J"; "SBAS" -> "S"; "IRNSS" -> "I"; else -> "?"
        }
        return "%s%02d".format(prefix, svid)
    }
}

/** One serving/neighbour cell — radio kind + signal strength. Android exposes
 *  identity + signal but NOT the tower's geographic position, so this is a
 *  relative-signal readout, never a map pin. */
data class CellSignal(val tech: String, val dbm: Int)

/** One serving/neighbour cell with identity + signal, for the triangulation
 *  cinematic. Android gives us the cell *identity* and *signal* but never the
 *  tower's real coordinates — the cinematic estimates a layout from signal
 *  strength and labels it as such (observed, not integrity-proven). */
data class CellTower(
    val tech: String,
    val dbm: Int,
    val mcc: Int?,
    val mnc: Int?,
    /** TAC (LTE/NR) or LAC (GSM/WCDMA). */
    val area: Int?,
    /** CI (LTE) / NCI (NR) / CID (GSM/WCDMA). */
    val cid: Long?,
    /** PCI (LTE/NR) or PSC (WCDMA). */
    val pci: Int?,
    /** EARFCN / NR-ARFCN / ARFCN / UARFCN. */
    val channel: Int?,
    /** true = the serving cell; false = a neighbour. */
    val registered: Boolean,
)

/** Live battery snapshot from BatteryManager (no permission needed). */
data class BatterySnapshot(
    val levelPct: Int?,
    val charging: Boolean,
    /** Instantaneous current in mA (magnitude; sign convention is OEM-specific). */
    val currentMa: Int?,
    /** Remaining charge in mAh (from the coulomb counter). */
    val chargeCounterMah: Int?,
)

/**
 * Live device-sensor readout for the Sensors screen. Public-framework data only
 * (SensorManager / LocationManager / TelephonyManager / WifiManager /
 * BatteryManager) — never anything from the Octet SDK. Unlike iOS, Android *does*
 * expose per-satellite GNSS, neighbouring cell signal, and Wi-Fi scans, so those
 * are real here.
 *
 * The app already holds ACCESS_FINE_LOCATION before this runs (required for GNSS,
 * cell identity, and Wi-Fi scan results).
 */
class SensorMonitor(private val ctx: Context) : SensorEventListener {

    // Motion & inertial (raw).
    var accel by mutableStateOf<Triple<Float, Float, Float>?>(null); private set
    var gyro by mutableStateOf<Triple<Float, Float, Float>?>(null); private set
    var mag by mutableStateOf<Triple<Float, Float, Float>?>(null); private set
    var pressureHpa by mutableStateOf<Float?>(null); private set

    // Location detail (the fused/GPS fix that GNSS produces).
    var authStatus by mutableStateOf("—"); private set
    var coordinate by mutableStateOf<Pair<Double, Double>?>(null); private set
    var horizontalAccuracyM by mutableStateOf<Float?>(null); private set
    var verticalAccuracyM by mutableStateOf<Float?>(null); private set
    var altitudeM by mutableStateOf<Double?>(null); private set
    var speedMps by mutableStateOf<Float?>(null); private set
    var courseDeg by mutableStateOf<Float?>(null); private set
    var locationSource by mutableStateOf("—"); private set

    // Second fix — the OS **fused** provider (or NETWORK as a fallback below API
    // 31), shown alongside the GPS fix so the two accuracy circles can be compared
    // on the fix map. The GPS fix above is satellite-only; the fused fix blends
    // GNSS + network + sensors.
    var fusedCoordinate by mutableStateOf<Pair<Double, Double>?>(null); private set
    var fusedAccuracyM by mutableStateOf<Float?>(null); private set
    var fusedSource by mutableStateOf("—"); private set

    // GNSS.
    var satellitesTotal by mutableStateOf<Int?>(null); private set
    var satellitesUsedInFix by mutableStateOf(0); private set
    var constellations by mutableStateOf<Map<String, Int>>(emptyMap()); private set
    /** Per-satellite sky positions for the sky dome. */
    var satellites by mutableStateOf<List<SatInfo>>(emptyList()); private set

    // Cellular.
    var radioTech by mutableStateOf("—"); private set
    var cellCount by mutableStateOf<Int?>(null); private set
    var strongestDbm by mutableStateOf<Int?>(null); private set
    /** Per-cell signal list for the signal-strength view (no positions). */
    var cells by mutableStateOf<List<CellSignal>>(emptyList()); private set
    /** Per-cell identity + signal, for the triangulation cinematic. */
    var cellTowers by mutableStateOf<List<CellTower>>(emptyList()); private set

    // Wi-Fi.
    var wifiCount by mutableStateOf<Int?>(null); private set
    var wifiTop by mutableStateOf<List<Pair<String, Int>>>(emptyList()); private set

    // Battery.
    var battery by mutableStateOf<BatterySnapshot?>(null); private set

    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    val accelAvailable get() = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    val gyroAvailable get() = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
    val magAvailable get() = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null
    val barometerAvailable get() = sm.getDefaultSensor(Sensor.TYPE_PRESSURE) != null

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            satellitesTotal = status.satelliteCount
            var used = 0
            val byConst = HashMap<String, Int>()
            val list = ArrayList<SatInfo>(status.satelliteCount)
            for (i in 0 until status.satelliteCount) {
                val inFix = status.usedInFix(i)
                if (inFix) used++
                val name = constellationName(status.getConstellationType(i))
                byConst[name] = (byConst[name] ?: 0) + 1
                val az = status.getAzimuthDegrees(i)
                val el = status.getElevationDegrees(i)
                // Az/el/Cn0 are always readable (0 when the receiver has no fix on
                // that satellite yet); drop the (0,0) placeholders so the dome
                // only shows satellites with a real sky position.
                if (!(az == 0f && el == 0f)) {
                    list.add(
                        SatInfo(
                            constellation = name,
                            svid = status.getSvid(i),
                            azimuthDeg = az,
                            elevationDeg = el,
                            cn0 = status.getCn0DbHz(i),
                            usedInFix = inFix,
                            band = bandFor(status, i),
                            hasEphemeris = status.hasEphemerisData(i),
                            hasAlmanac = status.hasAlmanacData(i),
                        ),
                    )
                }
            }
            satellitesUsedInFix = used
            constellations = byConst
            satellites = list
        }
    }

    private val locationListener = LocationListener { loc -> onLocation(loc) }
    private val fusedListener = LocationListener { loc -> onFused(loc) }

    @SuppressLint("MissingPermission")
    fun start() {
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        sm.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        authStatus = authString()
        runCatching { lm.registerGnssStatusCallback(ctx.mainExecutor, gnssCallback) }
        runCatching {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(::onLocation)
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener, Looper.getMainLooper())
        }
        runCatching {
            // Prefer the OS fused provider (API 31+); fall back to NETWORK below that.
            val fused = if (Build.VERSION.SDK_INT >= 31 &&
                lm.allProviders.contains(LocationManager.FUSED_PROVIDER)
            ) LocationManager.FUSED_PROVIDER else LocationManager.NETWORK_PROVIDER
            fusedSource = if (fused == LocationManager.FUSED_PROVIDER) "Fused (OS)" else "Network"
            lm.getLastKnownLocation(fused)?.let(::onFused)
            lm.requestLocationUpdates(fused, 1000L, 0f, fusedListener, Looper.getMainLooper())
        }
        refreshCellular()
        refreshWifi()
        refreshBattery()
    }

    fun stop() {
        sm.unregisterListener(this)
        runCatching { lm.unregisterGnssStatusCallback(gnssCallback) }
        runCatching { lm.removeUpdates(locationListener) }
        runCatching { lm.removeUpdates(fusedListener) }
    }

    override fun onSensorChanged(e: SensorEvent) {
        // Guard element access — the barometer (TYPE_PRESSURE) delivers a single
        // value, so values[1]/[2] must never be read unconditionally.
        fun triple() = Triple(
            e.values.getOrElse(0) { 0f },
            e.values.getOrElse(1) { 0f },
            e.values.getOrElse(2) { 0f },
        )
        when (e.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> accel = triple()
            Sensor.TYPE_GYROSCOPE -> gyro = triple()
            Sensor.TYPE_MAGNETIC_FIELD -> mag = triple()
            Sensor.TYPE_PRESSURE -> pressureHpa = e.values.getOrNull(0)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun onLocation(loc: Location) {
        coordinate = loc.latitude to loc.longitude
        horizontalAccuracyM = if (loc.hasAccuracy()) loc.accuracy else null
        verticalAccuracyM = if (loc.hasVerticalAccuracy()) loc.verticalAccuracyMeters else null
        altitudeM = if (loc.hasAltitude()) loc.altitude else null
        speedMps = if (loc.hasSpeed()) loc.speed else null
        courseDeg = if (loc.hasBearing()) loc.bearing else null
        val mock = if (Build.VERSION.SDK_INT >= 31) loc.isMock else @Suppress("DEPRECATION") loc.isFromMockProvider
        locationSource = when {
            mock -> "Simulated (mock provider)"
            loc.provider == LocationManager.GPS_PROVIDER -> "GPS (device sensor)"
            loc.provider == LocationManager.NETWORK_PROVIDER -> "Network"
            else -> loc.provider ?: "Device sensor"
        }
    }

    private fun onFused(loc: Location) {
        fusedCoordinate = loc.latitude to loc.longitude
        fusedAccuracyM = if (loc.hasAccuracy()) loc.accuracy else null
    }

    @SuppressLint("MissingPermission")
    fun refreshCellular() {
        val t = tm ?: return
        radioTech = runCatching { radioName(t.dataNetworkType) }.getOrDefault("—")
        val infos = runCatching { t.allCellInfo }.getOrNull() ?: return
        val towers = infos.mapNotNull { info ->
            when (info) {
                is CellInfoLte -> info.cellIdentity.let { id ->
                    CellTower("LTE", info.cellSignalStrength.dbm, id.mccString?.toIntOrNull(),
                        id.mncString?.toIntOrNull(), id.tac.orNull(), id.ci.orNull()?.toLong(),
                        id.pci.orNull(), id.earfcn.orNull(), info.isRegistered)
                }
                is CellInfoNr -> {
                    val id = info.cellIdentity as? android.telephony.CellIdentityNr
                    (info.cellSignalStrength as? CellSignalStrengthNr)?.dbm?.let { dbm ->
                        CellTower("5G", dbm, id?.mccString?.toIntOrNull(), id?.mncString?.toIntOrNull(),
                            id?.tac?.orNull(), id?.nci?.orNullL(), id?.pci?.orNull(), id?.nrarfcn?.orNull(),
                            info.isRegistered)
                    }
                }
                is CellInfoWcdma -> info.cellIdentity.let { id ->
                    CellTower("3G", info.cellSignalStrength.dbm, id.mccString?.toIntOrNull(),
                        id.mncString?.toIntOrNull(), id.lac.orNull(), id.cid.orNull()?.toLong(),
                        id.psc.orNull(), id.uarfcn.orNull(), info.isRegistered)
                }
                is CellInfoGsm -> info.cellIdentity.let { id ->
                    CellTower("2G", info.cellSignalStrength.dbm, id.mccString?.toIntOrNull(),
                        id.mncString?.toIntOrNull(), id.lac.orNull(), id.cid.orNull()?.toLong(),
                        null, id.arfcn.orNull(), info.isRegistered)
                }
                else -> null
            }
        }.filter { it.dbm != Int.MAX_VALUE && it.dbm != Int.MIN_VALUE }
            .sortedByDescending { it.dbm }
        cellTowers = towers
        cells = towers.map { CellSignal(it.tech, it.dbm) }
        cellCount = towers.size
        strongestDbm = towers.firstOrNull()?.dbm
    }

    private fun Int.orNull(): Int? = if (this == Int.MAX_VALUE || this == Int.MIN_VALUE) null else this
    private fun Long.orNullL(): Long? = if (this == Long.MAX_VALUE || this == Long.MIN_VALUE) null else this

    @SuppressLint("MissingPermission")
    fun refreshWifi() {
        val w = wm ?: return
        val results = runCatching { w.scanResults }.getOrNull() ?: return
        wifiCount = results.size
        wifiTop = results.sortedByDescending { it.level }.take(6).map { r ->
            val name = if (Build.VERSION.SDK_INT >= 33) r.wifiSsid?.toString()?.trim('"')?.ifEmpty { "(hidden)" } ?: "(hidden)"
            else @Suppress("DEPRECATION") (r.SSID.ifEmpty { "(hidden)" })
            name to r.level
        }
    }

    fun refreshBattery() {
        val b = bm ?: return
        val level = b.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
        // CURRENT_NOW is µA (OEM sign varies); CHARGE_COUNTER is µAh.
        val currentUa = b.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val chargeUah = b.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        battery = BatterySnapshot(
            levelPct = level,
            charging = b.isCharging,
            currentMa = if (currentUa == Int.MIN_VALUE || currentUa == 0) null else Math.abs(currentUa) / 1000,
            chargeCounterMah = if (chargeUah <= 0) null else chargeUah / 1000,
        )
    }

    private fun authString(): String {
        val fine = ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ctx.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return when {
            fine -> "Precise (fine)"
            coarse -> "Approximate (coarse)"
            else -> "Denied"
        }
    }

    /** Map a satellite's carrier frequency to a human band label (L1/L5/…). */
    private fun bandFor(status: GnssStatus, i: Int): String {
        if (!status.hasCarrierFrequencyHz(i)) return "—"
        val mhz = status.getCarrierFrequencyHz(i) / 1e6
        return when {
            mhz in 1565.0..1585.0 -> "L1/E1/B1"   // ~1575.42
            mhz in 1197.0..1214.0 -> "L5/E5a"      // ~1176/1207
            mhz in 1200.0..1214.5 -> "E5b"
            mhz in 1223.0..1231.0 -> "L2"          // ~1227.6
            mhz in 1596.0..1610.0 -> "G1"          // GLONASS L1
            mhz in 1242.0..1252.0 -> "G2"          // GLONASS L2
            else -> "%.0f MHz".format(mhz)
        }
    }

    private fun constellationName(type: Int) = when (type) {
        GnssStatus.CONSTELLATION_GPS -> "GPS"
        GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
        GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
        GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
        GnssStatus.CONSTELLATION_QZSS -> "QZSS"
        GnssStatus.CONSTELLATION_SBAS -> "SBAS"
        GnssStatus.CONSTELLATION_IRNSS -> "IRNSS"
        else -> "Other"
    }

    private fun radioName(type: Int) = when (type) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G (NR)"
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA -> "3G"
        TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
        TelephonyManager.NETWORK_TYPE_UNKNOWN -> "—"
        else -> "cellular"
    }
}
