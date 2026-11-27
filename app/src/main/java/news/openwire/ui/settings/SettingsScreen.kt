// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.settings

import androidx.compose.ui.semantics.contentDescription
import news.openwire.i18n.tr
import news.openwire.i18n.text
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.NotificationsPaused
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.SystemUpdate
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.MyLocation
import kotlinx.coroutines.launch
import news.openwire.R
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

private enum class Page(@androidx.annotation.StringRes private val res: Int) {
    HOME(R.string.settings), EDITION(R.string.edition), LANGUAGE(R.string.language), TOPICS(R.string.topics), ADD_TOPIC(R.string.new_topic), EDIT_TOPIC(R.string.edit_topic), SOURCES(R.string.sources),
    ADD_SOURCE(R.string.add_source), RULES(R.string.paywall_rules), NOTIFICATIONS(R.string.notifications_refresh),
    APPEARANCE(R.string.appearance), NOTIFY_HUB(R.string.hub_title), BRIEFING(R.string.audio_briefing), BACKUP(R.string.backup_restore), UPDATES(R.string.updates_changelog), GUIDE(R.string.g_title), ABOUT(R.string.about_editrove),
    ;

    val title: String get() = tr(res)
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
    updateManager: news.openwire.update.UpdateManager? = null,
    changelog: news.openwire.update.Changelog? = null,
    startPage: String? = null,
) {
    val g = Glass
    val context = LocalContext.current
    var page by rememberSaveable {
        mutableStateOf(
            when {
                startPage == "updates" && updateManager != null -> Page.UPDATES
                startPage == "briefing" -> Page.BRIEFING
                startPage != null -> Page.entries.firstOrNull { it.name.equals(startPage, true) } ?: Page.HOME
                else -> Page.HOME
            },
        )
    }
    val back: () -> Unit = {
        page = when (page) {
            Page.HOME -> { onClose(); Page.HOME }
            Page.ADD_TOPIC, Page.EDIT_TOPIC -> Page.TOPICS
            Page.ADD_SOURCE -> Page.SOURCES
            Page.NOTIFY_HUB -> Page.NOTIFICATIONS
            else -> Page.HOME
        }
    }
    BackHandler(onBack = back)

    val message by vm.message.collectAsState()
    LaunchedEffect(message) {
        message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); vm.consumeMessage() }
    }

    val offer by vm.languageOffer.collectAsState()
    val sourcesOffer by vm.sourcesOffer.collectAsState()
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(g.deviceBg)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIcon(if (page == Page.HOME) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack, tr(if (page == Page.HOME) R.string.cd_close_settings else R.string.cd_back), back)
            Spacer(Modifier.width(12.dp))
            val editingTopic by vm.editingTopic.collectAsState()
            AnimatedContent(if (page == Page.EDIT_TOPIC) editingTopic?.label ?: page.title else page.title, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "title") {
                Text(it, style = serif(24.sp, FontWeight.Bold), color = g.textPrimary)
            }
        }
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
                        Page.LANGUAGE -> item { LanguagePage(vm) }
                        Page.TOPICS -> topicsPage(vm, onAdd = { page = Page.ADD_TOPIC }) { t -> vm.editingTopic.value = t; page = Page.EDIT_TOPIC }
                        Page.ADD_TOPIC -> item { AddTopicPage(vm) { page = Page.TOPICS } }
                        Page.EDIT_TOPIC -> item {
                            val t by vm.editingTopic.collectAsState()
                            t?.let { AddTopicPage(vm, editing = it) { page = Page.TOPICS } }
                        }
                        Page.SOURCES -> sourcesPage(vm) { page = Page.ADD_SOURCE }
                        Page.ADD_SOURCE -> item { AddSourcePage(vm) { page = Page.SOURCES } }
                        Page.RULES -> rulesPage(vm)
                        Page.NOTIFICATIONS -> item { NotificationsPage(vm) { page = Page.NOTIFY_HUB } }
                        Page.NOTIFY_HUB -> item { NotificationHub(vm) }
                        Page.APPEARANCE -> item { AppearancePage(vm, themeMode, onTheme) }
                        Page.BACKUP -> item { BackupPage(vm) }
                        Page.ABOUT -> item { AboutPage() }
                        Page.GUIDE -> item { news.openwire.guide.GuidePage(onReplayTour = { news.openwire.guide.Guide.showing = true }) }
                        Page.BRIEFING -> item {
                            BriefingSettings(vm, chip = { l, sel, on -> Chip(l, selected = sel, onClick = on) }, switch = { c, f -> GlassSwitch(c, onChange = f) })
                        }
                        Page.UPDATES -> item {
                            val s by vm.settings.collectAsState()
                            if (updateManager != null && changelog != null) news.openwire.update.UpdatesSettings(
                                manager = updateManager, changelog = changelog,
                                autoCheck = s.autoCheckUpdates, autoInstall = s.autoInstallUpdates,
                                onAutoCheck = vm::setAutoCheckUpdates, onAutoInstall = vm::setAutoInstallUpdates,
                                switch = { c, f -> GlassSwitch(c, onChange = f) },
                            )
                        }
                    }
                }
            }
        }
    }
    offer?.let { o ->
        val s by vm.settings.collectAsState()
        val activity = context.findActivity()
        LanguageOfferDialog(
            offer = o,
            current = news.openwire.domain.engine.Languages.of(news.openwire.domain.engine.Languages.effective(s.language))!!,
            onSwitch = { vm.setLanguage(o.code); activity?.let { news.openwire.i18n.AppLocale.apply(it, o.code) } },
            onKeep = vm::dismissLanguageOffer,
        )
    }
    if (offer == null) sourcesOffer?.let { LocalSourcesDialog(vm, it) }
    }
}

/**
 * After choosing an edition (and its language): the country's and region's newsrooms, to add
 * on purpose. The same list stays at the bottom of the Edition page.
 */
