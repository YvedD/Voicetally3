package com.yvesds.voicetally3.managers

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

/**
 * Moderne permissiebeheerder:
 * - Geen verouderde storage-permissies (SAF wordt gebruikt).
 * - Bluetooth permissies volgens Android-versie.
 * - Fine location blijft nodig voor BLE scan en GPS.
 */
object PermissionsManager {

    private fun requiredPermissions(): Array<String> {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31): nieuwe Bluetooth-permissies
            perms += Manifest.permission.BLUETOOTH_CONNECT
            perms += Manifest.permission.BLUETOOTH_SCAN
        } else {
            // Oudere toestellen
            @Suppress("DEPRECATION")
            perms += Manifest.permission.BLUETOOTH
            @Suppress("DEPRECATION")
            perms += Manifest.permission.BLUETOOTH_ADMIN
        }

        return perms.toTypedArray()
    }

    /** ✅ Controleer of alle vereiste permissies verleend zijn. */
    fun allPermissionsGranted(context: Context): Boolean {
        val all = requiredPermissions()
        return all.all { p ->
            ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** ✅ Vraag alle vereiste permissies via de meegegeven launcher. */
    fun requestPermissions(
        activity: Activity,
        launcher: ActivityResultLauncher<Array<String>>
    ) {
        val toRequest = requiredPermissions().filter { p ->
            ContextCompat.checkSelfPermission(activity, p) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            launcher.launch(toRequest.toTypedArray())
        }
    }
}
