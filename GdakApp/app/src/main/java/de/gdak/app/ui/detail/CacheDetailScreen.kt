package de.gdak.app.ui.detail

import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import de.gdak.app.data.cachedb.CacheDetail
import de.gdak.app.data.cachedb.CacheRepository
import de.gdak.app.data.catalog.DatabaseCatalogRepository
import de.gdak.app.ui.catalog.DatabasesViewModel
import de.gdak.app.ui.common.EmptyState
import de.gdak.app.ui.common.RatingBar
import de.gdak.app.ui.common.gdakTopAppBarColors
import de.gdak.app.ui.format.formatRating
import de.gdak.app.ui.map.cacheTypeLabel
import de.gdak.app.ui.map.containerLabel
import de.gdak.app.ui.map.formatDmm
import de.gdak.app.ui.map.germanDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Detailansicht eines Caches auf einer eigenen Seite. Der Kopf mit Name und Kennzahlen bleibt
 * stehen, darunter wechseln Tabs zwischen Details, Beschreibung und Logs. Alles nur lesend.
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
                colors = gdakTopAppBarColors(),
            )
        },
    ) { innerPadding ->
        val current = detail
        when {
            databaseFile == null -> EmptyState(
                icon = Icons.Filled.Storage,
                message = "Keine Datenbank aktiv.",
                modifier = Modifier.padding(innerPadding),
            )
            !loaded -> Text(text = "Lade …", modifier = Modifier.padding(innerPadding).padding(16.dp))
            current == null -> EmptyState(
                icon = Icons.Filled.SearchOff,
                message = "Cache nicht gefunden.",
                modifier = Modifier.padding(innerPadding),
            )
            else -> DetailContent(current, Modifier.padding(innerPadding))
        }
    }
}

/** Die Tabs der Detailansicht in ihrer Reihenfolge. Hinweis, Attribute und Notiz stehen unter „Details“. */
private enum class DetailTab(val label: String) {
    INFO("Details"),
    DESCRIPTION("Beschreibung"),
    LOGS("Logs"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailContent(detail: CacheDetail, modifier: Modifier) {
    val cache = detail.summary
    val tabs = DetailTab.entries
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
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
            CacheStats(detail, modifier = Modifier.padding(top = 12.dp))
        }

        PrimaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 16.dp,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label) },
                )
            }
        }
        HorizontalDivider()

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 24.dp),
            ) {
                when (tabs[page]) {
                    DetailTab.INFO -> InfoTab(detail)
                    DetailTab.DESCRIPTION -> DescriptionTab(detail)
                    DetailTab.LOGS -> LogsTab(detail)
                }
            }
        }
    }
}

/** Schwierigkeit und Gelände als Balken, Größe als Text. Steht über den Tabs und bleibt beim Wechseln sichtbar. */
@Composable
private fun CacheStats(detail: CacheDetail, modifier: Modifier = Modifier) {
    val cache = detail.summary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StatItem(label = "Schwierigkeit", value = cache.difficulty)
        StatItem(label = "Gelände", value = cache.terrain)
        Column {
            Text(
                text = "Größe",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = containerLabel(cache.container), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun StatItem(label: String, value: Double) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            RatingBar(rating = value)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = formatRating(value), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Kerndaten, dann Hinweis, Attribute und die eigene Notiz. */
@Composable
private fun InfoTab(detail: CacheDetail) {
    val cache = detail.summary
    InfoRow(label = "Typ", value = cacheTypeLabel(cache.cacheType))
    InfoRow(label = "Größe", value = containerLabel(cache.container))
    if (cache.ownerName.isNotBlank()) InfoRow(label = "Besitzer", value = cache.ownerName)
    if (cache.placedDate.isNotBlank()) InfoRow(label = "Versteckt", value = germanDate(cache.placedDate))
    InfoRow(label = "Position", value = formatDmm(cache.latitude, cache.longitude))
    val status = listOfNotNull(
        "Gefunden".takeIf { cache.found },
        "Nicht gefunden (DNF)".takeIf { cache.dnf },
        "Archiviert".takeIf { cache.archived },
    )
    if (status.isNotEmpty()) InfoRow(label = "Status", value = status.joinToString(" · "))

    SectionTitle("Hinweis")
    if (detail.hints.isNotBlank()) {
        Text(text = detail.hints)
    } else {
        Text(text = "Keine Hinweise vorhanden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    SectionTitle("Attribute")
    if (detail.attributes.isEmpty()) {
        Text(text = "Keine Attribute vorhanden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        detail.attributes.forEach { attribute ->
            Text(text = "Attribut ${attribute.id}: ${if (attribute.positive) "ja" else "nein"}")
        }
    }

    SectionTitle("Eigene Notiz")
    Text(
        text = cache.userNote.ifBlank { "Keine Notiz vorhanden." },
        color = if (cache.userNote.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
}

/** Eine Zeile mit Bezeichnung links und Wert rechts, durch eine Linie getrennt. */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
        )
    }
    HorizontalDivider()
}

@Composable
private fun DescriptionTab(detail: CacheDetail) {
    if (detail.shortDescription.isBlank() && detail.longDescription.isBlank() && detail.waypoints.isEmpty()) {
        Text(text = "Keine Beschreibung vorhanden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    if (detail.shortDescription.isNotBlank()) {
        SectionTitle("Kurzbeschreibung")
        HtmlText(detail.shortDescription)
    }
    if (detail.longDescription.isNotBlank()) {
        SectionTitle("Beschreibung")
        HtmlText(detail.longDescription)
    }
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
}

@Composable
private fun LogsTab(detail: CacheDetail) {
    if (detail.logs.isEmpty()) {
        Text(text = "Keine Logs vorhanden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    detail.logs.forEachIndexed { index, log ->
        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = "${germanDate(log.date)} · ${log.type} · ${log.by}",
            style = MaterialTheme.typography.titleSmall,
        )
        if (log.text.isNotBlank()) Text(text = log.text, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
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
