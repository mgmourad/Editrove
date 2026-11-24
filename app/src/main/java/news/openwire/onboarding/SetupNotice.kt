// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import news.openwire.R
import news.openwire.domain.model.BriefingSlot
import news.openwire.domain.onboarding.OnboardingState
import news.openwire.i18n.tr
import news.openwire.ui.home.InnerRadius
import news.openwire.ui.home.sans
import news.openwire.ui.theme.Amber400
import news.openwire.ui.theme.Glass

/** The line shown on the front page after setup when a briefing time was picked. Tapping it plays the briefing. */
object SetupNotice {
    /** "Morning briefing is set · Anchor · 7:00 AM" (the earliest chosen time), or null with no time. */
    fun text(st: OnboardingState): String? {
        val slot = BriefingSlot.DEFAULTS.filter { it.kind in st.choices.slots }.minByOrNull { it.minuteOfDay } ?: return null
        return tr(R.string.onb_notice, slotShort(slot.kind), presenterName(st.choices.presenter), slotTime(slot))
    }
}

@Composable
fun SetupNotice(text: String?, onPlay: () -> Unit, onDismiss: () -> Unit) {
    LaunchedEffect(text) { if (text != null) { delay(12_000); onDismiss() } }
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp).padding(bottom = 84.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(text != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
            val g = Glass
            Row(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().clip(RoundedCornerShape(InnerRadius)).background(g.glassSurface)
                    .border(1.dp, Amber400.copy(alpha = .45f), RoundedCornerShape(InnerRadius))
                    .clickable(onClick = onPlay).padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Amber400.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Amber400, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(text.orEmpty(), style = sans(14.sp, FontWeight.SemiBold), color = g.textPrimary)
                    Text(tr(R.string.onb_notice_play), style = sans(12.sp), color = g.textSecondary)
                }
                Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, tr(R.string.onb_dismiss), tint = g.textSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
