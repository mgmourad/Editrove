// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.model

/**
 * The user's edition: a city/region inside a country. The country selects the
 * Google News locale (hl/gl/ceid) used for the national and local topics.
 */
data class LocalEdition(
    val city: String,
    val region: String,
    val country: String = "US",
) {
    val displayName: String get() = if (city.equals(region, ignoreCase = true)) city else "$city, $region"

    companion object {
        /** SPEC §6.4: the default edition is the US with Washington, D.C. as the local city. */
        val DEFAULT = LocalEdition(city = "Washington, D.C.", region = "District of Columbia", country = "US")
    }
}