@Composable
private fun LocalSourcesDialog(vm: SettingsViewModel, country: String) {
    val s by vm.settings.collectAsState()
    val L = news.openwire.domain.engine.LocalSources
    val local = L.forCountry(country)
    val region = L.regionFor(country)
    val regional = region?.let { L.forRegion(it, country) }.orEmpty()
    val all = local + regional
    val added = all.count { sug -> s.customFeeds.any { it.url == sug.url } }
    GlassDialog(icon = Icons.Outlined.RssFeed, kicker = tr(R.string.sources_offer_kicker), onDismiss = vm::dismissSourcesOffer) {
        Text(tr(R.string.sources_offer_title, countryName(country)), style = serif(24.sp, FontWeight.Bold, lineHeight = 30.sp), color = Glass.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(tr(R.string.sources_offer_body), style = sans(13.sp, lineHeight = 20.sp), color = Glass.textPrimary.copy(alpha = 0.9f))
        Spacer(Modifier.height(10.dp))
        Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
            fun lang(code: String) = news.openwire.domain.engine.Languages.of(code)?.nativeName ?: code
            if (local.isNotEmpty()) Label(tr(R.string.edition_local_sources, countryName(country)))
            local.forEachIndexed { i, sug ->
                if (i > 0) Divider()
                SwitchRow(sug.name, lang(sug.language), s.customFeeds.any { it.url == sug.url }) { vm.toggleLocalSource(sug, it) }
            }
            if (regional.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Label(tr(when (region!!) {
                    news.openwire.domain.engine.LocalSources.Region.ARAB -> R.string.region_arab
                    news.openwire.domain.engine.LocalSources.Region.FRENCH -> R.string.region_french
                    news.openwire.domain.engine.LocalSources.Region.GERMAN -> R.string.region_german
                    news.openwire.domain.engine.LocalSources.Region.SPANISH -> R.string.region_spanish
                }))
                regional.forEachIndexed { i, sug ->
                    if (i > 0) Divider()
                    SwitchRow(sug.name, lang(sug.language), s.customFeeds.any { it.url == sug.url }) { vm.toggleLocalSource(sug, it, regional = true) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (added < all.size) {
            DialogButton(tr(R.string.sources_offer_add_all, all.size), primary = true) { vm.addAllLocalSources(country); vm.dismissSourcesOffer() }
            Spacer(Modifier.height(8.dp))
        }
        DialogButton(tr(if (added > 0) R.string.sources_offer_done else R.string.sources_offer_later), primary = added == all.size) { vm.dismissSourcesOffer() }
        Spacer(Modifier.height(6.dp))
        Text(tr(R.string.sources_offer_footer), style = sans(11.sp, lineHeight = 16.sp), color = Glass.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
            NavRow(Icons.Outlined.LocationOn, tr(R.string.edition), s.edition.displayName + " · " + countryName(s.edition.country)) { go(Page.EDITION) }
            Divider()
            val lang = news.openwire.domain.engine.Languages.of(news.openwire.domain.engine.Languages.effective(s.language))!!
            // Always says "Language" in English too, so the way back is findable in any language.
            NavRow(
                Icons.Outlined.Translate,
                stringResource(R.string.language).let { if (lang.code == "en") it else "$it · Language" },
                if (s.language.isEmpty()) stringResource(R.string.language_phone, lang.nativeName) else lang.nativeName,
            ) { go(Page.LANGUAGE) }
            Divider()
            NavRow(Icons.Outlined.ViewList, tr(R.string.topics), tr(R.string.topics_summary, topics.count { it.isVisible }, s.customTopics.size)) { go(Page.TOPICS) }
            Divider()
            NavRow(Icons.Outlined.RssFeed, tr(R.string.sources), tr(R.string.sources_summary, library.count { it.enabled }, library.size)) { go(Page.SOURCES) }
            Divider()
            NavRow(Icons.Outlined.Lock, tr(R.string.paywall_rules), tr(R.string.rules_summary, rules.size, rules.values.count { it.isCustom })) { go(Page.RULES) }
        }
    }
    item {
        val s by vm.settings.collectAsState()
        Section {
            NavRow(Icons.Outlined.Notifications, tr(R.string.notifications_refresh), if (s.notificationsEnabled) tr(R.string.notify_on_for, s.notifyTopics.size) else tr(R.string.off)) { go(Page.NOTIFICATIONS) }
            Divider()
            NavRow(Icons.Outlined.GraphicEq, tr(R.string.audio_briefing), tr(R.string.briefing_summary, s.briefing.detail.text, s.briefing.count, s.briefing.speed.toString())) { go(Page.BRIEFING) }
            Divider()
            NavRow(Icons.Outlined.Palette, tr(R.string.appearance), tr(R.string.appearance_summary, themeLabel(themeMode), s.sortMode.text)) { go(Page.APPEARANCE) }
        }
    }
    item {
        Section {
            NavRow(Icons.Outlined.Link, tr(R.string.open_a_link), tr(R.string.open_a_link_sub)) { onOpenLink() }
            Divider()
            NavRow(Icons.Outlined.Save, tr(R.string.backup_restore), tr(R.string.backup_sub)) { go(Page.BACKUP) }
            Divider()
            NavRow(Icons.Outlined.SystemUpdate, tr(R.string.updates_changelog), tr(R.string.updates_sub, news.openwire.BuildConfig.VERSION_NAME)) { go(Page.UPDATES) }
            Divider()
            NavRow(Icons.AutoMirrored.Outlined.MenuBook, tr(R.string.g_title), tr(R.string.g_sub)) { go(Page.GUIDE) }
            Divider()
            NavRow(Icons.Outlined.Info, tr(R.string.about_editrove), tr(R.string.about_sub)) { go(Page.ABOUT) }
        }
    }
}

private fun themeLabel(m: ThemeMode) = when (m) { ThemeMode.SYSTEM -> tr(R.string.theme_system); ThemeMode.DARK -> tr(R.string.theme_dark); ThemeMode.LIGHT -> tr(R.string.theme_light) }

// ---- Edition --------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditionPage(vm: SettingsViewModel) {
    val s by vm.settings.collectAsState()
    var city by rememberSaveable { mutableStateOf(s.edition.city) }
    var region by rememberSaveable { mutableStateOf(s.edition.region) }
    var country by rememberSaveable { mutableStateOf(s.edition.country) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    val detect: () -> Unit = {
        scope.launch {
            locating = true
            when (val r = news.openwire.i18n.LocationDetector.detect(context)) {
                is news.openwire.i18n.LocationDetector.Result.Found -> {
                    city = r.edition.city; region = r.edition.region; country = r.edition.country
                    vm.setEdition(city, region, country)
                    if (!r.supportedCountry) Toast.makeText(context, tr(R.string.location_unsupported), Toast.LENGTH_LONG).show()
                }
                news.openwire.i18n.LocationDetector.Result.NoFix ->
                    Toast.makeText(context, tr(R.string.location_failed), Toast.LENGTH_LONG).show()
            }
            locating = false
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) detect() else Toast.makeText(context, tr(R.string.location_denied), Toast.LENGTH_LONG).show()
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hint(stringResource(R.string.edition_hint))
        val prev = s.previousEdition
        if (prev != null && (prev != s.edition || (s.previousLanguage != null && s.previousLanguage != s.language))) {
            val prevLang = news.openwire.domain.engine.Languages.of(news.openwire.domain.engine.Languages.effective(s.previousLanguage))!!
            val activity = context.findActivity()
            SecondaryButton(stringResource(R.string.edition_return, prev.displayName, prevLang.nativeName)) {
                val lang = vm.restorePrevious()
                city = prev.city; region = prev.region; country = prev.country
                if (lang != null && activity != null) news.openwire.i18n.AppLocale.apply(activity, lang)
            }
        }
        PrimaryButton(stringResource(if (locating) R.string.location_finding else R.string.location_use), Icons.Outlined.MyLocation) {
            if (locating) return@PrimaryButton
            val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) detect() else permission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        Hint(stringResource(R.string.location_privacy))
        Section {
            Label(stringResource(R.string.edition_quick_picks))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("Washington, D.C.", "District of Columbia", "US"), Triple("New York", "New York", "US"),
                    Triple("Seattle", "Washington", "US"), Triple("San Francisco", "California", "US"),
                    Triple("London", "England", "GB"), Triple("Toronto", "Ontario", "CA"), Triple("Vancouver", "British Columbia", "CA"),
                    Triple("Sydney", "New South Wales", "AU"), Triple("Dublin", "Leinster", "IE"), Triple("Dubai", "Dubai", "AE"),
                    Triple("Cairo", "Cairo", "EG"), Triple("Riyadh", "Riyadh", "SA"), Triple("Paris", "Île-de-France", "FR"),
                    Triple("Berlin", "Berlin", "DE"), Triple("Madrid", "Madrid", "ES"), Triple("Mexico City", "CDMX", "MX"),
                ).forEach { (c, r, k) ->
                    Chip(c, selected = city == c && country == k) { city = c; region = r; country = k }
                }
            }
        }
        Section {
            Field(stringResource(R.string.edition_city), city, { city = it })
            Spacer(Modifier.height(10.dp))
            Field(stringResource(R.string.edition_region), region, { region = it })
            Spacer(Modifier.height(14.dp))
            Label(stringResource(R.string.edition_country))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TopicRegistry.COUNTRIES.forEach { c -> Chip(countryName(c.code), selected = country == c.code) { country = c.code } }
            }
        }
        PrimaryButton(stringResource(R.string.edition_use)) { vm.setEdition(city, region, country) }
        val suggestions = news.openwire.domain.engine.LocalSources.forCountry(s.edition.country)
        if (suggestions.isNotEmpty()) {
            Section {
                Label(stringResource(R.string.edition_local_sources, countryName(s.edition.country)))
                Hint(stringResource(R.string.edition_local_sources_hint))
                Spacer(Modifier.height(6.dp))
                suggestions.forEachIndexed { i, sug ->
                    if (i > 0) Divider()
                    SwitchRow(sug.name, news.openwire.domain.engine.Languages.of(sug.language)?.nativeName ?: sug.language, s.customFeeds.any { it.url == sug.url }) {
                        vm.toggleLocalSource(sug, it)
                    }
                }
            }
        }
        val region = news.openwire.domain.engine.LocalSources.regionFor(s.edition.country)
        if (region != null) {
            val regional = news.openwire.domain.engine.LocalSources.forRegion(region, s.edition.country)
            Section {
                Label(tr(when (region) {
                    news.openwire.domain.engine.LocalSources.Region.ARAB -> R.string.region_arab
                    news.openwire.domain.engine.LocalSources.Region.FRENCH -> R.string.region_french
                    news.openwire.domain.engine.LocalSources.Region.GERMAN -> R.string.region_german
                    news.openwire.domain.engine.LocalSources.Region.SPANISH -> R.string.region_spanish
                }))
                Hint(tr(R.string.region_hint))
                Spacer(Modifier.height(6.dp))
                regional.forEachIndexed { i, sug ->
                    if (i > 0) Divider()
                    SwitchRow(sug.name, news.openwire.domain.engine.Languages.of(sug.language)?.nativeName ?: sug.language, s.customFeeds.any { it.url == sug.url }) {
                        vm.toggleLocalSource(sug, it, regional = true)
                    }
                }
            }
        }
    }
}

/** A country's name in the app's language ("Egypt", "مصر", "Égypte"). */
@Composable
private fun countryName(code: String): String {
    val lang = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    return java.util.Locale("", code).getDisplayCountry(lang).ifBlank { TopicRegistry.country(code).name }
}

// ---- Language -----------------------------------------------------------------------------

@Composable
private fun LanguagePage(vm: SettingsViewModel) {
    val s by vm.settings.collectAsState()
    val activity = LocalContext.current.findActivity()
    val pick: (String) -> Unit = { tag -> if (tag != s.language) { vm.setLanguage(tag); activity?.let { news.openwire.i18n.AppLocale.apply(it, tag) } } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hint(stringResource(R.string.language_hint))
        Section {
            val phone = news.openwire.domain.engine.Languages.of(android.content.res.Resources.getSystem().configuration.locales[0].language)
            SelectRow(stringResource(R.string.language_system), phone?.nativeName ?: "English", s.language.isEmpty()) { pick("") }
            news.openwire.domain.engine.Languages.ALL.forEach { l ->
                Divider()
                SelectRow(l.nativeName, l.englishName, s.language == l.code) { pick(l.code) }
            }
        }
    }
}

@Composable
private fun SelectRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = sans(15.sp, FontWeight.Medium), color = Glass.textPrimary)
            Text(subtitle, style = sans(12.sp), color = Glass.textSecondary)
        }
        CheckDot(selected)
    }
}

// ---- Topics -----------------------------------------------------------------------------

