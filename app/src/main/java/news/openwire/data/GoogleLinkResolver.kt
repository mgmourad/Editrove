// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import news.openwire.domain.engine.GoogleNewsUrls

/**
 * Finds the publisher's page behind a news.google.com link, the way a browser does:
 * the link is opened in an invisible WebView and Google's page redirects to the
 * publisher, whose address is captured before anything loads. Results are cached;
 * links resolve one at a time so the phone isn't flooded with WebViews.
 */
class GoogleLinkResolver(context: Context) {
    private val app = context.applicationContext
    /** Resolved links survive restarts (a story's link never changes). */
    private val prefs = app.getSharedPreferences("google_links", Context.MODE_PRIVATE)
    private val cache = ConcurrentHashMap<String, String>()
    private val failed = ConcurrentHashMap<String, Long>()
    /** Three hidden WebViews at a time: fast enough for a feed, light on the phone. */
    private val gate = kotlinx.coroutines.sync.Semaphore(3)

    private fun stored(link: String): String? = cache[link] ?: prefs.getString(key(link), null)?.also { cache[link] = it }

    private fun store(link: String, url: String) {
        cache[link] = url
        val e = prefs.edit().putString(key(link), url)
        if (prefs.all.size > 3000) e.clear().putString(key(link), url) // simple cap
        e.apply()
    }

    private fun key(link: String) = GoogleNewsUrls.googleNewsArticleId(link).ifEmpty { link }

    suspend fun resolve(link: String): String? {
        if (!GoogleNewsUrls.isGoogleNewsUrl(link)) return link
        // Older links carry the publisher URL inside them.
        GoogleNewsUrls.decodeEmbeddedGoogleUrl(link).takeIf { it.isNotEmpty() && !isGoogle(it) }?.let { return it }
        stored(link)?.let { return it }
        failed[link]?.let { if (System.currentTimeMillis() - it < RETRY_MS) return null }
        return gate.withPermit {
            stored(link) ?: withContext(Dispatchers.Main) { withTimeoutOrNull(TIMEOUT_MS) { follow(link) } }
        }.also { if (it != null) store(link, it) else failed[link] = System.currentTimeMillis() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun follow(link: String): String? = suspendCancellableCoroutine { cont ->
        val web = WebView(app)
        var done = false
        fun finish(url: String?) {
            if (done) return
            done = true
            web.stopLoading()
            web.post { web.destroy() }
            if (cont.isActive) cont.resume(url)
        }
        web.settings.apply {
            javaScriptEnabled = true // Google's page redirects with a script
            blockNetworkImage = true
            loadsImagesAutomatically = false
            domStorageEnabled = true
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (!isGoogle(url)) { finish(url); return true }
                return false
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (!isGoogle(url)) finish(url)
            }
        }
        cont.invokeOnCancellation { web.post { runCatching { web.stopLoading(); web.destroy() } } }
        web.loadUrl(link)
    }

    /**
     * The page's HTML as a browser renders it (scripts run, bot walls passed), for sites
     * that refuse plain HTTP clients. Uses the same hidden-WebView pool.
     */
    suspend fun render(url: String): String? = gate.withPermit {
        withContext(Dispatchers.Main) { withTimeoutOrNull(RENDER_MS) { load(url) } }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun load(url: String): String? = suspendCancellableCoroutine { cont ->
        val web = WebView(app)
        var done = false
        fun finish(html: String?) {
            if (done) return
            done = true
            web.post { runCatching { web.stopLoading(); web.destroy() } }
            if (cont.isActive) cont.resume(html)
        }
        web.settings.apply { javaScriptEnabled = true; domStorageEnabled = true; blockNetworkImage = true; loadsImagesAutomatically = false }
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, finishedUrl: String) {
                // Give client-rendered pages a moment to fill in.
                view.postDelayed({
                    if (!done) view.evaluateJavascript("document.documentElement.outerHTML") { json ->
                        finish(runCatching { org.json.JSONTokener(json).nextValue() as? String }.getOrNull())
                    }
                }, 1200)
            }
        }
        cont.invokeOnCancellation { web.post { runCatching { web.stopLoading(); web.destroy() } } }
        web.loadUrl(url)
    }

    private fun isGoogle(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return true
        if (!url.startsWith("http")) return true
        return host == "google.com" || host.endsWith(".google.com") || host.endsWith(".gstatic.com") ||
            host.endsWith("googleusercontent.com") || host.endsWith(".googleapis.com") || GOOGLE_TLD.containsMatchIn(host)
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
        const val RETRY_MS = 3 * 60_000L
        const val RENDER_MS = 15_000L
    }
}

private val GOOGLE_TLD = Regex("(^|\\.)google\\.[a-z.]+$")
