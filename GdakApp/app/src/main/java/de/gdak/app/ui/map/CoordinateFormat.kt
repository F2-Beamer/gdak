package de.gdak.app.ui.map

import java.util.Locale
import kotlin.math.abs

/**
 * Formatiert eine Position als Grad und Dezimalminuten (DMM), z. B.
 * `N 52° 31.249' E 013° 24.567'`. Breitengrad hat zwei, Längengrad drei Gradstellen.
 */
fun formatDmm(latitude: Double, longitude: Double): String =
    "${formatAxis(latitude, 'N', 'S', 2)}  ${formatAxis(longitude, 'E', 'W', 3)}"

private fun formatAxis(value: Double, positive: Char, negative: Char, degreeDigits: Int): String {
    val hemisphere = if (value < 0) negative else positive
    // Tausendstel einer Minute als Ganzzahl, damit Rundung nicht „60.000'“ ergibt.
    val thousandths = Math.round(abs(value) * 60_000)
    val degrees = thousandths / 60_000
    val minutes = thousandths % 60_000
    return String.format(
        Locale.US,
        "%c %0${degreeDigits}d° %02d.%03d'",
        hemisphere,
        degrees,
        minutes / 1000,
        minutes % 1000,
    )
}
