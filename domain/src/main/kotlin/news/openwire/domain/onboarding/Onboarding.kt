// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.onboarding

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import news.openwire.domain.engine.SourceLibrary
import news.openwire.domain.engine.TopicRegistry
import news.openwire.domain.model.BriefingSlot
import news.openwire.domain.model.FeedSource
import news.openwire.domain.model.LocalEdition
import news.openwire.domain.model.Presenter
import news.openwire.domain.model.Topic
import news.openwire.domain.model.UserSettings

/**
 * First-run setup as an explicit state machine: one [OnboardingState] (the step, the edition
 * sub-state and every choice so far), changed only by [Onboarding.reduce]. No flag
 * combinations: each step's guard lives in one place, and a late location result can only
 * land while the edition step is still locating.
 */
@Serializable
enum class Step { WELCOME, EDITION, TOPICS, SOURCES, PRESENTER, FINISHING, DONE }

/** The edition step's own phases. */
@Serializable
enum class EditionMode { CHOOSE, LOCATING, TYPING, CONFIRMED }

/** Why the edition step fell back to typing a city. */
@Serializable
enum class LocationProblem { DENIED, NO_FIX }

@Serializable
data class Place(val city: String, val region: String, val country: String) {
    fun toEdition() = LocalEdition(city, region.ifBlank { city }, country)

    companion object {
        fun of(e: LocalEdition) = Place(e.city, e.region, e.country)
    }
}

/** A topic made from keywords during setup (a preset or a typed name). */
@Serializable
data class KeywordTopic(val name: String, val keywords: List<String>) {
    val id: String get() = KEYWORD_PREFIX + name
}

@Serializable
data class AddedFeed(val name: String, val url: String)

@Serializable
data class Choices(
    val edition: Place? = null,
    /** Selected topic ids, in the order chosen ("kw:" + name for keyword topics). */
    val topics: List<String> = emptyList(),
    val keywordTopics: List<KeywordTopic> = emptyList(),
    /** Selected source keys (feed URLs). */
    val sources: Set<String> = emptySet(),
    /** Topics whose default sources were already selected (so a re-visit doesn't re-add removed ones). */
    val seeded: Set<String> = emptySet(),
    val feeds: List<AddedFeed> = emptyList(),
    val presenter: Presenter = Presenter.ANCHOR,
    /** The sample opening's variant ("Another line"). */
    val seed: Int = 0,
    val slots: Set<BriefingSlot.Kind> = setOf(BriefingSlot.Kind.MORNING),
)

@Serializable
data class OnboardingState(
    val step: Step = Step.WELCOME,
    val editionMode: EditionMode = EditionMode.CHOOSE,
    val problem: LocationProblem? = null,
    val choices: Choices = Choices(),
    /** Steps already seen (Skip keeps what was chosen there). */
    val visited: Set<Step> = setOf(Step.WELCOME),
    val skipped: Boolean = false,
)

/** What the machine needs to know about the app: the topic lineup and the source catalog. */
data class OnboardingContext(
    /** Built-in (and the reader's custom) topic ids for an edition, in display order. */
    val lineup: (Place?) -> List<String>,
    /** Catalog source keys tagged with a topic, in the catalog's order. */
    val sourcesFor: (String) -> List<String>,
)

sealed interface OnboardingEvent {
    data object Next : OnboardingEvent
    data object Back : OnboardingEvent
    data object Skip : OnboardingEvent
    data object Finished : OnboardingEvent

    data object UseLocation : OnboardingEvent
    data object TypeCity : OnboardingEvent
    data class LocationFound(val place: Place) : OnboardingEvent
    data class LocationFailed(val problem: LocationProblem) : OnboardingEvent
    data class ChooseEdition(val place: Place) : OnboardingEvent
    data object ChangeEdition : OnboardingEvent

    data class ToggleTopic(val id: String) : OnboardingEvent
    data class AddKeywordTopic(val topic: KeywordTopic) : OnboardingEvent

