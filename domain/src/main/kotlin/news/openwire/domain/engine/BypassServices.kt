// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import news.openwire.domain.model.Strategy

/**
 * The third-party reader/bypass services the paywall engine relies on, and the
 * order to fall back through when one is down, changed, or switched off by the
 * user. Archive services are last-resort fallbacks because they only work when a
 * snapshot already exists.
 */
object BypassServices {

    data class Service(val strategy: Strategy, val name: String, val homeUrl: String)

    val ALL: List<Service> = listOf(
        Service(Strategy.SMRY, "smry.ai", "https://smry.ai/"),
        Service(Strategy.REMOVEPAYWALL, "RemovePaywall", "https://www.removepaywall.com/"),
        Service(Strategy.UNWALL, "UnWall", "https://unwall.app/"),
        Service(Strategy.FREEDIUM, "Freedium", "https://freedium-mirror.cfd/"),
        Service(Strategy.ARCHIVE_IS, "archive.today", "https://archive.ph/"),
        Service(Strategy.WAYBACK, "Wayback Machine", "https://web.archive.org/"),
    )

    fun service(strategy: Strategy?): Service? = ALL.firstOrNull { it.strategy == strategy }

    fun isArchive(strategy: Strategy) = strategy == Strategy.ARCHIVE_IS || strategy == Strategy.WAYBACK

    /** Fallbacks for each strategy, best first. DIRECT and READER need no third party. */
    fun fallbacks(strategy: Strategy): List<Strategy> = when (strategy) {
        Strategy.SMRY -> listOf(Strategy.REMOVEPAYWALL, Strategy.UNWALL, Strategy.ARCHIVE_IS, Strategy.WAYBACK)
        Strategy.REMOVEPAYWALL -> listOf(Strategy.SMRY, Strategy.UNWALL, Strategy.ARCHIVE_IS, Strategy.WAYBACK)
        Strategy.UNWALL -> listOf(Strategy.SMRY, Strategy.REMOVEPAYWALL, Strategy.ARCHIVE_IS, Strategy.WAYBACK)
        Strategy.FREEDIUM -> listOf(Strategy.SMRY, Strategy.REMOVEPAYWALL, Strategy.ARCHIVE_IS, Strategy.WAYBACK)
        Strategy.ARCHIVE_IS -> listOf(Strategy.WAYBACK)
        Strategy.WAYBACK -> listOf(Strategy.ARCHIVE_IS)
        Strategy.DIRECT, Strategy.READER -> emptyList()
    }

    /**
     * The strategies to try, in order: the publisher's own strategy first unless the
     * user switched that service off, then its fallbacks, skipping disabled services.
     */
    fun plan(strategy: Strategy, disabled: Set<Strategy>): List<Strategy> =
        (listOf(strategy) + fallbacks(strategy)).filter { it !in disabled }.distinct()
}
