// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.LocalDate
import news.openwire.data.local.ThemeMode
import news.openwire.domain.engine.NewsProcessor
import news.openwire.domain.engine.SourceLibrary
import news.openwire.domain.engine.TopicRegistry
import news.openwire.domain.model.FeedSource
import news.openwire.domain.model.SortMode
import news.openwire.domain.model.Strategy
import news.openwire.ui.home.GlassCard
import news.openwire.ui.home.SourceLogo
import news.openwire.ui.home.glassSurface
import news.openwire.ui.home.mono
import news.openwire.ui.home.sans
import news.openwire.ui.home.serif
import news.openwire.ui.home.springTween
import news.openwire.ui.theme.Amber400
import news.openwire.ui.theme.Emerald400
import news.openwire.ui.theme.Glass

private enum class Page(val title: String) {
    HOME("Settings"), EDITION("Edition"), TOPICS("Topics"), ADD_TOPIC("New topic"), SOURCES("Sources"),
    ADD_SOURCE("Add a source"), RULES("Paywall rules"), NOTIFICATIONS("Notifications & refresh"),
    APPEARANCE("Appearance"), BACKUP("Backup & restore"), ABOUT("About OpenWire"),
}

/**
 * Personalization hub (SPEC §5): edition, topics, sources, paywall rules,
 * notifications, appearance, backup — all stored on this device only.
 */
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    themeMode: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
    onOpenLink: () -> Unit,
    onClose: () -> Unit,
) {
    val g = Glass
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    val back: () -> Unit = {
        page = when (page) {
            Page.HOME -> { onClose(); Page.HOME }
            Page.ADD_TOPIC -> Page.TOPICS
            Page.ADD_SOURCE -> Page.SOURCES
            else -> Page.HOME
        }
    }
    BackHandler(onBack = back)

    val message by vm.message.collectAsState()
    LaunchedEffect(message) {
        message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); vm.consumeMessage() }
    }

    Column(Modifier.fillMaxSize().background(g.deviceBg)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIcon(if (page == Page.HOME) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack, if (page == Page.HOME) "Close settings" else "Back", back)
            Spacer(Modifier.width(12.dp))
            AnimatedContent(page.title, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "title") {
                Text(it, style = serif(24.sp, FontWeight.Bold), color = g.textPrimary)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(g.hairline))
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(springTween(380)) { if (forward) it / 4 else -it / 4 } + fadeIn(springTween(300)))
                    .togetherWith(slideOutHorizontally(springTween(300)) { if (forward) -it / 6 else it / 6 } + fadeOut(tween(150)))
            },
            label = "settingsPage",
            modifier = Modifier.fillMaxSize(),
        ) { p ->
            val pad = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 48.dp)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    Modifier.widthIn(max = 720.dp).fillMaxSize().navigationBarsPadding(),
                    contentPadding = pad,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (p) {
                        Page.HOME -> homePage(vm, themeMode, onOpenLink) { page = it }
                        Page.EDITION -> item { EditionPage(vm) }
                        Page.TOPICS -> topicsPage(vm) { page = Page.ADD_TOPIC }
                        Page.ADD_TOPIC -> item { AddTopicPage(vm) { page = Page.TOPICS } }
                        Page.SOURCES -> sourcesPage(vm) { page = Page.ADD_SOURCE }
                        Page.ADD_SOURCE -> item { AddSourcePage(vm) { page = Page.SOURCES } }
                        Page.RULES -> rulesPage(vm)
                        Page.NOTIFICATIONS -> item { NotificationsPage(vm) }
                        Page.APPEARANCE -> item { AppearancePage(vm, themeMode, onTheme) }
                        Page.BACKUP -> item { BackupPage(vm) }
                        Page.ABOUT -> item { AboutPage() }
                    }
                }
            }
        }
    }
}

// ---- Home ---------------------------------------------------------------------------

