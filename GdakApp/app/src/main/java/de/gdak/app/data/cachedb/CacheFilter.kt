package de.gdak.app.data.cachedb

/** Untere und obere Grenze für Difficulty und Terrain. */
const val RATING_MIN = 1f
const val RATING_MAX = 5f

/** Zwischenschritte für 0,5er-Abstufungen zwischen [RATING_MIN] und [RATING_MAX]. */
const val RATING_STEPS = 7

/** Typgruppen für den Filter. Mystery umfasst die GSAK-Typen U und B. */
enum class CacheTypeGroup(val label: String, val gsakTypes: Set<String>) {
    TRADITIONAL("Traditionell", setOf("T")),
    MULTI("Multi-Cache", setOf("M")),
    MYSTERY("Mystery", setOf("U", "B")),
    EVENT("Event-Cache", setOf("E")),
    LAB("Lab-Cache", setOf("Q")),
    WHERIGO("Wherigo", setOf("I")),
    VIRTUAL("Virtuell", setOf("V")),
    EARTH("EarthCache", setOf("R")),
    OTHER("Sonstige", emptySet()),
    ;

    companion object {
        /** Gruppe eines GSAK-Typs. Alle nicht bekannten Typen landen in [OTHER]. */
        fun of(cacheType: String): CacheTypeGroup = entries.firstOrNull { cacheType in it.gsakTypes } ?: OTHER
    }
}

/** Der Filter für Karte und Liste. Standardmäßig ist alles sichtbar. */
data class CacheFilter(
    val types: Set<CacheTypeGroup> = CacheTypeGroup.entries.toSet(),
    val showFound: Boolean = true,
    val showNotFound: Boolean = true,
    val showDnf: Boolean = true,
    val showArchived: Boolean = true,
    val difficulty: ClosedFloatingPointRange<Float> = RATING_MIN..RATING_MAX,
    val terrain: ClosedFloatingPointRange<Float> = RATING_MIN..RATING_MAX,
    /** Umkreis in Kilometern um die eigene Position, `null` heißt ohne Begrenzung. */
    val maxDistanceKm: Float? = null,
    val searchText: String = "",
) {
    /** Ob ein Filter von der Standardeinstellung abweicht. Die Suche zählt nicht mit. */
    val isActive: Boolean
        get() = copy(searchText = "") != CacheFilter()
}

/**
 * Prüft den Entfernungsfilter. Ohne Umkreis oder ohne bekannte Entfernung passt jeder Cache,
 * damit ohne Standort nichts verschwindet.
 */
fun CacheFilter.allowsDistance(distanceKm: Double?): Boolean {
    val limit = maxDistanceKm ?: return true
    return distanceKm == null || distanceKm <= limit.toDouble()
}
