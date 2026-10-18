package de.gdak.app.data.cachedb

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.mapsforge.core.model.BoundingBox
import java.io.File
import java.util.Locale

/** Die Felder eines Caches, die für Karte, Liste und Schnellansicht gebraucht werden. */
data class CacheSummary(
    val code: String,
    val name: String,
    val cacheType: String,
    val latitude: Double,
    val longitude: Double,
    val archived: Boolean,
    val found: Boolean,
    val dnf: Boolean,
    /** Die Koordinaten wurden vom Nutzer korrigiert (GSAK-Spalte `HasCorrected`). */
    val hasCorrected: Boolean,
    /** Größe des Behälters, z. B. „Micro“ oder „Regular“ (GSAK-Spalte `Container`). */
    val container: String,
    val difficulty: Double,
    val terrain: Double,
    val userNote: String,
    /** Besitzer des Caches (GSAK-Spalte `OwnerName`). */
    val ownerName: String,
    /** Versteckdatum im Format JJJJ-MM-TT (GSAK-Spalte `PlacedDate`). */
    val placedDate: String,
)

/**
 * Liest Caches aus einer GSAK-Datenbank. Die Datei wird nur lesend geöffnet.
 * Die Koordinaten sind in GSAK als TEXT gespeichert und werden in der Abfrage umgewandelt.
 * Typ, Status, Difficulty, Terrain, Suche und Kartenausschnitt laufen in SQL. Die Entfernung
 * berechnet die App selbst.
 */
class CacheRepository(private val databaseFile: File) {

    /**
     * Caches, die zum [filter] passen. Mit [box] nur innerhalb des Kartenausschnitts,
     * mit [limit] höchstens so viele Ergebnisse.
     */
    fun cachesMatching(filter: CacheFilter, box: BoundingBox? = null, limit: Int? = null): List<CacheSummary> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()

        if (box != null) {
            where += "CAST(c.Latitude AS REAL) BETWEEN ${number(box.minLatitude)} AND ${number(box.maxLatitude)}"
            where += "CAST(c.Longitude AS REAL) BETWEEN ${number(box.minLongitude)} AND ${number(box.maxLongitude)}"
        }
        typeClause(filter)?.let { where += it }
        statusClause(filter)?.let { where += it }
        rangeClause("Difficulty", filter.difficulty)?.let { where += it }
        rangeClause("Terrain", filter.terrain)?.let { where += it }

        val term = filter.searchText.trim()
        if (term.isNotEmpty()) {
            val pattern = "%${escapeLike(term)}%"
            where += "(c.Code LIKE ? ESCAPE '\\' OR c.Name LIKE ? ESCAPE '\\')"
            args += pattern
            args += pattern
        }

        val sql = buildString {
            append(SELECT_SQL)
            if (where.isNotEmpty()) append(" WHERE ").append(where.joinToString(" AND "))
            if (limit != null) append(" LIMIT ").append(limit)
        }

