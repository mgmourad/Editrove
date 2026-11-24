// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import news.openwire.R

/**
 * Newsreader at any point on its weight and optical-size axes, for motion that animates the type
 * itself. Each family is built once and cached; callers step the axes (a dozen or so stops) so an
 * animation reuses a handful of typefaces instead of building one per frame.
 */
object VariableType {
    private val cache = HashMap<Int, FontFamily>()

    /** [weight] 200–800, [opsz] 6–72 (null: the font's automatic optical size). */
    fun newsreader(weight: Int, opsz: Int? = null): FontFamily {
        val w = weight.coerceIn(200, 800)
        val o = opsz?.coerceIn(6, 72)
        val key = w * 100 + (o ?: 0)
        return synchronized(cache) {
            cache.getOrPut(key) {
                val axes = if (o == null) FontVariation.Settings(FontVariation.weight(w))
                else FontVariation.Settings(FontVariation.weight(w), FontVariation.Setting("opsz", o.toFloat()))
                FontFamily(Font(R.font.newsreader, FontWeight(w), variationSettings = axes))
            }
        }
    }

    /** Snaps [value] to one of [stops] even steps between [from] and [to]. */
    fun step(value: Float, from: Int, to: Int, stops: Int): Int {
        val k = (value.coerceIn(0f, 1f) * stops).let { kotlin.math.round(it) } / stops
        return (from + (to - from) * k).toInt()
    }
}

/** True when the system animation scale is 0: keep end states, cut every transition. */
fun reduceMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

@Composable
fun rememberReduceMotion(): Boolean {
    val c = LocalContext.current
    return remember(c) { reduceMotion(c) }
}
