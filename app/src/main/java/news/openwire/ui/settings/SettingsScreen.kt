// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.settings

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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onGloballyPositioned
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
    HOME(R.string.settings), EDITION(R.string.edition), LANGUAGE(R.string.language), TOPICS(R.string.topics), ADD_TOPIC(R.string.new_topic), SOURCES(R.string.sources),
    ADD_SOURCE(R.string.add_source), RULES(R.string.paywall_rules), NOTIFICATIONS(R.string.notifications_refresh),
    APPEARANCE(R.string.appearance), BRIEFING(R.string.audio_briefing), BACKUP(R.string.backup_restore), UPDATES(R.string.updates_changelog), GUIDE(R.string.g_title), ABOUT(R.string.about_editrove),
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
            AnimatedContent(page.title, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "title") {
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
                        Page.TOPICS -> topicsPage(vm) { page = Page.ADD_TOPIC }
                        Page.ADD_TOPIC -> item { AddTopicPage(vm) { page = Page.TOPICS } }
                        Page.SOURCES -> sourcesPage(vm) { page = Page.ADD_SOURCE }
                        Page.ADD_SOURCE -> item { AddSourcePage(vm) { page = Page.SOURCES } }
                        Page.RULES -> rulesPage(vm)
                        Page.NOTIFICATIONS -> item { NotificationsPage(vm) }
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

private fun LazyListScope.topicsPage(vm: SettingsViewModel, onAdd: () -> Unit) {
    item { Hint(tr(R.string.topics_hint)) }
    item { PrimaryButton(tr(R.string.topics_new_button), Icons.Filled.Add, onAdd) }
    item {
        val topics by vm.allTopics.collectAsState()
        val s by vm.settings.collectAsState()
        // Drag a topic by its handle to reorder (move up / down stay available to accessibility services).
        var dragging by remember { mutableStateOf<String?>(null) }
        var dragOffset by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
        val rowHeights = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
        val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
        Section {
            topics.forEachIndexed { i, t ->
                androidx.compose.runtime.key(t.id) {
                    if (i > 0) Divider()
                    val lifted = dragging == t.id
                    val elevation by androidx.compose.animation.core.animateDpAsState(if (lifted) 10.dp else 0.dp, label = "lift")
                    Row(
                        Modifier.fillMaxWidth()
                            .onGloballyPositioned { rowHeights[t.id] = it.size.height }
                            .zIndex(if (lifted) 1f else 0f)
                            .graphicsLayer { translationY = if (lifted) dragOffset else 0f; scaleX = if (lifted) 1.02f else 1f; scaleY = if (lifted) 1.02f else 1f }
                            .shadow(elevation, RoundedCornerShape(14.dp))
                            .background(if (lifted) Glass.deviceBg else Color.Transparent, RoundedCornerShape(14.dp))
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
                                    onDragEnd = { dragging = null; dragOffset = 0f },
                                    onDragCancel = { dragging = null; dragOffset = 0f },
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
                        val notify = t.id in s.notifyTopics
                        SmallIcon(
                            if (notify) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsNone,
                            tr(if (notify) R.string.cd_stop_notify else R.string.cd_notify_me), tint = if (notify) Glass.accent else null,
                        ) { vm.setNotifyTopic(t.id, !notify) }
                        if (t.isCustom) SmallIcon(Icons.Outlined.Delete, tr(R.string.cd_delete_topic)) { vm.deleteTopic(t.id) }
                        GlassSwitch(t.isVisible) { vm.setTopicVisible(t.id, it) }
                    }
                }
            }
        }
    }
    item { TextButton(tr(R.string.topics_reset)) { vm.resetTopics() } }
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
        Hint(tr(R.string.add_topic_hint))
        val ideas by vm.keywordIdeas.collectAsState()
        // Presets matching what's typed ("Middle E" → Middle East with Iran, Israel, Lebanon, …).
        val matches = remember(name) { news.openwire.domain.engine.TopicPresets.suggest(name) }
        val exact = matches.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
        Section {
            Label(tr(R.string.popular_topics))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                news.openwire.domain.engine.TopicPresets.POPULAR.forEach { p -> Chip(p.name, selected = name == p.name) { name = p.name; keywords = p.keywordText; vm.clearKeywordIdeas() } }
            }
        }
        Section {
            Field(tr(R.string.topic_name), name, { name = it; vm.clearKeywordIdeas() }, placeholder = tr(R.string.topic_name_eg))
            if (matches.isNotEmpty() && exact?.keywordText != keywords) {
                Spacer(Modifier.height(6.dp))
                matches.forEach { p ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { name = p.name; keywords = p.keywordText; vm.clearKeywordIdeas() }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                    ) {
                        Text(p.name, style = sans(14.sp, FontWeight.SemiBold), color = Glass.textPrimary)
                        Text(p.keywordText, style = sans(11.sp, lineHeight = 15.sp), color = Glass.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Field(tr(R.string.keywords_field), keywords, { keywords = it }, placeholder = tr(R.string.keywords_eg))
            // Related keywords, live: each keyword typed or picked brings its own headlines, and
            // terms that recur across them surface next (a chain that sharpens as the topic grows).
            val sug by vm.topicSuggest.collectAsState()
            LaunchedEffect(name, keywords) {
                kotlinx.coroutines.delay(650)
                vm.updateTopicSeeds(name, TopicRegistry.parseCommaKeywords(keywords))
            }
            val current = TopicRegistry.parseCommaKeywords(keywords).map { it.lowercase() }.toSet()
            val fresh = sug.keywords.filter { it.lowercase() !in current }
            if (name.isNotBlank() || keywords.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(R.string.related_keywords).uppercase(), style = mono(10.sp, FontWeight.SemiBold, 0.14.em), color = Glass.accentSoft, modifier = Modifier.weight(1f))
                    if (sug.loading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Glass.accent)
                }
                Spacer(Modifier.height(6.dp))
                if (fresh.isEmpty() && !sug.loading) Text(tr(R.string.related_keywords_hint), style = sans(11.sp, lineHeight = 16.sp), color = Glass.textSecondary)
                androidx.compose.animation.AnimatedContent(fresh, label = "ideas") { list ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        list.forEach { k ->
                            Chip("+ $k", selected = false) { keywords = (TopicRegistry.parseCommaKeywords(keywords) + k).joinToString(", ") }
                        }
                    }
                }
            }
        }
        // Outlets that cover these keywords most, across all of them (logos, how many stories).
        val sug2 by vm.topicSuggest.collectAsState()
        val pickedOutlets = remember { mutableStateListOf<String>() }
        if (sug2.outlets.isNotEmpty()) {
            Section {
                Label(tr(R.string.suggested_sources))
                Text(tr(R.string.suggested_sources_hint), style = sans(11.sp, lineHeight = 16.sp), color = Glass.textSecondary)
                Spacer(Modifier.height(6.dp))
                sug2.outlets.forEach { o ->
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
                            Text(o.name, style = sans(13.sp, FontWeight.Medium), color = Glass.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(tr(R.string.suggested_source_hits, o.hits, o.domain), style = sans(11.sp), color = Glass.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        CheckDot(on)
                    }
                }
            }
        }
        Section {
            Label(tr(R.string.sources_optional, chosen.size))
            Field(tr(R.string.filter_sources), filter, { filter = it })
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
        PrimaryButton(tr(R.string.create_topic)) {
            val extra = vm.topicSuggest.value.outlets.filter { it.domain in pickedOutlets }
            if (vm.addTopic(name, keywords, chosen.toList(), extra)) onDone()
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
            SourceLibrary.groupSources(library, filter).forEach { group ->
                Section {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(group.category.uppercase(), style = mono(11.sp, FontWeight.SemiBold, 0.14.em), color = Glass.accentSoft, modifier = Modifier.weight(1f))
                        Text("${group.all.count { it.enabled }}/${group.all.size}", style = mono(11.sp), color = Glass.textSecondary)
                        Spacer(Modifier.width(8.dp))
                        GlassSwitch(group.all.all { it.enabled }) { vm.setGroupEnabled(group.all, it) }
                    }
                    listOf(tr(R.string.aggregators) to group.aggregators, tr(R.string.individual_feeds) to group.publishers).forEach { (title, list) ->
                        if (list.isEmpty()) return@forEach
                        Spacer(Modifier.height(8.dp))
                        Text(title, style = sans(11.sp, FontWeight.Medium), color = Glass.textSecondary)
                        list.forEach { src -> SourceRow(src, checks[SourceLibrary.sourceKey(src)], checks.containsKey(SourceLibrary.sourceKey(src)), vm) }
                    }
                }
            }
        }
    }
    item { TextButton(tr(R.string.sources_reset)) { vm.resetSources() } }
}