private fun LazyListScope.topicsPage(vm: SettingsViewModel, onAdd: () -> Unit, onEdit: (news.openwire.domain.model.Topic) -> Unit) {
    item { Hint(tr(R.string.topics_hint) + " " + tr(R.string.swipe_hint_topics)) }
    item { PrimaryButton(tr(R.string.topics_new_button), Icons.Filled.Add, onAdd) }
    item {
        val topics by vm.allTopics.collectAsState()
        val s by vm.settings.collectAsState()
        // Drag a topic by its handle to reorder (move up / down stay available to accessibility services).
        var dragging by remember { mutableStateOf<String?>(null) }
        var renaming by remember { mutableStateOf<news.openwire.domain.model.Topic?>(null) }
        renaming?.let { t -> RenameDialog(t.label, onDismiss = { renaming = null }) { vm.renameTopic(t.id, it); renaming = null } }
        var sourcesOf by remember { mutableStateOf<news.openwire.domain.model.Topic?>(null) }
        sourcesOf?.let { t -> TopicSourcesDialog(vm, t) { sourcesOf = null } }
        var dragOffset by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
        val rowHeights = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
        val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        // On release the lifted row glides into its slot instead of snapping.
        val settle = remember { androidx.compose.animation.core.Animatable(0f) }
        var settlingId by remember { mutableStateOf<String?>(null) }
        Section {
            topics.forEachIndexed { i, t ->
                androidx.compose.runtime.key(t.id) {
                    if (i > 0) Divider()
                    val lifted = dragging == t.id
                    val elevation by androidx.compose.animation.core.animateDpAsState(if (lifted) 12.dp else 0.dp, androidx.compose.animation.core.spring(stiffness = 500f), label = "lift")
                    val lift by androidx.compose.animation.core.animateFloatAsState(if (lifted) 1.03f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 500f), label = "liftScale")
                    // A neighbour that swaps places slides to its new slot (from where it was) rather than jumping.
                    val shift = remember { androidx.compose.animation.core.Animatable(0f) }
                    var lastY by remember { mutableStateOf<Float?>(null) }
                    // Moved outside the swipe row's clip, so the lifted row can float over its neighbours.
                    Box(Modifier
                        .zIndex(if (lifted || settlingId == t.id) 1f else 0f)
                        .graphicsLayer {
                            translationY = when { lifted -> dragOffset; settlingId == t.id -> settle.value; else -> shift.value }
                            scaleX = lift; scaleY = lift
                        }
                        .shadow(elevation, RoundedCornerShape(14.dp))
                        .onPlaced { c ->
                        val y = c.positionInParent().y
                        val before = lastY
                        lastY = y
                        if (before != null && before != y && dragging != t.id && settlingId != t.id) {
                            scope.launch {
                                shift.snapTo(shift.value + (before - y))
                                shift.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 420f))
                            }
                        }
                    }) {
                    // Swipe right: rename / edit (your topics). Swipe left: delete (yours) or hide / show.
                    SwipeActions(
                        // Built-in topics are never renamed or deleted: their sources can be edited.
                        start = if (t.isCustom) listOf(
                            SwipeAction(tr(R.string.rename_topic), Icons.Outlined.Edit, Glass.indigo) { renaming = t },
                            SwipeAction(tr(R.string.edit_topic_action), Icons.Outlined.Tune, Glass.accent) { onEdit(t) },
                        ) else listOf(SwipeAction(tr(R.string.topic_edit_sources), Icons.Outlined.Tune, Glass.accent) { sourcesOf = t }),
                        end = if (t.isCustom) listOf(SwipeAction(tr(R.string.delete_topic_action), Icons.Outlined.Delete, Color(0xFFE5484D)) { vm.deleteTopic(t.id) })
                        else listOf(SwipeAction(tr(if (t.isVisible) R.string.hide_topic_action else R.string.show_topic_action), if (t.isVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, Glass.indigo) { vm.setTopicVisible(t.id, !t.isVisible) }),
                    ) {
                    Row(
                        Modifier.fillMaxWidth()
                            .onGloballyPositioned { rowHeights[t.id] = it.size.height }
                            .background(if (lifted || settlingId == t.id) Glass.deviceBg else Color.Transparent, RoundedCornerShape(14.dp))
                            .semantics {
                                customActions = listOf(
                                    androidx.compose.ui.semantics.CustomAccessibilityAction(tr(R.string.cd_move_up)) { vm.moveTopic(t.id, up = true); true },
                                    androidx.compose.ui.semantics.CustomAccessibilityAction(tr(R.string.cd_move_down)) { vm.moveTopic(t.id, up = false); true },
                                )
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The handle: press and drag.
                        Icon(
                            Icons.Filled.DragIndicator, tr(R.string.cd_drag_topic), tint = if (lifted) Glass.accent else Glass.textMuted,
                            modifier = Modifier.size(28.dp).padding(2.dp).pointerInput(t.id) {
                                detectDragGestures(
                                    onDragStart = { dragging = t.id; dragOffset = 0f; haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) },
                                    onDragEnd = {
                                        val from = dragOffset
                                        settlingId = t.id; dragging = null; dragOffset = 0f
                                        scope.launch {
                                            settle.snapTo(from)
                                            settle.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 380f))
                                            if (settlingId == t.id) settlingId = null
                                        }
                                    },
                                    onDragCancel = {
                                        val from = dragOffset
                                        settlingId = t.id; dragging = null; dragOffset = 0f
                                        scope.launch { settle.snapTo(from); settle.animateTo(0f, androidx.compose.animation.core.spring(stiffness = 400f)); if (settlingId == t.id) settlingId = null }
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val list = vm.allTopics.value
                                        val idx = list.indexOfFirst { it.id == t.id }
                                        val h = (rowHeights[t.id] ?: 120).toFloat()
                                        if (dragOffset > h / 2 && idx < list.lastIndex) {
                                            vm.moveTopic(t.id, up = false); dragOffset -= (rowHeights[list[idx + 1].id] ?: h.toInt())
                                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        } else if (dragOffset < -h / 2 && idx > 0) {
                                            vm.moveTopic(t.id, up = true); dragOffset += (rowHeights[list[idx - 1].id] ?: h.toInt())
                                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        }
                                    },
                                )
                            },
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.label, style = sans(14.sp, FontWeight.SemiBold), color = if (t.isVisible) Glass.textPrimary else Glass.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                when {
                                    t.isCustom && t.keywords.isNotEmpty() -> tr(R.string.topic_keywords, t.keywords.joinToString())
                                    t.isCustom -> tr(R.string.topic_sources, t.sources.size)
                                    t.liveOnly -> tr(R.string.topic_live)
                                    else -> tr(R.string.topic_snapshot)
                                },
                                style = sans(11.sp), color = Glass.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        SmallIcon(Icons.Outlined.Tune, tr(R.string.topic_edit_sources)) { if (t.isCustom) onEdit(t) else sourcesOf = t }
                        val notify = t.id in s.notifyTopics
                        SmallIcon(
                            if (notify) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsNone,
                            tr(if (notify) R.string.cd_stop_notify else R.string.cd_notify_me), tint = if (notify) Glass.accent else null,
                        ) { vm.setNotifyTopic(t.id, !notify) }
                        GlassSwitch(t.isVisible) { vm.setTopicVisible(t.id, it) }
                    }
                    }
                    }
                }
            }
        }
    }
    item { OutlineButton(tr(R.string.topics_reset)) { vm.resetTopics() } }
}

/**
 * New topic, and editing one (same page, prefilled, "Save changes"). Suggestions only ever
 * grow: keywords and sources found for each choice are added to what's shown, never replace
 * it, in panels that scroll inside themselves.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddTopicPage(vm: SettingsViewModel, editing: news.openwire.domain.model.Topic? = null, onDone: () -> Unit) {
    val g = Glass
    val key = editing?.id ?: "new"
    var name by rememberSaveable(key) { mutableStateOf(editing?.label.orEmpty()) }
    var keywords by rememberSaveable(key) { mutableStateOf(editing?.keywords?.joinToString(", ").orEmpty()) }
    val chosen = remember(key) { mutableStateListOf<String>().apply { editing?.sources?.let(::addAll) } }
    var filter by rememberSaveable(key) { mutableStateOf("") }
    val library by vm.library.collectAsState()
    val s by vm.settings.collectAsState()
    // Suggestions shown so far (appended to, never replaced).
    val shownIdeas = remember(key) { mutableStateListOf<String>() }
    val own = remember(key) { editing?.let { vm.topicOwnFeeds(it.id) }.orEmpty() }
    val shownOutlets = remember(key) {
        mutableStateListOf<news.openwire.domain.engine.TopicGraph.Outlet>().apply { own.forEach { add(news.openwire.domain.engine.TopicGraph.Outlet(it.name, it.domain, 0.0, 0)) } }
    }
    val pickedOutlets = remember(key) { mutableStateListOf<String>().apply { own.forEach { add(it.domain) } } }
    val pickedFound = remember(key) { mutableStateListOf<news.openwire.data.remote.SourceFinder.Found>() }
    val popular by vm.popularNow.collectAsState()
    LaunchedEffect(key) { vm.clearKeywordIdeas(); vm.loadPopular() }
    val sug by vm.topicSuggest.collectAsState()
    LaunchedEffect(name, keywords) {
        kotlinx.coroutines.delay(650)
        vm.updateTopicSeeds(name, TopicRegistry.parseCommaKeywords(keywords))
    }
    var nameChosen by rememberSaveable(key) { mutableStateOf(editing != null) }
    var keywordsTouched by rememberSaveable(key) { mutableStateOf(editing != null) }
    /** The name the keywords were filled in for (null: you wrote or kept your own). */
    var autoFor by rememberSaveable(key) { mutableStateOf<String?>(null) }
    /** Where the latest batch of ideas starts (they're outlined until the next batch). */
    var newFrom by remember(key) { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(sug.keywords) {
        val start = shownIdeas.size
        sug.keywords.forEach { k -> if (shownIdeas.none { it.equals(k, true) }) shownIdeas.add(k) }
        if (shownIdeas.size > start && start > 0) newFrom = start
    }
    // A name settles (picked, or typing pauses): its 3–5 strongest keywords fill the field,
    // unless you've written your own.
    val nameIdeasNow by vm.nameIdeas.collectAsState()
    LaunchedEffect(name, nameIdeasNow, popular) {
        if (nameChosen || name.trim().length < 3) return@LaunchedEffect
        // Still a fragment of a suggested name ("Nvid" → "Nvidia")? Wait for a pick instead.
        val t = name.trim().lowercase()
        val extends = (popular + nameIdeasNow).any { it.lowercase().startsWith(t) && it.length > t.length } ||
            news.openwire.domain.engine.TopicPresets.suggest(name).any { it.name.lowercase().startsWith(t) && it.name.length > t.length }
        if (extends) return@LaunchedEffect
        kotlinx.coroutines.delay(2_000)
        nameChosen = true
    }
    LaunchedEffect(nameChosen, name, sug.keywords) {
        if (!nameChosen || keywordsTouched || sug.keywords.isEmpty()) return@LaunchedEffect
        // Only fill an empty field, or replace what was filled for an earlier name.
        if (keywords.isNotBlank() && autoFor == null) return@LaunchedEffect
        if (autoFor != null && autoFor.equals(name.trim(), true) && keywords.isNotBlank()) return@LaunchedEffect
        keywords = (listOf(name.trim()) + sug.keywords.filter { !it.equals(name.trim(), true) }.take(4)).joinToString(", ")
        autoFor = name.trim()
    }
    LaunchedEffect(sug.outlets) { sug.outlets.forEach { o -> if (shownOutlets.none { it.domain == o.domain }) shownOutlets.add(o) } }
    val current = TopicRegistry.parseCommaKeywords(keywords).map { it.lowercase() }.toSet()
    fun toggleKeyword(k: String) {
        val list = TopicRegistry.parseCommaKeywords(keywords)
        keywords = (if (list.any { it.equals(k, true) }) list.filterNot { it.equals(k, true) } else list + k).joinToString(", ")
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hint(tr(R.string.add_topic_hint))
        val matches = remember(name) { news.openwire.domain.engine.TopicPresets.suggest(name) }
        val exact = matches.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
        if (editing == null) Section {
            Label(tr(R.string.popular_topics))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // In today's headlines first (refreshed on each visit), then the classics.
                popular.take(6).forEach { t -> Chip("↗ $t", selected = name == t) { name = t; nameChosen = true; if (!keywordsTouched) keywords = "" } }
                news.openwire.domain.engine.TopicPresets.POPULAR.forEach { p -> Chip(p.name, selected = name == p.name) { name = p.name; nameChosen = true; if (!keywordsTouched) keywords = p.keywords.take(5).joinToString(", ") } }
            }
        }
        Section {
            Field(tr(R.string.topic_name), name, { name = it; nameChosen = false }, placeholder = tr(R.string.topic_name_eg))
            // Suggestions while typing: ready-made topics, today's headline names, live completions.
            LaunchedEffect(name) { if (!nameChosen) vm.suggestNames(name) }
            val nameIdeas by vm.nameIdeas.collectAsState()
            val typed = name.trim().lowercase()
            val live = (popular + nameIdeas).filter { typed.length >= 2 && it.lowercase().contains(typed) && !it.equals(name.trim(), true) }.distinctBy { it.lowercase() }.take(4)
            if (!nameChosen && typed.length >= 2 && (matches.isNotEmpty() || live.isNotEmpty())) {
                Spacer(Modifier.height(6.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(g.chipFill).border(1.dp, g.hairline, RoundedCornerShape(14.dp)).padding(4.dp)) {
                    matches.take(4).forEach { p ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { name = p.name; nameChosen = true; if (!keywordsTouched) keywords = p.keywords.take(5).joinToString(", ") }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Text(p.name, style = sans(14.sp, FontWeight.SemiBold), color = g.textPrimary)
                            Text(p.keywordText, style = sans(11.sp, lineHeight = 15.sp), color = g.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    live.forEach { n ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { name = n; nameChosen = true; if (!keywordsTouched) { keywords = ""; autoFor = null } }.padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("↗", style = sans(13.sp, FontWeight.SemiBold), color = g.accentSoft)
                            Spacer(Modifier.width(8.dp))
                            Text(n, style = sans(14.sp, FontWeight.Medium), color = g.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Field(tr(R.string.keywords_field), keywords, { keywords = it; keywordsTouched = true }, placeholder = tr(R.string.keywords_eg))
            if (name.isNotBlank() || keywords.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(R.string.related_keywords).uppercase(), style = mono(10.sp, FontWeight.SemiBold, 0.14.em), color = g.accentSoft, modifier = Modifier.weight(1f))
                    if (sug.loading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = g.accent)
                }
                Spacer(Modifier.height(6.dp))
                if (shownIdeas.isEmpty() && !sug.loading) Text(tr(R.string.related_keywords_hint), style = sans(11.sp, lineHeight = 16.sp), color = g.textSecondary)
                // Grows as you pick: new ideas join at the end, which scrolls into view.
                val ideaScroll = rememberScrollState()
                LaunchedEffect(shownIdeas.size) { if (newFrom > 0) ideaScroll.animateScrollTo(ideaScroll.maxValue) }
                Box(Modifier.fillMaxWidth().heightIn(max = 196.dp).clip(RoundedCornerShape(14.dp)).verticalScroll(ideaScroll)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        shownIdeas.forEachIndexed { i, k ->
                            val on = k.lowercase() in current
                            val fresh = i >= newFrom && newFrom > 0 && !on
                            Box(
                                if (fresh) Modifier.border(1.dp, g.accent.copy(alpha = 0.55f), CircleShape) else Modifier,
                            ) { Chip(if (on) "✓ $k" else "+ $k", selected = on) { toggleKeyword(k) } }
                        }
                    }
                }
            }
        }
        if (shownOutlets.isNotEmpty()) {
            Section {
                Label(tr(R.string.suggested_sources))
                Text(tr(R.string.suggested_sources_hint), style = sans(11.sp, lineHeight = 16.sp), color = g.textSecondary)
                Spacer(Modifier.height(6.dp))
                Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    shownOutlets.forEach { o ->
                        // An installed source is chosen as one; any other outlet is added for this topic.
                        val lib = library.firstOrNull { !it.isPrimary && it.domain.removePrefix("www.").equals(o.domain, true) }
                        val on = if (lib != null) lib.id in chosen else o.domain in pickedOutlets
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                if (lib != null) { if (on) chosen.remove(lib.id) else chosen.add(lib.id) }
                                else if (on) pickedOutlets.remove(o.domain) else pickedOutlets.add(o.domain)
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SourceLogo(o.domain, o.name, size = 22.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(o.name, style = sans(13.sp, FontWeight.Medium), color = g.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (o.hits > 0) tr(R.string.suggested_source_hits, o.hits, o.domain) else o.domain, style = sans(11.sp), color = g.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            CheckDot(on)
                        }
                    }
                }
            }
        }
        Section {
            Label(tr(R.string.sources_optional, chosen.size + pickedFound.size))
            Field(tr(R.string.filter_sources), filter, { filter = it }, placeholder = tr(R.string.find_sources_eg))
            Spacer(Modifier.height(6.dp))
            // Yours first, then (as you type) newspapers found online, checked live.
            library.filter { !it.isPrimary && (filter.isBlank() || it.name.contains(filter, true) || it.domain.contains(filter, true)) }
                .take(if (filter.isBlank()) 40 else 30)
                .forEach { src ->
                    val on = src.id in chosen
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { if (on) chosen.remove(src.id) else chosen.add(src.id) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SourceLogo(src.domain, src.name, size = 20.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(src.name, style = sans(13.sp), color = g.textPrimary, modifier = Modifier.weight(1f))
                        CheckDot(on)
                    }
                }
            if (filter.trim().length >= 2 && vm.finder != null) {
                var found by remember { mutableStateOf<List<news.openwire.data.remote.SourceFinder.Found>>(emptyList()) }
                var searching by remember { mutableStateOf(false) }
                val lang = news.openwire.domain.engine.Languages.effective(s.language)
                LaunchedEffect(filter.trim()) {
                    searching = true
                    kotlinx.coroutines.delay(500)
                    vm.finder.search(filter.trim(), lang, s.edition.country).collect { found = it }
                    searching = false
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(R.string.find_sources_title, filter.trim()).uppercase(), style = mono(10.sp, FontWeight.SemiBold, 0.14.em), color = g.accentSoft, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (searching) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = g.accent)
                }
                found.filter { f -> library.none { it.url == f.url } }.forEach { f ->
                    val on = pickedFound.any { it.url == f.url }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { if (on) pickedFound.removeAll { it.url == f.url } else pickedFound.add(f) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SourceLogo(f.domain, f.name, size = 20.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.name, style = sans(13.sp), color = g.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(f.domain, style = sans(11.sp), color = g.textSecondary, maxLines = 1)
                        }
                        CheckDot(on)
                    }
                }
            }
        }
        PrimaryButton(tr(if (editing != null) R.string.save_changes else R.string.create_topic)) {
            val outlets = shownOutlets.filter { it.domain in pickedOutlets }
            val ok = if (editing != null) vm.updateTopic(editing.id, name, keywords, chosen.toList(), outlets, pickedFound.toList())
            else vm.addTopic(name, keywords, chosen.toList(), outlets, pickedFound.toList())
            if (ok) onDone()
        }
    }
}

/** An action revealed by swiping a row. */
private class SwipeAction(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color, val onClick: () -> Unit)

/**
 * A row that slides to reveal actions: swipe right for [start] actions, left for [end] ones.
 * It snaps open past halfway, and closes again after an action or a tap on the row.
 */
@Composable
private fun SwipeActions(start: List<SwipeAction>, end: List<SwipeAction>, content: @Composable () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val actionPx = with(density) { SwipeActionWidth.toPx() }
    val startW = actionPx * start.size
    val endW = actionPx * end.size
    val offset = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    fun close() = scope.launch { offset.animateTo(0f, androidx.compose.animation.core.spring(stiffness = 600f)) }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))) {
        // Actions behind the row.
        Row(Modifier.matchParentSize(), verticalAlignment = Alignment.CenterVertically) {
            start.forEach { a -> SwipeButton(a, Modifier.width(SwipeActionWidth).fillMaxHeight()) { a.onClick(); close() } }
            Spacer(Modifier.weight(1f))
            end.forEach { a -> SwipeButton(a, Modifier.width(SwipeActionWidth).fillMaxHeight()) { a.onClick(); close() } }
        }
        Box(
            Modifier.fillMaxWidth()
                .graphicsLayer { translationX = offset.value }
                .background(Glass.deviceBg.copy(alpha = 1f).compositeOver(if (Glass.isDark) Color.Black else Color.White))
                .pointerInput(start.size, end.size, rtl) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            // Right (start) is positive in left-to-right; flipped for Arabic.
                            val maxRight = if (rtl) endW else startW
                            val maxLeft = if (rtl) startW else endW
                            scope.launch { offset.snapTo((offset.value + dx).coerceIn(-maxLeft, maxRight)) }
                        },
                        onDragEnd = {
                            val maxRight = if (rtl) endW else startW
                            val maxLeft = if (rtl) startW else endW
                            val target = when {
                                offset.value > maxRight / 2 && maxRight > 0 -> maxRight
                                offset.value < -maxLeft / 2 && maxLeft > 0 -> -maxLeft
                                else -> 0f
                            }
                            scope.launch { offset.animateTo(target, androidx.compose.animation.core.spring(stiffness = 600f)) }
                        },
                    )
                },
        ) { content() }
    }
}

