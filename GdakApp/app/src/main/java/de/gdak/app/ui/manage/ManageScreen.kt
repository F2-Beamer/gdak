package de.gdak.app.ui.manage

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.gdak.app.data.catalog.DatabaseEntry
import de.gdak.app.data.catalog.MapEntry
import de.gdak.app.data.importer.DatabaseTarget
import de.gdak.app.ui.catalog.DatabasesViewModel
import de.gdak.app.ui.catalog.MapsViewModel
import java.util.Locale

/** Platzhalter-Schlüssel für „neue Datenbank“ im Auswahldialog. */
private const val NEW_DATABASE = "__new_database__"

@Composable
fun ManageScreen(mapsViewModel: MapsViewModel, databasesViewModel: DatabasesViewModel) {
    val maps by mapsViewModel.maps.collectAsState()
    val activeMapId by mapsViewModel.activeMapId.collectAsState()
    val busy by mapsViewModel.busy.collectAsState()
    val message by mapsViewModel.message.collectAsState()

    val databases by databasesViewModel.databases.collectAsState()
    val activeDatabaseId by databasesViewModel.activeDatabaseId.collectAsState()
    val databaseBusy by databasesViewModel.busy.collectAsState()
    val databaseMessage by databasesViewModel.message.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var nameInput by remember { mutableStateOf("") }
    var entryToDelete by remember { mutableStateOf<MapEntry?>(null) }

    var pendingDatabaseUri by remember { mutableStateOf<Uri?>(null) }
    var databaseNameSuggestion by remember { mutableStateOf("") }
    var databaseToDelete by remember { mutableStateOf<DatabaseEntry?>(null) }

    val pickMap = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingUri = uri
            nameInput = mapsViewModel.suggestName(uri)
        }
    }

    val pickDatabase = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingDatabaseUri = uri
            databaseNameSuggestion = databasesViewModel.suggestName(uri)
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            mapsViewModel.clearMessage()
        }
    }

    LaunchedEffect(databaseMessage) {
        databaseMessage?.let {
            snackbarHostState.showSnackbar(it)
            databasesViewModel.clearMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ManageSection(
                title = "Karten",
                description = "Es ist immer nur eine Karte aktiv. Tippe auf eine Karte, um sie zu aktivieren.",
                importLabel = "Karte importieren",
                importEnabled = !busy,
                busy = busy,
                onImport = { pickMap.launch(arrayOf("*/*")) },
            ) {
                if (maps.isEmpty()) {
                    EmptyText("Noch keine Karte gespeichert.")
                } else {
                    maps.forEach { entry ->
                        MapRow(
                            entry = entry,
                            active = entry.id == activeMapId,
                            onSelect = { mapsViewModel.setActive(entry.id) },
                            onDelete = { entryToDelete = entry },
                        )
                    }
                }
            }

            ManageSection(
                title = "Datenbanken",
                description = "Es ist immer nur eine Datenbank aktiv. Beim Import werden die Caches standardmäßig " +
                    "mit der gewählten Datenbank zusammengeführt.",
                importLabel = "Datenbank importieren",
                importEnabled = !databaseBusy,
                busy = databaseBusy,
                onImport = { pickDatabase.launch(arrayOf("*/*")) },
            ) {
                if (databases.isEmpty()) {
                    EmptyText("Noch keine Datenbank gespeichert.")
                } else {
                    databases.forEach { entry ->
                        DatabaseRow(
                            entry = entry,
                            active = entry.id == activeDatabaseId,
                            onSelect = { databasesViewModel.setActive(entry.id) },
                            onDelete = { databaseToDelete = entry },
                        )
                    }
                }
            }
        }
    }

    pendingUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingUri = null },
            title = { Text("Karte benennen") },
            text = {
                TextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Anzeigename") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    mapsViewModel.importMap(uri, nameInput)
                    pendingUri = null
                }) { Text("Importieren") }
            },
            dismissButton = {
                TextButton(onClick = { pendingUri = null }) { Text("Abbrechen") }
            },
        )
    }

    entryToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Karte löschen?") },
            text = { Text("„${entry.displayName}“ wird aus dem App-Speicher entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    mapsViewModel.delete(entry)
                    entryToDelete = null
                }) { Text("Löschen") }
            },
            dismissButton = {
                OutlinedButton(onClick = { entryToDelete = null }) { Text("Abbrechen") }
            },
        )
    }

    pendingDatabaseUri?.let { uri ->
        ImportDatabaseDialog(
            databases = databases,
            activeId = activeDatabaseId,
            suggestedName = databaseNameSuggestion,
            onConfirm = { target, clearFirst ->
                databasesViewModel.importDatabase(uri, target, clearFirst)
                pendingDatabaseUri = null
            },
            onDismiss = { pendingDatabaseUri = null },
        )
    }

    databaseToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { databaseToDelete = null },
            title = { Text("Datenbank löschen?") },
            text = { Text("„${entry.displayName}“ wird aus dem App-Speicher entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    databasesViewModel.delete(entry)
                    databaseToDelete = null
                }) { Text("Löschen") }
            },
            dismissButton = {
                OutlinedButton(onClick = { databaseToDelete = null }) { Text("Abbrechen") }
            },
        )
    }
}

