package de.gdak.app.data.cachedb

/** Alle Angaben der Detailansicht eines Caches. Nur lesend. */
data class CacheDetail(
    val summary: CacheSummary,
    /** Kurzbeschreibung, HTML (GSAK-Spalte `ShortDescription`). */
    val shortDescription: String,
    /** Langbeschreibung, HTML (GSAK-Spalte `LongDescription`). */
    val longDescription: String,
    /** Hinweise als Klartext (GSAK-Spalte `Hints`). */
    val hints: String,
    /** Die letzten Logs, neueste zuerst. */
    val logs: List<CacheLog>,
    val waypoints: List<CacheWaypoint>,
    val attributes: List<CacheAttribute>,
)

/** Ein Log aus `Logs`, der Text kommt aus `LogMemo`. */
data class CacheLog(val type: String, val by: String, val date: String, val text: String)

/** Ein Wegpunkt aus `Waypoints`. */
data class CacheWaypoint(
    val prefix: String,
    val code: String,
    val name: String,
    val type: String,
    val latitude: Double,
    val longitude: Double,
)

/** Ein Attribut aus `Attributes`. [positive] ist `true` für „ja“, sonst „nein“. */
data class CacheAttribute(val id: Int, val positive: Boolean)