/** Wide enough for a two-word label ("Edit Sources") in every language we ship. */
private val SwipeActionWidth = 100.dp

@Composable
private fun SwipeButton(a: SwipeAction, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.background(a.color).clickable(onClick = onClick).padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(a.icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(a.label, style = sans(11.sp, FontWeight.SemiBold, lineHeight = 13.sp), color = Color.White, maxLines = 2, textAlign = TextAlign.Center)
    }
}

/** A built-in topic's sources: switch each on or off, or add a feed to it. */
@Composable
private fun TopicSourcesDialog(vm: SettingsViewModel, topic: news.openwire.domain.model.Topic, onDismiss: () -> Unit) {
    val g = Glass
    val library by vm.library.collectAsState()
    val list = remember(library, topic.id) { library.filter { src -> src.topics.any { it.equals(topic.id, true) } || (src.isPrimary && topic.liveUrl != null) } }
    var adding by remember { mutableStateOf(false) }
    if (adding) AddToSectionDialog(vm, topic.label, onDismiss = { adding = false }, topicId = topic.id)
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 640.dp).clip(RoundedCornerShape(26.dp))
                .background(g.deviceBg).border(1.dp, g.hairline, RoundedCornerShape(26.dp)).padding(18.dp),
        ) {
            Text(tr(R.string.topic_sources_title, topic.label).uppercase(), style = mono(11.sp, FontWeight.SemiBold, 0.14.em), color = g.accentSoft)
            Spacer(Modifier.height(4.dp))
            Text(tr(R.string.topic_sources_hint), style = sans(12.sp, lineHeight = 17.sp), color = g.textSecondary)
            Spacer(Modifier.height(10.dp))
            PrimaryButton(tr(R.string.add_source), Icons.Filled.Add) { adding = true }
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).padding(top = 8.dp)) {
                items(list.size) { i -> val src = list[i]; SourceRow(src, null, false, vm) }
            }
            Spacer(Modifier.height(8.dp))
            OutlineButton(tr(R.string.src_done_editing), icon = Icons.Filled.Check, accent = false, onClick = onDismiss)
        }
    }
}

/** Rename a topic in place. */
@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    GlassDialog(icon = Icons.Outlined.Edit, kicker = tr(R.string.rename_topic_title), onDismiss = onDismiss) {
        Field(tr(R.string.topic_name), text, { text = it })
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { DialogButton(tr(R.string.action_cancel), primary = false, onClick = onDismiss) }
            Box(Modifier.weight(1f)) { DialogButton(tr(R.string.action_save), primary = true) { onSave(text) } }
        }
    }
}

// ---- Sources ------------------------------------------------------------------------------

private fun LazyListScope.sourcesPage(vm: SettingsViewModel, onAdd: () -> Unit) {
    item { Hint(tr(R.string.sources_hint)) }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { PrimaryButton(tr(R.string.add_source), Icons.Filled.Add, onAdd) }
            Box(Modifier.weight(1f)) { SecondaryButton(tr(R.string.check_feeds)) { vm.checkEnabledFeeds() } }
        }
    }
    item {
        var filter by rememberSaveable { mutableStateOf(SourcesQuery.take()) }
        val library by vm.library.collectAsState()
        val checks by vm.checks.collectAsState()
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(tr(R.string.search_sources), filter, { filter = it }, placeholder = tr(R.string.find_sources_eg))
            // Beyond what's installed: any newspaper or site, found by name or address and checked live.
            if (filter.trim().length >= 3) FindSources(vm, filter.trim())
            var editing by rememberSaveable { mutableStateOf<String?>(null) }
            var adding by remember { mutableStateOf<String?>(null) }
            adding?.let { cat -> AddToSectionDialog(vm, cat, onDismiss = { adding = null }) }
            SourceLibrary.groupSources(library, filter).forEach { group ->
                val edit = editing == group.category
                Section {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(group.category.uppercase(), style = mono(11.sp, FontWeight.SemiBold, 0.14.em), color = Glass.accentSoft)
                        // (+) adds a feed to this section; the pencil turns the rows into removable ones.
                        Spacer(Modifier.width(6.dp))
                        SmallIcon(Icons.Filled.Add, tr(R.string.src_add_here, group.category)) { adding = group.category }
                        SmallIcon(if (edit) Icons.Filled.Check else Icons.Outlined.Edit, tr(if (edit) R.string.src_done_editing else R.string.src_edit_list)) {
                            editing = if (edit) null else group.category
                        }
                        Spacer(Modifier.weight(1f))
                        Text("${group.all.count { it.enabled }}/${group.all.size}", style = mono(11.sp), color = Glass.textSecondary)
                        Spacer(Modifier.width(8.dp))
                        GlassSwitch(group.all.all { it.enabled }) { vm.setGroupEnabled(group.all, it) }
                    }
                    listOf(tr(R.string.aggregators) to group.aggregators, tr(R.string.individual_feeds) to group.publishers).forEach { (title, list) ->
                        if (list.isEmpty()) return@forEach
                        Spacer(Modifier.height(8.dp))
                        Text(title, style = sans(11.sp, FontWeight.Medium), color = Glass.textSecondary)
                        list.forEach { src -> SourceRow(src, checks[SourceLibrary.sourceKey(src)], checks.containsKey(SourceLibrary.sourceKey(src)), vm, removable = edit && !src.isPrimary) }
                    }
                }
            }
        }
    }
    item { OutlineButton(tr(R.string.sources_reset)) { vm.resetSources() } }
}

