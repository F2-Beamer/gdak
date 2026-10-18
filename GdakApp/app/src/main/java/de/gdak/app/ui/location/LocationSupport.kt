package de.gdak.app.ui.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import androidx.core.util.Consumer

/** Berechtigungen für den Standort, wie sie im Dialog angefragt werden. */
val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Liefert den Standort. Der letzte bekannte Standort kommt sofort, damit die Karte nicht warten muss.
 * Mit `preferCurrent` wird danach ein frischer Fix angefordert und nachgeliefert, sobald er da ist.
 * Ohne GPS- oder Netzwerkanbieter passiert nichts.
 */
@SuppressLint("MissingPermission")
fun findLocation(context: Context, preferCurrent: Boolean = false, onFound: (Location) -> Unit) {
    if (!hasLocationPermission(context)) return
    val locationManager = context.getSystemService(LocationManager::class.java)
    val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .firstOrNull { locationManager.isProviderEnabled(it) } ?: return

    val lastKnown = locationManager.getLastKnownLocation(provider)
    lastKnown?.let(onFound)
    if (lastKnown != null && !preferCurrent) return

    LocationManagerCompat.getCurrentLocation(
        locationManager,
        provider,
        CancellationSignal(),
        ContextCompat.getMainExecutor(context),
        Consumer<Location?> { location -> location?.let(onFound) },
    )
}
