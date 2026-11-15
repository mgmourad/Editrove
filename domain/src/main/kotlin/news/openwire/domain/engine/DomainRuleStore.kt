// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

import news.openwire.domain.model.DomainRule

/**
 * Persistence boundary for user-defined domain overrides. Kept as an interface
 * so the domain module stays free of Android/storage dependencies; the data
 * module supplies a DataStore-backed implementation.
 */
interface DomainRuleStore {
    fun load(): Map<String, DomainRule>
    fun save(rules: Map<String, DomainRule>)
}

/** Simple in-memory store used as the default and in tests. */
class InMemoryDomainRuleStore : DomainRuleStore {
    private var rules: Map<String, DomainRule> = emptyMap()

    override fun load(): Map<String, DomainRule> = rules

    override fun save(rules: Map<String, DomainRule>) {
        this.rules = rules
    }
}