@Composable
private fun SourceRow(src: FeedSource, check: news.openwire.data.repository.FeedCheck?, checking: Boolean, vm: SettingsViewModel, removable: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        // Edit mode: a red (−) takes the source off the list.
        androidx.compose.animation.AnimatedVisibility(removable) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(Color(0xFFE5484D)).clickable { vm.removeSource(src) }
                        .semantics { this.contentDescription = tr(R.string.cd_remove_source) },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.width(11.dp).height(2.dp).background(Color.White)) }
                Spacer(Modifier.width(10.dp))
            }
        }
        SourceLogo(src.domain, src.name, size = 22.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(src.name, style = sans(13.sp, FontWeight.Medium), color = if (src.enabled) Glass.textPrimary else Glass.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = when {
                checking && check == null -> tr(R.string.checking)
                check?.ok == true -> "✓ " + tr(R.string.check_stories, check.count) + (check.newestAt?.let { " · " + tr(R.string.check_newest, NewsProcessor.formatRelativeTime(it)) } ?: "") + if (check.stale) " · " + tr(R.string.check_stale) else ""
                check != null -> "✗ ${check.error}"
                else -> listOfNotNull(src.domain.ifEmpty { null }, src.access, if (src.isCustom) tr(R.string.your_source) else null).joinToString(" · ")
            }
            Text(
                detail, style = sans(11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = when { check?.ok == false -> Glass.accentSoft; check?.stale == true -> Amber400; check?.ok == true -> Emerald400; else -> Glass.textSecondary },
            )
        }
        if (checking && check == null) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Glass.accent)
            Spacer(Modifier.width(14.dp)) // clear of the switch
        }
        if (src.isCustom && !removable) SmallIcon(Icons.Outlined.Delete, tr(R.string.cd_remove_source)) { vm.deleteSource(src.id) }
        if (!removable) GlassSwitch(src.enabled) { vm.setSourceEnabled(src, it) }
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
    // Outlets picked from "Add sources by country" (no feed address needed).
    val picked = remember { mutableStateListOf<news.openwire.data.remote.SourceDirectory.Outlet>() }
    val topics by vm.allTopics.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hint(tr(R.string.add_source_hint))
        Section {
            Field(tr(R.string.name_optional), name, { name = it }, placeholder = "e.g. Polygon")
            Spacer(Modifier.height(10.dp))
            Field(tr(R.string.feed_url), url, { url = it }, placeholder = "https://www.polygon.com/rss/index.xml", keyboard = KeyboardType.Uri)
        }
        SourcesByCountry(vm, picked) { vm.addDirectoryOutlets(picked.toList(), chosen.toList()); picked.clear() }
        Section {
            Label(tr(R.string.weave_into))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                topics.forEach { t -> Chip(t.label, selected = t.id in chosen) { if (t.id in chosen) chosen.remove(t.id) else chosen.add(t.id) } }
            }
            Spacer(Modifier.height(14.dp))
            Label(tr(R.string.fair_share, quota))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { (7..10).forEach { q -> Chip("$q", selected = quota == q) { quota = q } } }
        }
        val byCountry = url.isBlank() && picked.isNotEmpty()
        PrimaryButton(if (byCountry) tr(R.string.add_selected, picked.size) else tr(if (busy) R.string.checking_feed else R.string.verify_add)) {
            if (busy) return@PrimaryButton
            // Outlets picked by country go straight in (into the chosen topics, if any).
            if (byCountry) { vm.addDirectoryOutlets(picked.toList(), chosen.toList()); picked.clear(); onDone(); return@PrimaryButton }
            busy = true
            vm.addSource(name, url, chosen.toList(), quota) { ok -> busy = false; if (ok) onDone() }
        }
    }
}

/**
 * "Add sources by country": a country (Global by default) and a topic (all by default), then
 * that country's news media for each section, to pick several at once. The results scroll
 * inside the card, so the page stays as it was.
 */
