package news.openwire.domain.model

/**
 * Paywall routing strategies, ported from js/domainRegistry.js.
 * `wireValue` is the serialized form used in stored rules and JSON.
 */
enum class Strategy(val wireValue: String) {
    DIRECT("direct"),
    SMRY("smry"),
    REMOVEPAYWALL("removepaywall"),
    UNWALL("unwall"),
    FREEDIUM("freedium"),
    READER("reader"),
    ARCHIVE_IS("archive_is"),
    WAYBACK("wayback");

    companion object {
        /**
         * Parses a stored strategy value, migrating the legacy "12ft" strategy
         * to READER (mirrors the PWA's loadCustomOverrides migration).
         */
        fun fromWire(value: String?): Strategy? {
            val v = value?.trim()?.lowercase() ?: return null
            if (v == "12ft") return READER
            return entries.firstOrNull { it.wireValue == v }
        }
    }
}
