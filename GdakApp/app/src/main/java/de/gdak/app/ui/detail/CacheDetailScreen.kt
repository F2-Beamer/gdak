package de.gdak.app.ui.detail

import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.widget.TextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import de.gdak.app.data.cachedb.CacheDetail
import de.gdak.app.data.cachedb.CacheRepository
import de.gdak.app.data.catalog.DatabaseCatalogRepository
import de.gdak.app.ui.catalog.DatabasesViewModel
import de.gdak.app.ui.map.cacheTypeLabel
import de.gdak.app.ui.map.containerLabel
import de.gdak.app.ui.map.formatDmm
import de.gdak.app.ui.map.germanDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Detailansicht eines Caches auf einer eigenen Seite: Beschreibungen, Hinweise, Notiz,
 * Wegpunkte, Attribute und die letzten Logs. Alles nur lesend.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheDetailScreen(code: String, databasesViewModel: DatabasesViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val activeDatabaseId by databasesViewModel.activeDatabaseId.collectAsState()
    val databaseFile: File? = activeDatabaseId
        ?.let { File(DatabaseCatalogRepository.databasesDirectory(context), "$it.db") }
        ?.takeIf { it.exists() }

    var detail by remember { mutableStateOf<CacheDetail?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(databaseFile, code) {
        loaded = false
        detail = databaseFile?.let { file ->
            withContext(Dispatchers.IO) { CacheRepository(file).cacheDetail(code) }
        }
        loaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(code) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { innerPadding ->
        val current = detail
        when {
            databaseFile == null -> Text(
                text = "Keine Datenbank aktiv.",
                modifier = Modifier.padding(innerPadding).padding(16.dp),
            )
            !loaded -> Text(text = "Lade …", modifier = Modifier.padding(innerPadding).padding(16.dp))
            current == null -> Text(
                text = "Cache nicht gefunden.",
                modifier = Modifier.padding(innerPadding).padding(16.dp),
            )
            else -> DetailContent(current, Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun DetailContent(detail: CacheDetail, modifier: Modifier) {
    val cache = detail.summary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(text = cache.name, style = MaterialTheme.typography.titleLarge)
        if (GC_CODE.matches(cache.code)) {
            val uriHandler = LocalUriHandler.current
            Text(
                text = cache.code,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { uriHandler.openUri("https://www.geocaching.com/geocache/${cache.code}") },
            )
        }
        Text(text = "Typ: ${cacheTypeLabel(cache.cacheType)}", modifier = Modifier.padding(top = 8.dp))
        Text(text = "Größe: ${containerLabel(cache.container)}")
        Text(
            text = String.format(
                Locale.GERMANY,
                "Difficulty: %.1f   Terrain: %.1f",
                cache.difficulty,
                cache.terrain,
            ),
        )
        if (cache.ownerName.isNotBlank()) Text(text = "Besitzer: ${cache.ownerName}")
        if (cache.placedDate.isNotBlank()) Text(text = "Versteckt: ${germanDate(cache.placedDate)}")
        Text(text = "Position: ${formatDmm(cache.latitude, cache.longitude)}")
        val status = listOfNotNull(
            "Gefunden".takeIf { cache.found },
            "Nicht gefunden (DNF)".takeIf { cache.dnf },
            "Archiviert".takeIf { cache.archived },
        )
        if (status.isNotEmpty()) Text(text = "Status: ${status.joinToString(" · ")}")

        if (detail.shortDescription.isNotBlank()) {
            SectionTitle("Kurzbeschreibung")
            HtmlText(detail.shortDescription)
        }
        if (detail.longDescription.isNotBlank()) {
            SectionTitle("Beschreibung")
            HtmlText(detail.longDescription)
        }
        if (detail.hints.isNotBlank()) {
            SectionTitle("Hinweise")
            Text(text = detail.hints)
        }

        SectionTitle("Eigene Notiz")
        Text(text = cache.userNote.ifBlank { "Keine Notiz vorhanden." })

        if (detail.waypoints.isNotEmpty()) {
            SectionTitle("Wegpunkte (${detail.waypoints.size})")
            detail.waypoints.forEach { waypoint ->
                Text(
                    text = "${waypoint.code} · ${waypoint.name}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(text = "${waypoint.type} · ${formatDmm(waypoint.latitude, waypoint.longitude)}")
            }
        }

        if (detail.attributes.isNotEmpty()) {
            SectionTitle("Attribute")
            detail.attributes.forEach { attribute ->
                Text(text = "Attribut ${attribute.id}: ${if (attribute.positive) "ja" else "nein"}")
            }
        }

        if (detail.logs.isNotEmpty()) {
            SectionTitle("Logs (letzte ${detail.logs.size})")
            detail.logs.forEach { log ->
                Text(
                    text = "${germanDate(log.date)} · ${log.type} · ${log.by}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (log.text.isNotBlank()) Text(text = log.text)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}

/** Groundspeak-Codes (z. B. `GCY8YF`). Nur sie bekommen einen Link auf geocaching.com. */
private val GC_CODE = Regex("GC[A-Z0-9]+", RegexOption.IGNORE_CASE)

/**
 * HTML-Text mit anklickbaren Links. Die Farbe folgt dem Design.
 * Der Inhalt stammt aus importierten Datenbanken und ist nicht vertrauenswürdig:
 * Nur `http`- und `https`-Links bleiben klickbar, alle anderen Ziele werden entfernt.
 */
private fun safeHtml(html: String): CharSequence {
    val text = SpannableStringBuilder(HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY))
    text.getSpans(0, text.length, URLSpan::class.java).forEach { span ->
        val scheme = Uri.parse(span.url).scheme?.lowercase()
        if (scheme != "http" && scheme != "https") text.removeSpan(span)
    }
    return text
}

@Composable
private fun HtmlText(html: String) {
    val color = LocalContentColor.current.toArgb()
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                movementMethod = LinkMovementMethod.getInstance()
                textSize = 16f
            }
        },
        update = { view ->
            view.setTextColor(color)
            view.text = safeHtml(html)
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
