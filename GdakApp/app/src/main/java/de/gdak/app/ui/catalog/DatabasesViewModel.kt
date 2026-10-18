package de.gdak.app.ui.catalog

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.gdak.app.data.catalog.DatabaseCatalogRepository
import de.gdak.app.data.catalog.DatabaseEntry
import de.gdak.app.data.importer.DatabaseImporter
import de.gdak.app.data.importer.DatabaseTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Datenbank-Katalog für die Verwaltung. */
class DatabasesViewModel(application: Application) : AndroidViewModel(application) {

    private val catalog = DatabaseCatalogRepository(application)
    private val importer = DatabaseImporter(application, catalog)

    val databases: StateFlow<List<DatabaseEntry>> = catalog.databases
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeDatabaseId: StateFlow<String?> = catalog.activeDatabaseId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Kurze Meldung für die Oberfläche, z. B. Erfolg oder Fehler. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Vorschlag für den Namen einer neuen Datenbank: Dateiname ohne Endung. */
    fun suggestName(uri: Uri): String {
        val fileName = queryDisplayName(uri) ?: return ""
        return fileName.substringBeforeLast('.', fileName)
    }

    fun importDatabase(uri: Uri, target: DatabaseTarget, clearFirst: Boolean) {
        if (target is DatabaseTarget.New) {
            val name = target.name.trim()
            if (name.isEmpty()) {
                _message.value = "Bitte einen Namen für die Datenbank eingeben."
                return
            }
            if (databases.value.any { it.displayName.equals(name, ignoreCase = true) }) {
                _message.value = "Eine Datenbank mit diesem Namen gibt es schon."
                return
            }
        }
        val effectiveTarget = if (target is DatabaseTarget.New) {
            DatabaseTarget.New(target.name.trim())
        } else {
            target
        }
        viewModelScope.launch {
            _busy.value = true
            _message.value = try {
                val entry = withContext(Dispatchers.IO) {
                    importer.import(uri, effectiveTarget, clearFirst)
                }
                "Datenbank „${entry.displayName}“ importiert: ${entry.cacheCount} Caches."
            } catch (error: Exception) {
                error.message ?: "Die Datenbank konnte nicht importiert werden."
            } finally {
                _busy.value = false
            }
        }
    }

    fun setActive(id: String) {
        viewModelScope.launch { catalog.setActive(id) }
    }

    fun delete(entry: DatabaseEntry) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { catalog.delete(entry.id) }
            _message.value = "Datenbank „${entry.displayName}“ wurde gelöscht."
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
