package de.gdak.app.ui.map

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import de.gdak.app.data.cachedb.CacheFilter
import de.gdak.app.data.cachedb.CacheRepository
import de.gdak.app.data.cachedb.CacheSummary
import de.gdak.app.data.cachedb.allowsDistance
import de.gdak.app.data.catalog.DatabaseCatalogRepository
import de.gdak.app.data.catalog.MapCatalogRepository
import de.gdak.app.data.geo.distanceKm
import de.gdak.app.ui.catalog.DatabasesViewModel
import de.gdak.app.ui.catalog.MapsViewModel
import de.gdak.app.ui.filter.CacheFilterSheet
import de.gdak.app.ui.filter.CacheFilterViewModel
import de.gdak.app.ui.common.EmptyState
import de.gdak.app.ui.filter.CacheSearchBar
import de.gdak.app.ui.filter.FilterIconButton
import de.gdak.app.ui.location.LOCATION_PERMISSIONS
import de.gdak.app.ui.theme.LocationBlue
import androidx.compose.ui.graphics.toArgb
import de.gdak.app.ui.location.findLocation
import de.gdak.app.ui.location.hasLocationPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.Layer
import org.mapsforge.map.layer.overlay.Marker
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.model.common.Observer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import java.io.File

/** Zoomstufe, wenn die Karte auf den Standort zentriert wird. */
private const val LOCATION_ZOOM = 15.toByte()

/** Zoomstufe, wenn kein Standort bekannt ist und die Mitte der Kartendatei gezeigt wird. */
private const val FALLBACK_ZOOM = 10.toByte()

@Composable
fun MapScreen(
    mapsViewModel: MapsViewModel,
    databasesViewModel: DatabasesViewModel,
    filterViewModel: CacheFilterViewModel,
    onOpenDetails: (String) -> Unit,
    /** Ziel, auf das die Karte zentriert wird, z. B. von der Liste aus. */
    focus: LatLong?,
    onFocusHandled: () -> Unit,
    positionState: MapPositionViewModel,
) {
    val context = LocalContext.current
    val maps by mapsViewModel.maps.collectAsState()
    val activeMapId by mapsViewModel.activeMapId.collectAsState()
    val activeMap = maps.firstOrNull { it.id == activeMapId }
    val activeDatabaseId by databasesViewModel.activeDatabaseId.collectAsState()
    val filter by filterViewModel.filter.collectAsState()

    if (activeMap == null) {
        EmptyState(
            icon = Icons.Filled.Map,
            message = "Keine Karte aktiv. Importiere unter „Verwaltung“ eine Karte und aktiviere sie.",
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val file = File(MapCatalogRepository.mapsDirectory(context), "${activeMap.id}.map")
    val databaseFile = activeDatabaseId
        ?.let { File(DatabaseCatalogRepository.databasesDirectory(context), "$it.db") }
        ?.takeIf { it.exists() }
    // Bei jedem Kartenwechsel wird die MapView neu aufgebaut.
    key(activeMap.id) {
        OfflineMap(
            file = file,
            databaseFile = databaseFile,
            filter = filter,
            filterViewModel = filterViewModel,
            onOpenDetails = onOpenDetails,
            focus = focus,
            onFocusHandled = onFocusHandled,
            positionState = positionState,
        )
    }
}

@Composable
private fun OfflineMap(
    file: File,
    databaseFile: File?,
    filter: CacheFilter,
    filterViewModel: CacheFilterViewModel,
    onOpenDetails: (String) -> Unit,
    focus: LatLong?,
    onFocusHandled: () -> Unit,
    positionState: MapPositionViewModel,
) {
    val context = LocalContext.current
    var locationGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    var showFilter by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        locationGranted = results.values.any { it }
    }

    LaunchedEffect(Unit) {
        if (!locationGranted) permissionLauncher.launch(LOCATION_PERMISSIONS)
    }

    val mapView = remember(file) { createMapView(context, file, positionState) }
    val bounds = remember(file) { MapFile(file).boundingBox() }
    val marker = remember(mapView) {
        createLocationMarker(context).also { mapView.layerManager.layers.add(it) }
    }

    val symbols = remember(context) { CacheSymbols(context) }
    val cacheLayers = remember(mapView) { mutableListOf<Layer>() }
    var caches by remember { mutableStateOf<List<CacheSummary>>(emptyList()) }
    var selectedCache by remember { mutableStateOf<CacheSummary?>(null) }
    // Erhöht sich bei jeder Kartenbewegung und löst das Nachladen der Caches aus.
    var viewVersion by remember { mutableStateOf(0) }

    DisposableEffect(mapView) {
        val observer = Observer {
            // Jede Bewegung merken, damit die Karte später dort weitergeht.
            positionState.center = mapView.model.mapViewPosition.center
            positionState.zoom = mapView.model.mapViewPosition.zoomLevel
            positionState.mapPath = file.path
            viewVersion += 1
        }
        mapView.model.mapViewPosition.addObserver(observer)
        onDispose {
            mapView.model.mapViewPosition.removeObserver(observer)
            mapView.destroyAll()
        }
    }

    LaunchedEffect(viewVersion, databaseFile, filter) {
        caches = if (databaseFile == null) {
            emptyList()
        } else {
            val box: BoundingBox = mapView.boundingBox
            // Der Entfernungsfilter bezieht sich auf den Mittelpunkt der Karte, wie die Liste.
            val center = mapView.model.mapViewPosition.center
            withContext(Dispatchers.IO) {
                CacheRepository(databaseFile)
                    .cachesMatching(filter, box, CacheRepository.MAX_MAP_RESULTS)
                    .filter { cache ->
                        filter.allowsDistance(
                            distanceKm(center.latitude, center.longitude, cache.latitude, cache.longitude),
                        )
                    }
            }
        }
    }

    LaunchedEffect(caches) {
        mapView.layerManager.layers.removeAll(cacheLayers)
        cacheLayers.clear()
        caches.forEach { cache ->
            cacheLayers += CacheMarker(cache, symbols.markerBitmapFor(cache), mapView) { selectedCache = it }
        }
        mapView.layerManager.layers.addAll(cacheLayers)
    }

    val showAtLocation: (Location) -> Unit = { location ->
        showLocation(mapView, marker, location, bounds)
    }

    LaunchedEffect(focus) {
        focus?.let {
            mapView.setCenter(it)
            mapView.setZoomLevel(LOCATION_ZOOM)
            onFocusHandled()
        }
    }

    // Wird beim Aufbau der MapView festgehalten. Später setzt die Kartenbewegung die gespeicherte
    // Position schon, bevor der Standort eintrifft, und das Zentrieren würde sonst ausbleiben.
    val centerOnStart = remember(mapView) { focus == null && positionState.center == null }

    LaunchedEffect(locationGranted) {
        if (locationGranted) {
            // Der Standortpunkt wird immer gezeigt. Zentriert wird nur beim allerersten Öffnen,
            // damit ein Ziel aus der Liste oder die letzte Kartenansicht erhalten bleiben.
            // Erst nach dem Layout, weil die MapView vorher keine Größe hat und Zentrieren ignoriert.
            findLocation(context) { location ->
                mapView.doOnLayout {
                    showLocation(mapView, marker, location, bounds, center = centerOnStart)
                }
            }
        }
    }

    // Die Karte beginnt unter der Statusleiste, damit Uhrzeit, Empfang und Akku lesbar bleiben.
    Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).clipToBounds()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        CacheSearchBar(
            searchText = filter.searchText,
            filterActive = filter.isActive,
            onSearchTextChange = { text -> filterViewModel.update { it.copy(searchText = text) } },
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 72.dp),
        )
        // Positionsschaltfläche und Filter-Schaltfläche untereinander rechts oben, wie in der Liste.
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalIconButton(
                onClick = {
                    if (locationGranted) {
                        findLocation(context, preferCurrent = true, onFound = showAtLocation)
                    } else {
                        permissionLauncher.launch(LOCATION_PERMISSIONS)
                    }
                },
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Auf aktuelle Position springen")
            }
            FilterIconButton(active = filter.isActive, onClick = { showFilter = true })
        }
    }

    if (showFilter) {
        CacheFilterSheet(
            filter = filter,
            hasLocation = true,
            onFilterChange = { value -> filterViewModel.update { value } },
            onDismiss = { showFilter = false },
        )
    }

    selectedCache?.let { cache ->
        CacheQuickView(
            cache = cache,
            onDismiss = { selectedCache = null },
            onDetails = {
                selectedCache = null
                onOpenDetails(cache.code)
            },
        )
    }
}