private fun LazyListScope.homePage(vm: SettingsViewModel, themeMode: ThemeMode, onOpenLink: () -> Unit, go: (Page) -> Unit) {
    item {
        val s by vm.settings.collectAsState()
        val topics by vm.allTopics.collectAsState()
        val library by vm.library.collectAsState()
        val rules by vm.rules.collectAsState()
        Section {
            NavRow(Icons.Outlined.LocationOn, "Edition", s.edition.displayName + " · " + TopicRegistry.country(s.edition.country).name) { go(Page.EDITION) }
            Divider()
            NavRow(Icons.Outlined.ViewList, "Topics", "${topics.count { it.isVisible }} shown · ${s.customTopics.size} custom") { go(Page.TOPICS) }
            Divider()
            NavRow(Icons.Outlined.RssFeed, "Sources", "${library.count { it.enabled }} of ${library.size} enabled") { go(Page.SOURCES) }
            Divider()
            NavRow(Icons.Outlined.Lock, "Paywall rules", "${rules.size} publishers · ${rules.values.count { it.isCustom }} customized") { go(Page.RULES) }
        }
    }
    item {
        val s by vm.settings.collectAsState()
        Section {
            NavRow(Icons.Outlined.Notifications, "Notifications & refresh", if (s.notificationsEnabled) "On for ${s.notifyTopics.size} topics" else "Off") { go(Page.NOTIFICATIONS) }
            Divider()
            NavRow(Icons.Outlined.Palette, "Appearance", "${themeLabel(themeMode)} · ${s.sortMode.label} order") { go(Page.APPEARANCE) }
        }
    }
    item {
        Section {
            NavRow(Icons.Outlined.Link, "Open a link", "Paste any article URL — routed around its paywall") { onOpenLink() }
            Divider()
            NavRow(Icons.Outlined.Save, "Backup & restore", "Export, import (incl. the OpenWire web app)") { go(Page.BACKUP) }
            Divider()
            NavRow(Icons.Outlined.Info, "About OpenWire", "Open source · no ads · no tracking") { go(Page.ABOUT) }
        }
    }
}

private fun themeLabel(m: ThemeMode) = when (m) { ThemeMode.SYSTEM -> "System"; ThemeMode.DARK -> "Black Glass"; ThemeMode.LIGHT -> "Editorial Paper" }

// ---- Edition --------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditionPage(vm: SettingsViewModel) {
    val s by vm.settings.collectAsState()
    var city by rememberSaveable { mutableStateOf(s.edition.city) }
    var region by rememberSaveable { mutableStateOf(s.edition.region) }
    var country by rememberSaveable { mutableStateOf(s.edition.country) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hint("Your edition sets the national topic and your local topic. Other places use live Google News in the right language and region.")
        Section {
            Label("Quick picks")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("Washington, D.C.", "District of Columbia", "US"), Triple("New York", "New York", "US"),
                    Triple("Seattle", "Washington", "US"), Triple("San Francisco", "California", "US"),
                    Triple("London", "England", "GB"), Triple("Toronto", "Ontario", "CA"), Triple("Vancouver", "British Columbia", "CA"),
                    Triple("Sydney", "New South Wales", "AU"), Triple("Dublin", "Leinster", "IE"), Triple("Dubai", "Dubai", "AE"),
                ).forEach { (c, r, k) ->
                    Chip(c, selected = city == c && country == k) { city = c; region = r; country = k }
                }
            }
        }
        Section {
            Field("City", city, { city = it })
            Spacer(Modifier.height(10.dp))
            Field("Region / state / province", region, { region = it })
            Spacer(Modifier.height(14.dp))
            Label("Country (news language & region)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TopicRegistry.COUNTRIES.forEach { c -> Chip(c.name, selected = country == c.code) { country = c.code } }
            }
        }
        PrimaryButton("Use this edition") { vm.setEdition(city, region, country) }
    }
}

// ---- Topics -----------------------------------------------------------------------------

