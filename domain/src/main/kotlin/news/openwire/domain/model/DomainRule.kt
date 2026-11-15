package news.openwire.domain.model

/**
 * A resolved paywall rule for a publisher domain.
 *
 * @property domain the normalized domain this rule applies to.
 * @property matchedDomain when matched via subdomain suffix, the registered parent domain.
 * @property isDefault true when no rule existed and the DIRECT fallback was returned.
 */
data class DomainRule(
    val domain: String,
    val strategy: Strategy,
    val paywall: String? = null,
    val label: String? = null,
    val isCustom: Boolean = false,
    val matchedDomain: String? = null,
    val isDefault: Boolean = false,
)
