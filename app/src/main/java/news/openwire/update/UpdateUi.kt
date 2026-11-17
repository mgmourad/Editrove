// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.update

import news.openwire.i18n.tr
import news.openwire.R
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import news.openwire.domain.engine.Releases
import news.openwire.ui.home.GlassCard
import news.openwire.ui.home.glassSurface
import news.openwire.ui.home.glowShadow
import news.openwire.ui.home.mono
import news.openwire.ui.home.sans
import news.openwire.ui.home.serif
import news.openwire.ui.home.springTween
import news.openwire.ui.theme.Glass

/**
 * In-app notification that a newer Editrove is out (or downloading). Slides down
 * under the status bar; tap to open Updates, × to dismiss this version.
 */
@Composable
fun UpdateBanner(manager: UpdateManager, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val g = Glass
    val state by manager.state.collectAsState()
    val release = when (val s = state) {
        is UpdateState.Available -> s.release.takeIf { !manager.isDismissed(it) }
        is UpdateState.Downloading -> s.release
        is UpdateState.Installing -> s.release
        else -> null
    }
    AnimatedVisibility(
        release != null,
        enter = slideInVertically(springTween(460)) { -it } + fadeIn(springTween(300)),
        exit = slideOutVertically(springTween(360)) { -it } + fadeOut(),
        modifier = modifier,
    ) {
        val r = release ?: return@AnimatedVisibility
        Row(
            Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 560.dp).fillMaxWidth()
                .glassSurface(CircleShape, fill = g.deviceBg.copy(alpha = 0.96f), elevation = 20.dp)
                .clickable(onClick = onOpen)
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.glowShadow(g.accent).size(36.dp).clip(CircleShape).background(g.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.SystemUpdate, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (state) {
                        is UpdateState.Downloading -> tr(R.string.upd_downloading, r.version)
                        is UpdateState.Installing -> tr(R.string.upd_installing, r.version)
                        else -> tr(R.string.upd_available_banner, r.version)
                    },
                    style = sans(13.sp, FontWeight.SemiBold), color = g.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                when (val s = state) {
                    is UpdateState.Downloading -> LinearProgressIndicator(
                        progress = { s.progress }, color = g.accent, trackColor = g.chipFill,
                        modifier = Modifier.padding(top = 6.dp, end = 8.dp).fillMaxWidth().height(3.dp).clip(CircleShape),
                    )
                    else -> Text(tr(R.string.upd_tap), style = sans(11.sp), color = g.textSecondary, maxLines = 1)
                }
            }
            if (state is UpdateState.Available) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).clickable { manager.dismiss(r); manager.check() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, tr(R.string.dismiss), tint = g.textSecondary, modifier = Modifier.size(16.dp)) }
            }
        }
    }
}

/** "What's new" sheet shown once after the app updates. */
@Composable
fun WhatsNewDialog(entry: Changelog.Entry, onDismiss: () -> Unit) {
    val g = Glass
    BackHandler(onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(
            modifier = Modifier.padding(24.dp).widthIn(max = 560.dp).fillMaxWidth()
                .background(g.deviceBg.copy(alpha = 0.94f), androidx.compose.foundation.shape.RoundedCornerShape(news.openwire.ui.home.BentoRadius))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            contentPadding = PaddingValues(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = g.accentSoft, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr(R.string.whats_new), style = mono(10.sp, FontWeight.SemiBold, 0.16.em), color = g.accentSoft)
            }
            Spacer(Modifier.height(6.dp))
            Text(entry.title, style = serif(24.sp, FontWeight.Bold, lineHeight = 28.sp), color = g.textPrimary)
            Spacer(Modifier.height(14.dp))
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                entry.items.forEach { ChangeItem(it) }
            }
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.fillMaxWidth().clip(CircleShape).background(g.accent).clickable(onClick = onDismiss).padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) { Text(tr(R.string.got_it), style = sans(13.sp, FontWeight.SemiBold), color = Color.White) }
        }
    }
}

@Composable
private fun ChangeItem(text: String) {
    val g = Glass
    Row {
        Box(Modifier.padding(top = 7.dp).size(5.dp).clip(CircleShape).background(g.accent))
        Spacer(Modifier.width(10.dp))
        Text(text, style = sans(13.sp, lineHeight = 20.sp), color = g.textPrimary.copy(alpha = 0.9f))
    }
}

