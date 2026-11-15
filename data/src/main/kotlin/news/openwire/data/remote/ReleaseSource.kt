// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data.remote

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import news.openwire.domain.engine.AppVersion
import news.openwire.domain.engine.ReleaseInfo
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Reads the app's published builds from GitHub Releases (public, unauthenticated —
 * no identifiers are sent) and downloads an APK with SHA-256 verification.
 */
class ReleaseSource(
    private val repo: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class Asset(val name: String, val size: Long = 0, val digest: String? = null, val browser_download_url: String)
    @Serializable private data class Release(
        val tag_name: String, val name: String? = null, val body: String? = null,
        val prerelease: Boolean = false, val draft: Boolean = false, val assets: List<Asset> = emptyList(),
    )

    suspend fun releases(): List<ReleaseInfo> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("https://api.github.com/repos/$repo/releases?per_page=15")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Editrove-updater")
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException(if (r.code == 403) "GitHub rate limit — try again later." else "HTTP ${r.code}")
            json.decodeFromString<List<Release>>(r.body?.string().orEmpty()).filter { !it.draft }.mapNotNull { rel ->
                val version = AppVersion.parse(rel.tag_name) ?: return@mapNotNull null
                val apk = rel.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return@mapNotNull null
                ReleaseInfo(
                    version = version, tag = rel.tag_name, title = rel.name ?: rel.tag_name, notes = rel.body.orEmpty(),
                    apkUrl = apk.browser_download_url, apkSize = apk.size,
                    sha256 = apk.digest?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(),
                    prerelease = rel.prerelease,
                )
            }
        }
    }

    /** Downloads to [dest], reporting progress 0..1, and verifies the published SHA-256. */
    suspend fun download(release: ReleaseInfo, dest: File, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        val req = Request.Builder().url(release.apkUrl).header("User-Agent", "Editrove-updater").build()
        val digest = MessageDigest.getInstance("SHA-256")
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Download failed (HTTP ${r.code})")
            val body = r.body ?: throw IOException("Empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize
            body.byteStream().use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        read += n
                        if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (release.sha256 != null && hash != release.sha256) {
            dest.delete()
            throw IOException("The download didn't match the published checksum and was discarded.")
        }
        dest
    }
}