    data class ToggleSource(val key: String) : OnboardingEvent
    data class SetSources(val keys: Collection<String>, val on: Boolean) : OnboardingEvent
    data class AddFeed(val feed: AddedFeed) : OnboardingEvent

    data class SetPresenter(val presenter: Presenter) : OnboardingEvent
    data object AnotherLine : OnboardingEvent
    data class ToggleSlot(val kind: BriefingSlot.Kind) : OnboardingEvent
}

const val KEYWORD_PREFIX = "kw:"

object Onboarding {
    /** Bump to show the setup again to everyone (a future redesign). */
    const val VERSION = 1
    const val MIN_TOPICS = 3
    const val MAX_KEYWORD_TOPICS = 3

    private val ORDER = listOf(Step.WELCOME, Step.EDITION, Step.TOPICS, Step.SOURCES, Step.PRESENTER, Step.FINISHING)

    /** Steps shown as nodes on the progress line. */
    val PROGRESS = listOf(Step.EDITION, Step.TOPICS, Step.SOURCES, Step.PRESENTER, Step.FINISHING)

    fun canAdvance(s: OnboardingState): Boolean = when (s.step) {
        Step.WELCOME -> true
        Step.EDITION -> s.editionMode == EditionMode.CONFIRMED && s.choices.edition != null
        Step.TOPICS -> s.choices.topics.size >= MIN_TOPICS
        Step.SOURCES, Step.PRESENTER -> true
        Step.FINISHING, Step.DONE -> false
    }

    fun reduce(s: OnboardingState, e: OnboardingEvent, ctx: OnboardingContext): OnboardingState {
        if (s.step == Step.DONE) return s
        val c = s.choices
        return when (e) {
            OnboardingEvent.Next -> if (!canAdvance(s)) s else enter(s, ORDER[ORDER.indexOf(s.step) + 1], ctx)
            OnboardingEvent.Back -> when (s.step) {
                Step.WELCOME, Step.FINISHING -> s
                // Typing or locating goes back to the two choices before leaving the step.
                Step.EDITION -> if (s.editionMode == EditionMode.TYPING || s.editionMode == EditionMode.LOCATING) s.copy(editionMode = if (c.edition != null) EditionMode.CONFIRMED else EditionMode.CHOOSE, problem = null)
                else s.copy(step = Step.WELCOME)
                else -> s.copy(step = ORDER[ORDER.indexOf(s.step) - 1])
            }
            OnboardingEvent.Skip -> if (s.step == Step.FINISHING) s else skip(s, ctx)
            OnboardingEvent.Finished -> if (s.step == Step.FINISHING) s.copy(step = Step.DONE) else s

            OnboardingEvent.UseLocation -> if (s.step != Step.EDITION) s else s.copy(editionMode = EditionMode.LOCATING, problem = null)
            OnboardingEvent.TypeCity -> if (s.step != Step.EDITION) s else s.copy(editionMode = EditionMode.TYPING, problem = null)
            // A late result only counts while the step is still waiting for it.
            is OnboardingEvent.LocationFound -> if (s.step == Step.EDITION && s.editionMode == EditionMode.LOCATING) confirm(s, e.place, ctx) else s
            is OnboardingEvent.LocationFailed -> if (s.step == Step.EDITION && s.editionMode == EditionMode.LOCATING) s.copy(editionMode = EditionMode.TYPING, problem = e.problem) else s
            is OnboardingEvent.ChooseEdition -> if (s.step == Step.EDITION) confirm(s, e.place, ctx) else s
            OnboardingEvent.ChangeEdition -> if (s.step == Step.EDITION) s.copy(editionMode = EditionMode.CHOOSE, problem = null) else s

            is OnboardingEvent.ToggleTopic -> if (s.step != Step.TOPICS) s else s.copy(
                choices = c.copy(topics = if (e.id in c.topics) c.topics - e.id else c.topics + e.id),
            )
            is OnboardingEvent.AddKeywordTopic -> {
                val name = e.topic.name.trim()
                if (s.step != Step.TOPICS || name.isEmpty() || c.keywordTopics.size >= MAX_KEYWORD_TOPICS ||
                    c.keywordTopics.any { it.name.equals(name, true) }
                ) s
                else {
                    val t = e.topic.copy(name = name)
                    s.copy(choices = c.copy(keywordTopics = c.keywordTopics + t, topics = c.topics + t.id))
                }
            }

            is OnboardingEvent.ToggleSource -> if (s.step != Step.SOURCES) s else s.copy(
                choices = c.copy(sources = if (e.key in c.sources) c.sources - e.key else c.sources + e.key),
            )
            is OnboardingEvent.SetSources -> if (s.step != Step.SOURCES) s else s.copy(
                choices = c.copy(sources = if (e.on) c.sources + e.keys else c.sources - e.keys.toSet()),
            )
            is OnboardingEvent.AddFeed -> if (s.step != Step.SOURCES || c.feeds.any { it.url == e.feed.url }) s else s.copy(
                choices = c.copy(feeds = c.feeds + e.feed, sources = c.sources + e.feed.url),
            )

            is OnboardingEvent.SetPresenter -> if (s.step != Step.PRESENTER) s else s.copy(choices = c.copy(presenter = e.presenter))
            OnboardingEvent.AnotherLine -> if (s.step != Step.PRESENTER) s else s.copy(choices = c.copy(seed = c.seed + 1))
            is OnboardingEvent.ToggleSlot -> if (s.step != Step.PRESENTER) s else s.copy(
                choices = c.copy(slots = if (e.kind in c.slots) c.slots - e.kind else c.slots + e.kind),
            )
        }
    }