        return SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(sql, args.toTypedArray()).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(readCache(cursor))
                }
            }
        }
    }

    /** Alle Angaben der Detailansicht für den Cache [code], oder null, wenn er nicht existiert. */
    fun cacheDetail(code: String): CacheDetail? {
        return SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val summary = db.rawQuery("$SELECT_SQL WHERE c.Code = ?", arrayOf(code)).use { cursor ->
                if (cursor.moveToFirst()) readCache(cursor) else null
            } ?: return null

            val memo = db.rawQuery(
                "SELECT ShortDescription, LongDescription, Hints FROM CacheMemo WHERE Code = ?",
                arrayOf(code),
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    Triple(cursor.getString(0) ?: "", cursor.getString(1) ?: "", cursor.getString(2) ?: "")
                } else {
                    Triple("", "", "")
                }
            }

            val logs = db.rawQuery(
                """
                SELECT l.lType, l.lBy, l.lDate, m.lText
                FROM Logs l
                LEFT JOIN LogMemo m ON m.lLogId = l.lLogId
                WHERE l.lParent = ?
                ORDER BY l.lDate DESC, l.lLogId DESC
                LIMIT $LOG_LIMIT
                """,
                arrayOf(code),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            CacheLog(
                                type = cursor.getString(0) ?: "",
                                by = cursor.getString(1) ?: "",
                                date = cursor.getString(2) ?: "",
                                text = cursor.getString(3) ?: "",
                            ),
                        )
                    }
                }
            }

            val waypoints = db.rawQuery(
                "SELECT cPrefix, cCode, cName, cType, cLat, cLon FROM Waypoints WHERE cParent = ? ORDER BY cPrefix, cCode",
                arrayOf(code),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            CacheWaypoint(
                                prefix = cursor.getString(0) ?: "",
                                code = cursor.getString(1) ?: "",
                                name = cursor.getString(2) ?: "",
                                type = cursor.getString(3) ?: "",
                                latitude = cursor.getString(4)?.toDoubleOrNull() ?: 0.0,
                                longitude = cursor.getString(5)?.toDoubleOrNull() ?: 0.0,
                            ),
                        )
                    }
                }
            }

            val attributes = db.rawQuery(
                "SELECT DISTINCT aId, aInc FROM Attributes WHERE aCode = ? ORDER BY aId, aInc",
                arrayOf(code),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(CacheAttribute(id = cursor.getInt(0), positive = cursor.getInt(1) != 0))
                    }
                }
            }

            CacheDetail(
                summary = summary,
                shortDescription = memo.first,
                longDescription = memo.second,
                hints = memo.third,
                logs = logs,
                waypoints = waypoints,
                attributes = attributes,
            )
        }
    }

    private fun readCache(cursor: Cursor): CacheSummary = CacheSummary(
        code = cursor.getString(0) ?: "",
        name = cursor.getString(1) ?: "",
        cacheType = cursor.getString(2) ?: "",
        latitude = cursor.getString(3)?.toDoubleOrNull() ?: 0.0,
        longitude = cursor.getString(4)?.toDoubleOrNull() ?: 0.0,
        archived = cursor.getInt(5) != 0,
        found = cursor.getInt(6) != 0,
        hasCorrected = cursor.getInt(7) != 0,
        dnf = cursor.getInt(8) != 0,
        container = cursor.getString(9) ?: "",
        difficulty = cursor.getDouble(10),
        terrain = cursor.getDouble(11),
        userNote = cursor.getString(12) ?: "",
        ownerName = cursor.getString(13) ?: "",
        placedDate = cursor.getString(14) ?: "",
    )

    /** Typfilter. Die Werte sind feste GSAK-Kürzel, keine Nutzereingaben. */
    private fun typeClause(filter: CacheFilter): String? {
        if (filter.types.size == CacheTypeGroup.entries.size) return null
        if (filter.types.isEmpty()) return "0 = 1"
        val parts = filter.types.map { group ->
            if (group == CacheTypeGroup.OTHER) {
                "c.CacheType NOT IN (${quotedKnownTypes()})"
            } else {
                "c.CacheType IN (${group.gsakTypes.joinToString { "'$it'" }})"
            }
        }
        return parts.joinToString(" OR ", prefix = "(", postfix = ")")
    }

    private fun quotedKnownTypes(): String =
        CacheTypeGroup.entries.flatMap { it.gsakTypes }.joinToString { "'$it'" }

    /**
     * Status: gefunden, DNF (nicht gefunden mit DNF) und nicht gefunden sind getrennte Gruppen.
     * Archivierte Caches werden zusätzlich ausgeblendet, wenn der Schalter aus ist.
     */
    private fun statusClause(filter: CacheFilter): String? {
        val allowed = buildList {
            if (filter.showFound) add("c.Found <> 0")
            if (filter.showDnf) add("(c.Found = 0 AND c.DNF <> 0)")
            if (filter.showNotFound) add("(c.Found = 0 AND c.DNF = 0)")
        }
        val parts = buildList {
            if (allowed.size < 3) add(if (allowed.isEmpty()) "0 = 1" else "(${allowed.joinToString(" OR ")})")
            if (!filter.showArchived) add("c.Archived = 0")
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
    }

    private fun rangeClause(column: String, range: ClosedFloatingPointRange<Float>): String? {
        if (range.start <= RATING_MIN && range.endInclusive >= RATING_MAX) return null
        return "CAST(c.$column AS REAL) BETWEEN ${number(range.start.toDouble())} AND " +
            number(range.endInclusive.toDouble())
    }

    /** Schreibt LIKE-Platzhalter in der Suche wörtlich. */
    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    /** Zahlen werden direkt in die Abfrage geschrieben, damit der Vergleich als REAL erfolgt. */
    private fun number(value: Double): String = String.format(Locale.US, "%.7f", value)

    companion object {
        /** Obergrenze für die Anzahl der Caches im sichtbaren Kartenausschnitt. */
        const val MAX_MAP_RESULTS = 500

        /** Anzahl der Logs in der Detailansicht. */
        const val LOG_LIMIT = 20

        private const val SELECT_SQL = """
            SELECT c.Code, c.Name, c.CacheType, c.Latitude, c.Longitude, c.Archived, c.Found,
                   c.HasCorrected, c.DNF, c.Container, c.Difficulty, c.Terrain, m.UserNote,
                   c.OwnerName, c.PlacedDate
            FROM Caches c
            LEFT JOIN CacheMemo m ON m.Code = c.Code
        """
    }
}
