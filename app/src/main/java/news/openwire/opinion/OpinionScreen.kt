// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.opinion

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import news.openwire.R
import news.openwire.data.remote.OpinionSource
import news.openwire.domain.engine.NewsProcessor
import news.openwire.domain.model.Article
import news.openwire.domain.opinion.OpinionCatalog
import news.openwire.domain.opinion.OpinionKind
import news.openwire.domain.opinion.SocialPost
import news.openwire.domain.opinion.Trending
import news.openwire.domain.opinion.Voice
import news.openwire.i18n.tr
import news.openwire.ui.home.SourceLogo
import news.openwire.ui.home.LocalArticleExcerpt
import news.openwire.ui.home.mono
import news.openwire.ui.home.sans
import news.openwire.ui.home.serif
import news.openwire.ui.theme.Glass

/**
 * The op-ed page's own look: a newspaper's comment pages. Night is warm charcoal with
 * cream type and an ochre accent; day is cream paper with ink type and an oxblood accent.
 */
private data class OpEdPalette(
    val bg: Color, val surface: Color, val text: Color, val dim: Color, val accent: Color, val rule: Color, val onAccent: Color,
)

private val Night = OpEdPalette(
    bg = Color(0xFF15120E), surface = Color(0xFF1F1A14), text = Color(0xFFF1E8D8), dim = Color(0xFFAFA491),
    accent = Color(0xFFE3A857), rule = Color(0xFF3A3229), onAccent = Color(0xFF1B140B),
)
private val Day = OpEdPalette(
    bg = Color(0xFFF7F0E1), surface = Color(0xFFFFFBF2), text = Color(0xFF1B1712), dim = Color(0xFF5E5547),
    accent = Color(0xFF8E2B2B), rule = Color(0xFFD9CDB6), onAccent = Color(0xFFFFF8EC),
)

/** What the page needs from the app. */
class OpinionActions(
    val openArticle: (Article) -> Unit,
    val openLink: (String) -> Unit,
    val toggleAuthor: (String) -> Unit,
    val toggleAccount: (String) -> Unit,
    val addTab: (String) -> Unit,
    val removeTab: (String) -> Unit,
    /** Alerts on/off for a followed writer ("a:"), account ("b:") or topic ("t:"). */
    val toggleMute: (String) -> Unit = {},
)

/** Alert keys: a writer, a Bluesky account, an opinion topic. */
internal fun writerKey(name: String) = "a:" + OpinionCatalog.authorKey(name)
internal fun accountKey(handle: String) = "b:$handle"
internal fun topicKey(topic: String) = "t:" + topic.trim().lowercase()

/** What a card's bell shows and does: off (not followed), on, or muted. */
private class Bells(val authors: Set<String>, val accounts: Set<String>, val tabs: List<String>, val muted: Set<String>)
private val LocalBells = androidx.compose.runtime.staticCompositionLocalOf { Bells(emptySet(), emptySet(), emptyList(), emptySet()) }

/** Every story on the page, for "More from …" indexes in columns whose text can't be read. */
private val LocalPageStories = androidx.compose.runtime.staticCompositionLocalOf<List<Article>> { emptyList() }

private const val FOLLOWING = "\u0000following"
private const val ALL = "\u0000all"
private const val ANALYSIS = "\u0000analysis"

@Composable
fun OpinionScreen(
    padding: PaddingValues,
    source: OpinionSource,
    appLanguage: String,
    news: List<Article>,
    followedAuthors: Set<String>,
    followedAccounts: Set<String>,
    tabs: List<String>,
    actions: OpinionActions,
    muted: Set<String> = emptySet(),
) {
    val c = if (Glass.isDark) Night else Day
    var discover by rememberSaveable { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(if (followedAuthors.isEmpty() && followedAccounts.isEmpty()) ALL else FOLLOWING) }
    var refresh by remember { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }

    val columns by produceState<List<OpinionSource.Column>?>(null, appLanguage, refresh) {
        refreshing = refresh > 0
        value = source.columns(appLanguage, force = refresh > 0)
        refreshing = false
    }
    // Start reading the stories' text as soon as the columns arrive, so short columns are filled
    // by the time they're scrolled to (three at a time; results are kept for the session).
    val excerpt = LocalArticleExcerpt.current
    LaunchedEffect(columns) {
        val list = columns ?: return@LaunchedEffect
        if (excerpt == null) return@LaunchedEffect
        list.filter { it.language == appLanguage || it.language == "en" }.take(30).forEach { col ->
            launch { runCatching { excerpt(col.article) } }
        }
    }
    // Suggestions in the page's own script (an English headline doesn't suggest a tab on an Arabic page).
    val trending = remember(news, appLanguage) {
        Trending.topics(news, max = 12).filter { t -> tabs.none { it.equals(t, true) } }
            .filter { t -> (appLanguage == "ar") == t.any { it in '\u0600'..'\u06FF' } }.take(10)
    }

    val pageStories = remember(columns) { columns.orEmpty().map { it.article } }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalBells provides Bells(followedAuthors.map(OpinionCatalog::authorKey).toSet(), followedAccounts, tabs, muted),
        LocalPageStories provides pageStories,
    ) {
    Box(Modifier.fillMaxSize().background(c.bg)) {
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item("head") { Column(Modifier.widthIn(max = 1100.dp).fillMaxWidth()) { Header(c, refreshing, onDiscover = { discover = true }) { refresh++ } } }
            item("tabs") {
                Column(Modifier.widthIn(max = 1100.dp).fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        TabChip(c, tr(R.string.op_following), tab == FOLLOWING) { tab = FOLLOWING }
                        TabChip(c, tr(R.string.op_all), tab == ALL) { tab = ALL }
                        TabChip(c, tr(R.string.op_analysis), tab == ANALYSIS) { tab = ANALYSIS }
                        tabs.forEach { t -> TabChip(c, t, tab == t, onRemove = { actions.removeTab(t); if (tab == t) tab = ALL }) { tab = t } }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
                    // Results fill in as you type: writers, columns, a live search, accounts.
                    SearchField(c, query, { query = it }) { q -> if (q.isNotBlank()) { actions.addTab(q.trim()); tab = q.trim(); query = "" } }
                    if (query.isBlank() && trending.isNotEmpty() && (tab == ALL || tab == FOLLOWING)) {
                        Trending(c, trending) { t -> actions.addTab(t); tab = t }
                    }
                }
            }
            if (query.trim().length >= 2) {
                searchResults(c, source, query.trim(), columns, appLanguage, followedAuthors, followedAccounts, actions,
                    onAddTab = { q -> actions.addTab(q); tab = q; query = "" })
            } else when (tab) {
                FOLLOWING -> followingTab(c, source, columns, followedAuthors, followedAccounts, appLanguage, actions, refresh) { discover = true }
                ALL, ANALYSIS -> {
                    val kind = if (tab == ALL) OpinionKind.OPINION else OpinionKind.ANALYSIS
                    val list = columns
                    if (list == null) item("loading") { Status(c, tr(R.string.op_loading)) }
                    else {
                        val mine = list.filter { it.kind == kind && it.language == appLanguage }.map { it.article }
                        val english = if (appLanguage == "en") emptyList() else list.filter { it.kind == kind && it.language == "en" }.map { it.article }
                        columnItems(c, "m", mine, followedAuthors, actions)
                        if (english.isNotEmpty()) {
                            item("en-head") { Rule(c, tr(R.string.op_in_english)) }
                            columnItems(c, "e", english, followedAuthors, actions)
                        }
                        if (mine.isEmpty() && english.isEmpty()) item("none") { Status(c, tr(R.string.op_nothing)) }
                    }
                }
                else -> topicTab(c, source, tab, columns, appLanguage, followedAuthors, followedAccounts, actions, refresh)
            }
            item("note") {
                Text(tr(R.string.op_social_note), style = sans(11.sp, lineHeight = 16.sp), color = c.dim,
                    modifier = Modifier.widthIn(max = 1100.dp).fillMaxWidth().padding(vertical = 16.dp))
            }
        }
        // Writers, columns, voices and trending topics to follow: a page of their own.
        if (discover) DiscoverSheet(c, columns, appLanguage, followedAuthors, followedAccounts, trending, actions, onClose = { discover = false }) { t -> tab = t }
    }
    }
}

