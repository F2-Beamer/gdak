package de.gdak.app.data.importer

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import de.gdak.app.data.catalog.DatabaseCatalogRepository
import de.gdak.app.data.catalog.DatabaseEntry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.File

/** Prüft den Import gegen echte Dateien: Neuanlage, Zusammenführen, Ersetzen und Abbruch. */
class DatabaseImporterTest {

    private lateinit var context: Context
    private lateinit var catalog: DatabaseCatalogRepository
    private lateinit var importer: DatabaseImporter
    private val createdIds = mutableListOf<String>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        catalog = DatabaseCatalogRepository(context)
        importer = DatabaseImporter(context, catalog)
    }

    @After
    fun tearDown() {
        runBlocking {
            createdIds.forEach { id ->
                catalog.delete(id)
                File(DatabaseCatalogRepository.databasesDirectory(context), "$id.db").delete()
            }
        }
    }

    /** Eine kleine GSAK-ähnliche Datei mit den Pflichtspalten der Tabelle `Caches`. */
    private fun gsakFile(name: String, caches: List<Pair<String, String>>): Uri {
        val file = File(context.cacheDir, "$name.db3").apply { delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE Caches (Code TEXT PRIMARY KEY, Name TEXT, Latitude TEXT, " +
                    "Longitude TEXT, CacheType TEXT, Archived INTEGER, Found INTEGER)",
            )
            caches.forEach { (code, cacheName) ->
                db.execSQL(
                    "INSERT INTO Caches VALUES (?, ?, '48.1', '11.5', 'T', 0, 0)",
                    arrayOf<Any>(code, cacheName),
                )
            }
        }
        return Uri.fromFile(file)
    }

    private fun importNew(name: String, caches: List<Pair<String, String>>): DatabaseEntry = runBlocking {
        importer.import(gsakFile(name, caches), DatabaseTarget.New(name), clearFirst = false)
    }.also { createdIds += it.id }

    private fun cachesIn(entry: DatabaseEntry): List<Pair<String, String>> {
        val file = File(DatabaseCatalogRepository.databasesDirectory(context), "${entry.id}.db")
        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT Code, Name FROM Caches ORDER BY Code", null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1))
                }
            }
        }
    }

    @Test
    fun newDatabaseIsImportedWithCacheCount() {
        val entry = importNew("neu", listOf("GC1" to "Eins", "GC2" to "Zwei"))

        assertEquals(2, entry.cacheCount)
        assertEquals(listOf("GC1" to "Eins", "GC2" to "Zwei"), cachesIn(entry))
    }

    @Test
    fun mergeKeepsOldCachesAndReplacesSameCode() {
        val base = importNew("basis", listOf("GC1" to "alt", "GC2" to "B"))

        val merged = runBlocking {
            importer.import(
                gsakFile("update", listOf("GC1" to "neu", "GC3" to "C")),
                DatabaseTarget.Existing(base),
                clearFirst = false,
            )
        }

        assertEquals(base.id, merged.id)
        assertEquals(listOf("GC1" to "neu", "GC2" to "B", "GC3" to "C"), cachesIn(merged))
    }

    @Test
    fun clearFirstReplacesAllCaches() {
        val base = importNew("basis2", listOf("GC1" to "alt", "GC2" to "B"))

        val replaced = runBlocking {
            importer.import(
                gsakFile("update2", listOf("GC1" to "neu", "GC3" to "C")),
                DatabaseTarget.Existing(base),
                clearFirst = true,
            )
        }

        assertEquals(listOf("GC1" to "neu", "GC3" to "C"), cachesIn(replaced))
    }

    @Test
    fun fileWithoutCachesTableIsRejectedAndTargetStaysUnchanged() {
        val base = importNew("basis3", listOf("GC1" to "alt", "GC2" to "B"))
        val broken = File(context.cacheDir, "kaputt.db3").apply { delete() }
        SQLiteDatabase.openOrCreateDatabase(broken, null).use { db ->
            db.execSQL("CREATE TABLE Other (x TEXT)")
        }

        assertThrows(ImportException::class.java) {
            runBlocking {
                importer.import(Uri.fromFile(broken), DatabaseTarget.Existing(base), clearFirst = true)
            }
            Unit
        }

        assertEquals(listOf("GC1" to "alt", "GC2" to "B"), cachesIn(base))
    }

    @Test
    fun failureInsideTransactionRollsBackAndKeepsTarget() {
        val base = importNew("basis4", listOf("GC1" to "alt", "GC2" to "B"))
        val dbDir = DatabaseCatalogRepository.databasesDirectory(context)
        val baseFile = File(dbDir, "${base.id}.db")
        // Ein Trigger lässt das Einfügen von GC9 scheitern, nachdem Caches bereits geleert und eingefügt wurden.
        SQLiteDatabase.openDatabase(baseFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "CREATE TRIGGER fail_on_gc9 BEFORE INSERT ON Caches WHEN NEW.Code = 'GC9' " +
                    "BEGIN SELECT RAISE(ABORT, 'Test-Fehler'); END",
            )
        }
        val source = gsakFile("update4", listOf("GC1" to "neu", "GC3" to "C", "GC9" to "Fehler"))

        assertThrows(SQLiteException::class.java) {
            runBlocking {
                importer.import(source, DatabaseTarget.Existing(base), clearFirst = true)
            }
            Unit
        }

        // Alle Zeilen müssen nach dem Rollback wieder da sein, also auch die vorher geleerten.
        assertEquals(listOf("GC1" to "alt", "GC2" to "B"), cachesIn(base))
        assertFalse(File(dbDir, "${base.id}.db.bak").exists())
    }

    @Test
    fun nonSqliteFileIsRejected() {
        val text = File(context.cacheDir, "text.db3").apply { writeText("das ist kein SQLite") }

        assertThrows(ImportException::class.java) {
            runBlocking {
                importer.import(Uri.fromFile(text), DatabaseTarget.New("Text"), clearFirst = false)
            }
            Unit
        }
    }
}
