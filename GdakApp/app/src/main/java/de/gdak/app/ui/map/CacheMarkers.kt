package de.gdak.app.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import de.gdak.app.data.cachedb.CacheSummary
import de.gdak.app.data.cachedb.CacheTypeGroup
import org.mapsforge.core.graphics.Bitmap
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Point
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.overlay.Marker

/**
 * Ein Cache auf der Karte. Tippen ruft [onSelected] mit dem Cache auf, aber nur, wenn der
 * Tipp auf dem Symbol liegt. Sonst gibt die Karte den Tipp an die anderen Ebenen weiter.
 */
class CacheMarker(
    val cache: CacheSummary,
    bitmap: Bitmap,
    private val mapView: MapView,
    private val onSelected: (CacheSummary) -> Unit,
) : Marker(LatLong(cache.latitude, cache.longitude), bitmap, 0, 0) {

    override fun onTap(tapLatLong: LatLong, viewPosition: Point, tapPoint: Point): Boolean {
        if (!contains(viewPosition, tapPoint, mapView)) return false
        onSelected(cache)
        return true
    }
}

/**
 * Erzeugt die Symbole der Caches. Mystery-Caches (Typ U und B) zeigen ein blaues „M“,
 * Multi-Caches ein orangefarbenes „M“. Alle anderen Typen zeigen ein violettes „?“.
 * Caches mit korrigierten Koordinaten bekommen einen roten Rand, gefundene ein Smiley unten rechts.
 * Archivierte Caches werden ausgegraut.
 *
 * [imageFor] liefert das Symbol für die Liste, [markerBitmapFor] das Kartensymbol.
 */
class CacheSymbols(private val context: Context) {

    private val images = mutableMapOf<String, android.graphics.Bitmap>()
    private val markers = mutableMapOf<String, Bitmap>()

    fun imageFor(cache: CacheSummary): android.graphics.Bitmap {
        val group = CacheTypeGroup.of(cache.cacheType)
        return images.getOrPut(keyFor(cache)) {
            createSymbol(STYLES.getValue(group), cache.hasCorrected, cache.found, cache.archived)
        }
    }

    fun markerBitmapFor(cache: CacheSummary): Bitmap =
        markers.getOrPut(keyFor(cache)) {
            AndroidGraphicFactory.convertToBitmap(BitmapDrawable(context.resources, imageFor(cache)))
        }

    private fun keyFor(cache: CacheSummary): String {
        val group = CacheTypeGroup.of(cache.cacheType)
        return "${group.name}|${cache.hasCorrected}|${cache.found}|${cache.archived}"
    }

    private fun createSymbol(symbolStyle: Style, corrected: Boolean, found: Boolean, archived: Boolean): android.graphics.Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (26 * density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val radius = center - density
        // Archivierte Caches werden durchsichtig gezeichnet.
        val opacity = if (archived) FADED_OPACITY else OPAQUE

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = symbolStyle.color
            alpha = opacity
        }
        canvas.drawCircle(center, center, radius, fill)

        val borderWidth = (if (corrected) 3 else 2) * density
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = borderWidth
            color = if (corrected) RED else Color.WHITE
            alpha = opacity
        }
        canvas.drawCircle(center, center, radius - borderWidth / 2, border)

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = opacity
            textSize = size * 0.5f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val baseline = center - (text.descent() + text.ascent()) / 2
        canvas.drawText(symbolStyle.letter, center, baseline, text)

        if (found) drawSmiley(canvas, size, density, opacity)
        return bitmap
    }

    /** Gelbes Smiley-Zeichen in der rechten unteren Ecke des Symbols. */
    private fun drawSmiley(canvas: Canvas, size: Int, density: Float, opacity: Int) {
        val badgeRadius = 6.5f * density
        val cx = size - badgeRadius - density / 2
        val cy = cx

        val face = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = YELLOW
            alpha = opacity
        }
        canvas.drawCircle(cx, cy, badgeRadius, face)

        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = density
            color = DARK
            alpha = opacity
        }
        canvas.drawCircle(cx, cy, badgeRadius - density / 2, outline)

        val feature = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DARK
            alpha = opacity
        }
        val eyeRadius = 1.1f * density
        canvas.drawCircle(cx - 2.5f * density, cy - 2f * density, eyeRadius, feature)
        canvas.drawCircle(cx + 2.5f * density, cy - 2f * density, eyeRadius, feature)

        val mouth = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
            strokeCap = Paint.Cap.ROUND
            color = DARK
            alpha = opacity
        }
        val mouthBounds = RectF(cx - 3f * density, cy - 2f * density, cx + 3f * density, cy + 4f * density)
        canvas.drawArc(mouthBounds, 20f, 140f, false, mouth)
    }

    private class Style(val letter: String, val color: Int)

    private companion object {
        val RED = 0xFFD32F2F.toInt()
        val YELLOW = 0xFFFDD835.toInt()
        val DARK = 0xFF212121.toInt()
        const val OPAQUE = 255
        const val FADED_OPACITY = 150

        val STYLES = mapOf(
            CacheTypeGroup.TRADITIONAL to Style("T", 0xFF2E7D32.toInt()),
            CacheTypeGroup.MULTI to Style("M", 0xFFEF6C00.toInt()),
            CacheTypeGroup.MYSTERY to Style("M", 0xFF1565C0.toInt()),
            CacheTypeGroup.OTHER to Style("?", 0xFF6A1B9A.toInt()),
        )
    }
}