/** The (+) page: everything there is to follow, without pushing what you follow down the page. */
@Composable
private fun DiscoverSheet(
    c: OpEdPalette, columns: List<OpinionSource.Column>?, appLanguage: String, authors: Set<String>, accounts: Set<String>,
    trending: List<String>, actions: OpinionActions, onClose: () -> Unit, onTab: (String) -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(c.bg)) {
            LazyColumn(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item("d-head") {
                    Row(Modifier.widthIn(max = 1100.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(R.string.op_discover_title), style = serif(28.sp, FontWeight.Bold), color = c.text, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.Close, tr(R.string.action_close), tint = c.text, modifier = Modifier.clip(CircleShape).clickable(onClick = onClose).padding(8.dp).size(22.dp))
                    }
                    Text(tr(R.string.op_discover_sub), style = serif(15.sp, lineHeight = 21.sp).copy(fontStyle = FontStyle.Italic), color = c.dim, modifier = Modifier.widthIn(max = 1100.dp).fillMaxWidth())
                }
                if (trending.isNotEmpty()) item("d-trend") {
                    Column(Modifier.widthIn(max = 1100.dp).fillMaxWidth()) { Trending(c, trending) { t -> actions.addTab(t); onTab(t); onClose() } }
                }
                val writers = columnists(columns.orEmpty(), appLanguage, authors)
                if (columns == null) item("d-load") { Status(c, tr(R.string.op_loading)) }
                writers.groupBy { it.language }.forEach { (lang, list) ->
                    item("d-cw-$lang") { Rule(c, tr(R.string.op_columnists, news.openwire.domain.engine.Languages.of(lang)?.nativeName ?: lang)) }
                    items(list.take(20), key = { "dw" + it.key }) { w ->
                        val on = OpinionCatalog.authorKey(w.name) in authors.map(OpinionCatalog::authorKey)
                        WriterRow(c, w, followed = on) { actions.toggleAuthor(w.name) }
                    }
                }
                val voices = OpinionCatalog.voicesFor(appLanguage)
                if (voices.isNotEmpty()) {
                    item("d-voices") { Rule(c, tr(R.string.op_on_bluesky)) }
                    items(voices, key = { "dv" + it.handle }) { v -> VoiceRow(c, v, followed = v.handle in accounts) { actions.toggleAccount(v.handle) } }
                }
            }
        }
    }
}

/** The alerts bell on a card: on for what you follow (tap to mute), off otherwise (tap to follow, with alerts). */
@Composable
private fun CardBell(c: OpEdPalette, key: String, followed: Boolean, onFollow: () -> Unit, onToggle: () -> Unit) {
    val bells = LocalBells.current
    val on = followed && key !in bells.muted
    Icon(
        if (on) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsNone,
        tr(if (on) R.string.cd_stop_notify else R.string.cd_notify_me),
        tint = if (on) c.accent else c.dim,
        modifier = Modifier.clip(CircleShape).clickable { if (!followed) onFollow() else onToggle() }.padding(6.dp).size(16.dp),
    )
}

private fun LazyListScope.followingTab(
    c: OpEdPalette, source: OpinionSource, columns: List<OpinionSource.Column>?, authors: Set<String>, accounts: Set<String>,
    appLanguage: String, actions: OpinionActions, refresh: Int, onDiscover: () -> Unit,
) {
    val byWriters = OpinionCatalog.byAuthors(columns.orEmpty().map { it.article }, authors)
    if (authors.isEmpty() && accounts.isEmpty()) item("f-empty") {
        Column(Modifier.widthIn(max = 1100.dp).fillMaxWidth().padding(vertical = 18.dp)) {
            Text("“", style = serif(64.sp, FontWeight.Bold, lineHeight = 50.sp), color = c.accent)
            Text(tr(R.string.op_follow_empty), style = serif(19.sp, lineHeight = 27.sp).copy(fontStyle = FontStyle.Italic), color = c.text)
        }
    }
    item("f-discover") {
        Row(
            Modifier.widthIn(max = 1100.dp).fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, c.rule, RoundedCornerShape(12.dp))
                .clickable(onClick = onDiscover).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, null, tint = c.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(tr(R.string.op_discover_cta), style = sans(14.sp, FontWeight.SemiBold), color = c.text)
        }
    }
    if (byWriters.isNotEmpty()) {
        item("fw") { Rule(c, tr(R.string.op_from_writers)) }
        columnItems(c, "fw", byWriters, authors, actions)
    }
    if (accounts.isNotEmpty()) {
        item("fa") { Rule(c, tr(R.string.op_from_accounts)) }
        item("fa-list") {
            val posts by produceState<List<SocialPost>?>(null, accounts, refresh) { value = source.following(accounts, force = refresh > 0) }
            Column(Modifier.widthIn(max = 1100.dp).fillMaxWidth()) {
                when {
                    posts == null -> Status(c, tr(R.string.op_loading))
                    posts!!.isEmpty() -> Status(c, tr(R.string.op_nothing))
                    else -> posts!!.take(40).forEach { PostCard(c, it, followed = true, actions) }
                }
            }
        }
    }
}