@Composable
private fun SourcesByCountry(
    vm: SettingsViewModel,
    picked: androidx.compose.runtime.snapshots.SnapshotStateList<news.openwire.data.remote.SourceDirectory.Outlet>,
    onAdd: () -> Unit,
) {
    val g = Glass
    var on by rememberSaveable { mutableStateOf(false) }
    var country by rememberSaveable { mutableStateOf<String?>(null) }
    var sectionName by rememberSaveable { mutableStateOf<String?>(null) }
    val section = sectionName?.let { n -> news.openwire.data.remote.SourceDirectory.Section.entries.firstOrNull { it.name == n } }
    val state by vm.directoryState.collectAsState()
    val s by vm.settings.collectAsState()
    val app = news.openwire.domain.engine.Languages.effective(s.language)
    LaunchedEffect(on, country, sectionName) { if (on) { picked.clear(); vm.loadDirectory(country, section) } }
    Section {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr(R.string.by_country_title), style = sans(14.sp, FontWeight.SemiBold), color = g.textPrimary)
                Text(tr(R.string.by_country_hint), style = sans(11.sp, lineHeight = 16.sp), color = g.textSecondary)
            }
            GlassSwitch(on) { on = it }
        }
        androidx.compose.animation.AnimatedVisibility(on) {
            Column {
                Spacer(Modifier.height(12.dp))
                val countries = remember(app) {
                    listOf<Pair<String?, String>>(null to tr(R.string.global)) +
                        TopicRegistry.COUNTRIES.map { it.code to news.openwire.domain.engine.Languages.countryName(it.code, app) }.sortedBy { it.second }
                }
                val sections = listOf<Pair<String?, String>>(null to tr(R.string.all_topics)) +
                    news.openwire.data.remote.SourceDirectory.Section.entries.map { it.name to sectionLabel(it, app) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassDropdown(tr(R.string.country_label), countries, country, Modifier.weight(1f)) { country = it }
                    GlassDropdown(tr(R.string.topic_label), sections, sectionName, Modifier.weight(1f)) { sectionName = it }
                }
                Spacer(Modifier.height(12.dp))
                when (val st = state) {
                    SettingsViewModel.Directory.Idle, SettingsViewModel.Directory.Loading -> {
                        news.openwire.ui.home.SweepBar()
                        Text(tr(R.string.dir_loading), style = sans(12.sp), color = g.textSecondary, modifier = Modifier.padding(top = 8.dp))
                    }
                    SettingsViewModel.Directory.Failed -> Text(tr(R.string.dir_failed), style = sans(12.sp, lineHeight = 18.sp), color = g.textSecondary)
                    is SettingsViewModel.Directory.Ready -> {
                        // Results scroll within the card.
                        Column(
                            Modifier.fillMaxWidth().heightIn(max = 380.dp).clip(RoundedCornerShape(14.dp)).background(g.chipFill.copy(alpha = g.chipFill.alpha / 2))
                                .verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            st.sections.forEach { (sec, outlets) ->
                                Text(sectionLabel(sec, app).uppercase(), style = mono(10.sp, FontWeight.SemiBold, 0.14.em), color = g.accentSoft, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                                outlets.forEach { o ->
                                    val have = remember(s.customFeeds, o) { vm.hasOutlet(o.domain) }
                                    val sel = picked.any { it.domain == o.domain && it.section == o.section }
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                            .clickable(enabled = !have) { if (sel) picked.removeAll { it.domain == o.domain && it.section == o.section } else picked.add(o) }
                                            .padding(vertical = 7.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        SourceLogo(o.domain, o.name, size = 22.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(o.name, style = sans(13.sp, FontWeight.Medium), color = g.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                if (have) tr(R.string.dir_added) else "${o.domain} · " + if (o.stories == 1) tr(R.string.dir_story_one) else tr(R.string.dir_stories, o.stories),
                                                style = sans(11.sp), color = g.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (have) Icon(Icons.Filled.Check, null, tint = Emerald400, modifier = Modifier.size(18.dp)) else CheckDot(sel)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(tr(R.string.by_country_weave_hint), style = sans(11.sp, lineHeight = 16.sp), color = g.textSecondary)
                        Spacer(Modifier.height(8.dp))
                        PrimaryButton(tr(R.string.add_selected, picked.size)) { if (picked.isNotEmpty()) onAdd() }
                    }
                }
            }
        }
    }
}

private fun sectionLabel(s: news.openwire.data.remote.SourceDirectory.Section, lang: String): String = when (s) {
    news.openwire.data.remote.SourceDirectory.Section.GENERAL -> tr(R.string.dir_general)
    news.openwire.data.remote.SourceDirectory.Section.HEALTH -> tr(R.string.dir_health)
    else -> news.openwire.domain.engine.Languages.topicName(s.topicId, lang) ?: s.name.lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * A labelled glass dropdown: the field shows the choice; tapped, a frosted panel the field's
 * width unfolds beneath it (fade and scale from the top), the current choice tinted and
 * ticked and scrolled into view.
 */
@Composable
private fun GlassDropdown(label: String, options: List<Pair<String?, String>>, selected: String?, modifier: Modifier = Modifier, onSelect: (String?) -> Unit) {
    val g = Glass
    var open by remember { mutableStateOf(false) }
    var fieldWidth by remember { mutableStateOf(0) }
    var fieldHeight by remember { mutableStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val chevron by androidx.compose.animation.core.animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    val fieldShape = RoundedCornerShape(16.dp)
    Column(modifier) {
        Text(label.uppercase(), style = mono(10.sp, FontWeight.SemiBold, 0.14.em), color = g.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Box {
            Row(
                Modifier.fillMaxWidth()
                    .onGloballyPositioned { fieldWidth = it.size.width; fieldHeight = it.size.height }
                    .clip(fieldShape)
                    .background(if (open) g.accent.copy(alpha = 0.10f) else g.chipFill)
                    .border(1.dp, if (open) g.accent.copy(alpha = 0.45f) else g.hairline, fieldShape)
                    .clickable { open = !open }
                    .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    options.firstOrNull { it.first == selected }?.second.orEmpty(),
                    style = sans(14.sp, FontWeight.SemiBold), color = g.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = if (open) g.accentSoft else g.textSecondary, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = chevron })
            }
            if (open) {
                androidx.compose.ui.window.Popup(
                    offset = androidx.compose.ui.unit.IntOffset(0, fieldHeight + with(density) { 6.dp.roundToPx() }),
                    onDismissRequest = { open = false },
                    properties = androidx.compose.ui.window.PopupProperties(focusable = true),
                ) {
                    val shown = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
                    androidx.compose.animation.AnimatedVisibility(
                        shown,
                        enter = fadeIn(tween(140)) + androidx.compose.animation.scaleIn(tween(180), initialScale = 0.94f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)),
                    ) {
                        val panel = RoundedCornerShape(18.dp)
                        val scroll = rememberScrollState()
                        val rowPx = with(density) { 46.dp.toPx() }
                        LaunchedEffect(Unit) {
                            val k = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
                            scroll.scrollTo(((k - 2) * rowPx).toInt().coerceAtLeast(0))
                        }
                        Column(
                            Modifier.width(with(density) { fieldWidth.toDp() })
                                .shadow(18.dp, panel, ambientColor = Color.Black, spotColor = Color.Black)
                                .clip(panel)
                                // Solid, a step lighter than the page: it floats above the list, nothing shows through.
                                // The page's own dark (light) colour, made fully solid so nothing shows through.
                                .background(g.deviceBg.copy(alpha = 1f).compositeOver(if (g.isDark) Color.Black else Color.White))
                                .border(1.dp, g.hairline, panel)
                                .heightIn(max = 330.dp)
                                .verticalScroll(scroll)
                                .padding(6.dp),
                        ) {
                            options.forEach { (key, name) ->
                                val on = key == selected
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                                        .background(if (on) g.accent.copy(alpha = 0.14f) else Color.Transparent)
                                        .clickable { onSelect(key); open = false }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        name, style = sans(14.sp, if (on) FontWeight.SemiBold else FontWeight.Medium),
                                        color = if (on) g.accentSoft else g.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                    )
                                    if (on) Icon(Icons.Filled.Check, null, tint = g.accentSoft, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Paywall rules -------------------------------------------------------------------------

private fun LazyListScope.rulesPage(vm: SettingsViewModel) {
    item { Hint(tr(R.string.rules_hint)) }
    item {
        val s by vm.settings.collectAsState()
        val status by vm.serviceStatus.collectAsState()
        LaunchedEffect(Unit) { if (status.isEmpty()) vm.checkServices() }
        Section {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr(R.string.reader_services), style = mono(10.sp, FontWeight.SemiBold, 0.14.em), color = Glass.textSecondary)
                    Text(tr(R.string.reader_services_hint), style = sans(11.sp, lineHeight = 16.sp), color = Glass.textSecondary)
                }
                SmallIcon(androidx.compose.material.icons.Icons.Filled.Refresh, tr(R.string.cd_check_services)) { vm.checkServices() }
            }
            news.openwire.domain.engine.BypassServices.ALL.forEach { svc ->
                Divider()
                val st = status[svc.strategy]
                val enabled = svc.strategy !in s.disabledServices
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape).background(
                            when { !enabled -> Glass.textMuted; st == null -> Amber400; st.up -> Emerald400; else -> Glass.accent },
                        ),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(svc.name, style = sans(14.sp, FontWeight.Medium), color = if (enabled) Glass.textPrimary else Glass.textMuted)
                        Text(
                            when {
                                !enabled -> tr(R.string.svc_off)
                                st == null -> if (status.containsKey(svc.strategy)) tr(R.string.checking) else svc.homeUrl
                                st.up -> tr(R.string.svc_reachable) + (st.code?.let { " · HTTP $it" } ?: "")
                                else -> tr(R.string.svc_unavailable, st.error.orEmpty())
                            },
                            style = sans(11.sp), color = Glass.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    GlassSwitch(enabled) { vm.setServiceEnabled(svc.strategy, it) }
                }
            }
        }
    }
    item {
        var domain by rememberSaveable { mutableStateOf("") }
        var strategy by rememberSaveable { mutableStateOf(Strategy.SMRY) }
        Section {
            Label(tr(R.string.rule_add))
            Field(tr(R.string.domain), domain, { domain = it }, placeholder = "e.g. economist.com", keyboard = KeyboardType.Uri)
            Spacer(Modifier.height(10.dp))
            StrategyPicker(strategy) { strategy = it }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(tr(R.string.rule_save)) { vm.setRule(domain, strategy); domain = "" }
        }
    }
    item {
        var filter by rememberSaveable { mutableStateOf("") }
        var editing by remember { mutableStateOf<String?>(null) }
        val rules by vm.rules.collectAsState()
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(tr(R.string.search_publishers), filter, { filter = it })
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
                                Text(r.domain + (r.paywall?.let { " · " + tr(R.string.paywall_kind, it) } ?: ""), style = sans(11.sp), color = Glass.textSecondary, maxLines = 1)
                            }
                            StrategyBadge(r.strategy, r.isCustom)
                        }
                        AnimatedVisibility(editing == r.domain, enter = expandVertically(springTween(300)) + fadeIn(), exit = shrinkVertically(springTween(250)) + fadeOut()) {
                            Column(Modifier.padding(top = 10.dp)) {
                                StrategyPicker(r.strategy) { vm.setRule(r.domain, it) }
                                if (r.isCustom) { Spacer(Modifier.height(8.dp)); OutlineButton(tr(R.string.rule_reset), compact = true) { vm.resetRule(r.domain) } }
                            }
                        }
                    }
                }
            }
        }
    }
    item { OutlineButton(tr(R.string.rules_reset_all)) { vm.resetAllRules() } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StrategyPicker(current: Strategy, onPick: (Strategy) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Strategy.entries.forEach { s -> Chip(strategyLabel(s), selected = s == current) { onPick(s) } }
    }
}

private fun strategyLabel(s: Strategy) = when (s) {
    Strategy.DIRECT -> tr(R.string.strategy_direct); Strategy.SMRY -> "SMRY"; Strategy.REMOVEPAYWALL -> "RemovePaywall"; Strategy.UNWALL -> "UnWall"
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
private fun NotificationsPage(vm: SettingsViewModel, onHub: () -> Unit) {
    val context = LocalContext.current
    val s by vm.settings.collectAsState()
    val topics by vm.allTopics.collectAsState()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setNotificationsEnabled(granted)
        if (granted && !news.openwire.delivery.DeliveryCheck.status(context).ok) news.openwire.delivery.DeliveryCheck.showing = true
        if (!granted) Toast.makeText(context, tr(R.string.notify_blocked), Toast.LENGTH_LONG).show()
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hint(tr(R.string.notify_hint))
        Section {
            // The row opens the Notification Hub; the switch (past the | ) is the master switch.
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onHub).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(R.string.notifications), style = sans(14.sp, FontWeight.Medium), color = Glass.textPrimary)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Glass.textSecondary, modifier = Modifier.size(18.dp))
                    }
                    Text(tr(R.string.hub_row_sub), style = sans(11.sp, lineHeight = 16.sp), color = Glass.textSecondary)
                }
                Box(Modifier.padding(horizontal = 12.dp).width(1.dp).height(30.dp).background(Glass.hairline))
                GlassSwitch(s.notificationsEnabled) { on ->
                    if (on && Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else {
                        vm.setNotificationsEnabled(on)
                        if (on && !news.openwire.delivery.DeliveryCheck.status(context).ok) news.openwire.delivery.DeliveryCheck.showing = true
                    }
                }
            }
            Divider()
            NavRowCheck(context)
            Divider()
            SwitchRow(tr(R.string.bg_refresh), tr(R.string.bg_refresh_sub, refreshLabel(s.refreshMinutes, long = true)), s.backgroundRefresh) { vm.setBackgroundRefresh(it) }
        }
        AlertsQuietSection(vm, s)
        Section {
            Label(tr(R.string.refresh_every))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                news.openwire.domain.engine.REFRESH_CHOICES.forEach { m -> Chip(refreshLabel(m), selected = m == s.refreshMinutes) { vm.setRefreshMinutes(m) } }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                tr(R.string.refresh_hint),
                style = sans(11.sp, lineHeight = 16.sp), color = Glass.textSecondary,
            )
        }
        Section {
            Label(tr(R.string.breaking_news))
            SwitchRow(
                tr(R.string.breaking_alerts),
                tr(R.string.breaking_alerts_sub),
                s.breakingEnabled, enabled = s.notificationsEnabled,
            ) { vm.setBreakingEnabled(it) }
            if (s.breakingEnabled) {
                Spacer(Modifier.height(10.dp))
                Label(tr(R.string.breaking_watch, s.breakingOutlets.size))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    news.openwire.domain.engine.BreakingNews.OUTLETS.forEach { o -> Chip(o.name, selected = o.key in s.breakingOutlets) { vm.toggleBreakingOutlet(o.key) } }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    tr(R.string.breaking_checked, refreshLabel(s.refreshMinutes, long = true)),
                    style = sans(11.sp, lineHeight = 16.sp), color = Glass.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                Divider()
                SwitchRow(tr(R.string.rt_title), tr(R.string.rt_sub), s.realtimeAlerts) { vm.setRealtimeAlerts(it) }
                // What keeping a connection open means, said plainly before (and while) it's on.
                Column(
                    Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp))
                        .background(Amber400.copy(alpha = 0.10f)).border(1.dp, Amber400.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Text(tr(R.string.rt_disclaimer_title), style = sans(12.sp, FontWeight.SemiBold), color = Glass.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(tr(R.string.rt_disclaimer), style = sans(11.sp, lineHeight = 16.sp), color = Glass.textSecondary)
                }
            }
        }
        Section {
            Label(tr(R.string.notify_about))
            topics.filter { it.isVisible }.forEachIndexed { i, t ->
                if (i > 0) Divider()
                SwitchRow(t.label, null, t.id in s.notifyTopics, enabled = s.notificationsEnabled) { vm.setNotifyTopic(t.id, it) }
            }
        }
    }
}

private enum class HubState { OFF, SILENT, ON }

/** One thing that can notify: its state, and how to change it. */
private class HubItem(val group: String, val label: String, val state: HubState, val canSilence: Boolean, val set: (HubState) -> Unit)

