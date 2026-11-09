package com.octetproof.sample

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration

/**
 * Compose host for the Octet sample. Requests location, starts the SDK, feeds
 * live GPS to the view-model's map, and (later) routes incoming `.octetproof`
 * files to the import preview. Mirrors the iOS OctetSampleApp entry.
 */
class MainActivity : ComponentActivity(), LocationListener {

    private val vm: SampleViewModel by viewModels()

    // Ask for precise and approximate together (the Android 12+ pattern). The user may
    // grant approximate only; the SDK still runs then (country-level proofs work, finer levels
    // report insufficientPrecision), so either grant starts it.
    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> if (result.values.any { it }) onLocationGranted() }

    // Best-effort: step counter access needs ACTIVITY_RECOGNITION on API 29+.
    // Not required for the SDK to run.
    private val requestActivityRecognition = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result ignored: the SDK runs either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // osmdroid needs a user-agent string + prefs at process start (OSM tile
        // policy). Idempotent — the Configuration is a static singleton.
        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osmdroid", Context.MODE_PRIVATE),
        )
        Configuration.getInstance().userAgentValue = packageName
        setContent {
            OctetSampleTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RootScreen(vm)
                }
            }
        }
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) onLocationGranted()
        else requestPermission.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun onLocationGranted() {
        startLocationUpdates()
        maybeRequestActivityRecognition()
        vm.onPermissionGranted(precise = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
    }

    private fun maybeRequestActivityRecognition() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestActivityRecognition.launch(Manifest.permission.ACTIVITY_RECOGNITION)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        // The GPS provider needs precise location; with approximate only, use the network one.
        val precise = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val provider = when {
            precise && lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return
        }
        lm.requestLocationUpdates(provider, 5_000L, 10f, this)
        lm.getLastKnownLocation(provider)?.let { onLocationChanged(it) }
    }

    override fun onLocationChanged(location: Location) {
        vm.onLocation(location.latitude, location.longitude)
    }
}