private fun LazyListScope.topicTab(
    c: OpEdPalette, source: OpinionSource, topic: String, columns: List<OpinionSource.Column>?, appLanguage: String,
    authors: Set<String>, accounts: Set<String>, actions: OpinionActions, refresh: Int,
) {
    item("t-cols") {
        val searched by produceState<List<Article>?>(null, topic, appLanguage, refresh) { value = source.topicColumns(topic, appLanguage, force = refresh > 0) }
        val matching = remember(columns, topic) {
            columns.orEmpty().map { it.article }.filter { a -> a.title.contains(topic, true) || a.summary?.contains(topic, true) == true }
        }
        val list = (matching + searched.orEmpty()).distinctBy { it.title.lowercase() }.take(30)
        Column(Modifier.widthIn(max = 1100.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Rule(c, tr(R.string.op_on_topic, topic)) }
                CardBell(c, topicKey(topic), followed = true, onFollow = {}) { actions.toggleMute(topicKey(topic)) }
            }
            when {
                searched == null && matching.isEmpty() -> Status(c, tr(R.string.op_loading))
                list.isEmpty() -> Status(c, tr(R.string.op_nothing))
                else -> { val keys = authors.map(OpinionCatalog::authorKey).toSet(); pageModules(list).forEach { ModuleView(c, it, keys, actions) } }
            }
        }
    }
    item("t-posts") {
        val posts by produceState<List<SocialPost>?>(null, topic, appLanguage, refresh) { value = source.about(topic, appLanguage, force = refresh > 0) }
        Column(Modifier.widthIn(max = 1100.dp).fillMaxWidth()) {
            Rule(c, tr(R.string.op_conversation))
            when {
                posts == null -> Status(c, tr(R.string.op_loading))
                posts!!.isEmpty() -> Status(c, tr(R.string.op_nothing))
                else -> posts!!.take(25).forEach { PostCard(c, it, followed = it.authorHandle in accounts, actions) }
            }
        }
    }
}

private fun LazyListScope.columnItems(c: OpEdPalette, prefix: String, list: List<Article>, authors: Set<String>, actions: OpinionActions) {
    val keys = authors.map(OpinionCatalog::authorKey).toSet()
    val mods = pageModules(list.take(60))
    items(mods.size, key = { i -> prefix + "mod-" + mods[i].stories.joinToString("|") { it.id } }) { i ->
        Box(Modifier.widthIn(max = 1100.dp).fillMaxWidth()) { ModuleView(c, mods[i], keys, actions) }
    }
}

/** One block of the page: a ruled, square box holding one to three stories in a fixed arrangement. */
private enum class ModuleKind { LEAD, THREE, PAIR, FEATURE, QUOTES }
private class PageModule(val kind: ModuleKind, val stories: List<Article>)

/**
 * Lays the stories out the way a comment page is made up: a lead with a side column, three
 * columns across, a photo feature, a box of pull-quotes, and round again. Leftovers at the
 * end go into the smaller blocks.
 */
private fun pageModules(list: List<Article>): List<PageModule> {
    val out = mutableListOf<PageModule>()
    if (list.isEmpty()) return out
    // The front always opens with the lead column (with its side column when there are stories for it).
    val lead = minOf(3, list.size)
    out += PageModule(ModuleKind.LEAD, list.subList(0, lead))
    val cycle = listOf(
        ModuleKind.PAIR to 2, ModuleKind.PAIR to 2, ModuleKind.THREE to 3, ModuleKind.FEATURE to 1,
        ModuleKind.PAIR to 2, ModuleKind.QUOTES to 2, ModuleKind.THREE to 3, ModuleKind.PAIR to 2,
    )
    var i = lead
    var k = 0
    while (i < list.size) {
        val (kind, n) = cycle[k % cycle.size]
        val take = minOf(n, list.size - i)
        val chosen = list.subList(i, i + take)
        out += when {
            take == 1 -> PageModule(ModuleKind.FEATURE, chosen)
            take == 2 && kind == ModuleKind.THREE -> PageModule(ModuleKind.PAIR, chosen)
            else -> PageModule(kind, chosen)
        }
        i += take; k++
    }
    return out
}