/**
 * Notification Hub: every source of notifications in one list (topics, breaking news, real-time
 * alerts, writers and accounts you follow, briefing reminders), each Off, Silent or On, with a
 * quick filter by state.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationHub(vm: SettingsViewModel) {
    val g = Glass
    val s by vm.settings.collectAsState()
    val topics by vm.allTopics.collectAsState()
    var filter by rememberSaveable { mutableStateOf("ALL") }
    fun state(on: Boolean, channel: String) = when { !on -> HubState.OFF; channel in s.alerts.silent -> HubState.SILENT; else -> HubState.ON }
    fun setSilent(channel: String, silent: Boolean) = vm.updateAlerts { a -> a.copy(silent = if (silent) a.silent + channel else a.silent - channel) }
    val items = buildList {
        val gTopics = tr(R.string.hub_group_topics)
        topics.filter { it.isVisible }.forEach { t ->
            val ch = "topic:${t.id}"
            add(HubItem(gTopics, t.label, state(t.id in s.notifyTopics, ch), true) { st -> vm.setNotifyTopic(t.id, st != HubState.OFF); setSilent(ch, st == HubState.SILENT) })
        }
        val gBreaking = tr(R.string.breaking_news)
        add(HubItem(gBreaking, tr(R.string.breaking_alerts), state(s.breakingEnabled, "breaking"), true) { st -> vm.setBreakingEnabled(st != HubState.OFF); setSilent("breaking", st == HubState.SILENT) })
        add(HubItem(gBreaking, tr(R.string.rt_channel), state(s.realtimeAlerts, "realtime"), true) { st ->
            if (st != HubState.OFF && !s.breakingEnabled) vm.setBreakingEnabled(true)
            vm.setRealtimeAlerts(st != HubState.OFF); setSilent("realtime", st == HubState.SILENT)
        })
        val gOp = tr(R.string.hub_group_following)
        fun follow(label: String, key: String) {
            val ch = "op:$key"
            add(HubItem(gOp, label, state(key !in s.opinionMuted, ch), true) { st ->
                vm.setOpinionMuted(key, st == HubState.OFF); setSilent(ch, st == HubState.SILENT)
            })
        }
        s.followedAuthors.forEach { follow(it, news.openwire.opinion.writerKey(it)) }
        s.followedAccounts.forEach { follow("@$it", news.openwire.opinion.accountKey(it)) }
        s.opinionTabs.forEach { follow(it, news.openwire.opinion.topicKey(it)) }
        val gBrief = tr(R.string.audio_briefing)
        s.briefing.slots.forEach { slot ->
            val label = tr(when (slot.kind) {
                news.openwire.domain.model.BriefingSlot.Kind.MORNING -> R.string.slot_morning
                news.openwire.domain.model.BriefingSlot.Kind.AFTERNOON -> R.string.slot_afternoon
                news.openwire.domain.model.BriefingSlot.Kind.EVENING -> R.string.slot_evening
            })
            add(HubItem(gBrief, label, if (slot.enabled) HubState.ON else HubState.OFF, false) { st ->
                vm.setBriefing { b -> b.copy(slots = b.slots.map { if (it.kind == slot.kind) it.copy(enabled = st != HubState.OFF) else it }) }
            })
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hint(tr(R.string.hub_hint))
        if (!s.notificationsEnabled) Text(tr(R.string.hub_master_off), style = sans(12.sp, FontWeight.Medium, lineHeight = 17.sp), color = Amber400)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ALL" to R.string.hub_all, "SILENT" to R.string.hub_silent, "ON" to R.string.hub_on, "OFF" to R.string.hub_off).forEach { (k, r) ->
                val n = if (k == "ALL") items.size else items.count { it.state.name == k }
                Chip("${tr(r)} · $n", selected = filter == k) { filter = k }
            }
        }
        val shown = if (filter == "ALL") items else items.filter { it.state.name == filter }
        if (shown.isEmpty()) Hint(tr(R.string.hub_none))
        shown.groupBy { it.group }.forEach { (group, list) ->
            Section {
                Label(group)
                list.forEachIndexed { i, it ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(it.label, style = sans(14.sp, FontWeight.Medium), color = if (it.state == HubState.OFF) g.textMuted else g.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        HubSwitch(it.state, it.canSilence, it.set)
                    }
                }
            }
        }
    }
}

/** Off · Silent · On (Silent only where the alert can arrive without sound). */
@Composable
private fun HubSwitch(state: HubState, canSilence: Boolean, onChange: (HubState) -> Unit) {
    val g = Glass
    Row(Modifier.clip(CircleShape).background(g.chipFill).border(1.dp, g.hairline, CircleShape).padding(3.dp)) {
        HubState.entries.filter { canSilence || it != HubState.SILENT }.forEach { st ->
            val on = st == state
            val c = when (st) { HubState.OFF -> g.textSecondary; HubState.SILENT -> Amber400; HubState.ON -> g.accent }
            Text(
                tr(when (st) { HubState.OFF -> R.string.hub_off; HubState.SILENT -> R.string.hub_silent; HubState.ON -> R.string.hub_on }),
                style = sans(11.5.sp, if (on) FontWeight.SemiBold else FontWeight.Medium),
                color = if (on) Color.White else g.textSecondary,
                modifier = Modifier.clip(CircleShape).background(if (on) c.copy(alpha = if (st == HubState.OFF) .35f else .9f) else Color.Transparent)
                    .clickable { onChange(st) }.padding(horizontal = 11.dp, vertical = 7.dp),
            )
        }
    }
}

/** Sounds on or off, pause for a while, and daily quiet hours, all inside Editrove. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlertsQuietSection(vm: SettingsViewModel, s: news.openwire.domain.model.UserSettings) {
    val context = LocalContext.current
    val a = s.alerts
    val zone = java.time.ZoneId.systemDefault()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(a.pausedUntil) { while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(30_000) } }
    fun clock(ms: Long) = java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).format(java.time.Instant.ofEpochMilli(ms).atZone(zone))
    fun clockOf(minute: Int) = java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).format(java.time.LocalTime.of(minute / 60, minute % 60))
    Section {
        Label(tr(R.string.alerts_sound_quiet))
        SwitchRow(tr(R.string.alerts_sound), tr(R.string.alerts_sound_sub), a.sound, enabled = s.notificationsEnabled) { on -> vm.updateAlerts { it.copy(sound = on) } }
        Divider()
        SwitchRow(tr(R.string.alerts_breaking_sound), tr(R.string.alerts_breaking_sound_sub), a.breakingSound, enabled = s.notificationsEnabled) { on -> vm.updateAlerts { it.copy(breakingSound = on) } }
        Divider()
        Spacer(Modifier.height(8.dp))
        Label(tr(R.string.alerts_pause))
        if (a.pausedUntil > now) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.NotificationsPaused, null, tint = Glass.accentSoft, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr(R.string.alerts_paused_until, clock(a.pausedUntil)), style = sans(13.5.sp, FontWeight.SemiBold), color = Glass.accentSoft, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                OutlineButton(tr(R.string.alerts_resume), icon = Icons.Filled.PlayArrow, compact = true) { vm.updateAlerts { it.copy(pausedUntil = 0L) } }
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 8).forEach { h -> Chip(if (h == 1) tr(R.string.one_hour) else tr(R.string.n_hours, h), selected = false) { vm.updateAlerts { it.copy(pausedUntil = System.currentTimeMillis() + h * 3_600_000L) } } }
                Chip(tr(R.string.alerts_until_morning), selected = false) { vm.updateAlerts { it.copy(pausedUntil = news.openwire.domain.alerts.QuietTime.nextMorning(System.currentTimeMillis())) } }
            }
        }
        Spacer(Modifier.height(8.dp))
        Divider()
        SwitchRow(
            tr(R.string.alerts_quiet_hours), tr(R.string.alerts_quiet_hours_sub, clockOf(a.quietStart), clockOf(a.quietEnd)),
            a.quietEnabled,
        ) { on -> vm.updateAlerts { it.copy(quietEnabled = on) } }
        if (a.quietEnabled) {
            fun pick(minute: Int, set: (Int) -> Unit) = android.app.TimePickerDialog(
                context, { _, h, m -> set(h * 60 + m) }, minute / 60, minute % 60, android.text.format.DateFormat.is24HourFormat(context),
            ).show()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr(R.string.alerts_from), style = sans(12.sp), color = Glass.textSecondary)
                Chip(clockOf(a.quietStart), selected = true) { pick(a.quietStart) { v -> vm.updateAlerts { it.copy(quietStart = v) } } }
                Text(tr(R.string.alerts_to), style = sans(12.sp), color = Glass.textSecondary)
                Chip(clockOf(a.quietEnd), selected = true) { pick(a.quietEnd) { v -> vm.updateAlerts { it.copy(quietEnd = v) } } }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(tr(R.string.alerts_quiet_hint), style = sans(11.sp, lineHeight = 16.sp), color = Glass.textSecondary)
    }
}

private fun refreshLabel(minutes: Int, long: Boolean = false): String = when {
    minutes < 60 -> if (long) tr(R.string.every_minutes, minutes) else tr(R.string.n_min, minutes)
    minutes == 60 -> if (long) tr(R.string.once_an_hour) else tr(R.string.one_hour)
    else -> if (long) tr(R.string.every_hours, minutes / 60) else tr(R.string.n_hours, minutes / 60)
}

// ---- Appearance --------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearancePage(vm: SettingsViewModel, themeMode: ThemeMode, onTheme: (ThemeMode) -> Unit) {
    val s by vm.settings.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Section {
            Label(tr(R.string.theme))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ThemeMode.entries.forEach { m -> Chip(themeLabel(m), selected = m == themeMode) { onTheme(m) } } }
        }
        Section {
            Label(tr(R.string.text_size))
            androidx.compose.runtime.CompositionLocalProvider(news.openwire.ui.home.LocalTextScale provides s.textScale) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    news.openwire.ui.home.TextSizeControl(onChange = vm::setTextScale)
                    Spacer(Modifier.width(12.dp))
                    Text("${(s.textScale * 100).toInt()}%", style = mono(12.sp, FontWeight.SemiBold), color = Glass.textSecondary)
                    if (s.textScale != 1f) {
                        Spacer(Modifier.weight(1f))
                        OutlineButton(tr(R.string.reset), compact = true) { vm.setTextScale(1f) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // A sample paragraph at the chosen size.
            Text(
                tr(R.string.text_size_sample),
                style = sans((14.5f * s.textScale).sp, lineHeight = (25f * s.textScale).sp), color = Glass.textPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(tr(R.string.text_size_hint), style = sans(11.sp, lineHeight = 16.sp), color = Glass.textSecondary)
        }
        Section {
            Label(tr(R.string.unfolded_layout))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                news.openwire.domain.model.UnfoldedLayout.entries.forEach { m -> Chip(m.text, selected = m == s.unfoldedLayout) { vm.setUnfoldedLayout(m) } }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                tr(R.string.layout_hint),
                style = sans(11.sp, lineHeight = 16.sp), color = Glass.textSecondary,
            )
        }
        Section {
            Label(tr(R.string.feed_order))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortMode.entries.forEach { m -> Chip(m.text, selected = m == s.sortMode) { vm.setSortMode(m) } }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                tr(R.string.order_hint),
                style = sans(11.sp, lineHeight = 16.sp), color = Glass.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr(R.string.tune), style = sans(14.sp, FontWeight.SemiBold), color = Glass.textPrimary)
                    Text(
                        tr(R.string.tune_sub),
                        style = sans(11.sp, lineHeight = 16.sp), color = Glass.textSecondary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                GlassSwitch(s.personalize) { vm.setPersonalize(it) }
            }
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
            .onSuccess { Toast.makeText(context, tr(R.string.exported), Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, tr(R.string.save_failed), Toast.LENGTH_SHORT).show() }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
        if (text == null) Toast.makeText(context, tr(R.string.read_failed), Toast.LENGTH_SHORT).show() else vm.importJson(text)
    }
    var confirmReset by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hint(tr(R.string.backup_hint))
        PrimaryButton(tr(R.string.export_settings)) { exporter.launch("openwire_settings_${LocalDate.now()}.json") }
        SecondaryButton(tr(R.string.import_settings)) { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }
        // First-run setup again, starting from what's set now.
        Spacer(Modifier.height(4.dp))
        Hint(tr(R.string.onb_run_again_sub))
        SecondaryButton(tr(R.string.onb_run_again)) {
            news.openwire.onboarding.OnboardingGate.rerun = true
            news.openwire.onboarding.OnboardingGate.showing = true
        }
        Spacer(Modifier.height(8.dp))
        Label(tr(R.string.danger_zone))
        DangerButton(tr(R.string.reset_everything)) { confirmReset = true }
        if (confirmReset) ResetEverythingDialog(onDismiss = { confirmReset = false }) {
            vm.resetEverything(); confirmReset = false
            Toast.makeText(context, tr(R.string.reset_everything_done), Toast.LENGTH_SHORT).show()
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
            Text("Editrove", style = serif(28.sp, FontWeight.Bold), color = Glass.textPrimary)
            Text(tr(R.string.version, version), style = mono(11.sp), color = Glass.textSecondary)
            Spacer(Modifier.height(10.dp))
            Text(
                tr(R.string.about_body),
                style = sans(13.sp, lineHeight = 20.sp), color = Glass.textPrimary,
            )
        }
        Section {
            Label(tr(R.string.credits))
            Text(
                tr(R.string.credits_body),
                style = sans(13.sp, lineHeight = 20.sp), color = Glass.textPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(tr(R.string.credits_type), style = sans(12.sp, lineHeight = 18.sp), color = Glass.textSecondary)
        }
        Section {
            Label(tr(R.string.license))
            Text(tr(R.string.license_body), style = sans(13.sp, lineHeight = 20.sp), color = Glass.textPrimary)
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

/** Settings › Notifications: "Check that alerts arrive" (opens the delivery check). */
@Composable
private fun NavRowCheck(context: android.content.Context) {
    val ok = remember { news.openwire.delivery.DeliveryCheck.status(context).ok }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { news.openwire.delivery.DeliveryCheck.showing = true }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(tr(R.string.delivery_row), style = sans(14.sp, FontWeight.Medium), color = Glass.textPrimary)
            Text(tr(if (ok) R.string.delivery_row_ok else R.string.delivery_row_bad), style = sans(11.sp, lineHeight = 16.sp), color = if (ok) Glass.textSecondary else Color(0xFFFF9F0A))
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

/**
 * A quiet outlined pill: resets (accent outline, restart icon) and dismissals (neutral, no
 * icon). [compact] wraps its text for use inside a row; otherwise it spans the width.
 */
@Composable
private fun OutlineButton(text: String, icon: ImageVector? = Icons.Outlined.RestartAlt, accent: Boolean = icon != null, compact: Boolean = false, onClick: () -> Unit) {
    val g = Glass
    val tint = if (accent) g.accentSoft else g.textPrimary
    Row(
        (if (compact) Modifier else Modifier.fillMaxWidth()).clip(CircleShape)
            .background(if (accent) g.accent.copy(alpha = 0.08f) else g.chipFill)
            .border(1.dp, if (accent) g.accent.copy(alpha = 0.45f) else g.hairline, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = if (compact) 16.dp else 20.dp, vertical = if (compact) 9.dp else 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, tint = tint, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)) }
        Text(text, style = sans(13.sp, FontWeight.SemiBold), color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The one destructive button: red, filled with a warning tint, unmistakably not a casual link. */
@Composable
private fun DangerButton(text: String, onClick: () -> Unit) {
    val red = Color(0xFFE5484D)
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(red.copy(alpha = 0.14f)).border(1.5.dp, red.copy(alpha = 0.75f), CircleShape)
            .clickable(onClick = onClick).padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.WarningAmber, null, tint = Color(0xFFFF6B6F), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = sans(13.sp, FontWeight.Bold), color = Color(0xFFFF6B6F), maxLines = 1)
    }
}