private fun LazyListScope.topicsPage(vm: SettingsViewModel, onAdd: () -> Unit) {
    item { Hint("Reorder with the arrows, hide topics you don't read, and tap the bell to get a local notification when new stories land.") }
    item { PrimaryButton("New topic from keywords or sources", Icons.Filled.Add, onAdd) }
    item {
        val topics by vm.allTopics.collectAsState()
        val s by vm.settings.collectAsState()
        Section {
            topics.forEachIndexed { i, t ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.label, style = sans(14.sp, FontWeight.SemiBold), color = if (t.isVisible) Glass.textPrimary else Glass.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when {
                                t.isCustom && t.keywords.isNotEmpty() -> "Keywords: " + t.keywords.joinToString()
                                t.isCustom -> "${t.sources.size} sources"
                                t.liveOnly -> "Live · Google News"
                                else -> "Published snapshot"
                            },
                            style = sans(11.sp), color = Glass.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    SmallIcon(Icons.Filled.KeyboardArrowUp, "Move up", enabled = i > 0) { vm.moveTopic(t.id, up = true) }
                    SmallIcon(Icons.Filled.KeyboardArrowDown, "Move down", enabled = i < topics.lastIndex) { vm.moveTopic(t.id, up = false) }
                    val notify = t.id in s.notifyTopics
                    SmallIcon(
                        if (notify) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsNone,
                        if (notify) "Stop notifications" else "Notify me", tint = if (notify) Glass.accent else null,
                    ) { vm.setNotifyTopic(t.id, !notify) }
                    if (t.isCustom) SmallIcon(Icons.Outlined.Delete, "Delete topic") { vm.deleteTopic(t.id) }
                    GlassSwitch(t.isVisible) { vm.setTopicVisible(t.id, it) }
                }
            }
        }
    }
    item { TextButton("Reset topics & edition to defaults") { vm.resetTopics() } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddTopicPage(vm: SettingsViewModel, onDone: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var keywords by rememberSaveable { mutableStateOf("") }
    val chosen = remember { mutableStateListOf<String>() }
    var filter by rememberSaveable { mutableStateOf("") }
    val library by vm.library.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hint("Keywords search Google News and also filter your chosen sources by headline and excerpt. Pick sources only to build a topic from specific publishers.")
        Section {
            Label("Suggestions")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TopicRegistry.PRESET_SUGGESTIONS.forEach { (label, kw) -> Chip(label, selected = name == label) { name = label; keywords = kw } }
            }
        }
        Section {
            Field("Topic name", name, { name = it })
            Spacer(Modifier.height(10.dp))
            Field("Keywords (comma-separated)", keywords, { keywords = it }, placeholder = "e.g. Formula 1, F1, Grand Prix")
        }
        Section {
            Label("Sources (optional) · ${chosen.size} selected")
            Field("Filter sources", filter, { filter = it })
            Spacer(Modifier.height(6.dp))
            library.filter { !it.isPrimary && (filter.isBlank() || it.name.contains(filter, true) || it.domain.contains(filter, true)) }
                .take(if (filter.isBlank()) 40 else 80)
                .forEach { src ->
                    val on = src.id in chosen
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { if (on) chosen.remove(src.id) else chosen.add(src.id) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SourceLogo(src.domain, src.name, size = 20.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(src.name, style = sans(13.sp), color = Glass.textPrimary, modifier = Modifier.weight(1f))
                        CheckDot(on)
                    }
                }
        }
        PrimaryButton("Create topic") { if (vm.addTopic(name, keywords, chosen.toList())) onDone() }
    }
}

// ---- Sources ------------------------------------------------------------------------------

