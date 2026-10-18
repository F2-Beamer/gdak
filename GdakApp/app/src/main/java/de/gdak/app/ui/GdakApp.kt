package de.gdak.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.gdak.app.ui.catalog.DatabasesViewModel
import de.gdak.app.ui.detail.CacheDetailScreen
import de.gdak.app.ui.map.MapPositionViewModel
import org.mapsforge.core.model.LatLong
import de.gdak.app.ui.catalog.MapsViewModel
import de.gdak.app.ui.filter.CacheFilterViewModel
import de.gdak.app.ui.list.CacheListScreen
import de.gdak.app.ui.manage.ManageScreen
import de.gdak.app.ui.map.MapScreen
import de.gdak.app.ui.settings.SettingsScreen
import de.gdak.app.ui.settings.SettingsViewModel

/** Hauptziele der Bottom-Navigation. */
private sealed class Destination(val route: String, val label: String, val icon: ImageVector) {
    data object Map : Destination("map", "Karte", Icons.Filled.Map)
    data object List : Destination("list", "Liste", Icons.AutoMirrored.Filled.List)
    data object Manage : Destination("manage", "Verwaltung", Icons.Filled.Storage)
    data object Settings : Destination("settings", "Einstellungen", Icons.Filled.Settings)
}

private val destinations = listOf(
    Destination.Map,
    Destination.List,
    Destination.Manage,
    Destination.Settings,
)

/** Route der Detailansicht mit dem Cache-Code als Argument. */
private const val DETAIL_ARG = "code"
private const val DETAIL_ROUTE = "detail/{$DETAIL_ARG}"

private fun detailRoute(code: String): String = "detail/$code"

@Composable
fun GdakApp(
    settingsViewModel: SettingsViewModel = viewModel(),
    mapsViewModel: MapsViewModel = viewModel(),
    databasesViewModel: DatabasesViewModel = viewModel(),
    filterViewModel: CacheFilterViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val themeMode by settingsViewModel.themeMode.collectAsState()
    // Ziel für die Karte, wenn sie aus der Liste heraus auf einen Cache zentriert werden soll.
    var mapFocus by remember { mutableStateOf<LatLong?>(null) }
    // Letzte Kartenansicht, geteilt von Karte und Liste.
    val mapPositionState: MapPositionViewModel = viewModel()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination?.route == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Map.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.Map.route) {
                MapScreen(
                    mapsViewModel = mapsViewModel,
                    databasesViewModel = databasesViewModel,
                    filterViewModel = filterViewModel,
                    onOpenDetails = { code -> navController.navigate(detailRoute(code)) },
                    focus = mapFocus,
                    onFocusHandled = { mapFocus = null },
                    positionState = mapPositionState,
                )
            }
            composable(Destination.List.route) {
                CacheListScreen(
                    databasesViewModel = databasesViewModel,
                    filterViewModel = filterViewModel,
                    onOpenDetails = { code -> navController.navigate(detailRoute(code)) },
                    positionState = mapPositionState,
                    onShowOnMap = { cache ->
                        mapFocus = LatLong(cache.latitude, cache.longitude)
                        navController.navigate(Destination.Map.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(
                route = DETAIL_ROUTE,
                arguments = listOf(navArgument(DETAIL_ARG) { type = NavType.StringType }),
            ) { entry ->
                CacheDetailScreen(
                    code = entry.arguments?.getString(DETAIL_ARG).orEmpty(),
                    databasesViewModel = databasesViewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.Manage.route) {
                ManageScreen(mapsViewModel = mapsViewModel, databasesViewModel = databasesViewModel)
            }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeModeSelected = settingsViewModel::setThemeMode,
                )
            }
        }
    }
}
