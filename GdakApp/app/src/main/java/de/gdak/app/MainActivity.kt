package de.gdak.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import de.gdak.app.ui.GdakApp
import de.gdak.app.ui.settings.SettingsViewModel
import de.gdak.app.ui.theme.GdakTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val themeMode by settingsViewModel.themeMode.collectAsState()
            GdakTheme(themeMode = themeMode) {
                GdakApp(settingsViewModel = settingsViewModel)
            }
        }
    }
}
