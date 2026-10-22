package de.gdak.app.ui.filter

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Suchfeld in Pillenform. Mit [onFilterClick] folgt rechts der Filter-Button, der
 * hervorgehoben ist, wenn ein Filter aktiv ist. Ohne [onFilterClick] zeigt das Feld nur die Suche.
 */
@Composable
fun CacheSearchBar(
    searchText: String,
    filterActive: Boolean,
    onSearchTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onFilterClick: (() -> Unit)? = null,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            placeholder = { Text("Code oder Name") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    IconButton(onClick = { onSearchTextChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Suche leeren")
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
            ),
        )
        if (onFilterClick != null) {
            Spacer(modifier = Modifier.width(8.dp))
            FilterIconButton(active = filterActive, onClick = onFilterClick)
        }
    }
}

/** Filter-Button für Liste und Karte: gefüllt, wenn ein Filter aktiv ist, sonst tonal. */
@Composable
fun FilterIconButton(active: Boolean, onClick: () -> Unit) {
    if (active) {
        FilledIconButton(onClick = onClick) {
            Icon(Icons.Filled.FilterList, contentDescription = "Filter")
        }
    } else {
        FilledTonalIconButton(onClick = onClick) {
            Icon(Icons.Filled.FilterList, contentDescription = "Filter")
        }
    }
}