@Composable
private fun SourceRow(src: FeedSource, check: news.openwire.data.repository.FeedCheck?, checking: Boolean, vm: SettingsViewModel) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
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
        if (checking && check == null) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Glass.accent)
        if (src.isCustom) SmallIcon(Icons.Outlined.Delete, tr(R.string.cd_remove_source)) { vm.deleteSource(src.id) }
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
        Hint(tr(R.string.add_source_hint))
        Section {
            Field(tr(R.string.name_optional), name, { name = it }, placeholder = "e.g. Polygon")
            Spacer(Modifier.height(10.dp))
            Field(tr(R.string.feed_url), url, { url = it }, placeholder = "https://www.polygon.com/rss/index.xml", keyboard = KeyboardType.Uri)
        }
        SourcesByCountry(vm)
        Section {
            Label(tr(R.string.weave_into))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                topics.forEach { t -> Chip(t.label, selected = t.id in chosen) { if (t.id in chosen) chosen.remove(t.id) else chosen.add(t.id) } }
            }
            Spacer(Modifier.height(14.dp))
            Label(tr(R.string.fair_share, quota))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { (7..10).forEach { q -> Chip("$q", selected = quota == q) { quota = q } } }
        }
        PrimaryButton(tr(if (busy) R.string.checking_feed else R.string.verify_add)) {
            if (busy) return@PrimaryButton
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
private fun SourcesByCountry(vm: SettingsViewModel) {
    val g = Glass
    var on by rememberSaveable { mutableStateOf(false) }
    var country by rememberSaveable { mutableStateOf<String?>(null) }
    var sectionName by rememberSaveable { mutableStateOf<String?>(null) }
    val section = sectionName?.let { n -> news.openwire.data.remote.SourceDirectory.Section.entries.firstOrNull { it.name == n } }
    val state by vm.directoryState.collectAsState()
    val s by vm.settings.collectAsState()
    val picked = remember { mutableStateListOf<news.openwire.data.remote.SourceDirectory.Outlet>() }
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
                        PrimaryButton(tr(R.string.add_selected, picked.size)) {
                            if (picked.isNotEmpty()) { vm.addDirectoryOutlets(picked.toList()); picked.clear() }
                        }
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

/** A labelled glass dropdown: tap to open the list, pick one. */
@Composable
private fun GlassDropdown(label: String, options: List<Pair<String?, String>>, selected: String?, modifier: Modifier = Modifier, onSelect: (String?) -> Unit) {
    val g = Glass
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = sans(11.sp, FontWeight.Medium), color = g.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        Box {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(g.chipFill).border(1.dp, g.hairline, RoundedCornerShape(14.dp))
                    .clickable { open = true }.padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(options.firstOrNull { it.first == selected }?.second.orEmpty(), style = sans(13.sp, FontWeight.Medium), color = g.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = g.textSecondary, modifier = Modifier.size(18.dp))
            }
            androidx.compose.material3.DropdownMenu(
                expanded = open, onDismissRequest = { open = false },
                modifier = Modifier.heightIn(max = 360.dp).background(g.deviceBg),
            ) {
                options.forEach { (key, name) ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(name, style = sans(13.sp, if (key == selected) FontWeight.SemiBold else FontWeight.Normal), color = if (key == selected) g.accentSoft else g.textPrimary) },
                        onClick = { onSelect(key); open = false },
                    )
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
                                if (r.isCustom) { Spacer(Modifier.height(8.dp)); TextButton(tr(R.string.rule_reset)) { vm.resetRule(r.domain) } }
                            }
                        }
                    }
                }
            }
        }
    }
    item { TextButton(tr(R.string.rules_reset_all)) { vm.resetAllRules() } }
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
private fun NotificationsPage(vm: SettingsViewModel) {
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
            SwitchRow(tr(R.string.notifications), tr(R.string.notify_master), s.notificationsEnabled) { on ->
                if (on && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else {
                    vm.setNotificationsEnabled(on)
                    if (on && !news.openwire.delivery.DeliveryCheck.status(context).ok) news.openwire.delivery.DeliveryCheck.showing = true
                }
            }
            Divider()
            NavRowCheck(context)
            Divider()
            SwitchRow(tr(R.string.bg_refresh), tr(R.string.bg_refresh_sub, refreshLabel(s.refreshMinutes, long = true)), s.backgroundRefresh) { vm.setBackgroundRefresh(it) }
        }
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
                        TextButton(tr(R.string.reset)) { vm.setTextScale(1f) }
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
        Spacer(Modifier.height(8.dp))
        if (!confirmReset) TextButton(tr(R.string.reset_everything)) { confirmReset = true }
        else Section {
            Text(tr(R.string.reset_confirm), style = sans(13.sp, lineHeight = 19.sp), color = Glass.textPrimary)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { SecondaryButton(tr(R.string.action_cancel)) { confirmReset = false } }
                Box(Modifier.weight(1f)) { PrimaryButton(tr(R.string.reset)) { vm.resetEverything(); confirmReset = false } }
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

/** A search to start the Sources page with (from a deep link), used once. */
object SourcesQuery {
    @Volatile var pending: String? = null
    fun take(): String = (pending ?: "").also { pending = null }
}
