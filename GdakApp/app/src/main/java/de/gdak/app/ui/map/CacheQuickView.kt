package de.gdak.app.ui.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.gdak.app.data.cachedb.CacheSummary
import java.util.Locale

/**
 * Schnellansicht eines Caches: Kerndaten und die eigene Notiz, nur lesend.
 * Das Blatt öffnet sich ganz, damit lange Notizen gescrollt werden können.
 * Die Schaltfläche „Details“ öffnet die Detailansicht.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheQuickView(
    cache: CacheSummary,
    onDismiss: () -> Unit,
    onDetails: () -> Unit,
    /** Zeigt die Schaltfläche „Auf Karte zeigen“, wenn gesetzt. */
    onShowOnMap: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(text = cache.code, style = MaterialTheme.typography.labelLarge)
            Text(text = cache.name, style = MaterialTheme.typography.titleLarge)

            if (onShowOnMap != null) {
                OutlinedButton(onClick = onShowOnMap, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Icon(Icons.Filled.Map, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Auf Karte zeigen")
                }
            }
            Button(
                onClick = onDetails,
                modifier = Modifier.fillMaxWidth().padding(top = if (onShowOnMap != null) 8.dp else 12.dp),
            ) {
                Text("Details")
            }

            Text(
                text = "Typ: ${cacheTypeLabel(cache.cacheType)}",
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(text = "Größe: ${containerLabel(cache.container)}")
            Text(
                text = String.format(
                    Locale.GERMANY,
                    "Difficulty: %.1f   Terrain: %.1f",
                    cache.difficulty,
                    cache.terrain,
                ),
            )
            Text(text = "Position: ${formatDmm(cache.latitude, cache.longitude)}")
            if (cache.ownerName.isNotBlank()) Text(text = "Besitzer: ${cache.ownerName}")
            if (cache.placedDate.isNotBlank()) Text(text = "Versteckt: ${germanDate(cache.placedDate)}")
            if (cache.found) Text(text = "Gefunden")
            if (cache.dnf) Text(text = "Nicht gefunden (DNF)")
            if (cache.archived) Text(text = "Archiviert")

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Text(text = "Eigene Notiz", style = MaterialTheme.typography.titleSmall)
            Text(
                text = cache.userNote.ifBlank { "Keine Notiz vorhanden." },
                modifier = Modifier.padding(top = 4.dp),
            )

        }
    }
}

internal fun cacheTypeLabel(cacheType: String): String = when (cacheType) {
    "T" -> "Traditionell"
    "M" -> "Multi-Cache"
    "U", "B" -> "Mystery"
    "E" -> "Event-Cache"
    "Q" -> "Lab-Cache"
    "I" -> "Wherigo"
    "V" -> "Virtueller Cache"
    "R" -> "EarthCache"
    else -> "Typ $cacheType"
}

internal fun containerLabel(container: String): String = when (container) {
    "Micro" -> "Micro"
    "Small" -> "Klein"
    "Regular" -> "Normal"
    "Large" -> "Groß"
    "Other" -> "Sonstige"
    "Virtual" -> "Virtuell"
    "Not chosen" -> "Nicht angegeben"
    else -> "Unbekannt"
}

/** Wandelt ein Datum JJJJ-MM-TT in TT.MM.JJJJ um. Andere Werte bleiben unverändert. */
internal fun germanDate(isoDate: String): String {
    val match = Regex("""(\d{4})-(\d{2})-(\d{2})""").matchEntire(isoDate.trim()) ?: return isoDate
    val (year, month, day) = match.destructured
    return "$day.$month.$year"
}