@Composable
private fun ModuleView(c: OpEdPalette, m: PageModule, followedKeys: Set<String>, actions: OpinionActions) {
    fun follows(a: Article) = a.author?.let { OpinionCatalog.authorKey(it) in followedKeys } == true
    // A square box: heavy rule on top, hairlines round the rest, column rules between cells.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        val wide = maxWidth >= 600.dp
        Column(Modifier.fillMaxWidth().border(1.dp, c.rule)) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(c.text))
            val st = m.stories
            when (m.kind) {
                ModuleKind.LEAD -> if (st.size == 1) Box(Modifier.padding(14.dp)) { StoryLead(c, st[0], follows(st[0]), actions) }
                else if (wide) Cells {
                    Cell(c, 0, Modifier.weight(2f)) { StoryLead(c, st[0], follows(st[0]), actions) }
                    Column(Modifier.weight(1f).fillMaxHeight().drawBehind { drawLine(c.rule, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), 1.dp.toPx()) }) {
                        st.drop(1).forEachIndexed { j, a ->
                            if (j > 0) HRule(c)
                            // Each brief takes its share of the column; the article's text fills what's left.
                            Box(Modifier.weight(1f).fillMaxWidth().padding(14.dp)) { StoryBrief(c, a, follows(a), actions, photo = true) }
                        }
                    }
                } else Column {
                    Box(Modifier.padding(14.dp)) { StoryLead(c, st[0], follows(st[0]), actions) }
                    if (st.size > 1) {
                        HRule(c)
                        Cells { st.drop(1).forEachIndexed { j, a -> Cell(c, j, Modifier.weight(1f)) { StoryColumn(c, a, follows(a), actions) } } }
                    }
                }
                ModuleKind.THREE -> if (wide || st.size < 3) Cells {
                    st.forEachIndexed { j, a -> Cell(c, j, Modifier.weight(1f)) { StoryColumn(c, a, follows(a), actions) } }
                } else Column {
                    Cells { st.take(2).forEachIndexed { j, a -> Cell(c, j, Modifier.weight(1f)) { StoryColumn(c, a, follows(a), actions) } } }
                    HRule(c)
                    Box(Modifier.padding(14.dp)) { StoryBrief(c, st[2], follows(st[2]), actions, photo = true) }
                }
                // Two briefs side by side, photo to the right of each headline (one under the other on a phone).
                ModuleKind.PAIR -> if (wide) Cells {
                    st.forEachIndexed { j, a -> Cell(c, j, Modifier.weight(1f)) { StoryBrief(c, a, follows(a), actions, photo = true, big = true) } }
                } else Column {
                    st.forEachIndexed { j, a -> if (j > 0) HRule(c); Box(Modifier.padding(14.dp)) { StoryBrief(c, a, follows(a), actions, photo = true) } }
                }
                ModuleKind.FEATURE -> Box(Modifier.padding(14.dp)) { StoryFeature(c, st[0], follows(st[0]), actions, sideBySide = wide) }
                ModuleKind.QUOTES -> Cells { st.forEachIndexed { j, a -> Cell(c, j, Modifier.weight(1f)) { StoryQuote(c, a, follows(a), actions) } } }
            }
        }
    }
}

/** Cells side by side, all as tall as the tallest. */
@Composable
private fun Cells(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Max), content = content)

/** One cell of a box; every cell after the first draws the column rule down its leading edge. */
@Composable
private fun Cell(c: OpEdPalette, index: Int, modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.fillMaxHeight()
            .drawBehind { if (index > 0) drawLine(c.rule, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), 1.dp.toPx()) }
            .padding(14.dp),
    ) { content() }
}

@Composable
private fun HRule(c: OpEdPalette) = Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))

@Composable
private fun Kicker(c: OpEdPalette, text: String) =
    Text(text.uppercase(), style = mono(9.5.sp, FontWeight.Bold, 0.16.em), color = c.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)

/** "By NAME" over "time · Follow · bell": the name is never squeezed by the buttons. */
@Composable
private fun SmallByline(c: OpEdPalette, a: Article, followed: Boolean, actions: OpinionActions) {
    Column(Modifier.fillMaxWidth()) {
        a.author?.let { Text(tr(R.string.op_by, it).uppercase(), style = mono(9.5.sp, FontWeight.SemiBold, 0.08.em), color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(NewsProcessor.formatRelativeTime(a.publishedAt), style = mono(9.5.sp, tracking = 0.04.em), color = c.dim, maxLines = 1, modifier = Modifier.weight(1f))
            a.author?.let { name ->
                FollowChip(c, followed) { actions.toggleAuthor(name) }
                CardBell(c, writerKey(name), followed, onFollow = { actions.toggleAuthor(name) }) { actions.toggleMute(writerKey(name)) }
            }
        }
    }
}

/**
 * Fills the space a short story leaves in its box with the article's opening text (as many
 * whole lines as fit, ending "…"). It never makes the box taller: it reports no height of its
 * own, so the row is as tall as its longest story and the shorter ones are filled out.
 */
@Composable
private fun FillText(c: OpEdPalette, a: Article, modifier: Modifier, actions: OpinionActions) {
    val loader = LocalArticleExcerpt.current
    // null = still reading the article; "" = nothing readable found.
    val text by produceState<String?>(if (loader == null) "" else null, a.id) {
        value = loader?.let { runCatching { it(a) }.getOrNull() }
            ?.let { t -> a.summary?.trim()?.takeIf { it.isNotEmpty() && t.startsWith(it) }?.let { t.removePrefix(it).trim() } ?: t }
            ?.replace("\n", " ")
            ?.takeIf { it.length > 40 && it != a.summary && it != a.title }
            .orEmpty()
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.ui.layout.Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                val linePx = with(density) { 20.sp.toPx() }
                val gapPx = with(density) { 8.dp.toPx() }
                val lines = if (constraints.hasBoundedHeight) ((constraints.maxHeight - gapPx) / linePx).toInt() else 0
                val t = text
                // Until (or unless) the article's own text is in, an index of more from the same
                // pages fills the space; the text fades in over it when it arrives.
                if (lines >= 2) androidx.compose.animation.Crossfade(!t.isNullOrEmpty(), label = "fill") { hasText ->
                    if (hasText) Text(
                        t.orEmpty(), style = serif(14.sp, FontWeight.Normal, lineHeight = 20.sp), color = c.text.copy(alpha = 0.72f),
                        maxLines = lines, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
                    ) else MoreFrom(c, a, constraints.maxHeight, actions)
                }
            }
        },
        measurePolicy = ZeroIntrinsicsPolicy,
    )
}

