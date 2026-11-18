// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.guide

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import news.openwire.ui.theme.Glass

/** One part of the user guide: a screenshot (by name) and its explanation. */
data class GuideSection(val shot: String?, @StringRes val title: Int, @StringRes val body: Int)

object Guide {
    val SECTIONS = listOf(
        GuideSection("today", R.string.g_today_t, R.string.g_today_b),
        GuideSection("reader", R.string.g_reader_t, R.string.g_reader_b),
        GuideSection(null, R.string.g_paywall_t, R.string.g_paywall_b),
        GuideSection("discover", R.string.g_discover_t, R.string.g_discover_b),
        GuideSection("audio", R.string.g_audio_t, R.string.g_audio_b),
        GuideSection("live_tv", R.string.g_tv_t, R.string.g_tv_b),
        GuideSection("live_radio", R.string.g_radio_t, R.string.g_radio_b),
        GuideSection("opinion", R.string.g_opinion_t, R.string.g_opinion_b),
        GuideSection("saved", R.string.g_saved_t, R.string.g_saved_b),
        GuideSection("edition", R.string.g_edition_t, R.string.g_edition_b),
        GuideSection("language", R.string.g_language_t, R.string.g_language_b),
        GuideSection("sources", R.string.g_sources_t, R.string.g_sources_b),
        GuideSection("notifications", R.string.g_notify_t, R.string.g_notify_b),
        GuideSection("appearance", R.string.g_appearance_t, R.string.g_appearance_b),
        GuideSection(null, R.string.g_widgets_t, R.string.g_widgets_b),
        GuideSection(null, R.string.g_privacy_t, R.string.g_privacy_b),
    )

    /** The welcome tour: the five things worth knowing first. */
    val TOUR = listOf(
        GuideSection("today", R.string.w1_t, R.string.w1_b),
        GuideSection("reader", R.string.w2_t, R.string.w2_b),
        GuideSection("audio", R.string.w3_t, R.string.w3_b),
        GuideSection("live_tv", R.string.w4_t, R.string.w4_b),
        GuideSection("sources", R.string.w5_t, R.string.w5_b),
    )

    /** A screenshot in the app's language (English when a language has none). */
    fun shotUri(context: Context, shot: String, lang: String): String {
        val l = Languages.of(lang)?.code ?: "en"
        val has = runCatching { context.assets.list("guide/$l")?.contains("$shot.webp") == true }.getOrDefault(false)
        return "file:///android_asset/guide/${if (has) l else "en"}/$shot.webp"
    }

    private const val PREFS = "welcome"

    /** The tour shows once, on a fresh install (people updating already know the app). */
    fun shouldWelcome(context: Context): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.contains("seen")) return !p.getBoolean("seen", true)
        @Suppress("DEPRECATION")
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val fresh = info != null && info.firstInstallTime == info.lastUpdateTime
        p.edit().putBoolean("seen", !fresh).apply()
        return fresh
    }

    /** Whether the tour is on screen (first launch, or replayed from the guide). */
    var showing by androidx.compose.runtime.mutableStateOf(false)

    fun markWelcomed(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("seen", true).apply()
}

@Composable
private fun Shot(shot: String, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.FillWidth) {
    val context = LocalContext.current
    val lang = Languages.effective(null)
    coil.compose.AsyncImage(
        model = Guide.shotUri(context, shot, lang), contentDescription = null, contentScale = scale,
        modifier = modifier.clip(RoundedCornerShape(20.dp)).border(1.dp, Glass.hairline, RoundedCornerShape(20.dp)),
    )
}

/** Settings › User guide: every feature, with screenshots, in the app's language. */
@Composable
fun GuidePage(onReplayTour: () -> Unit) {
    val g = Glass
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(tr(R.string.g_intro), style = sans(13.sp, lineHeight = 20.sp), color = g.textSecondary, modifier = Modifier.padding(horizontal = 4.dp))
        Text(
            tr(R.string.g_replay), style = sans(13.sp, FontWeight.SemiBold), color = Color.White,
            modifier = Modifier.clip(CircleShape).background(g.accent).clickable(onClick = onReplayTour).padding(horizontal = 18.dp, vertical = 10.dp),
        )
        Guide.SECTIONS.forEachIndexed { i, s ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(g.chipFill)
                    .border(1.dp, g.hairline, RoundedCornerShape(22.dp)).padding(18.dp),
            ) {
                Text("%02d".format(i + 1), style = mono(11.sp, FontWeight.SemiBold, 0.14.em), color = g.accentSoft)
                Spacer(Modifier.height(4.dp))
                Text(tr(s.title), style = serif(22.sp, FontWeight.Bold, lineHeight = 27.sp), color = g.textPrimary)
                Spacer(Modifier.height(8.dp))
                Text(tr(s.body), style = sans(14.sp, lineHeight = 22.sp), color = g.textPrimary.copy(alpha = 0.9f))
                s.shot?.let {
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Shot(it, Modifier.widthIn(max = 300.dp).fillMaxWidth()) }
                }
            }
        }
    }
}

/** The first-launch tour: five slides with screenshots; Skip, Next, Get started. */
@Composable
fun WelcomeTour(onDone: () -> Unit) {
    val g = Glass
    val pager = rememberPagerState { Guide.TOUR.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == Guide.TOUR.lastIndex
    BackHandler(onBack = onDone)
    Box(Modifier.fillMaxSize().background(g.deviceBg).clickable(enabled = false) {}) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("EDITROVE", style = mono(11.sp, FontWeight.SemiBold, 0.2.em), color = g.accentSoft, modifier = Modifier.weight(1f))
                if (!last) Text(
                    tr(R.string.w_skip), style = sans(14.sp, FontWeight.SemiBold), color = g.textSecondary,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onDone).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i ->
                val s = Guide.TOUR[i]
                Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        // The whole screen, in its own shape (phone screenshots are about 9:20).
                        s.shot?.let { Shot(it, Modifier.fillMaxHeight().aspectRatio(0.448f), ContentScale.Fit) }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(tr(s.title), style = serif(30.sp, FontWeight.Bold, lineHeight = 34.sp), color = g.textPrimary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text(tr(s.body), style = sans(15.sp, lineHeight = 22.sp), color = g.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 460.dp))
                    Spacer(Modifier.height(10.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center) {
                repeat(Guide.TOUR.size) { i ->
                    val on = i == pager.currentPage
                    val w by animateDpAsState(if (on) 22.dp else 8.dp, label = "dot")
                    val c by animateColorAsState(if (on) g.accent else g.hairline, label = "dotc")
                    Box(Modifier.padding(horizontal = 4.dp).height(8.dp).width(w).clip(CircleShape).background(c))
                }
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(
                    tr(if (last) R.string.w_start else R.string.w_next),
                    style = sans(16.sp, FontWeight.SemiBold), color = Color.White, textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().clip(CircleShape).background(g.accent)
                        .clickable { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
                        .padding(vertical = 15.dp),
                )
            }
        }
    }
}