/** Settings › Updates & changelog. */
@Composable
fun UpdatesSettings(
    manager: UpdateManager,
    changelog: Changelog,
    autoCheck: Boolean,
    autoInstall: Boolean,
    onAutoCheck: (Boolean) -> Unit,
    onAutoInstall: (Boolean) -> Unit,
    switch: @Composable (Boolean, (Boolean) -> Unit) -> Unit,
) {
    val g = Glass
    val context = LocalContext.current
    val state by manager.state.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
            Text("Editrove ${manager.currentVersion}", style = serif(24.sp, FontWeight.Bold), color = g.textPrimary)
            Spacer(Modifier.height(4.dp))
            val checked = manager.lastChecked.takeIf { it > 0 }?.let { tr(R.string.upd_last_checked, news.openwire.domain.engine.NewsProcessor.formatRelativeTime(it)) } ?: tr(R.string.upd_not_checked)
            Text(
                when (val s = state) {
                    UpdateState.Checking -> tr(R.string.upd_checking)
                    is UpdateState.UpToDate -> tr(R.string.upd_uptodate, checked)
                    is UpdateState.Available -> tr(R.string.upd_version_available, s.release.version)
                    is UpdateState.Downloading -> tr(R.string.upd_downloading_pct, s.release.version, (s.progress * 100).toInt())
                    is UpdateState.Installing -> tr(R.string.upd_installing_v, s.release.version)
                    is UpdateState.NeedsPermission -> tr(R.string.upd_needs_permission)
                    is UpdateState.Failed -> s.message
                    UpdateState.Idle -> checked
                },
                style = sans(13.sp, lineHeight = 19.sp), color = if (state is UpdateState.Failed) g.accentSoft else g.textSecondary,
            )
            (state as? UpdateState.Downloading)?.let { s ->
                LinearProgressIndicator(progress = { s.progress }, color = g.accent, trackColor = g.chipFill, modifier = Modifier.padding(top = 10.dp).fillMaxWidth().height(4.dp).clip(CircleShape))
            }
            Spacer(Modifier.height(14.dp))
            val release = when (val s = state) {
                is UpdateState.Available -> s.release
                is UpdateState.NeedsPermission -> s.release
                is UpdateState.Failed -> s.release
                else -> null
            }
            when {
                state is UpdateState.NeedsPermission && !manager.canRequestInstalls() ->
                    Pill(tr(R.string.upd_allow), primary = true) { context.startActivity(manager.permissionIntent()) }
                state is UpdateState.NeedsPermission ->
                    Pill(tr(R.string.upd_continue), primary = true) { (state as UpdateState.NeedsPermission).release.let(manager::install) }
                release != null && manager.canInstall -> Pill(tr(R.string.upd_download_install, release.version), primary = true) { manager.install(release) }
                else -> Pill(tr(if (state is UpdateState.Checking) R.string.checking else R.string.upd_check), primary = false) { manager.check() }
            }
            release?.takeIf { it.notes.isNotBlank() }?.let { r ->
                Spacer(Modifier.height(16.dp))
                Text(tr(R.string.upd_in, r.version), style = mono(10.sp, FontWeight.SemiBold, 0.14.em), color = g.textSecondary)
                Spacer(Modifier.height(6.dp))
                Text(Releases.plainNotes(r.notes).take(1800), style = sans(12.5.sp, lineHeight = 19.sp), color = g.textPrimary.copy(alpha = 0.9f))
            }
        }
        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
            SwitchLine(tr(R.string.upd_auto_check), tr(R.string.upd_auto_check_sub), autoCheck, onAutoCheck, switch)
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(g.hairline))
            SwitchLine(
                tr(R.string.upd_auto_install),
                tr(R.string.upd_auto_install_sub),
                autoInstall, onAutoInstall, switch,
            )
        }
        Text(tr(R.string.changelog), style = mono(10.sp, FontWeight.SemiBold, 0.14.em), color = g.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        changelog.entries.forEach { e ->
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
                Text(e.title, style = serif(18.sp, FontWeight.Bold, lineHeight = 22.sp), color = g.textPrimary)
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { e.items.forEach { ChangeItem(it) } }
            }
        }
    }
}

@Composable
private fun SwitchLine(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit, switch: @Composable (Boolean, (Boolean) -> Unit) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = sans(14.sp, FontWeight.Medium), color = Glass.textPrimary)
            Text(body, style = sans(11.sp, lineHeight = 16.sp), color = Glass.textSecondary)
        }
        switch(checked, onChange)
    }
}

@Composable
private fun Pill(text: String, primary: Boolean, onClick: () -> Unit) {
    val g = Glass
    Box(
        Modifier.fillMaxWidth().clip(CircleShape)
            .background(if (primary) g.accent else g.chipFill)
            .border(1.dp, if (primary) Color.Transparent else g.hairline, CircleShape)
            .clickable(onClick = onClick).padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = sans(13.sp, FontWeight.SemiBold), color = if (primary) Color.White else g.textPrimary) }
}
