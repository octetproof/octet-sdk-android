package com.octetproof.sample

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Non-sensitive device capability readout for the Device Info screen —
 *  capabilities + states ONLY, never a device identifier, key, or coordinate.
 *  Mirrors iOS DeviceInfo. */
data class InfoRow(val key: String, val value: String)
data class InfoCategory(val title: String, val rows: List<InfoRow>)

object DeviceInfo {
    fun categories(ctx: Context): List<InfoCategory> =
        listOf(operatingSystem(), secureHardware(ctx), sensors(ctx), permissions(ctx))

    private fun operatingSystem() = InfoCategory("Operating system", listOf(
        InfoRow("system", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
        InfoRow("model", "${Build.MANUFACTURER} ${Build.MODEL}"),
    ))

    private fun secureHardware(ctx: Context): InfoCategory {
        val pm = ctx.packageManager
        val strongBox = Build.VERSION.SDK_INT >= 28 &&
            pm.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        val hwKeystore = Build.VERSION.SDK_INT >= 31 &&
            pm.hasSystemFeature(PackageManager.FEATURE_HARDWARE_KEYSTORE)
        return InfoCategory("Secure hardware", listOf(
            InfoRow("StrongBox", if (strongBox) "yes" else "no"),
            InfoRow("Hardware keystore", if (hwKeystore || strongBox) "yes" else "unknown"),
            InfoRow("key backing", if (strongBox) "StrongBox" else if (hwKeystore) "TEE" else "software/unknown"),
        ))
    }

    private fun sensors(ctx: Context): InfoCategory {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        fun yn(type: Int) = if (sm.getDefaultSensor(type) != null) "available" else "unavailable"
        val locEnabled = (ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
            .isProviderEnabled(LocationManager.GPS_PROVIDER)
        return InfoCategory("Sensors", listOf(
            InfoRow("Location (GPS)", if (locEnabled) "enabled" else "disabled"),
            InfoRow("Accelerometer", yn(Sensor.TYPE_ACCELEROMETER)),
            InfoRow("Gyroscope", yn(Sensor.TYPE_GYROSCOPE)),
            InfoRow("Magnetometer", yn(Sensor.TYPE_MAGNETIC_FIELD)),
            InfoRow("Barometer", yn(Sensor.TYPE_PRESSURE)),
        ))
    }

    private fun permissions(ctx: Context): InfoCategory {
        fun state(perm: String) = if (ContextCompat.checkSelfPermission(ctx, perm) ==
            PackageManager.PERMISSION_GRANTED) "granted" else "denied"
        return InfoCategory("Permissions", listOf(
            InfoRow("Location (fine)", state(android.Manifest.permission.ACCESS_FINE_LOCATION)),
        ))
    }
}