/**
 * Baut die MapView mit der Kartendatei. Ohne bekannten Standort zeigt sie die
 * Mitte der Kartendatei.
 */
private fun createMapView(context: Context, file: File, positionState: MapPositionViewModel): MapView {
    val mapFile = MapFile(file)
    return MapView(context).apply {
        mapScaleBar.isVisible = true
        setBuiltInZoomControls(true)

        val tileCache = AndroidUtil.createTileCache(
            context,
            "mapcache",
            model.displayModel.tileSize,
            1f,
            model.frameBufferModel.overdrawFactor,
        )
        val layer = TileRendererLayer(
            tileCache,
            mapFile,
            model.mapViewPosition,
            false,
            true,
            false,
            AndroidGraphicFactory.INSTANCE,
        )
        layer.setXmlRenderTheme(MapsforgeThemes.DEFAULT)
        layerManager.layers.add(layer)

        // Die zuletzt gezeigte Ansicht wiederherstellen, sofern sie zu dieser Kartendatei gehört.
        val saved = positionState.center
        if (saved != null && positionState.mapPath == file.path) {
            setCenter(saved)
            setZoomLevel(positionState.zoom)
        } else {
            setCenter(mapFile.boundingBox().centerPoint)
            setZoomLevel(FALLBACK_ZOOM)
        }
    }
}

/** Blauer Punkt für die aktuelle Position. Anfangs unsichtbar, bis ein Standort bekannt ist. */
private fun createLocationMarker(context: Context): Marker {
    val density = context.resources.displayMetrics.density
    val size = (20 * density).toInt()
    val drawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(LocationBlue.toArgb())
        setStroke((3 * density).toInt(), Color.WHITE)
        setSize(size, size)
    }
    return Marker(LatLong(0.0, 0.0), AndroidGraphicFactory.convertToBitmap(drawable), 0, 0).apply {
        isVisible = false
    }
}

/**
 * Setzt den Punkt auf den Standort und zentriert die Karte. Liegt der Standort außerhalb
 * der Kartendatei, wird stattdessen die Mitte der Kartendatei gezeigt.
 */
private fun showLocation(
    mapView: MapView,
    marker: Marker,
    location: Location,
    bounds: BoundingBox,
    center: Boolean = true,
) {
    val target = LatLong(location.latitude, location.longitude)
    marker.setLatLong(target)
    marker.isVisible = true
    marker.requestRedraw()
    if (!center) return
    if (bounds.contains(target)) {
        mapView.setCenter(target)
        mapView.setZoomLevel(LOCATION_ZOOM)
    } else {
        mapView.setCenter(bounds.centerPoint)
        mapView.setZoomLevel(FALLBACK_ZOOM)
    }
}