/** "More from Washington Post Opinions": other headlines, as many as fit, each opening its story. */
@Composable
private fun MoreFrom(c: OpEdPalette, a: Article, @Suppress("UNUSED_PARAMETER") heightPx: Int, actions: OpinionActions) {
    val all = LocalPageStories.current
    val src = a.source ?: a.domain
    val same = all.filter { it.id != a.id && (it.source ?: it.domain) == src }
    val list = same.ifEmpty { all.filter { it.id != a.id } }
    if (list.isEmpty()) return
    // Places the rule, the label and then as many whole headlines as the space holds (none if
    // not even one fits under the label).
    androidx.compose.ui.layout.Layout(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), content = {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
        Text(
            (if (same.isNotEmpty() && src != null) tr(R.string.op_more_from, src) else tr(R.string.op_also_in)).uppercase(),
            style = mono(9.sp, FontWeight.Bold, 0.16.em), color = c.dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
        )
        list.take(12).forEach { o ->
            Row(Modifier.fillMaxWidth().clickable { actions.openArticle(o) }.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Text("›", style = serif(15.sp, FontWeight.Bold, lineHeight = 19.sp), color = c.accent, modifier = Modifier.padding(end = 8.dp))
                Text(o.title, style = serif(14.5.sp, FontWeight.SemiBold, lineHeight = 19.sp), color = c.text.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)
        val placeables = measurables.map { it.measure(loose) }
        var y = 0
        var n = 0
        for (pl in placeables) { if (y + pl.height > constraints.maxHeight) break; y += pl.height; n++ }
        // Rule + label alone (no headline) would look like a stray caption: show nothing then.
        if (n < 3) n = 0
        layout(constraints.maxWidth, if (n == 0) 0 else y) {
            var yy = 0
            placeables.take(n).forEach { it.place(0, yy); yy += it.height }
        }
    }
}
/** Takes the space it's given but never asks for any: rows size to their real content. */
private object ZeroIntrinsicsPolicy : androidx.compose.ui.layout.MeasurePolicy {
    override fun androidx.compose.ui.layout.MeasureScope.measure(
        measurables: List<androidx.compose.ui.layout.Measurable>, constraints: androidx.compose.ui.unit.Constraints,
    ): androidx.compose.ui.layout.MeasureResult {
        val h = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0, maxHeight = h)) }
        return layout(constraints.maxWidth, maxOf(constraints.minHeight, h)) { placeables.forEach { it.place(0, 0) } }
    }
    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>, width: Int) = 0
    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>, width: Int) = 0
    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>, height: Int) = 0
    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>, height: Int) = 0
}

/** Newsprint photos: colour held back, so the page reads like paper. */
private val Newsprint = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0.25f) })

/**
 * A story's photo, sharp: the image service's large size where it has one; otherwise (feeds
 * often carry a ~140 px signed thumbnail) the publisher page's own full-size photo, swapped in
 * when found. Small thumbnails ([large] = false) skip the page lookup.
 */
@Composable
private fun Photo(c: OpEdPalette, a: Article, modifier: Modifier, large: Boolean = true) {
    val feed = a.imageUrl?.takeIf { it.startsWith("http") } ?: return
    val upgraded = remember(feed) { news.openwire.domain.engine.ImageExtractor.highRes(feed) ?: feed }
    val resolver = news.openwire.ui.home.LocalImageResolver.current
    val page by produceState<String?>(null, feed, large) {
        value = if (large && upgraded == feed && resolver != null)
            runCatching { resolver.resolve(a.originalUrl ?: a.targetUrl) }.getOrNull()?.takeIf { it.startsWith("http") && it != feed }
                ?.let { news.openwire.domain.engine.ImageExtractor.highRes(it) ?: it }
        else null
    }
    var failed by remember(upgraded, page) { mutableStateOf(false) }
    val model = when {
        page != null && !failed -> page
        upgraded != feed && !failed -> upgraded
        else -> feed
    }
    coil.compose.AsyncImage(
        coil.request.ImageRequest.Builder(LocalContext.current).data(model).crossfade(250).build(),
        null, contentScale = ContentScale.Crop, colorFilter = Newsprint,
        onError = { if (model != feed) failed = true },
        modifier = modifier.background(c.surface).border(1.dp, c.rule),
    )
}

