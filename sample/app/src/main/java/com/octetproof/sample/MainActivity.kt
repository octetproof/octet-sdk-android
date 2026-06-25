package com.octetproof.sample

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.octetproof.sdk.api.AdvancedConfig
import com.octetproof.sdk.api.LicenseState
import com.octetproof.sdk.api.Octet
import com.octetproof.sdk.api.OctetConfig
import com.octetproof.sdk.api.OctetRegion
import com.octetproof.sdk.api.OctetSdk
import com.octetproof.sample.BuildConfig
import java.time.Instant
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

// License key comes from `BuildConfig.OCTET_LICENSE_KEY`, generated
// from the `octet.licenseKey` line in `android/local.properties` at
// build time. See `android/local.properties.example` for the shape.
// Missing key → empty string → `Octet.start` throws
// `LicenseError.MalformedKey` on launch.

/** Display row in the country picker. */
private data class Country(val iso: String, val name: String) {
    override fun toString(): String = "$name ($iso)"
}

/**
 * ISO 3166-1 codes representing dependent territories, overseas
 * departments, or otherwise non-sovereign entities. Best effort —
 * diplomatically-contested edges (Taiwan / Palestine / Cook Islands /
 * Niue) are KEPT in the picker rather than asserted as non-sovereign
 * here. Kept in sync with iOS ContentView.swift's
 * `dependentTerritoryCodes`.
 */
private val DEPENDENT_TERRITORY_CODES: Set<String> = setOf(
    "AQ", "AX", "BV", "CC", "CX", "EH", "FK", "FO", "GF", "GG",
    "GI", "GL", "GP", "GS", "GU", "HK", "HM", "IM", "IO", "JE",
    "KY", "MF", "MO", "MP", "MQ", "MS", "NC", "NF", "PF", "PM",
    "PN", "PR", "RE", "SH", "SJ", "TC", "TF", "TK", "UM", "VG",
    "VI", "WF", "YT",
)

/**
 * Full ISO 3166-1 alpha-2 list minus dependent territories, localized
 * to the device locale and sorted by display name using
 * locale-aware collation. Pulled from `Locale.getISOCountries()` so we
 * don't carry a hardcoded country table that decays.
 */
private val DEMO_COUNTRIES: List<Country> = run {
    val deviceLocale = Locale.getDefault()
    val collator = Collator.getInstance(deviceLocale).apply { strength = Collator.PRIMARY }
    Locale.getISOCountries()
        .filter { it.length == 2 && it !in DEPENDENT_TERRITORY_CODES }
        .map { iso -> Country(iso, Locale("", iso).getDisplayCountry(deviceLocale)) }
        .filter { it.name.isNotEmpty() }
        .sortedWith(compareBy(collator) { it.name })
}

/**
 * Default selection — device's current country code, with US and the
 * first entry as fallbacks.
 */
private fun defaultCountry(): Country {
    val deviceCode = Locale.getDefault().country
    return DEMO_COUNTRIES.firstOrNull { it.iso == deviceCode }
        ?: DEMO_COUNTRIES.firstOrNull { it.iso == "US" }
        ?: DEMO_COUNTRIES.first()
}

class MainActivity : AppCompatActivity(), LocationListener {

    private var sdk: OctetSdk? = null

    private lateinit var permStatus: TextView
    private lateinit var sdkStatus: TextView
    private lateinit var verdictView: TextView
    private lateinit var licenseExpiryNotice: TextView
    private lateinit var btnTest: Button
    private lateinit var countrySpinner: Spinner
    private lateinit var mapView: MapView
    private var userMarker: Marker? = null
    private var hasZoomedToUser = false

    private var selectedCountry: Country = defaultCountry()

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> onPermissionResult(granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // osmdroid requires a user-agent string at process start; per
        // OSM's tile-usage policy, anything app-identifying is fine.
        // Configuration is a static singleton; this call is idempotent.
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_main)

        permStatus = findViewById(R.id.permStatus)
        sdkStatus = findViewById(R.id.sdkStatus)
        verdictView = findViewById(R.id.verdictView)
        licenseExpiryNotice = findViewById(R.id.licenseExpiryNotice)
        btnTest = findViewById(R.id.btnTest)
        countrySpinner = findViewById(R.id.countrySpinner)
        mapView = findViewById(R.id.mapView)

        setupMap()
        setupCountrySpinner()

