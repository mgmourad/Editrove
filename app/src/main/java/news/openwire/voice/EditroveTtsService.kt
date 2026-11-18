// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.voice

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.Locale

/**
 * Editrove as an Android speech engine, speaking with its downloaded voices. The audio
 * briefing and the reader use it like any engine (utterances, progress, speed), so it
 * fits in wherever Google's engine is missing. Speech is made on the phone; nothing is sent.
 */
class EditroveTtsService : TextToSpeechService() {
    private lateinit var store: VoiceStore
    private var loaded: VoicePack? = null
    private var engine: OfflineTts? = null
    @Volatile private var stopped = false
    private var sink: PcmSink? = null

    override fun onCreate() {
        store = VoiceStore(this)
        super.onCreate()
    }

    override fun onDestroy() {
        engine?.release()
        engine = null
        super.onDestroy()
    }

    private fun localeOf(p: VoicePack) = when (p.language) {
        "ar" -> Locale("ar", "JO"); "en" -> Locale.US; "fr" -> Locale.FRANCE; "es" -> Locale("es", "ES"); else -> Locale.GERMANY
    }

    private fun availability(lang: String?): Int {
        val p = VoicePacks.fromIso3(lang) ?: return TextToSpeech.LANG_NOT_SUPPORTED
        return if (store.isInstalled(p)) TextToSpeech.LANG_COUNTRY_AVAILABLE else TextToSpeech.LANG_MISSING_DATA
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int = availability(lang)

    override fun onGetLanguage(): Array<String> {
        val p = loaded ?: VoicePacks.ALL.firstOrNull { store.isInstalled(it) } ?: VoicePacks.ALL.first()
        val l = localeOf(p)
        return arrayOf(l.isO3Language, l.isO3Country, "")
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        val r = availability(lang)
        if (r >= TextToSpeech.LANG_AVAILABLE) VoicePacks.fromIso3(lang)?.let { load(it) }
        return r
    }

    override fun onGetVoices(): List<Voice> = VoicePacks.ALL.map { p ->
        Voice(
            p.voiceName, localeOf(p), Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, false,
            if (store.isInstalled(p)) emptySet() else setOf(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED),
        )
    }

    override fun onIsValidVoiceName(voiceName: String?): Int =
        if (VoicePacks.ALL.any { it.voiceName == voiceName && store.isInstalled(it) }) TextToSpeech.SUCCESS else TextToSpeech.ERROR

    override fun onLoadVoice(voiceName: String?): Int {
        val p = VoicePacks.ALL.firstOrNull { it.voiceName == voiceName } ?: return TextToSpeech.ERROR
        return if (store.isInstalled(p) && load(p) != null) TextToSpeech.SUCCESS else TextToSpeech.ERROR
    }

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? =
        VoicePacks.fromIso3(lang)?.voiceName

    override fun onStop() { stopped = true }

    /** The engine for [p] (one voice in memory at a time). */
    @Synchronized
    private fun load(p: VoicePack): OfflineTts? {
        if (loaded == p && engine != null) return engine
        if (!store.isInstalled(p)) return null
        engine?.release()
        val dir = store.dir(p)
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = File(dir, p.model).absolutePath,
                    tokens = File(dir, "tokens.txt").absolutePath,
                    dataDir = File(dir, "espeak-ng-data").absolutePath,
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
        )
        val t0 = android.os.SystemClock.elapsedRealtime()
        engine = runCatching { OfflineTts(null, config) }.onFailure { android.util.Log.w("EditroveVoice", "load ${p.id}", it) }.getOrNull()
        android.util.Log.i("EditroveVoice", "loaded ${p.id} in ${android.os.SystemClock.elapsedRealtime() - t0} ms")
        loaded = if (engine != null) p else null
        return engine
    }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        stopped = false
        val pack = VoicePacks.ALL.firstOrNull { it.voiceName == request.voiceName } ?: VoicePacks.fromIso3(request.language)
        val tts = pack?.let { load(it) }
        if (tts == null) { callback.error(TextToSpeech.ERROR_NOT_INSTALLED_YET); return }
        val text = request.charSequenceText?.toString().orEmpty().trim()
        if (text.isEmpty()) { callback.start(tts.sampleRate(), AudioFormat.ENCODING_PCM_16BIT, 1); callback.done(); return }
        if (callback.start(tts.sampleRate(), AudioFormat.ENCODING_PCM_16BIT, 1) != TextToSpeech.SUCCESS) return
        // Android's rate: 100 = normal. Piper's speed: 1 = normal.
        val speed = (request.speechRate / 100f).coerceIn(0.5f, 3f)
        val max = callback.maxBufferSize
        runCatching {
            tts.generateWithCallback(text, 0, speed, PcmSink(callback, max) { stopped }.also { sink = it })
        }.onFailure { callback.error(TextToSpeech.ERROR_SYNTHESIS); return }
        callback.done()
        android.util.Log.i("EditroveVoice", "${pack.language}: ${text.length} chars spoken")
    }
}

/**
 * Receives each chunk of generated speech and hands it to Android as 16-bit PCM. A named
 * class (not a lambda): sherpa-onnx's native code calls invoke(float[]) by signature.
 * Returning 0 tells the generator to stop.
 */
class PcmSink(
    private val callback: SynthesisCallback,
    private val max: Int,
    private val stopped: () -> Boolean,
) : Function1<FloatArray, Int> {
    var failed = false
        private set

    override fun invoke(samples: FloatArray): Int {
        if (stopped() || failed) return 0
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt()
            bytes[2 * i] = (v and 0xff).toByte()
            bytes[2 * i + 1] = ((v shr 8) and 0xff).toByte()
        }
        var off = 0
        while (off < bytes.size) {
            if (stopped()) return 0
            val n = minOf(max, bytes.size - off)
            if (callback.audioAvailable(bytes, off, n) != TextToSpeech.SUCCESS) { failed = true; return 0 }
            off += n
        }
        return 1
    }
}