private fun LazyListScope.sourcesPage(vm: SettingsViewModel, onAdd: () -> Unit) {
    item { Hint("Direct publisher feeds are woven into topics with a fair share each. Switch off anything you'd rather not read.") }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { PrimaryButton("Add a source", Icons.Filled.Add, onAdd) }
            Box(Modifier.weight(1f)) { SecondaryButton("Check enabled feeds") { vm.checkEnabledFeeds() } }
        }
    }
    item {
        var filter by rememberSaveable { mutableStateOf("") }
        val library by vm.library.collectAsState()
        val checks by vm.checks.collectAsState()
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field("Search sources", filter, { filter = it })
            SourceLibrary.groupSources(library, filter).forEach { group ->
                Section {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(group.category.uppercase(), style = mono(11.sp, FontWeight.SemiBold, 0.14.em), color = Glass.accentSoft, modifier = Modifier.weight(1f))
                        Text("${group.all.count { it.enabled }}/${group.all.size}", style = mono(11.sp), color = Glass.textSecondary)
                        Spacer(Modifier.width(8.dp))
                        GlassSwitch(group.all.all { it.enabled }) { vm.setGroupEnabled(group.all, it) }
                    }
                    listOf("News aggregators" to group.aggregators, "Individual news feeds" to group.publishers).forEach { (title, list) ->
                        if (list.isEmpty()) return@forEach
                        Spacer(Modifier.height(8.dp))
                        Text(title, style = sans(11.sp, FontWeight.Medium), color = Glass.textSecondary)
                        list.forEach { src -> SourceRow(src, checks[SourceLibrary.sourceKey(src)], checks.containsKey(SourceLibrary.sourceKey(src)), vm) }
                    }
                }
            }
        }
    }
    item { TextButton("Reset sources to defaults") { vm.resetSources() } }
}

@Composable
private fun SourceRow(src: FeedSource, check: news.openwire.data.repository.FeedCheck?, checking: Boolean, vm: SettingsViewModel) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        SourceLogo(src.domain, src.name, size = 22.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(src.name, style = sans(13.sp, FontWeight.Medium), color = if (src.enabled) Glass.textPrimary else Glass.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = when {
                checking && check == null -> "Checking…"
                check?.ok == true -> "✓ ${check.count} stories" + (check.newestAt?.let { " · newest ${NewsProcessor.formatRelativeTime(it)}" } ?: "") + if (check.stale) " · stale" else ""
                check != null -> "✗ ${check.error}"
                else -> listOfNotNull(src.domain.ifEmpty { null }, src.access, if (src.isCustom) "Your source" else null).joinToString(" · ")
            }
            Text(
                detail, style = sans(11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = when { check?.ok == false -> Glass.accentSoft; check?.stale == true -> Amber400; check?.ok == true -> Emerald400; else -> Glass.textSecondary },
            )
        }
        if (checking && check == null) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Glass.accent)
        if (src.isCustom) SmallIcon(Icons.Outlined.Delete, "Remove source") { vm.deleteSource(src.id) }
        GlassSwitch(src.enabled) { vm.setSourceEnabled(src, it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddSourcePage(vm: SettingsViewModel, onDone: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var quota by rememberSaveable { mutableStateOf(8) }
    var busy by remember { mutableStateOf(false) }
    val chosen = remember { mutableStateListOf<String>() }
    val topics by vm.allTopics.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hint("Paste any RSS or Atom feed. OpenWire reads it directly on your phone — no converter services.")
        Section {
            Field("Name (optional)", name, { name = it }, placeholder = "e.g. Polygon")
            Spacer(Modifier.height(10.dp))
            Field("Feed URL", url, { url = it }, placeholder = "https://www.polygon.com/rss/index.xml", keyboard = KeyboardType.Uri)
        }
        Section {
            Label("Weave into topics")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                topics.forEach { t -> Chip(t.label, selected = t.id in chosen) { if (t.id in chosen) chosen.remove(t.id) else chosen.add(t.id) } }
            }
            Spacer(Modifier.height(14.dp))
            Label("Fair share: up to $quota stories per refresh")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { (7..10).forEach { q -> Chip("$q", selected = quota == q) { quota = q } } }
        }
        PrimaryButton(if (busy) "Checking the feed…" else "Verify & add") {
            if (busy) return@PrimaryButton
            busy = true
            vm.addSource(name, url, chosen.toList(), quota) { ok -> busy = false; if (ok) onDone() }
        }
    }
}

// ---- Paywall rules -------------------------------------------------------------------------

private fun LazyListScope.rulesPage(vm: SettingsViewModel) {
    item { Hint("Each publisher is routed by a strategy. Hard paywalls use archive snapshots; if none exists the headline is greyed out and locked.") }
    item {
        var domain by rememberSaveable { mutableStateOf("") }
        var strategy by rememberSaveable { mutableStateOf(Strategy.SMRY) }
        Section {
            Label("Add or override a publisher")
            Field("Domain", domain, { domain = it }, placeholder = "e.g. economist.com", keyboard = KeyboardType.Uri)
            Spacer(Modifier.height(10.dp))
            StrategyPicker(strategy) { strategy = it }
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Save rule") { vm.setRule(domain, strategy); domain = "" }
        }
    }
    item {
        var filter by rememberSaveable { mutableStateOf("") }
        var editing by remember { mutableStateOf<String?>(null) }
        val rules by vm.rules.collectAsState()
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field("Search publishers", filter, { filter = it })
            Section {
                val list = rules.values.filter { filter.isBlank() || it.domain.contains(filter, true) || it.label.orEmpty().contains(filter, true) }
                    .sortedWith(compareByDescending<news.openwire.domain.model.DomainRule> { it.isCustom }.thenBy { it.label ?: it.domain })
                list.forEachIndexed { i, r ->
                    if (i > 0) Divider()
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { editing = if (editing == r.domain) null else r.domain }.padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SourceLogo(r.domain, r.label, size = 20.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.label?.takeIf { it.isNotBlank() } ?: r.domain, style = sans(13.sp, FontWeight.Medium), color = Glass.textPrimary, maxLines = 1)
                                Text(r.domain + (r.paywall?.let { " · $it paywall" } ?: ""), style = sans(11.sp), color = Glass.textSecondary, maxLines = 1)
                            }
                            StrategyBadge(r.strategy, r.isCustom)
                        }
                        AnimatedVisibility(editing == r.domain, enter = expandVertically(springTween(300)) + fadeIn(), exit = shrinkVertically(springTween(250)) + fadeOut()) {
                            Column(Modifier.padding(top = 10.dp)) {
                                StrategyPicker(r.strategy) { vm.setRule(r.domain, it) }
                                if (r.isCustom) { Spacer(Modifier.height(8.dp)); TextButton("Reset to default") { vm.resetRule(r.domain) } }
                            }
                        }
                    }
                }
            }
        }
    }
    item { TextButton("Reset all paywall rules") { vm.resetAllRules() } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StrategyPicker(current: Strategy, onPick: (Strategy) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Strategy.entries.forEach { s -> Chip(strategyLabel(s), selected = s == current) { onPick(s) } }
    }
}