/** Auswahl des Ziels für den Datenbank-Import: bestehender Eintrag oder neue Datenbank. */
@Composable
private fun ImportDatabaseDialog(
    databases: List<DatabaseEntry>,
    activeId: String?,
    suggestedName: String,
    onConfirm: (DatabaseTarget, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedId by remember { mutableStateOf(activeId ?: NEW_DATABASE) }
    var newName by remember { mutableStateOf(suggestedName) }
    var clearFirst by remember { mutableStateOf(false) }
    val isNew = selectedId == NEW_DATABASE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Datenbank importieren") },
        text = {
            Column {
                Text(text = "Ziel auswählen:", style = MaterialTheme.typography.bodyMedium)
                databases.forEach { entry ->
                    TargetOption(
                        label = entry.displayName,
                        selected = selectedId == entry.id,
                        onSelect = { selectedId = entry.id },
                    )
                }
                TargetOption(
                    label = "Neue Datenbank",
                    selected = isNew,
                    onSelect = { selectedId = NEW_DATABASE },
                )
                if (isNew) {
                    TextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Name der Datenbank") },
                        singleLine = true,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = clearFirst,
                                onClick = { clearFirst = !clearFirst },
                                role = Role.Checkbox,
                            )
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = clearFirst, onCheckedChange = null)
                        Text(text = "Vorher leeren", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val target = if (isNew) {
                    DatabaseTarget.New(newName)
                } else {
                    DatabaseTarget.Existing(databases.first { it.id == selectedId })
                }
                onConfirm(target, clearFirst && !isNew)
            }) { Text("Importieren") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

@Composable
private fun TargetOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * Abschnitt einer Verwaltungsseite als Karte: Titel, Erklärung, Import-Schaltfläche und die Liste.
 * Karten und Datenbanken sehen gleich aus, damit beide Bereiche einheitlich wirken.
 */
@Composable
private fun ManageSection(
    title: String,
    description: String,
    importLabel: String,
    importEnabled: Boolean,
    busy: Boolean,
    onImport: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(
                onClick = onImport,
                enabled = importEnabled,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Icon(Icons.Filled.FileOpen, contentDescription = null)
                Text(text = importLabel, modifier = Modifier.padding(start = 8.dp))
            }
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            Column(modifier = Modifier.padding(top = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

/** Kleines Kennzeichen hinter dem Namen des aktiven Eintrags. */
@Composable
private fun ActiveTag() {
    Text(
        text = "aktiv",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        softWrap = false,
        modifier = Modifier
            .padding(start = 8.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun MapRow(
    entry: MapEntry,
    active: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = active, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        RadioButton(selected = active, onClick = null)
        Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (active) ActiveTag()
            }
            Text(
                text = formatSize(entry.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Karte löschen")
        }
    }
}

@Composable
private fun DatabaseRow(
    entry: DatabaseEntry,
    active: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = active, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        RadioButton(selected = active, onClick = null)
        Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (active) ActiveTag()
            }
            Text(
                text = "${entry.cacheCount} Caches",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Datenbank löschen")
        }
    }
}

private fun formatSize(bytes: Long): String =
    String.format(Locale.GERMANY, "%.1f MB", bytes / 1_000_000.0)