@Composable
private fun StoryLead(c: OpEdPalette, a: Article, followed: Boolean, actions: OpinionActions) {
    Column(Modifier.fillMaxWidth().clickable { actions.openArticle(a) }) {
        Kicker(c, tr(R.string.op_lead))
        Spacer(Modifier.height(6.dp))
        Text(a.title, style = serif(28.sp, FontWeight.Bold, lineHeight = 33.sp, tracking = (-0.01).em), color = c.text, maxLines = 5, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Byline(c, a, followed, actions)
        Photo(c, a, Modifier.padding(top = 10.dp).fillMaxWidth().height(210.dp))
        a.summary?.takeIf { it.isNotBlank() && it != a.title }?.let { body ->
            Spacer(Modifier.height(10.dp))
            // A drop cap: the first letter set large in the accent, the paragraph beside it.
            Row {
                Text(body.first().uppercase(), style = serif(50.sp, FontWeight.Bold, lineHeight = 48.sp), color = c.accent, modifier = Modifier.padding(end = 8.dp))
                Text(body.drop(1), style = serif(15.5.sp, FontWeight.Normal, lineHeight = 23.sp), color = c.text, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun StoryBrief(c: OpEdPalette, a: Article, followed: Boolean, actions: OpinionActions, photo: Boolean, big: Boolean = false) {
    Column(Modifier.fillMaxSize().clickable { actions.openArticle(a) }) {
        Kicker(c, (a.source ?: a.domain).orEmpty())
        Spacer(Modifier.height(4.dp))
        Row {
            Text(
                a.title, style = if (big) serif(21.sp, FontWeight.Bold, lineHeight = 27.sp) else serif(17.sp, FontWeight.Bold, lineHeight = 22.sp),
                color = c.text, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (photo && a.imageUrl != null) { Spacer(Modifier.width(12.dp)); Photo(c, a, Modifier.size(if (big) 96.dp else 64.dp), large = big) }
        }
        a.summary?.takeIf { it.isNotBlank() && it != a.title }?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = serif(if (big) 15.sp else 13.5.sp, FontWeight.SemiBold, lineHeight = if (big) 21.sp else 19.sp), color = c.dim, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        // The article's own text runs on into any space left; bylines sit level along the bottom.
        FillText(c, a, Modifier.weight(1f), actions)
        Spacer(Modifier.height(10.dp))
        SmallByline(c, a, followed, actions)
    }
}

@Composable
private fun StoryColumn(c: OpEdPalette, a: Article, followed: Boolean, actions: OpinionActions) {
    Column(Modifier.fillMaxSize().clickable { actions.openArticle(a) }) {
        Photo(c, a, Modifier.padding(bottom = 10.dp).fillMaxWidth().aspectRatio(4f / 3f))
        Kicker(c, (a.source ?: a.domain).orEmpty())
        Spacer(Modifier.height(4.dp))
        Text(a.title, style = serif(18.sp, FontWeight.Bold, lineHeight = 23.sp), color = c.text, maxLines = 5, overflow = TextOverflow.Ellipsis)
        a.summary?.takeIf { it.isNotBlank() && it != a.title }?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = serif(13.5.sp, FontWeight.Normal, lineHeight = 19.sp), color = c.dim, maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
        // The article's own text runs on into any space left; the byline sits on the box's bottom line.
        FillText(c, a, Modifier.weight(1f), actions)
        Spacer(Modifier.height(10.dp))
        SmallByline(c, a, followed, actions)
    }
}

@Composable
private fun StoryFeature(c: OpEdPalette, a: Article, followed: Boolean, actions: OpinionActions, sideBySide: Boolean) {
    val text: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            Kicker(c, (a.source ?: a.domain).orEmpty())
            Spacer(Modifier.height(6.dp))
            Text(a.title, style = serif(24.sp, FontWeight.Bold, lineHeight = 29.sp), color = c.text, maxLines = 5, overflow = TextOverflow.Ellipsis)
            a.summary?.takeIf { it.isNotBlank() && it != a.title }?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = serif(15.sp, FontWeight.Normal, lineHeight = 22.sp), color = c.text.copy(alpha = 0.85f), maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
            // Beside a photo, the article's text runs on to the photo's foot.
            FillText(c, a, Modifier.weight(1f), actions)
            Spacer(Modifier.height(10.dp))
            SmallByline(c, a, followed, actions)
        }
    }
    if (sideBySide && a.imageUrl != null) {
        Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).clickable { actions.openArticle(a) }) {
            Photo(c, a, Modifier.weight(1f).fillMaxHeight().heightIn(min = 200.dp))
            Spacer(Modifier.width(16.dp))
            text(Modifier.weight(1f).fillMaxHeight())
        }
    } else Column(Modifier.fillMaxWidth().clickable { actions.openArticle(a) }) {
        Photo(c, a, Modifier.padding(bottom = 10.dp).fillMaxWidth().height(190.dp))
        text(Modifier.fillMaxWidth())
    }
}

/** A pull-quote box: the headline set as a quotation, attributed. */
@Composable
private fun StoryQuote(c: OpEdPalette, a: Article, followed: Boolean, actions: OpinionActions) {
    Column(Modifier.fillMaxSize().clickable { actions.openArticle(a) }) {
        Text("“", style = serif(54.sp, FontWeight.Bold, lineHeight = 54.sp), color = c.accent, modifier = Modifier.height(34.dp))
        Text(a.title, style = serif(19.sp, FontWeight.SemiBold, lineHeight = 25.sp).copy(fontStyle = FontStyle.Italic), color = c.text, maxLines = 6, overflow = TextOverflow.Ellipsis)
        FillText(c, a, Modifier.weight(1f), actions)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.width(36.dp).height(2.dp).background(c.accent))
        Spacer(Modifier.height(6.dp))
        Text((a.source ?: a.domain).orEmpty().uppercase(), style = mono(9.5.sp, FontWeight.Bold, 0.14.em), color = c.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        SmallByline(c, a, followed, actions)
    }
}

/** "By NAME · Source" with Follow and the bell. */
@Composable
private fun Byline(c: OpEdPalette, a: Article, followed: Boolean, actions: OpinionActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SourceLogo(a.domain, a.source, size = 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            (a.author?.let { tr(R.string.op_by, it) } ?: a.source.orEmpty()).uppercase(),
            style = mono(10.5.sp, FontWeight.SemiBold, 0.1.em), color = c.text,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
        a.author?.let { name ->
            Spacer(Modifier.width(8.dp))
            FollowChip(c, followed) { actions.toggleAuthor(name) }
            CardBell(c, writerKey(name), followed, onFollow = { actions.toggleAuthor(name) }) { actions.toggleMute(writerKey(name)) }
        }
    }
}

/** A newspaper's heavy-then-hairline rule. */
@Composable
private fun DoubleRule(c: OpEdPalette) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(c.text))
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.text))
    }
}

@Composable
private fun Header(c: OpEdPalette, refreshing: Boolean, onDiscover: () -> Unit, onRefresh: () -> Unit) {
    // Spins while the pages reload.
    val spin = androidx.compose.animation.core.rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing)), label = "angle")
    val today = remember {
        java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL).format(java.time.LocalDate.now())
    }
    Column(Modifier.padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // The ears: refresh on one side, discover on the other, the edition line between.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Refresh, tr(R.string.action_refresh), tint = if (refreshing) c.accent else c.dim,
                modifier = Modifier.clip(CircleShape).clickable(enabled = !refreshing, onClick = onRefresh).padding(8.dp).size(20.dp)
                    .graphicsLayer { rotationZ = if (refreshing) angle else 0f },
            )
            Text(
                tr(R.string.op_edition).uppercase(), style = mono(9.5.sp, FontWeight.SemiBold, 0.24.em), color = c.dim,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.Add, tr(R.string.op_discover_title), tint = c.onAccent,
                modifier = Modifier.padding(4.dp).clip(CircleShape).background(c.accent).clickable(onClick = onDiscover).padding(5.dp).size(18.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.text))
        Spacer(Modifier.height(10.dp))
        // The nameplate.
        Text(
            tr(R.string.op_title), style = serif(42.sp, FontWeight.Black, lineHeight = 46.sp, tracking = (-0.02).em), color = c.text,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        DoubleRule(c)
        // The dateline strip between the rules.
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(today.uppercase(), style = mono(9.5.sp, FontWeight.SemiBold, 0.12.em), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(tr(R.string.op_kicker), style = mono(9.5.sp, FontWeight.Bold, 0.12.em), color = c.accent, maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.text))
        Spacer(Modifier.height(10.dp))
        Text(
            tr(R.string.op_sub), style = serif(15.sp, lineHeight = 21.sp).copy(fontStyle = FontStyle.Italic), color = c.dim,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TabChip(c: OpEdPalette, label: String, on: Boolean, onRemove: (() -> Unit)? = null, onClick: () -> Unit) {
    // A section name on the paper's index strip: small capitals, the open one underlined in the accent.
    Column(Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.padding(top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = mono(11.sp, if (on) FontWeight.Bold else FontWeight.Medium, 0.14.em), color = if (on) c.text else c.dim, maxLines = 1)
            if (onRemove != null) Icon(
                Icons.Filled.Close, tr(R.string.op_remove_tab), tint = c.dim,
                modifier = Modifier.padding(start = 4.dp).clip(CircleShape).clickable(onClick = onRemove).padding(2.dp).size(13.dp),
            )
        }
        Box(Modifier.height(3.dp).fillMaxWidth().background(if (on) c.accent else Color.Transparent))
    }
}

@Composable
private fun Trending(c: OpEdPalette, topics: List<String>, onAdd: (String) -> Unit) {
    Column(Modifier.padding(top = 10.dp, bottom = 4.dp)) {
        Text(tr(R.string.op_trending).uppercase(), style = mono(10.sp, FontWeight.SemiBold, 0.16.em), color = c.dim)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            topics.forEach { t ->
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(c.surface).border(1.dp, c.rule, RoundedCornerShape(6.dp))
                        .clickable { onAdd(t) }.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("+ ", style = sans(13.sp, FontWeight.Bold), color = c.accent)
                    Text(t, style = sans(13.sp, FontWeight.Medium), color = c.text)
                }
            }
        }
    }
}

