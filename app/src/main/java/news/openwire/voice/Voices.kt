// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.voice

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

/**
 * A voice Editrove can speak with on its own, for phones without Google's speech engine
 * (Huawei and others). Piper voices, run on the device by sherpa-onnx; downloaded once
 * from sherpa-onnx's public releases, then used offline.
 */
data class VoicePack(
    val language: String,
    val id: String,
    val name: String,
    val sizeMb: Int,
    val license: String,
    val model: String,
) {
    val url: String get() = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$id.tar.bz2"
    /** Its name as an Android voice ("editrove-ar"). */
    val voiceName: String get() = "editrove-$language"
}

object VoicePacks {
    val ALL = listOf(
        VoicePack("ar", "vits-piper-ar_JO-kareem-medium-int8", "Kareem", 19, "Piper voice; dataset github.com/AliMokhammad/arabicttstrain", "ar_JO-kareem-medium.onnx"),
        VoicePack("en", "vits-piper-en_US-ljspeech-medium-int8", "LJ", 20, "Piper voice; LJ Speech dataset (public domain)", "en_US-ljspeech-medium.onnx"),
        VoicePack("fr", "vits-piper-fr_FR-siwis-medium-int8", "Siwis", 19, "Piper voice; SIWIS dataset (CC BY 4.0)", "fr_FR-siwis-medium.onnx"),
        VoicePack("es", "vits-piper-es_ES-davefx-medium-int8", "Davefx", 20, "Piper voice; davefx dataset (CC0)", "es_ES-davefx-medium.onnx"),
        VoicePack("de", "vits-piper-de_DE-thorsten-medium-int8", "Thorsten", 19, "Piper voice; Thorsten dataset (CC0)", "de_DE-thorsten-medium.onnx"),
    )

    fun forLanguage(lang: String?): VoicePack? = ALL.firstOrNull { it.language == lang?.take(2)?.lowercase() }

    /** ISO 639-2 codes, as Android's speech framework asks for them. */
    fun fromIso3(iso3: String?): VoicePack? = when (iso3?.lowercase()) {
        "ara" -> forLanguage("ar"); "eng" -> forLanguage("en"); "fra", "fre" -> forLanguage("fr")
        "spa" -> forLanguage("es"); "deu", "ger" -> forLanguage("de")
        else -> forLanguage(iso3)
    }
}

/** Which voices are on the phone, and fetching them. */
class VoiceStore(context: Context) {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "voices")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    sealed interface State {
        data object Missing : State
        data class Downloading(val progress: Float) : State
        data object Installed : State
        data class Failed(val reason: String) : State
    }

    private val _states = MutableStateFlow(VoicePacks.ALL.associate { it.id to (if (isInstalled(it)) State.Installed else State.Missing) as State })
    val states: StateFlow<Map<String, State>> = _states.asStateFlow()

    fun dir(pack: VoicePack) = File(root, pack.id)
    fun isInstalled(pack: VoicePack) = File(dir(pack), pack.model).exists() && File(dir(pack), "tokens.txt").exists()
    fun installed(lang: String?) = VoicePacks.forLanguage(lang)?.takeIf { isInstalled(it) }

    /** Downloads and unpacks [pack] (in the background; [states] follows along). */
    fun download(pack: VoicePack, onDone: (Boolean) -> Unit = {}) {
        val now = _states.value[pack.id]
        if (now is State.Downloading || now == State.Installed) return
        set(pack, State.Downloading(0f))
        android.util.Log.i("EditroveVoice", "downloading ${pack.id}")
        scope.launch {
            val ok = runCatching { fetch(pack) }.onFailure {
                android.util.Log.w("EditroveVoice", "download ${pack.id} failed", it)
                set(pack, State.Failed(it.message ?: "Download failed"))
            }.isSuccess
            if (ok) android.util.Log.i("EditroveVoice", "installed ${pack.id}")
            if (ok) set(pack, State.Installed)
            onDone(ok)
        }
    }

    fun delete(pack: VoicePack) {
        dir(pack).deleteRecursively()
        set(pack, State.Missing)
    }

    private fun set(pack: VoicePack, s: State) = _states.update { it + (pack.id to s) }

    private fun fetch(pack: VoicePack) {
        root.mkdirs()
        val tmp = File(root, pack.id + ".part")
        tmp.deleteRecursively(); tmp.mkdirs()
        client.newCall(Request.Builder().url(pack.url).header("User-Agent", "Editrove/1.0 (Android news reader)").build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val body = r.body ?: error("Empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: (pack.sizeMb * 1_048_576L)
            val counting = CountingStream(body.byteStream()) { read -> set(pack, State.Downloading((read.toFloat() / total).coerceIn(0f, 0.99f))) }
            TarArchiveInputStream(BZip2CompressorInputStream(counting.buffered())).use { tar ->
                val base = tmp.canonicalPath
                var e = tar.nextEntry
                while (e != null) {
                    // Files only, stripped of the archive's top folder, and never outside the voice's folder.
                    val rel = e.name.substringAfter('/', "")
                    val keep = rel.isNotEmpty() && !e.isDirectory && keepFile(rel, pack.language)
                    if (keep) {
                        val out = File(tmp, rel)
                        if (!out.canonicalPath.startsWith(base + File.separator)) error("Bad archive entry")
                        out.parentFile?.mkdirs()
                        out.outputStream().use { tar.copyTo(it) }
                    }
                    e = tar.nextEntry
                }
            }
        }
        if (!File(tmp, pack.model).exists() || !File(tmp, "tokens.txt").exists()) error("Voice files missing")
        dir(pack).deleteRecursively()
        if (!tmp.renameTo(dir(pack))) error("Couldn't store the voice")
    }

    /** Pronunciation data for this voice's language only (the archive carries 100+ languages). */
    private fun keepFile(rel: String, lang: String): Boolean {
        if (!rel.startsWith("espeak-ng-data/")) return !rel.endsWith(".md") && !rel.endsWith("MODEL_CARD")
        val name = rel.substringAfterLast('/')
        if (name.endsWith("_dict")) return name == "${lang}_dict" || name == "en_dict"
        return true
    }

    private class CountingStream(private val inner: java.io.InputStream, private val onRead: (Long) -> Unit) : java.io.InputStream() {
        private var count = 0L
        private var lastReport = 0L
        override fun read(): Int = inner.read().also { if (it >= 0) bump(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len).also { if (it > 0) bump(it.toLong()) }
        private fun bump(n: Long) { count += n; if (count - lastReport > 256_000) { lastReport = count; onRead(count) } }
        override fun close() = inner.close()
    }
}