private fun strategyLabel(s: Strategy) = when (s) {
    Strategy.DIRECT -> "Direct"; Strategy.SMRY -> "SMRY"; Strategy.REMOVEPAYWALL -> "RemovePaywall"; Strategy.UNWALL -> "UnWall"
    Strategy.FREEDIUM -> "Freedium"; Strategy.READER -> "Reader"; Strategy.ARCHIVE_IS -> "Archive.is"; Strategy.WAYBACK -> "Wayback"
}

@Composable
private fun StrategyBadge(s: Strategy, custom: Boolean) {
    val g = Glass
    Box(
        Modifier.clip(CircleShape).background(if (custom) g.accent.copy(alpha = 0.15f) else g.chipFill)
            .border(1.dp, if (custom) g.accent.copy(alpha = 0.3f) else g.hairline, CircleShape).padding(horizontal = 10.dp, vertical = 3.dp),
    ) { Text(strategyLabel(s), style = mono(10.sp, FontWeight.SemiBold), color = if (custom) g.accentSoft else g.textSecondary) }
}

// ---- Notifications --------------------------------------------------------------------------

@Composable
private fun NotificationsPage(vm: SettingsViewModel) {
    val context = LocalContext.current
    val s by vm.settings.collectAsState()
    val topics by vm.allTopics.collectAsState()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setNotificationsEnabled(granted)
        if (!granted) Toast.makeText(context, "Notifications are blocked for OpenWire in system settings.", Toast.LENGTH_LONG).show()
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hint("Notifications are generated on your phone — OpenWire has no server and no push. Every topic is off until you opt in.")
        Section {
            SwitchRow("Notifications", "Master switch for new-headline alerts", s.notificationsEnabled) { on ->
                if (on && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.setNotificationsEnabled(on)
            }
            Divider()
            SwitchRow("Background refresh", "Refresh feeds about once an hour on Wi-Fi or data, so the app opens fresh", s.backgroundRefresh) { vm.setBackgroundRefresh(it) }
        }
        Section {
            Label("Notify me about")
            topics.filter { it.isVisible }.forEachIndexed { i, t ->
                if (i > 0) Divider()
                SwitchRow(t.label, null, t.id in s.notifyTopics, enabled = s.notificationsEnabled) { vm.setNotifyTopic(t.id, it) }
            }
        }
    }
}