    private fun confirm(s: OnboardingState, place: Place, ctx: OnboardingContext): OnboardingState {
        // A new edition renames the local topic but keeps everything chosen so far.
        val c = s.choices.copy(edition = place)
        return s.copy(editionMode = EditionMode.CONFIRMED, problem = null, choices = c)
    }

    /** Entering a step: Topics starts with Top Stories and the local topic; Sources selects each new topic's sources. */
    private fun enter(s: OnboardingState, step: Step, ctx: OnboardingContext): OnboardingState {
        var c = s.choices
        if (step == Step.TOPICS && c.topics.isEmpty()) c = c.copy(topics = listOf("TOP", TopicRegistry.LOCAL_ID))
        if (step == Step.SOURCES) c = seed(c, ctx)
        return s.copy(step = step, choices = c, visited = s.visited + step)
    }

    /** Every catalog source of each topic not seeded yet (the app's own default: all on). */
    fun seed(c: Choices, ctx: OnboardingContext): Choices {
        val fresh = c.topics.filter { it !in c.seeded && !it.startsWith(KEYWORD_PREFIX) }
        if (fresh.isEmpty()) return c
        return c.copy(sources = c.sources + fresh.flatMap(ctx.sourcesFor), seeded = c.seeded + fresh)
    }

    /**
     * Skip: today's defaults (default edition, every topic, every source, the Anchor, no
     * briefing times), but anything already chosen on a step that was seen is kept.
     */
    fun skip(s: OnboardingState, ctx: OnboardingContext): OnboardingState {
        var c = s.choices
        val edition = c.edition ?: Place.of(LocalEdition.DEFAULT)
        c = c.copy(edition = edition)
        if (Step.TOPICS !in s.visited || c.topics.size < MIN_TOPICS) {
            c = c.copy(topics = (ctx.lineup(edition) + c.keywordTopics.map { it.id }).distinct())
        }
        if (Step.SOURCES !in s.visited) c = c.copy(sources = emptySet(), seeded = emptySet())
        c = seed(c, ctx)
        if (Step.PRESENTER !in s.visited) c = c.copy(presenter = Presenter.ANCHOR, slots = emptySet())
        return s.copy(step = Step.FINISHING, editionMode = EditionMode.CONFIRMED, choices = c, skipped = true)
    }

    // ---- Saving ----------------------------------------------------------------------