@Composable
private fun SearchField(c: OpEdPalette, text: String, onChange: (String) -> Unit, onDone: (String) -> Unit) {
    Row(
        Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.surface)
            .border(1.dp, if (text.isNotEmpty()) c.accent else c.rule, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = c.dim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) Text(tr(R.string.op_search_hint), style = sans(14.sp), color = c.dim)
            BasicTextField(
                text, onChange, singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 14.sp), cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone(text) }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (text.isNotEmpty()) Icon(
            Icons.Filled.Close, null, tint = c.dim,
            modifier = Modifier.clip(CircleShape).clickable { onChange("") }.padding(4.dp).size(16.dp),
        )
    }
}

@Composable
private fun Rule(c: OpEdPalette, title: String) {
    // A section head: heavy rule, the name centred in small capitals, hairline beneath.
    Column(Modifier.widthIn(max = 1100.dp).fillMaxWidth().padding(top = 20.dp, bottom = 4.dp)) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(c.text))
        Text(
            title.uppercase(), style = serif(13.sp, FontWeight.Bold, tracking = 0.18.em), color = c.text,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
    }
}

@Composable
private fun Status(c: OpEdPalette, text: String) =
    Text(text, style = serif(15.sp).copy(fontStyle = FontStyle.Italic), color = c.dim, modifier = Modifier.padding(vertical = 18.dp))

@Composable
private fun FollowChip(c: OpEdPalette, followed: Boolean, onClick: () -> Unit) {
    Text(
        tr(if (followed) R.string.op_following_btn else R.string.op_follow),
        style = sans(11.sp, FontWeight.SemiBold),
        color = if (followed) c.onAccent else c.accent,
        modifier = Modifier.clip(CircleShape).background(if (followed) c.accent else Color.Transparent)
            .border(1.dp, c.accent, CircleShape).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun ColumnCard(c: OpEdPalette, a: Article, followed: Boolean, actions: OpinionActions, rule: Boolean = true) {
    // A brief: kicker, headline, a line of standfirst, a small newsprint photo, the byline.
    Column(Modifier.fillMaxWidth().clickable { actions.openArticle(a) }.padding(vertical = 14.dp)) {
        Row {
            Column(Modifier.weight(1f)) {
                Text(
                    (a.source ?: a.domain).orEmpty().uppercase(), style = mono(9.5.sp, FontWeight.Bold, 0.16.em), color = c.accent,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(a.title, style = serif(19.sp, FontWeight.Bold, lineHeight = 24.sp), color = c.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            if (a.imageUrl != null) {
                Spacer(Modifier.width(12.dp))
                Photo(c, a, Modifier.padding(top = 2.dp).size(78.dp), large = false)
            }
        }
        a.summary?.takeIf { it.isNotBlank() && it != a.title }?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = serif(14.sp, lineHeight = 20.sp), color = c.dim, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (a.author?.let { tr(R.string.op_by, it) } ?: "").uppercase(),
                style = mono(9.5.sp, FontWeight.SemiBold, 0.08.em), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                (if (a.author != null) "  ·  " else "") + NewsProcessor.formatRelativeTime(a.publishedAt),
                style = mono(9.5.sp, tracking = 0.04.em), color = c.dim, maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            a.author?.let { name ->
                FollowChip(c, followed) { actions.toggleAuthor(name) }
                CardBell(c, writerKey(name), followed, onFollow = { actions.toggleAuthor(name) }) { actions.toggleMute(writerKey(name)) }
            }
        }
    }
    if (rule) Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
}

@Composable
private fun PostCard(c: OpEdPalette, p: SocialPost, followed: Boolean, actions: OpinionActions) {
    val context = LocalContext.current
    Column(
        // Letters to the editor: a ruled box, square like a page, not an app card.
        Modifier.fillMaxWidth().padding(vertical = 8.dp).background(c.surface)
            .border(1.dp, c.rule)
            .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.url))) } }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            coil.compose.AsyncImage(p.avatar, null, contentScale = ContentScale.Crop, modifier = Modifier.size(36.dp).clip(CircleShape).background(c.rule))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p.authorName, style = sans(14.sp, FontWeight.SemiBold), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    // Handle and network kept left-to-right inside right-to-left text.
                    "\u2066@${p.authorHandle}\u2069  ·  ${p.network}  ·  ${NewsProcessor.formatRelativeTime(p.createdAt)}",
                    style = sans(11.sp), color = c.dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (p.network == "Bluesky") FollowChip(c, followed) { actions.toggleAccount(p.authorHandle) }
        }
        Spacer(Modifier.height(10.dp))
        Text(p.text, style = sans(14.5.sp, lineHeight = 22.sp), color = c.text, maxLines = 10, overflow = TextOverflow.Ellipsis)
        val link = p.linkUrl
        if (link != null) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().border(1.dp, c.rule)
                    .clickable { actions.openLink(link) }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                p.image?.let { coil.compose.AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp))) ; Spacer(Modifier.width(10.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(p.linkTitle ?: link, style = serif(15.sp, FontWeight.SemiBold, lineHeight = 20.sp), color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(runCatching { Uri.parse(link).host?.removePrefix("www.") }.getOrNull().orEmpty(), style = mono(10.sp), color = c.dim)
                }
            }
        } else if (p.image != null) {
            Spacer(Modifier.height(10.dp))
            coil.compose.AsyncImage(p.image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp)))
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.FavoriteBorder, null, tint = c.dim, modifier = Modifier.size(14.dp))
            Text(" ${p.likes}", style = mono(11.sp), color = c.dim)
            Spacer(Modifier.width(14.dp))
            Icon(Icons.Outlined.Repeat, null, tint = c.dim, modifier = Modifier.size(14.dp))
            Text(" ${p.reposts}", style = mono(11.sp), color = c.dim)
            if (p.network == "Bluesky") {
                Spacer(Modifier.weight(1f))
                CardBell(c, accountKey(p.authorHandle), followed, onFollow = { actions.toggleAccount(p.authorHandle) }) { actions.toggleMute(accountKey(p.authorHandle)) }
            }
        }
    }
}