        btnTest.setOnClickListener { runIsWithinCheck() }

        val already = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (already) onPermissionResult(true)
        else requestPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        // Default view: mid-Europe at zoom 4 until we have a real fix.
        mapView.controller.setZoom(4.0)
        mapView.controller.setCenter(GeoPoint(47.5, 14.5))
    }

    private fun setupCountrySpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, DEMO_COUNTRIES)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        countrySpinner.adapter = adapter
        countrySpinner.setSelection(DEMO_COUNTRIES.indexOf(selectedCountry))
        countrySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long,
            ) {
                selectedCountry = DEMO_COUNTRIES[position]
                btnTest.text = "Test: isWithin(${selectedCountry.iso})"
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        btnTest.text = "Test: isWithin(${selectedCountry.iso})"
    }

    private fun onPermissionResult(granted: Boolean) {
        permStatus.text = "permission ACCESS_FINE_LOCATION: ${if (granted) "GRANTED" else "DENIED"}"
        if (!granted) {
            sdkStatus.text = "SDK: cannot start without location permission"
            return
        }
        startLocationUpdates()
        sdkStatus.text = "SDK starting…"
        lifecycleScope.launch {
            try {
                val config = OctetConfig(
                    licenseKey = BuildConfig.OCTET_LICENSE_KEY,
                    // #122/#123: opt-in proof upload to prod backend.
                    // Same host as activation since octet-proofs is
                    // co-hosted with the license server.
                    proofUploadUrl = BuildConfig.OCTET_ACTIVATION_SERVER_URL,
                    advanced = AdvancedConfig(
                        activationServerUrl = BuildConfig.OCTET_ACTIVATION_SERVER_URL,
                        // Enables Android Play Integrity when set in local.properties
                        // (octet.playIntegrityCloudProjectNumber); 0L → disabled.
                        playIntegrityCloudProjectNumber =
                            BuildConfig.OCTET_PI_CLOUD_PROJECT.takeIf { it != 0L }))
                val started = Octet.start(this@MainActivity, config)
                sdk = started
                sdkStatus.text = "SDK started — waiting for first proof…"
                started.licenseStatus?.let { s ->
                    verdictView.text = "license: ${s.state}, ${s.daysUntilHardStop ?: -1}d remaining"
                    if (s.state == LicenseState.GRACE_PERIOD && s.daysUntilHardStop != null) {
                        val days = s.daysUntilHardStop
                        licenseExpiryNotice.text =
                            "Your license expires in $days day${if (days == 1) "" else "s"} — please renew."
                        licenseExpiryNotice.visibility = android.view.View.VISIBLE
                    }
                }
                btnTest.isEnabled = true
            } catch (e: Exception) {
                sdkStatus.text = "SDK failed to start"
                verdictView.text = "start error: $e"
            }
        }
    }

    /**
     * Live device-location updates feed the map marker. Independent of
     * the SDK's location pipeline — the toy intentionally uses the raw
     * system `LocationManager` so the map shows the device's GPS fix,
     * not the SDK's internal estimate.
     */
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return
        }
        // Permission already checked before this call.
        lm.requestLocationUpdates(provider, 5_000L, 10f, this)
        lm.getLastKnownLocation(provider)?.let { onLocationChanged(it) }
    }

    override fun onLocationChanged(location: Location) {
        val point = GeoPoint(location.latitude, location.longitude)
        val marker = userMarker ?: Marker(mapView).also {
            it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            it.title = "You are here"
            mapView.overlays.add(it)
            userMarker = it
        }
        marker.position = point
        if (!hasZoomedToUser) {
            mapView.controller.setZoom(14.0)
            mapView.controller.animateTo(point)
            hasZoomedToUser = true
        }
        mapView.invalidate()
    }

    private fun runIsWithinCheck() {
        val activeSdk = sdk ?: return
        val iso = selectedCountry.iso
        verdictView.text = "querying $iso…"
        lifecycleScope.launch {
            val v = activeSdk.loc.isWithin(OctetRegion.country(iso), Instant.now())
            verdictView.text = buildString {
                append("country: ").append(iso).append(" (").append(selectedCountry.name).append(")\n")
                append("result:  ").append(v.result).append('\n')
                append("reason:  ").append(v.reason).append('\n')
                append("message: ").append(v.message).append('\n')
                append("proof:   ").append(v.proof != null)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}
