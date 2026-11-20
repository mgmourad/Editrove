// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.settings

import news.openwire.i18n.tr
import news.openwire.R
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import java.util.Locale
import kotlinx.coroutines.delay
import news.openwire.domain.model.BriefingDetail
import news.openwire.domain.model.BriefingPrefs
import news.openwire.ui.home.BriefingPlayer
import news.openwire.ui.home.GlassCard
import news.openwire.ui.home.mono
import news.openwire.ui.home.sans
import news.openwire.ui.home.serif
import news.openwire.ui.theme.Glass

private val CUE_NAMES = listOf(
    "Headline News", "Global Desk", "Breaking", "Evening Edition",
    "Newsroom", "Bulletin", "Top of the Hour", "Wire", "Special Report", "Newsflash", "News Intro",
)

/** Settings › Audio briefing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BriefingSettings(vm: SettingsViewModel, chip: @Composable (String, Boolean, () -> Unit) -> Unit, switch: @Composable (Boolean, (Boolean) -> Unit) -> Unit) {
    val g = Glass
    val context = LocalContext.current
    val s by vm.settings.collectAsState()
    val p = s.briefing

    // A private engine for listing voices and previews.
    var ready by remember { mutableStateOf(false) }
    // Editrove's own voices (downloaded once) for phones without Google's speech engine.
    val voiceStore = (context.applicationContext as news.openwire.OpenWireApp).container.voices
    val packStates by voiceStore.states.collectAsState()
    val engineName = remember(packStates, p.engine) { news.openwire.i18n.VoiceSetup.engine(context) }
    val tts = remember(engineName) { ready = false; TextToSpeech(context.applicationContext, { ready = it == TextToSpeech.SUCCESS }, engineName) }
    DisposableEffect(tts) { onDispose { tts.shutdown() } }
    // A voice in Editrove's language: fetched automatically when the engine has one to download.
    val appLang = news.openwire.domain.engine.Languages.effective(s.language)
    var voiceState by remember { mutableStateOf<news.openwire.i18n.VoiceSetup.State?>(null) }
    var voiceCheck by remember { mutableIntStateOf(0) }
    LaunchedEffect(appLang, voiceCheck, packStates) {
        if (appLang == "en") return@LaunchedEffect
        voiceState = news.openwire.i18n.VoiceSetup.ensure(context, appLang, s.edition.country)
        // While it downloads, look again now and then.
        while (voiceState == news.openwire.i18n.VoiceSetup.State.DOWNLOADING) {
            delay(5_000)
            voiceState = news.openwire.i18n.VoiceSetup.ensure(context, appLang, s.edition.country)
        }
        // A voice that just arrived becomes the briefing's voice (unless one in this language is chosen).
        val chosen = s.briefing.voice
        if (voiceState == news.openwire.i18n.VoiceSetup.State.READY && (chosen == null || !chosen.lowercase().startsWith(appLang))) {
            news.openwire.i18n.VoiceSetup.lastReady?.let { name -> vm.setBriefing { it.copy(voice = name) } }
        }
    }
    var cuePlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var cuePlaying by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) { onDispose { tts.stop(); tts.shutdown(); cuePlayer?.release() } }
    LaunchedEffect(cuePlaying) {
        if (cuePlaying == 0) return@LaunchedEffect
        delay(9000)
        cuePlayer?.release(); cuePlayer = null; cuePlaying = 0
    }

    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val language = locale.language
    var showAll by remember { mutableStateOf(false) }
    val voices = remember(ready, showAll) {
        if (!ready) emptyList() else runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
            .filter { v -> !v.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .filter { showAll || it.locale.language == language }
            .sortedWith(compareBy<Voice>({ it.isNetworkConnectionRequired }, { it.locale.displayName }, { it.name }))
            .take(60)
    }

    fun preview(voice: Voice?) {
        tts.stop()
        tts.setSpeechRate(p.speed)
        val lang = voice?.locale?.language ?: appLang
        (voice ?: news.openwire.ui.home.BriefingVoices.preferred(runCatching { tts.voices }.getOrNull().orEmpty(), lang) ?: tts.defaultVoice)?.let { tts.voice = it }
        val sample = news.openwire.domain.engine.BriefingScript.intro(
            news.openwire.domain.engine.Languages.topicName("TOP", lang) ?: "Top Stories", 8,
            news.openwire.domain.model.BriefingDetail.HEADLINES, java.time.LocalDateTime.now(), lang,
        )
        tts.speak(sample, TextToSpeech.QUEUE_FLUSH, null, "preview")
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            tr(R.string.briefing_intro),
            style = sans(12.sp, lineHeight = 18.sp), color = g.textSecondary, modifier = Modifier.padding(horizontal = 4.dp),
        )

        Section(tr(R.string.briefing_reminders_channel)) {
            Text(tr(R.string.scheduled_hint), style = sans(11.sp, lineHeight = 16.sp), color = g.textSecondary)
            Spacer(Modifier.height(6.dp))
            p.slots.forEach { slot ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(slot.kind.label, style = sans(14.sp, FontWeight.Medium), color = g.textPrimary)
                        Text(
                            java.time.LocalTime.of(slot.hour, slot.minute).format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)),
                            style = mono(13.sp, FontWeight.SemiBold), color = if (slot.enabled) g.accentSoft else g.textSecondary,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                android.app.TimePickerDialog(context, news.openwire.R.style.Editrove_TimePicker, { _, h, m ->
                                    vm.setBriefing { b -> b.copy(slots = b.slots.map { if (it.kind == slot.kind) it.copy(minuteOfDay = h * 60 + m, enabled = true) else it }) }
                                }, slot.hour, slot.minute, android.text.format.DateFormat.is24HourFormat(context)).show()
                            }.padding(vertical = 4.dp),
                        )
                    }
                    switch(slot.enabled) { on -> vm.setBriefing { b -> b.copy(slots = b.slots.map { if (it.kind == slot.kind) it.copy(enabled = on) else it }) } }
                }
            }
            if (p.slots.any { it.enabled } && !news.openwire.briefing.BriefingReminders.canExact(context)) {
                Spacer(Modifier.height(6.dp))
                Text(
                    tr(R.string.exact_hint),
                    style = sans(11.sp, FontWeight.Medium), color = g.accentSoft,
                    modifier = Modifier.clickable {
                        runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${context.packageName}"))) }
                    },
                )
            }
        }

        // Presenter: the briefing's editorial voice (its words, not the narrator's voice).
        Section(tr(R.string.presenter)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                news.openwire.domain.model.Presenter.entries.forEach { pr -> chip(presenterName(pr), p.presenter == pr) { vm.setBriefing { it.copy(presenter = pr) } } }
            }
            Spacer(Modifier.height(8.dp))
            Text(presenterHint(p.presenter), style = sans(11.sp, lineHeight = 16.sp), color = g.textSecondary)
            // How it opens, right now, in Editrove's language.
            val sample = remember(p.presenter, p.detail, p.count) {
                news.openwire.domain.engine.BriefingScript.intro(
                    tr(R.string.presenter_sample_topic), p.count, p.detail, java.time.LocalDateTime.now(),
                    news.openwire.domain.engine.Languages.effective(null), p.presenter,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "“$sample”", style = serif(14.sp, lineHeight = 21.sp).copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                color = g.textPrimary.copy(alpha = 0.85f),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(g.chipFill).padding(12.dp),
            )
        }

        Section(tr(R.string.detail)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BriefingDetail.entries.forEach { d -> chip(d.label, p.detail == d) { vm.setBriefing { it.copy(detail = d) } } }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when (p.detail) {
                    BriefingDetail.HEADLINES -> tr(R.string.detail_headlines_hint)
                    BriefingDetail.SUMMARIES -> tr(R.string.detail_summaries_hint)
                    BriefingDetail.FULL -> tr(R.string.detail_full_hint)
                },
                style = sans(11.sp, lineHeight = 16.sp), color = g.textSecondary,
            )
        }

        Section(tr(R.string.story_count)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BriefingPrefs.COUNTS.forEach { n -> chip("$n", p.count == n) { vm.setBriefing { it.copy(count = n) } } }
            }
        }

        Section(tr(R.string.playback_speed)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BriefingPrefs.SPEEDS.forEach { v ->
                    chip(speedLabel(v), p.speed == v) { vm.setBriefing { it.copy(speed = v) } }
                }
            }
        }

        Section(tr(R.string.opening_cue)) {
            Text(tr(R.string.opening_cue_hint), style = sans(11.sp, lineHeight = 16.sp), color = g.textSecondary)
            Spacer(Modifier.height(8.dp))
            OptionRow(tr(R.string.no_opening_cue), null, p.openingCue == 0, playing = false, onPreview = null) { vm.setBriefing { it.copy(openingCue = 0) } }
            BriefingPlayer.CUES.forEachIndexed { i, res ->
                val n = i + 1
                OptionRow(
                    tr(R.string.cue_n, n, CUE_NAMES[i]), if (n == 1) tr(R.string.default_label) else null, p.openingCue == n, playing = cuePlaying == n,
                    onPreview = {
                        cuePlayer?.release(); cuePlayer = null
                        if (cuePlaying == n) cuePlaying = 0
                        else { cuePlayer = MediaPlayer.create(context, res)?.apply { start() }; cuePlaying = n }
                    },
                ) { vm.setBriefing { it.copy(openingCue = n) } }
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(g.hairline))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr(R.string.stings), style = sans(14.sp, FontWeight.Medium), color = g.textPrimary)
                    Text(tr(R.string.stings_hint), style = sans(11.sp), color = g.textSecondary)
                }
                switch(p.stings) { on -> vm.setBriefing { it.copy(stings = on) } }
            }
            Spacer(Modifier.height(6.dp))
            Text(tr(R.string.music_credit), style = sans(10.sp), color = g.textMuted)
        }

        Section(tr(R.string.voice)) {
            // Which speech engine reads: automatic, or any engine on the phone (Google's, the maker's, Editrove's).
            val engines = remember(ready, engineName) { if (ready) runCatching { tts.engines }.getOrNull().orEmpty() else emptyList() }
            if (engines.size > 1 || p.engine != null) {
                Text(tr(R.string.engine_title), style = sans(12.sp, FontWeight.SemiBold), color = g.textSecondary)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chip(tr(R.string.engine_auto), p.engine == null) { vm.setBriefing { it.copy(engine = null, voice = null) }; voiceCheck++ }
                    engines.forEach { e -> chip(e.label, p.engine == e.name) { vm.setBriefing { it.copy(engine = e.name, voice = null) }; voiceCheck++ } }
                }
                Spacer(Modifier.height(10.dp))
            }
            // No Google engine: how to get it (its voices are the most natural), or switch it back on.
            if (!news.openwire.i18n.VoiceSetup.hasGoogle(context)) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(g.chipFill).padding(14.dp),
                ) {
                    Text(tr(R.string.gv_title), style = sans(14.sp, FontWeight.SemiBold), color = g.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(tr(R.string.gv_body), style = sans(12.sp, lineHeight = 17.sp), color = g.textSecondary)
                    Spacer(Modifier.height(10.dp))
                    val open: (android.content.Intent) -> Boolean = { i -> runCatching { context.startActivity(i) }.isSuccess }
                    if (news.openwire.i18n.VoiceSetup.googleDisabled(context)) {
                        GvButton(tr(R.string.gv_turn_on), primary = true) { open(news.openwire.i18n.VoiceSetup.googleAppInfoIntent()) }
                    } else {
                        GvButton(tr(R.string.gv_get), primary = true) {
                            // The phone's app store first; without one that has it, the mirror.
                            if (!open(news.openwire.i18n.VoiceSetup.storeIntent())) open(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(news.openwire.i18n.VoiceSetup.MIRROR_URL)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        Spacer(Modifier.height(8.dp))
                        GvButton(tr(R.string.gv_mirror), primary = false) {
                            open(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(news.openwire.i18n.VoiceSetup.MIRROR_URL)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    GvButton(tr(R.string.gv_settings), primary = false) { open(news.openwire.i18n.VoiceSetup.ttsSettingsIntent()) }
                    Spacer(Modifier.height(8.dp))
                    Text(tr(R.string.gv_note), style = sans(11.sp, lineHeight = 16.sp), color = g.textMuted)
                }
                Spacer(Modifier.height(10.dp))
            }
            voiceState?.let { st ->
                val name = news.openwire.domain.engine.Languages.of(appLang)?.nativeName ?: appLang
                val (msg, action) = when (st) {
                    news.openwire.i18n.VoiceSetup.State.READY -> tr(R.string.voice_ready, name) to null
                    news.openwire.i18n.VoiceSetup.State.DOWNLOADING -> tr(R.string.voice_downloading, name) to null
                    news.openwire.i18n.VoiceSetup.State.MISSING -> tr(R.string.voice_missing, name) to tr(R.string.voice_install)
                    news.openwire.i18n.VoiceSetup.State.UNSUPPORTED -> tr(R.string.voice_unsupported, name) to tr(R.string.voice_install)
                    news.openwire.i18n.VoiceSetup.State.OWN_VOICE -> {
                        val pack = news.openwire.voice.VoicePacks.forLanguage(appLang)
                        when (val ps = pack?.let { packStates[it.id] }) {
                            is news.openwire.voice.VoiceStore.State.Downloading -> tr(R.string.voice_own_progress, name, (ps.progress * 100).toInt()) to null
                            is news.openwire.voice.VoiceStore.State.Failed -> tr(R.string.voice_own_failed) to tr(R.string.voice_own_download, pack.sizeMb)
                            else -> tr(R.string.voice_own_offer, name) to tr(R.string.voice_own_download, pack?.sizeMb ?: 20)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(msg, style = sans(12.sp, FontWeight.Medium, lineHeight = 17.sp), color = if (st == news.openwire.i18n.VoiceSetup.State.READY) g.textSecondary else g.accentSoft, modifier = Modifier.weight(1f))
                    if (action != null) Text(
                        action, style = sans(12.sp, FontWeight.SemiBold), color = g.textPrimary,
                        modifier = Modifier.padding(start = 8.dp).clip(CircleShape).background(g.chipFill)
                            .clickable {
                                if (st == news.openwire.i18n.VoiceSetup.State.OWN_VOICE) news.openwire.voice.VoicePacks.forLanguage(appLang)?.let { voiceStore.download(it) }
                                else { runCatching { context.startActivity(news.openwire.i18n.VoiceSetup.installIntent(context)) }; voiceCheck++ }
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
            OptionRow(tr(R.string.voice_default), tr(R.string.voice_default_hint), p.voice == null, playing = false, onPreview = { preview(null) }) { vm.setBriefing { it.copy(voice = null) } }
            if (!ready) Text(tr(R.string.loading_voices), style = sans(12.sp), color = g.textSecondary, modifier = Modifier.padding(vertical = 8.dp))
            voices.forEach { v ->
                OptionRow(voiceLabel(v), voiceDetail(v), p.voice == v.name, playing = false, onPreview = { preview(v) }) { vm.setBriefing { it.copy(voice = v.name) } }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (showAll) tr(R.string.voices_only, locale.displayLanguage) else tr(R.string.voices_all),
                style = sans(12.sp, FontWeight.SemiBold), color = g.accentSoft,
                modifier = Modifier.clip(CircleShape).clickable { showAll = !showAll }.padding(vertical = 8.dp),
            )
            Text(tr(R.string.more_voices), style = sans(10.sp, lineHeight = 15.sp), color = g.textMuted)
        }

        // Editrove's own voices: used where Google's engine is missing; anyone can download and hear them.
        run {
            // A preview engine that always speaks with Editrove's own voices.
            var ownReady by remember { mutableStateOf(false) }
            val own = remember(packStates.values.count { it == news.openwire.voice.VoiceStore.State.Installed }) {
                ownReady = false
                TextToSpeech(context.applicationContext, { ownReady = it == TextToSpeech.SUCCESS }, context.packageName)
            }
            DisposableEffect(own) { onDispose { own.shutdown() } }
            Section(tr(R.string.voice_engine_name)) {
                Text(tr(R.string.voice_own_hint), style = sans(11.sp, lineHeight = 16.sp), color = g.textSecondary)
                news.openwire.voice.VoicePacks.ALL.sortedBy { if (it.language == appLang) 0 else 1 }.forEach { pack ->
                    val ps = packStates[pack.id]
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${news.openwire.domain.engine.Languages.of(pack.language)?.nativeName ?: pack.language} · ${pack.name}",
                                style = sans(14.sp, FontWeight.Medium), color = g.textPrimary,
                            )
                            Text(
                                when (ps) {
                                    is news.openwire.voice.VoiceStore.State.Downloading -> tr(R.string.voice_own_progress_short, (ps.progress * 100).toInt())
                                    is news.openwire.voice.VoiceStore.State.Failed -> tr(R.string.voice_own_failed)
                                    // Sizes and credits are Latin text: kept left-to-right inside right-to-left lines.
                                    news.openwire.voice.VoiceStore.State.Installed -> tr(R.string.voice_own_installed) + " · \u2066" + pack.license + "\u2069"
                                    else -> "\u2066${pack.sizeMb} MB · " + pack.license + "\u2069"
                                },
                                style = sans(11.sp), color = g.textSecondary, maxLines = 2,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        when (ps) {
                            is news.openwire.voice.VoiceStore.State.Downloading ->
                                androidx.compose.material3.CircularProgressIndicator(progress = { ps.progress }, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = g.accent)
                            news.openwire.voice.VoiceStore.State.Installed -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    tr(R.string.preview), style = sans(12.sp, FontWeight.SemiBold), color = Color.White,
                                    modifier = Modifier.clip(CircleShape).background(g.accent).clickable(enabled = ownReady) {
                                        own.stop()
                                        own.voices?.firstOrNull { it.name == pack.voiceName }?.let { own.voice = it }
                                        own.setSpeechRate(p.speed)
                                        val sample = news.openwire.domain.engine.BriefingScript.intro(
                                            news.openwire.domain.engine.Languages.topicName("TOP", pack.language) ?: "Top Stories", 8,
                                            news.openwire.domain.model.BriefingDetail.HEADLINES, java.time.LocalDateTime.now(), pack.language,
                                        )
                                        own.speak(sample, TextToSpeech.QUEUE_FLUSH, null, "own-preview")
                                    }.padding(horizontal = 12.dp, vertical = 7.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    tr(R.string.voice_own_delete), style = sans(12.sp, FontWeight.SemiBold), color = g.textPrimary,
                                    modifier = Modifier.clip(CircleShape).background(g.chipFill).clickable { voiceStore.delete(pack) }.padding(horizontal = 12.dp, vertical = 7.dp),
                                )
                            }
                            else -> Text(
                                tr(R.string.voice_own_get), style = sans(12.sp, FontWeight.SemiBold), color = Color.White,
                                modifier = Modifier.clip(CircleShape).background(g.accent).clickable { voiceStore.download(pack) }.padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun speedLabel(v: Float) = (if (v == v.toInt().toFloat()) "${v.toInt()}" else "$v").trimEnd('0').trimEnd('.') + "×"

private fun voiceLabel(v: Voice): String {
    val tag = v.name.substringAfter("-x-", "").substringBefore("-").uppercase().ifEmpty { "Standard" }
    return "${v.locale.displayName} · $tag"
}

private fun voiceDetail(v: Voice): String = listOfNotNull(
    if (v.isNetworkConnectionRequired) tr(R.string.voice_network) else tr(R.string.voice_local),
    if (v.quality >= Voice.QUALITY_HIGH) tr(R.string.voice_hq) else null,
).joinToString(" · ")

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Text(title.uppercase(), style = mono(10.sp, FontWeight.SemiBold, 0.14.em), color = Glass.textSecondary, modifier = Modifier.padding(bottom = 10.dp))
        content()
    }
}

@Composable
private fun OptionRow(title: String, detail: String?, selected: Boolean, playing: Boolean, onPreview: (() -> Unit)?, onSelect: () -> Unit) {
    val g = Glass
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onSelect).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(if (selected) g.accent else Color.Transparent)
                .border(1.5.dp, if (selected) g.accent else g.textMuted, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = sans(13.sp, FontWeight.Medium), color = g.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.let { Text(it, style = sans(11.sp), color = g.textSecondary) }
        }
        if (onPreview != null) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(g.chipFill).clickable(onClick = onPreview),
                contentAlignment = Alignment.Center,
            ) { Icon(if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, tr(R.string.preview), tint = g.textPrimary, modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun GvButton(text: String, primary: Boolean, onClick: () -> Unit) {
    val g = Glass
    Text(
        text, style = sans(13.sp, FontWeight.SemiBold), color = if (primary) Color.White else g.textPrimary,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clip(CircleShape).background(if (primary) g.accent else g.deviceBg.copy(alpha = 0.6f))
            .clickable(onClick = onClick).padding(vertical = 11.dp, horizontal = 14.dp),
    )
}

@Composable
private fun presenterName(p: news.openwire.domain.model.Presenter) = tr(when (p) {
    news.openwire.domain.model.Presenter.ANCHOR -> R.string.presenter_anchor
    news.openwire.domain.model.Presenter.MODERN -> R.string.presenter_modern
    news.openwire.domain.model.Presenter.CORRESPONDENT -> R.string.presenter_correspondent
    news.openwire.domain.model.Presenter.CONVERSATIONAL -> R.string.presenter_conversational
})

@Composable
private fun presenterHint(p: news.openwire.domain.model.Presenter) = tr(when (p) {
    news.openwire.domain.model.Presenter.ANCHOR -> R.string.presenter_anchor_hint
    news.openwire.domain.model.Presenter.MODERN -> R.string.presenter_modern_hint
    news.openwire.domain.model.Presenter.CORRESPONDENT -> R.string.presenter_correspondent_hint
    news.openwire.domain.model.Presenter.CONVERSATIONAL -> R.string.presenter_conversational_hint
})