// ---- Appearance --------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearancePage(vm: SettingsViewModel, themeMode: ThemeMode, onTheme: (ThemeMode) -> Unit) {
    val s by vm.settings.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Section {
            Label("Theme")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ThemeMode.entries.forEach { m -> Chip(themeLabel(m), selected = m == themeMode) { onTheme(m) } } }
        }
        Section {
            Label("Feed order")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortMode.entries.forEach { m -> Chip(m.label, selected = m == s.sortMode) { vm.setSortMode(m) } }
            }
            Spacer(Modifier.height(8.dp))
            Text("Curated weaves aggregator and publisher feeds fairly; Newest and Oldest sort by time.", style = sans(11.sp, lineHeight = 16.sp), color = Glass.textSecondary)
        }
    }
}

// ---- Backup ---------------------------------------------------------------------------------------

@Composable
private fun BackupPage(vm: SettingsViewModel) {
    val context = LocalContext.current
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportJson().toByteArray()) } }
            .onSuccess { Toast.makeText(context, "Settings exported.", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, "Couldn't save the file.", Toast.LENGTH_SHORT).show() }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
        if (text == null) Toast.makeText(context, "Couldn't read the file.", Toast.LENGTH_SHORT).show() else vm.importJson(text)
    }
    var confirmReset by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hint("One JSON file with your topics, edition, sources and paywall rules. It also imports backups exported from the OpenWire web app.")
        PrimaryButton("Export settings") { exporter.launch("openwire_settings_${LocalDate.now()}.json") }
        SecondaryButton("Import settings") { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }
        Spacer(Modifier.height(8.dp))
        if (!confirmReset) TextButton("Reset everything to defaults") { confirmReset = true }
        else Section {
            Text("Reset topics, edition, sources, paywall rules and preferences? Saved stories are kept.", style = sans(13.sp, lineHeight = 19.sp), color = Glass.textPrimary)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { SecondaryButton("Cancel") { confirmReset = false } }
                Box(Modifier.weight(1f)) { PrimaryButton("Reset") { vm.resetEverything(); confirmReset = false } }
            }
        }
    }
}

// ---- About -----------------------------------------------------------------------------------------

@Composable
private fun AboutPage() {
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Section {
            Text("OpenWire", style = serif(28.sp, FontWeight.Bold), color = Glass.textPrimary)
            Text("Version $version", style = mono(11.sp), color = Glass.textSecondary)
            Spacer(Modifier.height(10.dp))
            Text(
                "A fast, ad-free and tracking-free news reader with a paywall-routing engine. No accounts, no analytics, no server of its own — your settings and saved stories never leave this device.",
                style = sans(13.sp, lineHeight = 20.sp), color = Glass.textPrimary,
            )
        }
        Section {
            Label("Credits")
            Text(
                "OpenWire for Android is a fork of the original OpenWire web app by Anson Lai, whose paywall engine, source catalog and feed pipeline it ports and builds on.",
                style = sans(13.sp, lineHeight = 20.sp), color = Glass.textPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text("Typefaces: Newsreader, Inter and JetBrains Mono (SIL Open Font License).", style = sans(12.sp, lineHeight = 18.sp), color = Glass.textSecondary)
        }
        Section {
            Label("License")
            Text("Free software under the GNU Affero General Public License v3.0. Source code: github.com/mgmourad/OpenWire-Android", style = sans(13.sp, lineHeight = 20.sp), color = Glass.textPrimary)
        }
    }
}

