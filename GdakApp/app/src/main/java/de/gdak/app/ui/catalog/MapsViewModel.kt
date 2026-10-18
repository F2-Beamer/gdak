package de.gdak.app.ui.catalog

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.gdak.app.data.catalog.MapCatalogRepository
import de.gdak.app.data.catalog.MapEntry
import de.gdak.app.data.importer.MapImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Karten-Katalog für Verwaltung und Kartenanzeige. */
class MapsViewModel(application: Application) : AndroidViewModel(application) {

    private val catalog = MapCatalogRepository(application)
    private val importer = MapImporter(application, catalog)

    val maps: StateFlow<List<MapEntry>> = catalog.maps
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeMapId: StateFlow<String?> = catalog.activeMapId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Kurze Meldung für die Oberfläche, z. B. Erfolg oder Fehler. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Vorschlag für den Anzeigenamen: Dateiname ohne `.map`. */
    fun suggestName(uri: Uri): String {
        val fileName = queryDisplayName(uri) ?: return ""
        return fileName.removeSuffix(".map").removeSuffix(".MAP")
    }

    fun importMap(uri: Uri, displayName: String) {
        val name = displayName.trim()
        if (name.isEmpty()) {
            _message.value = "Bitte einen Namen eingeben."
            return
        }
        if (maps.value.any { it.displayName.equals(name, ignoreCase = true) }) {
            _message.value = "Eine Karte mit diesem Namen gibt es schon."
            return
        }
        viewModelScope.launch {
            _busy.value = true
            _message.value = try {
                withContext(Dispatchers.IO) { importer.import(uri, name) }
                "Karte „$name“ wurde importiert."
            } catch (error: Exception) {
                error.message ?: "Die Karte konnte nicht importiert werden."
            } finally {
                _busy.value = false
            }
        }
    }

    fun setActive(id: String) {
        viewModelScope.launch { catalog.setActive(id) }
    }

    fun delete(entry: MapEntry) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { catalog.delete(entry.id) }
            _message.value = "Karte „${entry.displayName}“ wurde gelöscht."
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun queryDisplayName(uri: Uri): String? {
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0) return cursor.getString(column)
                }
            }
        return null
    }
}
