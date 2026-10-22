package de.gdak.app.ui.list

import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.gdak.app.data.cachedb.CacheRepository
import de.gdak.app.data.cachedb.CacheSummary
import de.gdak.app.data.cachedb.allowsDistance
import de.gdak.app.data.catalog.DatabaseCatalogRepository
import de.gdak.app.data.geo.distanceKm
import de.gdak.app.ui.catalog.DatabasesViewModel
import de.gdak.app.ui.common.EmptyState
import de.gdak.app.ui.format.formatRating
import de.gdak.app.ui.filter.CacheFilterSheet
import de.gdak.app.ui.filter.CacheFilterViewModel
import de.gdak.app.ui.filter.CacheSearchBar
import de.gdak.app.ui.location.LOCATION_PERMISSIONS
import de.gdak.app.ui.location.findLocation
import de.gdak.app.ui.location.hasLocationPermission
import de.gdak.app.ui.map.CacheQuickView
import de.gdak.app.ui.map.CacheSymbols
import de.gdak.app.ui.map.MapPositionViewModel
import org.mapsforge.core.model.LatLong
import de.gdak.app.ui.map.cacheTypeLabel
import de.gdak.app.ui.map.containerLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/** Ein Eintrag der Liste: der Cache und seine Entfernung, falls der Standort bekannt ist. */
private class CacheEntry(val cache: CacheSummary, val distanceKm: Double?)

/**
 * Zweite Ansicht neben der Karte. Sie zeigt die Caches des gemeinsamen Filters, sortiert nach
 * Entfernung zur eigenen Position. Ohne Standort wird nach Name sortiert.
 */
@Composable
fun CacheListScreen(
    databasesViewModel: DatabasesViewModel,
    filterViewModel: CacheFilterViewModel,
    onOpenDetails: (String) -> Unit,
    onShowOnMap: (CacheSummary) -> Unit,
    positionState: MapPositionViewModel,
) {
    val context = LocalContext.current
    val symbols = remember(context) { CacheSymbols(context) }
    val activeDatabaseId by databasesViewModel.activeDatabaseId.collectAsState()
    val filter by filterViewModel.filter.collectAsState()
    val databaseFile: File? = activeDatabaseId
        ?.let { File(DatabaseCatalogRepository.databasesDirectory(context), "$it.db") }
        ?.takeIf { it.exists() }

    var locationGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    var location by remember { mutableStateOf<Location?>(null) }
    var entries by remember { mutableStateOf<List<CacheEntry>>(emptyList()) }
    var selectedCache by remember { mutableStateOf<CacheSummary?>(null) }
    var showFilter by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        locationGranted = results.values.any { it }
    }

    LaunchedEffect(Unit) {
        if (!locationGranted) permissionLauncher.launch(LOCATION_PERMISSIONS)
    }

    LaunchedEffect(locationGranted) {
        if (locationGranted) findLocation(context) { location = it }
    }

    // Bezugspunkt für Reihenfolge und Entfernung: der Mittelpunkt der Karte, sonst der Standort.
    val reference: LatLong? = positionState.center ?: location?.let { LatLong(it.latitude, it.longitude) }

    LaunchedEffect(databaseFile, filter, reference) {
        entries = if (databaseFile == null) {
            emptyList()
        } else {
            val here = reference
            withContext(Dispatchers.IO) {
                CacheRepository(databaseFile)
                    .cachesMatching(filter)
                    .map { cache ->
                        CacheEntry(
                            cache,
                            here?.let { distanceKm(it.latitude, it.longitude, cache.latitude, cache.longitude) },
                        )
                    }
                    .filter { filter.allowsDistance(it.distanceKm) }
                    .sortedWith(compareBy<CacheEntry>({ it.distanceKm ?: Double.MAX_VALUE }, { it.cache.name }))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CacheSearchBar(
            searchText = filter.searchText,
            filterActive = filter.isActive,
            onSearchTextChange = { text -> filterViewModel.update { it.copy(searchText = text) } },
            onFilterClick = { showFilter = true },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )

        if (databaseFile == null) {
            EmptyState(
                icon = Icons.Filled.Storage,
                message = "Keine Datenbank aktiv. Importiere unter „Verwaltung“ eine Datenbank.",
            )
        } else {
            val hint = when {
                positionState.center != null -> " · nach Kartenmitte sortiert"
                location == null -> " · Standort unbekannt, sortiert nach Name"
                else -> ""
            }
            Text(
                text = "${entries.size} Caches$hint",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(entries, key = { it.cache.code }) { entry ->
                    ListItem(
                        overlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    bitmap = symbols.imageFor(entry.cache).asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(26.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(entry.cache.code)
                            }
                        },
                        headlineContent = { Text(entry.cache.name) },
                        supportingContent = {
                            Column {
                                Text(typeAndSize(entry.cache))
                                Text(
                                    text = ratingLine(entry.cache),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        trailingContent = { entry.distanceKm?.let { Text(formatDistance(it)) } },
                        modifier = Modifier.clickable { selectedCache = entry.cache },
                    )
                }
            }
        }
    }

    selectedCache?.let { cache ->
        CacheQuickView(
            cache = cache,
            onDismiss = { selectedCache = null },
            onDetails = {
                selectedCache = null
                onOpenDetails(cache.code)
            },
            onShowOnMap = {
                selectedCache = null
                onShowOnMap(cache)
            },
        )
    }

    if (showFilter) {
        CacheFilterSheet(
            filter = filter,
            hasLocation = reference != null,
            onFilterChange = { value -> filterViewModel.update { value } },
            onDismiss = { showFilter = false },
        )
    }
}

private fun typeAndSize(cache: CacheSummary): String =
    "${cacheTypeLabel(cache.cacheType)} · ${containerLabel(cache.container)}"

private fun ratingLine(cache: CacheSummary): String =
    "Schw. ${formatRating(cache.difficulty)} · Gel. ${formatRating(cache.terrain)}"

private fun formatDistance(km: Double): String =
    if (km < 1) {
        "${(km * 1000).roundToInt()} m"
    } else {
        String.format(Locale.GERMANY, "%.1f km", km)
    }
