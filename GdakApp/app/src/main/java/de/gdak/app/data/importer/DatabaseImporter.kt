package de.gdak.app.data.importer

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import de.gdak.app.data.catalog.DatabaseCatalogRepository
import de.gdak.app.data.catalog.DatabaseEntry
import java.io.File
import java.util.UUID

/** Ziel eines Datenbank-Imports: ein bestehender Eintrag oder eine neue Datenbank mit diesem Namen. */
sealed interface DatabaseTarget {
    data class Existing(val entry: DatabaseEntry) : DatabaseTarget
    data class New(val name: String) : DatabaseTarget
}

/**
 * Importiert eine GSAK-Datenbank (`.db3`).
 *
 * Gearbeitet wird nur an einer Arbeitskopie der Zieldatenbank. Erst wenn das Zusammenführen
 * (bzw. das Leeren und Einfügen) in einer Transaktion gelungen ist, ersetzt die Arbeitskopie
 * die Zieldatei. Schlägt etwas fehl, wird die Transaktion zurückgerollt und die Zieldatei bleibt unverändert.
 */
class DatabaseImporter(private val context: Context, private val catalog: DatabaseCatalogRepository) {

    /** Reserve, die beim Speicherplatz-Check zusätzlich frei sein muss. */
    private val spaceBufferBytes = 100L * 1024 * 1024

