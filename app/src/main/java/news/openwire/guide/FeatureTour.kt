// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.guide

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import news.openwire.R
import news.openwire.domain.engine.Languages
import news.openwire.i18n.tr
import news.openwire.ui.home.mono
import news.openwire.ui.home.sans
import news.openwire.ui.home.serif
import news.openwire.ui.theme.Amber400
import news.openwire.ui.theme.Emerald400
import news.openwire.ui.theme.Fuchsia400
import news.openwire.ui.theme.Glass
import news.openwire.ui.theme.Indigo400
import news.openwire.ui.theme.Rose
import news.openwire.ui.theme.Rose400

/**
 * One slide of the feature tour. [wide] shots are of the unfolded screen (landscape-ish);
 * [where] says where to find the feature; [bigScreenOnly] slides only show on foldables and tablets.
 */
data class TourSlide(
    val shot: String?,
    @StringRes val kicker: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
    @StringRes val where: Int? = null,
    val glow: Color = Rose,
    val wide: Boolean = false,
    val bigScreenOnly: Boolean = false,
)

object FeatureTour {
    /** Bump to show a redesigned tour to everyone once. */
    const val VERSION = 2

    private val ALL = listOf(
        TourSlide("t_today", R.string.ft_k_start, R.string.ft_today_t, R.string.ft_today_b, glow = Rose),
        TourSlide("t_split", R.string.ft_k_fold, R.string.ft_split_t, R.string.ft_split_b, R.string.ft_split_w, Indigo400, wide = true, bigScreenOnly = true),
        TourSlide("t_full", R.string.ft_k_fold, R.string.ft_full_t, R.string.ft_full_b, R.string.ft_full_w, Fuchsia400, wide = true, bigScreenOnly = true),
        TourSlide("t_pull", R.string.ft_k_front, R.string.ft_pull_t, R.string.ft_pull_b, glow = Rose400),
        TourSlide("t_reader", R.string.ft_k_read, R.string.ft_reader_t, R.string.ft_reader_b, glow = Indigo400),
        TourSlide("t_videos", R.string.ft_k_watch, R.string.ft_videos_t, R.string.ft_videos_b, glow = Fuchsia400),
        TourSlide("t_player", R.string.ft_k_watch, R.string.ft_player_t, R.string.ft_player_b, glow = Fuchsia400),
        TourSlide("t_foryou", R.string.ft_k_front, R.string.ft_foryou_t, R.string.ft_foryou_b, glow = Rose),
        TourSlide("t_audio", R.string.ft_k_listen, R.string.ft_audio_t, R.string.ft_audio_b, R.string.ft_audio_w, Amber400),
        TourSlide("t_live", R.string.ft_k_watch, R.string.ft_live_t, R.string.ft_live_b, R.string.ft_live_w, Rose400),
        TourSlide("t_opinion", R.string.ft_k_read, R.string.ft_opinion_t, R.string.ft_opinion_b, R.string.ft_opinion_w, Amber400),
        TourSlide("t_discover", R.string.ft_k_read, R.string.ft_discover_t, R.string.ft_discover_b, R.string.ft_discover_w, Indigo400),
        TourSlide("t_saved", R.string.ft_k_read, R.string.ft_saved_t, R.string.ft_saved_b, R.string.ft_saved_w, Emerald400),
        TourSlide("t_topics", R.string.ft_k_yours, R.string.ft_topics_t, R.string.ft_topics_b, R.string.ft_topics_w, Indigo400),
        TourSlide("t_alerts", R.string.ft_k_yours, R.string.ft_alerts_t, R.string.ft_alerts_b, R.string.ft_alerts_w, Rose),
        TourSlide("t_appear", R.string.ft_k_yours, R.string.ft_appear_t, R.string.ft_appear_b, R.string.ft_appear_w, Fuchsia400),
        TourSlide(null, R.string.ft_k_end, R.string.ft_privacy_t, R.string.ft_privacy_b, R.string.ft_privacy_w, Emerald400),
    )