// ---- Building blocks -----------------------------------------------------------------------------

@Composable
private fun Section(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) =
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), content = content)

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(Glass.hairline))

@Composable
private fun Hint(text: String) = Text(text, style = sans(12.sp, lineHeight = 18.sp), color = Glass.textSecondary, modifier = Modifier.padding(horizontal = 4.dp))

@Composable
private fun Label(text: String) = Text(text.uppercase(), style = mono(10.sp, FontWeight.SemiBold, 0.14.em), color = Glass.textSecondary, modifier = Modifier.padding(bottom = 8.dp))

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(Glass.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Glass.accentSoft, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = sans(14.sp, FontWeight.SemiBold), color = Glass.textPrimary)
            Text(subtitle, style = sans(11.sp), color = Glass.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Glass.textMuted)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = sans(14.sp, FontWeight.Medium), color = if (enabled) Glass.textPrimary else Glass.textMuted)
            subtitle?.let { Text(it, style = sans(11.sp, lineHeight = 16.sp), color = Glass.textSecondary) }
        }
        GlassSwitch(checked, enabled, onChange)
    }
}

@Composable
private fun GlassSwitch(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val g = Glass
    Switch(
        checked = checked, onCheckedChange = onChange, enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = g.accent, checkedBorderColor = g.accent,
            uncheckedThumbColor = g.textSecondary, uncheckedTrackColor = g.chipFill, uncheckedBorderColor = g.hairline,
        ),
    )
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val g = Glass
    val bg by animateColorAsState(if (selected) g.accent else g.chipFill, springTween(260), label = "chip")
    Box(
        Modifier.clip(CircleShape).background(bg).border(1.dp, if (selected) Color.Transparent else g.hairline, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    ) { Text(label, style = sans(12.sp, if (selected) FontWeight.SemiBold else FontWeight.Medium), color = if (selected) Color.White else g.textPrimary) }
}

@Composable
private fun CheckDot(on: Boolean) {
    val g = Glass
    Box(
        Modifier.size(22.dp).clip(CircleShape).background(if (on) g.accent else Color.Transparent).border(1.5.dp, if (on) g.accent else g.textMuted, CircleShape),
        contentAlignment = Alignment.Center,
    ) { if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, placeholder: String? = null, keyboard: KeyboardType = KeyboardType.Text) {
    val g = Glass
    Column {
        Text(label, style = sans(11.sp, FontWeight.Medium), color = g.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        Box(Modifier.fillMaxWidth().glassSurface(RoundedCornerShape(16.dp), fill = g.glassPill, elevation = 0.dp).padding(horizontal = 14.dp, vertical = 12.dp)) {
            if (value.isEmpty() && placeholder != null) Text(placeholder, style = sans(14.sp), color = g.textMuted)
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = sans(14.sp).copy(color = g.textPrimary), cursorBrush = SolidColor(g.accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PrimaryButton(text: String, icon: ImageVector? = null, onClick: () -> Unit) {
    val g = Glass
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(g.accent).clickable(onClick = onClick).padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = sans(13.sp, FontWeight.SemiBold), color = Color.White, maxLines = 1)
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    val g = Glass
    Box(
        Modifier.fillMaxWidth().clip(CircleShape).background(g.chipFill).border(1.dp, g.hairline, CircleShape).clickable(onClick = onClick).padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = sans(13.sp, FontWeight.SemiBold), color = g.textPrimary, maxLines = 1) }
}

@Composable
private fun TextButton(text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(CircleShape).clickable(onClick = onClick).padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = Glass.accentSoft, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = sans(13.sp, FontWeight.Medium), color = Glass.accentSoft)
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    val g = Glass
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(g.chipFill).border(1.dp, g.hairline, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = g.textPrimary, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun SmallIcon(icon: ImageVector, description: String, enabled: Boolean = true, tint: Color? = null, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = tint ?: if (enabled) Glass.textPrimary.copy(alpha = 0.75f) else Glass.textMuted.copy(alpha = 0.4f), modifier = Modifier.size(18.dp)) }
}