    /**
     * Importiert die Datei. Ohne `clearFirst` werden die Caches zusammengeführt:
     * gleiche Codes werden durch die neue Version ersetzt, andere bleiben erhalten.
     */
    suspend fun import(uri: Uri, target: DatabaseTarget, clearFirst: Boolean): DatabaseEntry {
        val tmpDir = File(context.filesDir, "import_tmp").apply { mkdirs() }
        val dbDir = DatabaseCatalogRepository.databasesDirectory(context)
        val existing = (target as? DatabaseTarget.Existing)?.entry
        val existingFile = existing?.let { File(dbDir, "${it.id}.db") }?.takeIf { it.exists() }

        val tmpId = UUID.randomUUID().toString()
        val source = File(tmpDir, "$tmpId.source.db")
        val work = File(tmpDir, "$tmpId.work.db")
        val id = existing?.id ?: UUID.randomUUID().toString()

        val sizeBytes = querySize(uri)
        if (sizeBytes > MAX_IMPORT_BYTES) {
            throw ImportException("Die Datei ist zu groß (maximal ${MAX_IMPORT_BYTES / (1024 * 1024)} MB).")
        }
        // Streng: Quelle, Arbeitskopie und die Reserve müssen gleichzeitig Platz haben.
        checkFreeSpace(tmpDir, sizeBytes + (existingFile?.length() ?: 0L))

        try {
            copyToFile(uri, source)
            if (!hasSqliteHeader(source)) {
                throw ImportException("Die Datei ist keine SQLite-Datenbank.")
            }
            checkSchema(source)

            if (existingFile == null) {
                source.copyTo(work)
                dropGsakIndexes(work)
            } else {
                existingFile.copyTo(work)
                dropGsakIndexes(work)
                mergeInto(work, source, clearFirst)
            }

            val cacheCount = countCaches(work)
            replaceFile(work, File(dbDir, "$id.db"))

            val displayName = when (target) {
                is DatabaseTarget.Existing -> target.entry.displayName
                is DatabaseTarget.New -> target.name
            }
            val entry = DatabaseEntry(
                id = id,
                displayName = displayName,
                cacheCount = cacheCount,
                importedAt = System.currentTimeMillis(),
            )
            catalog.save(entry)
            return entry
        } finally {
            source.delete()
            work.delete()
            File(work.path + "-journal").delete()
        }
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

    private fun checkFreeSpace(directory: File, neededBytes: Long) {
        if (directory.usableSpace < neededBytes + spaceBufferBytes) {
            throw ImportException("Nicht genug Speicherplatz für diese Datenbank.")
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
                        throw ImportException("Die Datei ist zu groß (maximal ${MAX_IMPORT_BYTES / (1024 * 1024)} MB).")
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    private fun hasSqliteHeader(file: File): Boolean {
        val magic = SQLITE_MAGIC.toByteArray(Charsets.US_ASCII)
        val header = ByteArray(magic.size)
        val read = file.inputStream().use { it.read(header) }
        return read == magic.size && header.contentEquals(magic)
    }

    /** Prüft, ob die Datei eine Tabelle `Caches` mit den Pflichtspalten hat. */
    private fun checkSchema(file: File) {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val columns = columnNames(db, "main", "Caches")
            if (columns.isEmpty()) {
                throw ImportException("Die Datei enthält keine Tabelle „Caches“. Ist es eine GSAK-Datenbank?")
            }
            val missing = REQUIRED_COLUMNS.filter { it !in columns }
            if (missing.isNotEmpty()) {
                throw ImportException("In der Tabelle „Caches“ fehlen Spalten: ${missing.joinToString()}.")
            }
        }
    }

    /**
     * Entfernt Indizes mit der GSAK-Sortierung `gsaknocase`. Android-SQLite kennt diese Sortierung nicht,
     * Schreibzugriffe auf die Tabelle würden sonst fehlschlagen. Der Index dient nur der Sortierung in GSAK.
     */
    private fun dropGsakIndexes(work: File) {
        SQLiteDatabase.openDatabase(work.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            val names = mutableListOf<String>()
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND sql LIKE '%gsaknocase%'",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) names += cursor.getString(0)
            }
            // Namen stammen aus der importierten Datei: Anführungszeichen im Bezeichner verdoppeln.
            names.forEach { db.execSQL("DROP INDEX \"${it.replace("\"", "\"\"")}\"") }
        }
    }

    private fun countCaches(file: File): Int =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            DatabaseUtils.queryNumEntries(db, "Caches").toInt()
        }

    /**
     * Führt die Quelle in die Arbeitskopie ein. Alles läuft in einer Transaktion:
     * Scheitert ein Schritt, wird zurückgerollt und die Exception wirft weiter.
     */
    private fun mergeInto(work: File, source: File, clearFirst: Boolean) {
        SQLiteDatabase.openDatabase(work.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            // ATTACH ist nicht innerhalb einer Transaktion erlaubt.
            db.execSQL("ATTACH DATABASE ? AS src", arrayOf(source.path))
            try {
                db.beginTransaction()
                try {
                    // Der Trigger Add_Caches würde beim Ersetzen eines Caches Custom doppelt anlegen
                    // (cCode ist Primärschlüssel). Er wird deshalb während des Einfügens entfernt
                    // und danach wieder angelegt. Die Transaktion rollt den Entfernen-Schritt bei Fehler zurück.
                    val addCacheTrigger = queryFirstString(
                        db,
                        "SELECT sql FROM main.sqlite_master WHERE type = 'trigger' AND name = 'Add_Caches'",
                    )
                    if (addCacheTrigger != null) db.execSQL("DROP TRIGGER main.Add_Caches")

                    if (clearFirst) {
                        // Nur vorhandene Tabellen leeren, damit eine Datei ohne Logs oder Waypoints nicht scheitert.
                        CLEARED_TABLES
                            .filter { columnNames(db, "main", it).isNotEmpty() }
                            .forEach { table -> db.execSQL("DELETE FROM main.$table") }
                    }
                    copyRows(db, "Caches", where = "")
                    copyRows(db, "CacheMemo", where = "WHERE Code IN (SELECT Code FROM src.Caches)")
                    replaceChildRows(db, "Waypoints", parentColumn = "cParent")
                    replaceChildRows(db, "Logs", parentColumn = "lParent")

                    if (addCacheTrigger != null) {
                        db.execSQL("INSERT OR IGNORE INTO main.Custom (cCode) SELECT Code FROM main.Caches")
                        db.execSQL(addCacheTrigger)
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            } finally {
                db.execSQL("DETACH DATABASE src")
            }
        }
    }

    /** Kopiert die Zeilen der Quelle; gleiche Schlüssel werden ersetzt. */
    private fun copyRows(db: SQLiteDatabase, table: String, where: String) {
        val columns = commonColumns(db, table) ?: return
        db.execSQL("INSERT OR REPLACE INTO main.$table ($columns) SELECT $columns FROM src.$table $where")
    }

    /**
     * Ersetzt die Zeilen einer Tabelle, die zu den Caches der Quelle gehören (Waypoints, Logs).
     * Alte Einträge dieser Caches werden entfernt und durch die der Quelle ersetzt.
     */
    private fun replaceChildRows(db: SQLiteDatabase, table: String, parentColumn: String) {
        if (commonColumns(db, table) == null) return
        db.execSQL("DELETE FROM main.$table WHERE $parentColumn IN (SELECT Code FROM src.Caches)")
        copyRows(db, table, where = "WHERE $parentColumn IN (SELECT Code FROM src.Caches)")
    }

    /** Spalten, die in Ziel und Quelle vorkommen. Null, wenn die Tabelle in einer der beiden fehlt. */
    private fun commonColumns(db: SQLiteDatabase, table: String): String? {
        val target = columnNames(db, "main", table)
        val source = columnNames(db, "src", table)
        if (target.isEmpty() || source.isEmpty()) return null
        return target.filter { it in source }.takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { "\"$it\"" }
    }

    private fun columnNames(db: SQLiteDatabase, schema: String, table: String): Set<String> {
        val names = mutableSetOf<String>()
        db.rawQuery("PRAGMA ${schema}.table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) names += cursor.getString(nameIndex)
        }
        return names
    }

    private fun queryFirstString(db: SQLiteDatabase, sql: String): String? =
        db.rawQuery(sql, null).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    /** Ersetzt die Zieldatei durch die fertige Arbeitskopie. Schlägt das fehl, bleibt die alte Datei erhalten. */
    private fun replaceFile(work: File, target: File) {
        val backup = File(target.parentFile, "${target.name}.bak")
        if (target.exists() && !target.renameTo(backup)) {
            throw ImportException("Die bestehende Datenbank konnte nicht ersetzt werden.")
        }
        if (!work.renameTo(target)) {
            if (backup.exists()) backup.renameTo(target)
            throw ImportException("Die Datenbank konnte nicht im App-Speicher abgelegt werden.")
        }
        backup.delete()
    }

    private companion object {
        /** Obergrenze für eine importierte GSAK-Datenbank. Schützt vor übergroßen oder manipulierten Dateien. */
        const val MAX_IMPORT_BYTES = 512L * 1024 * 1024

        const val DEFAULT_BUFFER_BYTES = 64 * 1024

        /** Anfang jeder SQLite-Datei (16 Bytes inkl. Abschluss-Null). */
        const val SQLITE_MAGIC = "SQLite format 3\u0000"

        /** Pflichtspalten der Tabelle `Caches`. */
        val REQUIRED_COLUMNS = listOf("Code", "Name", "Latitude", "Longitude", "CacheType", "Archived", "Found")

        /** Tabellen, die beim Vorher-Leeren komplett geleert werden. */
        val CLEARED_TABLES = listOf("Caches", "CacheMemo", "Waypoints", "Logs")
    }
}
