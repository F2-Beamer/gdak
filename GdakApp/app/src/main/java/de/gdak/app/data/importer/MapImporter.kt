package de.gdak.app.data.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import de.gdak.app.data.catalog.MapCatalogRepository
import de.gdak.app.data.catalog.MapEntry
import java.io.File
import java.io.IOException
import java.util.UUID

/** Fehler, deren Text direkt dem Nutzer angezeigt wird. */
class ImportException(message: String) : IOException(message)

/**
 * Kopiert eine Mapsforge-Karte in den App-Speicher.
 *
 * Die Kopie entsteht direkt im Zielordner als `<id>.partial` und wird erst nach
 * erfolgreicher Prüfung in `<id>.map` umbenannt. Schlägt etwas fehl, wird die
 * Teildatei gelöscht.
 */
class MapImporter(private val context: Context, private val catalog: MapCatalogRepository) {

    /** Reserve, die beim Speicherplatz-Check zusätzlich frei sein muss. */
    private val spaceBufferBytes = 100L * 1024 * 1024

    suspend fun import(uri: Uri, displayName: String): MapEntry {
        val sizeBytes = querySize(uri)
        if (sizeBytes > MAX_IMPORT_BYTES) {
            throw ImportException("Die Karte ist zu groß (maximal ${MAX_IMPORT_BYTES / (1024L * 1024 * 1024)} GB).")
        }
        val directory = MapCatalogRepository.mapsDirectory(context)
        checkFreeSpace(directory, sizeBytes)

        val id = UUID.randomUUID().toString()
        val partial = File(directory, "$id.partial")
        val target = File(directory, "$id.map")

        try {
            copyToFile(uri, partial)
            if (!hasMapsforgeHeader(partial)) {
                throw ImportException("Die Datei ist keine gültige Mapsforge-Karte (.map).")
            }
            if (!partial.renameTo(target)) {
                throw ImportException("Die Karte konnte nicht im App-Speicher abgelegt werden.")
            }
        } catch (error: Exception) {
            partial.delete()
            throw error
        }

        val entry = MapEntry(
            id = id,
            displayName = displayName,
            sizeBytes = target.length(),
            importedAt = System.currentTimeMillis(),
        )
        catalog.add(entry)
        return entry
    }

    private fun querySize(uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val column = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (column >= 0 && !cursor.isNull(column)) return cursor.getLong(column)
                }
            }
        // Größe unbekannt: ohne Angabe wird nur die Reserve geprüft.
        return 0L
    }

    private fun checkFreeSpace(directory: File, sizeBytes: Long) {
        if (directory.usableSpace < sizeBytes + spaceBufferBytes) {
            throw ImportException("Nicht genug Speicherplatz für diese Karte.")
        }
    }

    /** Kopiert die Datei und bricht ab, sobald das Größenlimit überschritten wird (auch wenn die Größe unbekannt war). */
    private fun copyToFile(uri: Uri, target: File) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw ImportException("Die gewählte Datei kann nicht gelesen werden.")
        input.use { source ->
            target.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_BYTES)
                var total = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_IMPORT_BYTES) {
                        throw ImportException("Die Karte ist zu groß (maximal ${MAX_IMPORT_BYTES / (1024L * 1024 * 1024)} GB).")
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    private fun hasMapsforgeHeader(file: File): Boolean {
        val magic = MAGIC.toByteArray(Charsets.US_ASCII)
        val header = ByteArray(magic.size)
        val read = file.inputStream().use { it.read(header) }
        return read == magic.size && header.contentEquals(magic)
    }

    private companion object {
        /** Anfang jeder Mapsforge-Karte. */
        const val MAGIC = "mapsforge binary OSM"

        /** Obergrenze für eine Karte. Große Länder-Karten liegen im Bereich mehrerer GB. */
        const val MAX_IMPORT_BYTES = 4L * 1024 * 1024 * 1024

        const val DEFAULT_BUFFER_BYTES = 64 * 1024
    }
}