    /** A foldable (it has a hinge) or a tablet-sized screen: the layout slides apply. */
    fun bigScreen(context: Context): Boolean {
        val hinge = Build.VERSION.SDK_INT >= 30 && context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)
        return hinge || context.resources.configuration.smallestScreenWidthDp >= 600
    }

    fun slidesFor(context: Context): List<TourSlide> = if (bigScreen(context)) ALL else ALL.filter { !it.bigScreenOnly }

    private const val PREFS = "feature_tour"

    /** Shown once per tour version: after first-run setup, and once to people updating. */
    fun due(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("seen", 0) < VERSION

    fun markSeen(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("seen", VERSION).apply()

    fun shotUri(context: Context, shot: String): String = Guide.shotUri(context, shot, Languages.effective(null))
}

/**
 * The feature tour: one slide per feature with a real screenshot, what it does and where to find
 * it. On a big screen the shot sits beside the words; on a phone, above them.
 */
@Composable
fun FeatureTourScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val slides = remember { FeatureTour.slidesFor(context) }
    val g = Glass
    val pager = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    val last = page == slides.lastIndex
    BackHandler { if (page > 0) scope.launch { pager.animateScrollToPage(page - 1) } else onDone() }
    // The page's glow follows the slide.
    val glow = slides[page].glow
    Box(
        Modifier.fillMaxSize().background(g.deviceBg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(glow.copy(alpha = .22f), Color.Transparent), radius = 1400f)))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Progress: one segment per slide.
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                slides.indices.forEach { i ->
                    val k by animateFloatAsState(if (i <= page) 1f else 0f, tween(300), label = "seg")
                    Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(g.hairline)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(k).background(g.accent))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("EDITROVE · ${"%02d".format(page + 1)} / ${"%02d".format(slides.size)}", style = mono(11.sp, FontWeight.SemiBold, 0.16.em), color = g.accentSoft, modifier = Modifier.weight(1f))
                if (!last) Text(
                    tr(R.string.w_skip), style = sans(14.sp, FontWeight.SemiBold), color = g.textSecondary,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onDone).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val side = maxWidth >= 600.dp
                HorizontalPager(pager, Modifier.fillMaxSize(), pageSpacing = 16.dp) { i ->
                    val s = slides[i]
                    // A little parallax: the shot drifts slower than the words.
                    val off = (pager.currentPage - i) + pager.currentPageOffsetFraction
                    if (side) Row(Modifier.fillMaxSize().padding(horizontal = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(if (s.wide) 1.8f else 1.1f).fillMaxHeight().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            ShotFrame(s, Modifier.graphicsLayer { translationX = off * size.width * .25f })
                        }
                        Spacer(Modifier.width(36.dp))
                        SlideText(s, Modifier.weight(1f), center = false)
                    } else Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                            ShotFrame(s, Modifier.graphicsLayer { translationX = off * size.width * .25f })
                        }
                        SlideText(s, Modifier.fillMaxWidth(), center = true)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (page > 0) Box(
                    Modifier.size(52.dp).clip(CircleShape).background(g.chipFill).border(1.dp, g.hairline, CircleShape)
                        .clickable { scope.launch { pager.animateScrollToPage(page - 1) } },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr(R.string.onb_back), tint = g.textPrimary) }
                Spacer(Modifier.width(12.dp))
                Text(
                    tr(if (last) R.string.w_start else R.string.w_next),
                    style = sans(16.sp, FontWeight.SemiBold), color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f).widthIn(max = 460.dp).clip(CircleShape).background(g.accent)
                        .clickable { if (last) onDone() else scope.launch { pager.animateScrollToPage(page + 1) } }
                        .padding(vertical = 15.dp),
                )
            }
        }
    }
}

/** The screenshot in a device-like frame with a soft glow; the end slide gets the brand mark. */
@Composable
private fun ShotFrame(s: TourSlide, modifier: Modifier) {
    val context = LocalContext.current
    val g = Glass
    if (s.shot == null) {
        Box(
            modifier.size(160.dp).shadow(40.dp, CircleShape, ambientColor = s.glow, spotColor = s.glow).clip(CircleShape)
                .background(g.chipFill).border(1.dp, s.glow.copy(alpha = .5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(R.drawable.ic_brand), null,
                modifier = Modifier.size(110.dp).clip(CircleShape),
            )
        }
        return
    }
    val radius: Dp = if (s.wide) 22.dp else 26.dp
    val shape = RoundedCornerShape(radius)
    coil.compose.AsyncImage(
        model = FeatureTour.shotUri(context, s.shot), contentDescription = tr(s.title), contentScale = ContentScale.Fit,
        modifier = modifier
            .let { if (s.wide) it.fillMaxWidth() else it.fillMaxHeight() }
            .shadow(28.dp, shape, ambientColor = s.glow, spotColor = s.glow)
            .clip(shape).border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = .28f), Color.White.copy(alpha = .06f))), shape),
    )
}

@Composable
private fun SlideText(s: TourSlide, modifier: Modifier, center: Boolean) {
    val g = Glass
    val align = if (center) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start
    Column(modifier.padding(top = 12.dp), horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        Text(tr(s.kicker).uppercase(), style = mono(11.sp, FontWeight.SemiBold, 0.18.em), color = s.glow)
        Spacer(Modifier.height(6.dp))
        Text(tr(s.title), style = serif(if (center) 26.sp else 34.sp, FontWeight.Bold, lineHeight = if (center) 30.sp else 38.sp), color = g.textPrimary, textAlign = align)
        Spacer(Modifier.height(8.dp))
        Text(tr(s.body), style = sans(14.5.sp, lineHeight = 21.sp), color = g.textSecondary, textAlign = align, modifier = Modifier.widthIn(max = 480.dp))
        s.where?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                tr(it), style = mono(11.sp, FontWeight.SemiBold, 0.04.em), color = g.textPrimary,
                modifier = Modifier.clip(CircleShape).background(s.glow.copy(alpha = .16f)).border(1.dp, s.glow.copy(alpha = .45f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}
