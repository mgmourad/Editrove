// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.i18n

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Makes sure the briefing has a voice in Editrove's language. When the phone's speech
 * engine (Google's, usually) lists a voice for it that isn't installed yet, selecting it
 * and asking it to speak makes the engine download it; otherwise the engine's own
 * "install voice data" screen is offered.
 */
object VoiceSetup {
    /** OWN_VOICE: no voice on the phone, but Editrove's own can be downloaded. */
    enum class State { READY, DOWNLOADING, MISSING, UNSUPPORTED, OWN_VOICE }

    const val GOOGLE_TTS = "com.google.android.tts"

    private fun installed(v: Voice) = !v.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)

    /** The best voice for [lang] among [voices]: installed first, on-device first, then quality. */
    fun best(voices: Collection<Voice>, lang: String, country: String? = null): Voice? =
        voices.filter { it.locale.language == lang }
            .sortedWith(
                compareByDescending<Voice> { installed(it) }
                    // The engine's standard voice for the language first (listed first in settings).
                    .thenByDescending { !it.name.contains("-x-") }
                    .thenByDescending { country != null && it.locale.country.equals(country, true) }
                    .thenBy { it.isNetworkConnectionRequired }
                    .thenByDescending { it.quality },
            ).firstOrNull()

    /**
     * Checks (and, when needed, starts downloading) a voice for [lang]. [country] picks the
     * accent when there's a choice (an Egyptian edition prefers an Egyptian Arabic voice).
     */
    /** The voice [ensure] settled on (its name), when one is installed. */
    @Volatile var lastReady: String? = null
        private set

    suspend fun ensure(context: Context, lang: String, country: String? = null): State = withContext(Dispatchers.Main) {
        val app = context.applicationContext
        // No Google engine: Editrove's own voice for the language (downloaded once).
        if (!hasGoogle(app)) {
            val pack = news.openwire.voice.VoicePacks.forLanguage(lang) ?: return@withContext State.UNSUPPORTED
            val store = news.openwire.voice.VoiceStore(app)
            if (store.isInstalled(pack)) { lastReady = pack.voiceName; return@withContext State.READY }
            return@withContext State.OWN_VOICE
        }
        val engine = engine(app, lang)
        var tts: TextToSpeech? = null
        val ok = withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine { cont ->
                tts = TextToSpeech(app, { status -> if (cont.isActive) cont.resume(status == TextToSpeech.SUCCESS) }, engine)
            }
        } == true
        val t = tts ?: return@withContext State.UNSUPPORTED
        try {
            if (!ok) return@withContext State.UNSUPPORTED
            val all = runCatching { t.voices.orEmpty() }.getOrDefault(emptySet())
            val pick = best(all, lang, country)
            when {
                pick != null && installed(pick) -> { lastReady = pick.name; State.READY }
                pick != null -> {
                    // Selecting a listed-but-missing voice and speaking makes Google's engine fetch it.
                    t.voice = pick
                    t.speak(" ", TextToSpeech.QUEUE_FLUSH, null, "voice-setup")
                    delay(1_500)
                    State.DOWNLOADING
                }
                else -> when (t.isLanguageAvailable(Locale(lang))) {
                    TextToSpeech.LANG_MISSING_DATA -> { t.setLanguage(Locale(lang)); State.MISSING }
                    TextToSpeech.LANG_NOT_SUPPORTED -> State.UNSUPPORTED
                    else -> State.READY
                }
            }
        } finally {
            t.stop(); t.shutdown()
        }
    }

    /**
     * Makes the briefing speak Editrove's language: with the app in Arabic (say) and the voice
     * still on Editrove's default or on another language's voice, the language's standard
     * voice is selected. Back in English, a voice from another language is cleared.
     */
    suspend fun adopt(context: Context, settings: news.openwire.data.local.SettingsRepository) {
        val s = settings.settings.value
        val lang = news.openwire.domain.engine.Languages.effective(s.language)
        val current = s.briefing.voice
        val currentLang = current?.substringBefore('-')?.lowercase()
        if (lang == "en") {
            if (current != null && currentLang != null && currentLang != "en") settings.update { it.copy(briefing = it.briefing.copy(voice = null)) }
            return
        }
        if (current != null && currentLang == lang) return
        val state = ensure(context, lang, s.edition.country)
        // No Google engine: fetch Editrove's own voice for the language once, on Wi-Fi.
        if (state == State.OWN_VOICE && unmetered(context)) {
            news.openwire.voice.VoicePacks.forLanguage(lang)?.let { pack -> (context.applicationContext as news.openwire.OpenWireApp).container.voices.download(pack) }
            return
        }
        if (state != State.READY) return
        val name = lastReady ?: return
        settings.update { it.copy(briefing = it.briefing.copy(voice = name)) }
    }

    /** Google's engine is installed and switched on (a disabled one can't speak). */
    fun hasGoogle(context: Context): Boolean = runCatching { context.packageManager.getApplicationInfo(GOOGLE_TTS, 0).enabled }.getOrDefault(false)

    /**
     * The speech engine to use for [lang]: Google's when installed (it has the most voices);
     * else Editrove's own when its voice for [lang] is downloaded; else the phone's default.
     */
    fun engine(context: Context, lang: String = news.openwire.domain.engine.Languages.effective(null)): String? = when {
        hasGoogle(context) -> GOOGLE_TTS
        news.openwire.voice.VoiceStore(context).installed(lang) != null -> context.packageName
        else -> null
    }

    /** The speech engine's own screen for downloading voices (Google's, else the phone's text-to-speech settings). */
    fun installIntent(context: Context): Intent =
        if (hasGoogle(context)) Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(GOOGLE_TTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        else Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun unmetered(context: Context): Boolean {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
