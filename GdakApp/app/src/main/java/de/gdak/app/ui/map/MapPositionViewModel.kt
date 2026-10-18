package de.gdak.app.ui.map

import androidx.lifecycle.ViewModel
import org.mapsforge.core.model.LatLong

/**
 * Letzte Kartenansicht. Sie bleibt beim Wechsel zwischen Karte und Liste erhalten, damit die
 * Karte dort weitergeht, wo sie verlassen wurde. Die Liste sortiert nach dem Mittelpunkt.
 */
class MapPositionViewModel : ViewModel() {

    /** Mittelpunkt der Karte, `null` solange die Karte noch nie bewegt wurde. */
    var center: LatLong? = null

    /** Zoomstufe zum gespeicherten Mittelpunkt. */
    var zoom: Byte = 0

    /** Dateipfad der Kartendatei, zu der der Mittelpunkt gehört. */
    var mapPath: String? = null
}
