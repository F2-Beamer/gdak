package de.gdak.app.data.catalog

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal val Context.catalogDataStore by preferencesDataStore(name = "catalog")

/** Eine gespeicherte Offline-Karte. Die Datei liegt unter `filesDir/maps/<id>.map`. */
data class MapEntry(
    val id: String,
    val displayName: String,
    val sizeBytes: Long,
    val importedAt: Long,
)

/** Verwaltet den Katalog der Karten und die aktive Karte (DataStore). */
class MapCatalogRepository(private val context: Context) {

    private val mapsKey = stringPreferencesKey("maps")
    private val activeMapKey = stringPreferencesKey("active_map_id")

    val maps: Flow<List<MapEntry>> = context.catalogDataStore.data.map { prefs ->
        prefs[mapsKey]?.let(::parseEntries) ?: emptyList()
    }

    val activeMapId: Flow<String?> = context.catalogDataStore.data.map { prefs ->
        prefs[activeMapKey]
    }

    /** Legt einen Eintrag an. Ist noch keine Karte aktiv, wird er aktiv. */
    suspend fun add(entry: MapEntry) {
        context.catalogDataStore.edit { prefs ->
            val current = prefs[mapsKey]?.let(::parseEntries) ?: emptyList()
            prefs[mapsKey] = serializeEntries(current + entry)
            if (prefs[activeMapKey] == null) prefs[activeMapKey] = entry.id
        }
    }

    /** Entfernt den Eintrag und seine Datei. War er aktiv, ist danach keine Karte mehr aktiv. */
    suspend fun delete(id: String) {
        File(mapsDirectory(context), "$id.map").delete()
        context.catalogDataStore.edit { prefs ->
            val current = prefs[mapsKey]?.let(::parseEntries) ?: emptyList()
            prefs[mapsKey] = serializeEntries(current.filterNot { it.id == id })
            if (prefs[activeMapKey] == id) prefs.remove(activeMapKey)
        }
    }

    suspend fun setActive(id: String) {
        context.catalogDataStore.edit { prefs -> prefs[activeMapKey] = id }
    }

    private fun parseEntries(json: String): List<MapEntry> {
        val array = JSONArray(json)
        return List(array.length()) { index ->
            val obj = array.getJSONObject(index)
            MapEntry(
                id = obj.getString("id"),
                displayName = obj.getString("displayName"),
                sizeBytes = obj.getLong("sizeBytes"),
                importedAt = obj.getLong("importedAt"),
            )
        }
    }

    private fun serializeEntries(entries: List<MapEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("displayName", entry.displayName)
                    .put("sizeBytes", entry.sizeBytes)
                    .put("importedAt", entry.importedAt),
            )
        }
        return array.toString()
    }

    companion object {
        /** Ordner für kopierte Karten im App-Speicher. */
        fun mapsDirectory(context: Context): File =
            File(context.filesDir, "maps").apply { mkdirs() }
    }
}
