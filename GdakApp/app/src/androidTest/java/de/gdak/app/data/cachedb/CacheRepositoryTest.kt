package de.gdak.app.data.cachedb

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mapsforge.core.model.BoundingBox
import java.io.File

/** Prüft die Abfragen gegen eine kleine Datenbank mit GSAK-Spaltennamen. */
class CacheRepositoryTest {

    private lateinit var file: File

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        file = File(context.cacheDir, "repository-test.db").apply { delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE Caches (Code TEXT, Name TEXT, CacheType TEXT, Latitude TEXT, Longitude TEXT, " +
                    "Archived INTEGER, Found INTEGER, DNF INTEGER, HasCorrected INTEGER, Container TEXT, " +
                    "Difficulty REAL, Terrain REAL, OwnerName TEXT, PlacedDate TEXT)",
            )
            db.execSQL("CREATE TABLE CacheMemo (Code TEXT, UserNote TEXT, ShortDescription TEXT, LongDescription TEXT, Hints TEXT)")
            db.execSQL("CREATE TABLE Logs (lParent TEXT, lLogId INTEGER, lType TEXT, lBy TEXT, lDate TEXT)")
            db.execSQL("CREATE TABLE LogMemo (lLogId INTEGER, lText TEXT)")
            db.execSQL("CREATE TABLE Waypoints (cParent TEXT, cCode TEXT, cPrefix TEXT, cName TEXT, cType TEXT, cLat TEXT, cLon TEXT)")
            db.execSQL("CREATE TABLE Attributes (aCode TEXT, aId INTEGER, aInc INTEGER)")
        }
    }

    @After
    fun removeDatabase() {
        file.delete()
    }

    private fun repository() = CacheRepository(file)

    private fun write(block: SQLiteDatabase.() -> Unit) {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.block() }
    }

    private fun insertCache(
        code: String,
        type: String = "T",
        name: String = "Cache $code",
        lat: String = "48.1",
        lon: String = "11.5",
        archived: Int = 0,
        found: Int = 0,
        dnf: Int = 0,
        difficulty: Double = 2.0,
        terrain: Double = 2.0,
    ) = write {
        insertOrThrow(
            "Caches",
            null,
            ContentValues().apply {
                put("Code", code)
                put("Name", name)
                put("CacheType", type)
                put("Latitude", lat)
                put("Longitude", lon)
                put("Archived", archived)
                put("Found", found)
                put("DNF", dnf)
                put("HasCorrected", 0)
                put("Container", "Micro")
                put("Difficulty", difficulty)
                put("Terrain", terrain)
                put("OwnerName", "Owner")
                put("PlacedDate", "2014-04-21")
            },
        )
    }

    private fun codes(caches: List<CacheSummary>) = caches.map { it.code }.toSet()

    @Test
    fun typeFilterKeepsOnlyMysteryCaches() {
        insertCache("T1", type = "T")
        insertCache("M1", type = "M")
        insertCache("U1", type = "U")
        insertCache("B1", type = "B")

        val result = repository().cachesMatching(CacheFilter(types = setOf(CacheTypeGroup.MYSTERY)))

        assertEquals(setOf("U1", "B1"), codes(result))
    }

    @Test
    fun hidingFoundCachesRemovesThemFromResult() {
        insertCache("F1", found = 1)
        insertCache("N1")
        insertCache("D1", dnf = 1)

        val result = repository().cachesMatching(CacheFilter(showFound = false))

        assertEquals(setOf("N1", "D1"), codes(result))
    }

    @Test
    fun hidingArchivedCachesRemovesThemFromResult() {
        insertCache("A1", archived = 1)
        insertCache("N1")

        val result = repository().cachesMatching(CacheFilter(showArchived = false))

        assertEquals(setOf("N1"), codes(result))
    }

    @Test
    fun searchTreatsPercentSignAsPlainText() {
        insertCache("X1", name = "100% Spaß")
        insertCache("X2", name = "Anderer Name")

        val result = repository().cachesMatching(CacheFilter(searchText = "%"))

        assertEquals(setOf("X1"), codes(result))
    }

    @Test
    fun difficultyRangeKeepsCachesInsideTheRange() {
        insertCache("D1", difficulty = 1.0)
        insertCache("D2", difficulty = 3.0)
        insertCache("D3", difficulty = 5.0)

        val result = repository().cachesMatching(CacheFilter(difficulty = 2f..4f))

        assertEquals(setOf("D2"), codes(result))
    }

    @Test
    fun boundingBoxLimitsResultToVisibleArea() {
        insertCache("NEAR", lat = "48.1", lon = "11.5")
        insertCache("FAR", lat = "50.0", lon = "11.5")

        val result = repository().cachesMatching(
            CacheFilter(),
            box = BoundingBox(48.0, 11.0, 49.0, 12.0),
        )

        assertEquals(setOf("NEAR"), codes(result))
    }

    @Test
    fun cacheDetailReturnsLogsNewestFirstAndLimitsThemToTwenty() {
        insertCache("L1")
        write {
            for (i in 1..25) {
                insertOrThrow(
                    "Logs",
                    null,
                    ContentValues().apply {
                        put("lParent", "L1")
                        put("lLogId", i)
                        put("lType", "Found it")
                        put("lBy", "Finder $i")
                        put("lDate", "2020-01-" + i.toString().padStart(2, '0'))
                    },
                )
            }
        }

        val detail = repository().cacheDetail("L1")!!

        assertEquals(CacheRepository.LOG_LIMIT, detail.logs.size)
        assertEquals("2020-01-25", detail.logs.first().date)
    }

    @Test
    fun cacheDetailContainsMemoAttributesAndSummaryFields() {
        insertCache("C1", name = "Detailtest")
        write {
            insertOrThrow(
                "CacheMemo",
                null,
                ContentValues().apply {
                    put("Code", "C1")
                    put("UserNote", "Notiz")
                    put("ShortDescription", "<b>kurz</b>")
                    put("LongDescription", "lang")
                    put("Hints", "Unter dem Stein")
                },
            )
            insertOrThrow("Attributes", null, ContentValues().apply {
                put("aCode", "C1")
                put("aId", 24)
                put("aInc", 1)
            })
        }

        val detail = repository().cacheDetail("C1")!!

        assertEquals("Detailtest", detail.summary.name)
        assertEquals("Owner", detail.summary.ownerName)
        assertEquals("Notiz", detail.summary.userNote)
        assertEquals("<b>kurz</b>", detail.shortDescription)
        assertEquals("Unter dem Stein", detail.hints)
        assertEquals(listOf(CacheAttribute(24, true)), detail.attributes)
        assertTrue(detail.waypoints.isEmpty())
    }

    @Test
    fun cacheDetailForUnknownCodeIsNull() {
        assertNull(repository().cacheDetail("NOPE"))
    }
}
