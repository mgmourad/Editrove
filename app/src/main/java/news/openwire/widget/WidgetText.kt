// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import kotlin.math.ceil
import news.openwire.R

/**
 * Draws widget text in the app's own fonts. Launchers can't load an app's fonts in a
 * widget, so text is rendered here (Newsreader, Inter, JetBrains Mono at the app's
 * weights) into glyph masks (ALPHA_8 — a quarter of the memory of colour bitmaps) that
 * the layout tints, so light/dark theming still follows the system.
 */
object WidgetText {

    enum class Face(val font: Int, val weight: Int) {
        SERIF_BOLD(R.font.newsreader, 700),
        SANS(R.font.inter, 400),
        SANS_MEDIUM(R.font.inter, 500),
        SANS_SEMIBOLD(R.font.inter, 600),
        MONO_MEDIUM(R.font.jetbrains_mono, 500),
        MONO_SEMIBOLD(R.font.jetbrains_mono, 600),
    }

    private val faces = HashMap<Int, Typeface?>()

    private fun typeface(context: Context, face: Face): Typeface? =
        faces.getOrPut(face.font) { runCatching { ResourcesCompat.getFont(context, face.font) }.getOrNull() }

    private fun paint(context: Context, face: Face, sizeSp: Float, letterSpacingEm: Float): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = typeface(context, face)
            fontVariationSettings = "'wght' ${face.weight}"
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, context.resources.displayMetrics)
            letterSpacing = letterSpacingEm
            color = android.graphics.Color.BLACK
        }

    /**
     * [text] laid out within [maxWidthPx] on at most [maxLines] lines (ellipsized), as a
     * tight glyph mask. [lineHeightSp] sets the line pitch like Compose's lineHeight.
     */
    fun render(
        context: Context, text: String, face: Face, sizeSp: Float, maxWidthPx: Int,
        maxLines: Int = 1, lineHeightSp: Float? = null, letterSpacingEm: Float = 0f,
    ): Bitmap {
        val p = paint(context, face, sizeSp, letterSpacingEm)
        val width = maxWidthPx.coerceAtLeast(1)
        if (maxLines == 1) {
            val line = TextUtils.ellipsize(text, p, width.toFloat(), TextUtils.TruncateAt.END).toString()
            val fm = p.fontMetrics
            val w = ceil(p.measureText(line)).toInt().coerceAtLeast(1)
            val h = ceil(fm.descent - fm.ascent).toInt().coerceAtLeast(1)
            return Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8).also { Canvas(it).drawText(line, 0f, -fm.ascent, p) }
        }
        val extra = lineHeightSp?.let {
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, it, context.resources.displayMetrics) - (p.fontMetrics.descent - p.fontMetrics.ascent)
        } ?: 0f
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, p, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setLineSpacing(extra, 1f)
            .setIncludePad(false)
            .build()
        val w = (0 until layout.lineCount).maxOfOrNull { ceil(layout.getLineWidth(it)).toInt() }?.coerceIn(1, width) ?: 1
        val h = layout.height.coerceAtLeast(1)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8).also { layout.draw(Canvas(it)) }
    }
}