@Composable
private fun VoiceRow(c: OpEdPalette, v: Voice, followed: Boolean, onFollow: () -> Unit) {
    Row(
        Modifier.widthIn(max = 1100.dp).fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(if (v.newsroom) c.text else c.accent),
            contentAlignment = Alignment.Center,
        ) { Text(v.name.first().uppercase(), style = serif(17.sp, FontWeight.Bold), color = if (v.newsroom) c.bg else c.onAccent) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(v.name, style = serif(16.sp, FontWeight.SemiBold), color = c.text)
            Text("\u2066@${v.handle} · Bluesky\u2069", style = sans(11.sp), color = c.dim)
        }
        FollowChip(c, followed, onFollow)
    }
}

/** A writer seen in today's columns. */
private data class Writer(val key: String, val name: String, val outlet: String, val language: String, val latest: Article, val count: Int)

/**
 * Today's columnists: bylines from the columns (not an outlet's own name), most prolific
 * first, grouped with Editrove's language first, then English, then the rest.
 */
private fun columnists(columns: List<OpinionSource.Column>, appLanguage: String, followed: Set<String>): List<Writer> {
    val skip = followed.map(OpinionCatalog::authorKey).toSet()
    return columns.filter { it.article.author != null }
        .groupBy { OpinionCatalog.authorKey(it.article.author!!) }
        .filter { (k, list) ->
            val a = list.first().article
            k.isNotBlank() && k !in skip && a.author!!.length <= 40 &&
                !a.author.equals(a.source, true) && OpinionCatalog.authorKey(a.source.orEmpty()) != k
        }
        .map { (k, list) ->
            val newest = list.maxByOrNull { it.article.publishedAt ?: 0L }!!
            Writer(k, newest.article.author!!, newest.article.source.orEmpty(), newest.language, newest.article, list.size)
        }
        .sortedWith(compareBy<Writer>({ when (it.language) { appLanguage -> 0; "en" -> 1; else -> 2 } }, { -it.count }, { -(it.latest.publishedAt ?: 0L) }))
}

@Composable
private fun WriterRow(c: OpEdPalette, w: Writer, followed: Boolean, onFollow: () -> Unit) {
    Row(Modifier.widthIn(max = 1100.dp).fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
            Text(w.name.trim().firstOrNull()?.uppercase() ?: "•", style = serif(17.sp, FontWeight.Bold), color = c.onAccent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(w.name, style = serif(16.sp, FontWeight.SemiBold).copy(fontStyle = FontStyle.Italic), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(w.outlet, tr(R.string.op_pieces, w.count)).filter { it.isNotBlank() }.joinToString("  ·  "),
                style = sans(11.sp), color = c.dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(w.latest.title, style = sans(12.sp), color = c.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        FollowChip(c, followed, onFollow)
    }
}

/** Results as you type: matching writers, columns and accounts at once, then a live search. */
private fun LazyListScope.searchResults(
    c: OpEdPalette, source: OpinionSource, q: String, columns: List<OpinionSource.Column>?, appLanguage: String,
    authors: Set<String>, accounts: Set<String>, actions: OpinionActions, onAddTab: (String) -> Unit,
) {
    val all = columns.orEmpty()
    val keys = authors.map(OpinionCatalog::authorKey).toSet()
    item("s-add") {
        Row(Modifier.widthIn(max = 1100.dp).fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr(R.string.op_add_as_tab, q), style = sans(13.sp, FontWeight.SemiBold), color = c.onAccent,
                modifier = Modifier.clip(CircleShape).background(c.accent).clickable { onAddTab(q) }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
    val writers = columnists(all, appLanguage, emptySet()).filter { it.name.contains(q, true) || it.outlet.contains(q, true) }
    if (writers.isNotEmpty()) {
        item("s-w") { Rule(c, tr(R.string.op_results_writers)) }
        items(writers.take(8), key = { "sw" + it.key }) { w -> WriterRow(c, w, followed = w.key in keys) { actions.toggleAuthor(w.name) } }
    }
    val voices = OpinionCatalog.VOICES.filter { it.name.contains(q, true) || it.handle.contains(q, true) }
    if (voices.isNotEmpty()) {
        item("s-v") { Rule(c, tr(R.string.op_on_bluesky)) }
        items(voices, key = { "sv" + it.handle }) { v -> VoiceRow(c, v, followed = v.handle in accounts) { actions.toggleAccount(v.handle) } }
    }
    val local = all.map { it.article }.filter { a -> a.title.contains(q, true) || a.summary?.contains(q, true) == true || a.author?.contains(q, true) == true }
    item("s-c") {
        // The live search waits for a pause in typing before asking the network.
        val searched by produceState<List<Article>?>(null, q, appLanguage) {
            value = null
            kotlinx.coroutines.delay(450)
            value = source.topicColumns(q, appLanguage)
        }
        val list = (local + searched.orEmpty()).distinctBy { it.title.lowercase() }.take(25)
        Column(Modifier.widthIn(max = 1100.dp).fillMaxWidth()) {
            Rule(c, tr(R.string.op_results_columns))
            if (list.isEmpty()) Status(c, if (searched == null) tr(R.string.op_searching) else tr(R.string.op_nothing))
            list.forEach { ColumnCard(c, it, it.author?.let { a -> OpinionCatalog.authorKey(a) in keys } == true, actions) }
            if (searched == null && list.isNotEmpty()) Status(c, tr(R.string.op_searching))
        }
    }
}
