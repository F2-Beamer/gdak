package de.gdak.app.ui.format

import java.util.Locale

/** Wertung mit einer Nachkommastelle und deutschem Dezimalkomma, z. B. `2,5`. */
internal fun formatRating(value: Double): String = String.format(Locale.GERMANY, "%.1f", value)