/** A warning popup before Reset Everything. */
@Composable
private fun ResetEverythingDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val g = Glass
    val red = Color(0xFFE5484D)
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(g.deviceBg)
                .border(1.dp, red.copy(alpha = 0.5f), RoundedCornerShape(26.dp)).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(red.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.WarningAmber, null, tint = Color(0xFFFF6B6F), modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(tr(R.string.reset_everything_title), style = serif(22.sp, FontWeight.SemiBold), color = g.textPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(tr(R.string.reset_confirm), style = sans(13.5.sp, lineHeight = 20.sp), color = g.textSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(tr(R.string.reset_everything_undo), style = sans(12.5.sp, FontWeight.SemiBold), color = Color(0xFFFF6B6F), textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth().clip(CircleShape).background(red).clickable(onClick = onConfirm).padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) { Text(tr(R.string.reset_everything_confirm), style = sans(13.5.sp, FontWeight.Bold), color = Color.White) }
            Spacer(Modifier.height(10.dp))
            SecondaryButton(tr(R.string.action_cancel), onClick = onDismiss)
        }
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

/** "Find new sources": results from the web for what was typed, each one tap to add. */
@Composable
private fun FindSources(vm: SettingsViewModel, query: String) {
    val finder = vm.finder ?: return
    val s by vm.settings.collectAsState()
    var results by remember { mutableStateOf<List<news.openwire.data.remote.SourceFinder.Found>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val lang = news.openwire.domain.engine.Languages.effective(s.language)
    LaunchedEffect(query, lang, s.edition.country) {
        results = emptyList()
        searching = true
        kotlinx.coroutines.delay(650) // wait for a pause in typing
        finder.search(query, lang, s.edition.country).collect { results = it }
        searching = false
    }
    Section {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr(R.string.find_sources_title, query).uppercase(), style = mono(11.sp, FontWeight.SemiBold, 0.14.em), color = Glass.accentSoft, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (searching) androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Glass.accentSoft)
        }
        Spacer(Modifier.height(4.dp))
        Hint(tr(if (searching && results.isEmpty()) R.string.find_sources_searching else R.string.find_sources_hint))
        if (!searching && results.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(tr(R.string.find_sources_none), style = sans(13.sp, lineHeight = 19.sp), color = Glass.textPrimary)
        }
        results.forEachIndexed { i, f ->
            if (i > 0) Divider()
            val added = s.customFeeds.any { it.url == f.url }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                SourceLogo(f.domain, f.name, size = 30.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = sans(14.sp, FontWeight.SemiBold), color = Glass.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            f.domain,
                            f.language?.let { news.openwire.domain.engine.Languages.of(it)?.nativeName },
                            f.newestAt?.let { tr(R.string.check_newest, NewsProcessor.formatRelativeTime(it)) },
                            if (f.viaGoogle) tr(R.string.find_via_google) else null,
                        ).joinToString(" · "),
                        style = sans(11.sp), color = Glass.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    f.sample?.let { Text("“$it”", style = sans(12.sp).copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = Glass.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    tr(if (added) R.string.find_added else R.string.find_add),
                    style = sans(13.sp, FontWeight.SemiBold),
                    color = if (added) Glass.textPrimary else Color.White,
                    modifier = Modifier.clip(CircleShape).background(if (added) Glass.chipFill else Glass.accent)
                        .clickable { if (added) vm.removeFound(f.url) else vm.addFound(f) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
    }
}

/**
 * (+) on a source section: add a feed by its address and a name, or search the web for an outlet
 * and pick any of its feeds (every section of it: World, Business, Tech…). Added feeds join the
 * section's topic.
 */
@Composable
private fun AddToSectionDialog(vm: SettingsViewModel, category: String, onDismiss: () -> Unit, topicId: String? = null) {
    val g = Glass
    val s by vm.settings.collectAsState()
    val topic = remember(category, topicId) { topicId ?: vm.topicForCategory(category) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<news.openwire.data.remote.SourceFinder.Found>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val lang = news.openwire.domain.engine.Languages.effective(s.language)
    LaunchedEffect(q) {
        results = emptyList()
        if (q.trim().length < 2) { searching = false; return@LaunchedEffect }
        searching = true
        kotlinx.coroutines.delay(600)
        vm.finder?.search(q.trim(), lang, s.edition.country, sections = true)?.collect { results = it }
        searching = false
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 640.dp).clip(RoundedCornerShape(26.dp))
                .background(g.deviceBg).border(1.dp, g.hairline, RoundedCornerShape(26.dp)).padding(18.dp),
        ) {
            Text(tr(R.string.src_add_here, category).uppercase(), style = mono(11.sp, FontWeight.SemiBold, 0.14.em), color = g.accentSoft)
            Spacer(Modifier.height(10.dp))
            Field(tr(R.string.src_feed_name), name, { name = it })
            Spacer(Modifier.height(8.dp))
            Field(tr(R.string.src_feed_url), url, { url = it }, placeholder = "https://example.com/rss")
            Spacer(Modifier.height(10.dp))
            PrimaryButton(if (busy) tr(R.string.checking) else tr(R.string.verify_add)) {
                if (busy || url.isBlank()) return@PrimaryButton
                busy = true
                vm.addSource(name, url, listOf(topic), 8) { ok -> busy = false; if (ok) onDismiss() }
            }
            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(12.dp))
            Text(tr(R.string.src_search_web).uppercase(), style = mono(11.sp, FontWeight.SemiBold, 0.14.em), color = g.textSecondary)
            Spacer(Modifier.height(8.dp))
            Field(tr(R.string.search_sources), q, { q = it }, placeholder = tr(R.string.find_sources_eg))
            if (searching) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = g.accent)
                Spacer(Modifier.width(8.dp))
                Text(tr(R.string.find_sources_searching), style = sans(12.sp), color = g.textSecondary)
            }
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).padding(top = 6.dp)) {
                items(results.size) { i ->
                    val f = results[i]
                    val added = s.customFeeds.any { it.url == f.url }
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SourceLogo(f.domain, f.name, size = 26.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.name, style = sans(13.5.sp, FontWeight.SemiBold), color = g.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(f.domain, f.newestAt?.let { tr(R.string.check_newest, NewsProcessor.formatRelativeTime(it)) }, if (f.viaGoogle) tr(R.string.find_via_google) else null).joinToString(" · "),
                                style = sans(11.sp), color = g.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            tr(if (added) R.string.find_added else R.string.find_add), style = sans(12.5.sp, FontWeight.SemiBold),
                            color = if (added) g.textPrimary else Color.White,
                            modifier = Modifier.clip(CircleShape).background(if (added) g.chipFill else g.accent)
                                .clickable { if (added) vm.removeFound(f.url) else vm.addFoundTo(f, topic) }.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlineButton(tr(R.string.action_cancel), icon = null, onClick = onDismiss)
        }
    }
}

/** A search to start the Sources page with (from a deep link), used once. */
object SourcesQuery {
    @Volatile var pending: String? = null
    fun take(): String = (pending ?: "").also { pending = null }
}
