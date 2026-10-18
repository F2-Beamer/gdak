package de.gdak.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.gdak.app.BuildConfig
import de.gdak.app.data.settings.ThemeMode

private val themeOptions = listOf(
    ThemeMode.SYSTEM to "Systemeinstellung",
    ThemeMode.LIGHT to "Hell",
    ThemeMode.DARK to "Dunkel",
)

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Darstellung", style = MaterialTheme.typography.titleMedium)
        themeOptions.forEach { (mode, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = mode == themeMode,
                        onClick = { onThemeModeSelected(mode) },
                        role = Role.RadioButton,
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = mode == themeMode, onClick = null)
                Text(text = label, modifier = Modifier.padding(start = 8.dp))
            }
        }

        Text(
            text = "Version",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = "${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
