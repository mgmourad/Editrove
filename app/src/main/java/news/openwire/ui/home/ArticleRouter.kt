// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.ui.home

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import news.openwire.data.remote.ArchiveChecker
import news.openwire.domain.engine.DomainRegistry
import news.openwire.domain.engine.StrategyRouter
import news.openwire.domain.model.Article
import news.openwire.domain.model.Strategy

/**
 * Routes an article tap through the paywall engine: applies the current domain
 * rule, checks archive availability for hard paywalls, and opens the result in
 * Custom Tabs (or reports "locked").
 */
class ArticleRouter(
    private val registry: DomainRegistry,
    private val archiveChecker: ArchiveChecker,
) {
    fun open(scope: CoroutineScope, context: Context, article: Article, onLocked: () -> Unit = {}) {
        scope.launch {
            val rule = registry.getRule(article.domain ?: "")
            val routed = StrategyRouter.applyStrategy(registry, article, rule)
            val url = when (rule.strategy) {
                Strategy.ARCHIVE_IS -> archiveChecker.checkArchiveIs(routed.originalUrl).archiveUrl
                Strategy.WAYBACK -> archiveChecker.checkWayback(routed.originalUrl).archiveUrl
                else -> routed.targetUrl
            }
            if (url != null) {
                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
            } else {
                onLocked()
            }
        }
    }
}
