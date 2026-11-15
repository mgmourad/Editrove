// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.home

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import news.openwire.data.remote.ArchiveChecker
import news.openwire.data.remote.ServiceHealth
import news.openwire.domain.engine.BypassServices
import news.openwire.domain.engine.DomainRegistry
import news.openwire.domain.engine.StrategyRouter
import news.openwire.domain.model.Article
import news.openwire.domain.model.Strategy

/**
 * Routes an article through the paywall engine with graceful degradation: the
 * publisher's strategy first, then — if that service is switched off, unreachable
 * or gone — the next reader service, then archive snapshots. Hard paywalls with no
 * snapshot are reported as locked.
 */
class ArticleRouter(
    private val registry: DomainRegistry,
    private val archiveChecker: ArchiveChecker,
    private val health: ServiceHealth = ServiceHealth(),
    private val disabledServices: () -> Set<Strategy> = { emptySet() },
) {
    data class Route(val url: String?, val via: Strategy?, val intended: Strategy)

    /** The reader service a story is routed through (null for direct/archive stories). */
    fun serviceFor(article: Article): BypassServices.Service? {
        val strategy = registry.getRule(article.domain.orEmpty()).strategy
        return BypassServices.service(strategy)?.takeIf { !BypassServices.isArchive(strategy) }
    }

    suspend fun resolve(article: Article): Route {
        val rule = registry.getRule(article.domain.orEmpty())
        val original = StrategyRouter.applyStrategy(registry, article, rule).originalUrl.orEmpty()
        for (s in BypassServices.plan(rule.strategy, disabledServices())) {
            val url = when {
                s == Strategy.ARCHIVE_IS -> archiveChecker.checkArchiveIs(original).takeIf { it.available }?.archiveUrl
                s == Strategy.WAYBACK -> archiveChecker.checkWayback(original).takeIf { it.available }?.archiveUrl
                BypassServices.service(s) != null && !health.check(s).up -> null
                else -> StrategyRouter.buildStrategyUrl(s, original)
            }
            if (!url.isNullOrEmpty()) return Route(url, s, rule.strategy)
        }
        // Nothing worked: hard paywalls stay locked; everything else opens the publisher page.
        return if (BypassServices.isArchive(rule.strategy)) Route(null, null, rule.strategy)
        else Route(original.ifEmpty { null }, Strategy.DIRECT, rule.strategy)
    }

    fun open(scope: CoroutineScope, context: Context, article: Article, onLocked: () -> Unit = {}) {
        scope.launch {
            val route = resolve(article)
            val url = route.url ?: return@launch onLocked()
            if (route.via != route.intended) {
                val from = BypassServices.service(route.intended)?.name ?: "The usual reader"
                val to = BypassServices.service(route.via)?.name ?: "the publisher's page"
                Toast.makeText(context, "$from is unavailable — opening via $to.", Toast.LENGTH_SHORT).show()
            }
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url))
        }
    }
}
