package de.gdak.app.data.catalog

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Eine gespeicherte GSAK-Datenbank. Die Datei liegt unter `filesDir/databases/<id>.db`. */
data class DatabaseEntry(
    val id: String,
    val displayName: String,
    val cacheCount: Int,
    val importedAt: Long,
)

/** Verwaltet den Katalog der Datenbanken und die aktive Datenbank (DataStore). */
class DatabaseCatalogRepository(private val context: Context) {

    private val databasesKey = stringPreferencesKey("databases")
    private val activeDatabaseKey = stringPreferencesKey("active_database_id")

    val databases: Flow<List<DatabaseEntry>> = context.catalogDataStore.data.map { prefs ->
        prefs[databasesKey]?.let(::parseEntries) ?: emptyList()
    }

    val activeDatabaseId: Flow<String?> = context.catalogDataStore.data.map { prefs ->
        prefs[activeDatabaseKey]
    }

    /** Legt den Eintrag an oder aktualisiert ihn. Ist noch keine Datenbank aktiv, wird er aktiv. */
    suspend fun save(entry: DatabaseEntry) {
        context.catalogDataStore.edit { prefs ->
            val current = prefs[databasesKey]?.let(::parseEntries) ?: emptyList()
            val updated = if (current.any { it.id == entry.id }) {
                current.map { if (it.id == entry.id) entry else it }
            } else {
                current + entry
            }
            prefs[databasesKey] = serializeEntries(updated)
            if (prefs[activeDatabaseKey] == null) prefs[activeDatabaseKey] = entry.id
        }
    }

    /** Entfernt den Eintrag und seine Datei. War er aktiv, ist danach keine Datenbank mehr aktiv. */
    suspend fun delete(id: String) {
        File(databasesDirectory(context), "$id.db").delete()
        context.catalogDataStore.edit { prefs ->
            val current = prefs[databasesKey]?.let(::parseEntries) ?: emptyList()
            prefs[databasesKey] = serializeEntries(current.filterNot { it.id == id })
            if (prefs[activeDatabaseKey] == id) prefs.remove(activeDatabaseKey)
        }
    }

    suspend fun setActive(id: String) {
        context.catalogDataStore.edit { prefs -> prefs[activeDatabaseKey] = id }
    }

    private fun parseEntries(json: String): List<DatabaseEntry> {
        val array = JSONArray(json)
        return List(array.length()) { index ->
            val obj = array.getJSONObject(index)
            DatabaseEntry(
                id = obj.getString("id"),
                displayName = obj.getString("displayName"),
                cacheCount = obj.getInt("cacheCount"),
                importedAt = obj.getLong("importedAt"),
            )
        }
    }

    private fun serializeEntries(entries: List<DatabaseEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("displayName", entry.displayName)
                    .put("cacheCount", entry.cacheCount)
                    .put("importedAt", entry.importedAt),
            )
        }
        return array.toString()
    }

    companion object {
        /** Ordner für die Datenbanken im App-Speicher. */
        fun databasesDirectory(context: Context): File =
            File(context.filesDir, "databases").apply { mkdirs() }
    }
}