    /**
     * The settings these choices make, applied over [base] (the current settings: a re-run keeps
     * everything setup doesn't ask about).
     */
    fun apply(
        base: UserSettings,
        c: Choices,
        catalog: List<FeedSource>,
        nowMillis: Long,
    ): UserSettings {
        val edition = (c.edition ?: Place.of(base.edition)).toEdition()
        // Keyword topics become custom topics (the same path as Settings › Topics › New topic).
        val existing = base.customTopics.map { it.label.lowercase() }.toSet()
        val newTopics = c.keywordTopics.filter { it.name.lowercase() !in existing }.mapIndexed { i, k ->
            TopicRegistry.newCustomTopic(k.name, k.keywords.ifEmpty { listOf(k.name) }, emptyList(), edition, nowMillis + i)
        }
        val keywordIds = c.keywordTopics.associate { it.id to (newTopics.firstOrNull { t -> t.label.equals(it.name, true) }?.id ?: base.customTopics.firstOrNull { t -> t.label.equals(it.name, true) }?.id) }
        val selected = c.topics.mapNotNull { if (it.startsWith(KEYWORD_PREFIX)) keywordIds[it] else it }.toSet()
        val customTopics = base.customTopics + newTopics
        val lineup = TopicRegistry.buildTopics(edition, customTopics, emptySet(), null, base.language).map { it.id }
        val hidden = lineup.filter { it !in selected }.toSet()

        // Sources: only those of the chosen topics are decided here; others are left as they were.
        val decided = selected.flatMap { id -> catalog.filter { f -> f.topics.any { it.equals(id, true) } } }
        val decidedKeys = decided.map(SourceLibrary::sourceKey).toSet()
        val disabled = (base.disabledSources - decidedKeys) + decidedKeys.filter { it !in c.sources }

        var feeds = base.customFeeds
        c.feeds.filter { f -> feeds.none { it.url == f.url } }.forEachIndexed { i, f ->
            feeds = SourceLibrary.addCustomFeed(feeds, f.name, f.url, listOf("TOP"), 8, nowMillis + 100 + i)
        }

        val slots = base.briefing.slots.map { it.copy(enabled = it.kind in c.slots) }
        return base.copy(
            edition = edition,
            customTopics = customTopics,
            hiddenTopics = hidden,
            topicOrder = base.topicOrder?.let { it + newTopics.map(Topic::id) },
            disabledSources = disabled,
            customFeeds = feeds,
            briefing = base.briefing.copy(presenter = c.presenter, slots = slots),
        )
    }

    /** "Run setup again": the current settings as choices (nothing reset to defaults). */
    fun choicesFrom(s: UserSettings, catalog: List<FeedSource>): Choices {
        val topics = TopicRegistry.buildTopics(s.edition, s.customTopics, s.hiddenTopics, s.topicOrder, s.language)
            .filter { it.isVisible }.map { it.id }
        val keys = topics.flatMap { id -> catalog.filter { f -> f.topics.any { it.equals(id, true) } } }
            .map(SourceLibrary::sourceKey).filter { it !in s.disabledSources }.toSet()
        return Choices(
            edition = Place.of(s.edition),
            topics = topics,
            sources = keys,
            seeded = topics.toSet(),
            presenter = s.briefing.presenter,
            slots = s.briefing.slots.filter { it.enabled }.map { it.kind }.toSet(),
        )
    }

    // ---- First run -------------------------------------------------------------------

    /**
     * Someone who used the app before this setup existed: an updated (not fresh) install, or
     * settings that differ from the defaults. They're counted as set up and never shown it.
     */
    fun isExistingUser(settings: UserSettings, updatedInstall: Boolean): Boolean =
        updatedInstall || settings.copy(previousEdition = null, previousLanguage = null) != UserSettings()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun encode(s: OnboardingState): String = json.encodeToString(OnboardingState.serializer(), s)
    fun decode(text: String?): OnboardingState? = text?.let { runCatching { json.decodeFromString(OnboardingState.serializer(), it) }.getOrNull() }
}
