package de.gdak.app.ui.filter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.gdak.app.data.cachedb.CacheFilter
import de.gdak.app.data.cachedb.CacheTypeGroup
import de.gdak.app.data.cachedb.RATING_MAX
import de.gdak.app.data.cachedb.RATING_MIN
import de.gdak.app.data.cachedb.RATING_STEPS
import java.util.Locale
import kotlin.math.roundToInt

private const val DEFAULT_DISTANCE_KM = 10f
private const val MIN_DISTANCE_KM = 1f
private const val MAX_DISTANCE_KM = 100f

/**
 * Filterblatt für Karte und Liste. Jede Änderung wird sofort über [onFilterChange] gemeldet.
 * Ohne Kartenmitte oder Standort wirkt der Entfernungsfilter nicht.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheFilterSheet(
    filter: CacheFilter,
    hasLocation: Boolean,
    onFilterChange: (CacheFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(text = "Filter", style = MaterialTheme.typography.titleLarge)

            SectionTitle("Typ")
            CacheTypeGroup.entries.forEach { group ->
                FilterCheckbox(label = group.label, checked = group in filter.types) { checked ->
                    onFilterChange(filter.copy(types = if (checked) filter.types + group else filter.types - group))
                }
            }

            SectionTitle("Status")
            FilterCheckbox("Gefunden", filter.showFound) { onFilterChange(filter.copy(showFound = it)) }
            FilterCheckbox("Nicht gefunden", filter.showNotFound) { onFilterChange(filter.copy(showNotFound = it)) }
            FilterCheckbox("DNF", filter.showDnf) { onFilterChange(filter.copy(showDnf = it)) }
            FilterCheckbox("Archiviert", filter.showArchived) { onFilterChange(filter.copy(showArchived = it)) }

            SectionTitle("Schwierigkeit ${rangeText(filter.difficulty)}")
            RangeSlider(
                value = filter.difficulty,
                onValueChange = { onFilterChange(filter.copy(difficulty = it)) },
                valueRange = RATING_MIN..RATING_MAX,
                steps = RATING_STEPS,
            )

            SectionTitle("Gelände ${rangeText(filter.terrain)}")
            RangeSlider(
                value = filter.terrain,
                onValueChange = { onFilterChange(filter.copy(terrain = it)) },
                valueRange = RATING_MIN..RATING_MAX,
                steps = RATING_STEPS,
            )

            SectionTitle("Entfernung")
            FilterCheckbox("Umkreis begrenzen", filter.maxDistanceKm != null) { on ->
                onFilterChange(filter.copy(maxDistanceKm = if (on) DEFAULT_DISTANCE_KM else null))
            }
            filter.maxDistanceKm?.let { km ->
                Text(text = "Höchstens ${km.roundToInt()} km", modifier = Modifier.padding(top = 8.dp))
                Slider(
                    value = km,
                    onValueChange = { onFilterChange(filter.copy(maxDistanceKm = it.roundToInt().toFloat())) },
                    valueRange = MIN_DISTANCE_KM..MAX_DISTANCE_KM,
                    steps = (MAX_DISTANCE_KM - MIN_DISTANCE_KM).toInt() - 1,
                )
            }
            if (!hasLocation) {
                Text(
                    text = "Ohne Kartenmitte oder Standort wirkt der Entfernungsfilter nicht.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            TextButton(
                onClick = { onFilterChange(CacheFilter(searchText = filter.searchText)) },
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("Alle Filter zurücksetzen")
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun FilterCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}

private fun rangeText(range: ClosedFloatingPointRange<Float>): String =
    String.format(Locale.GERMANY, "%.1f – %.1f", range.start, range.endInclusive)
