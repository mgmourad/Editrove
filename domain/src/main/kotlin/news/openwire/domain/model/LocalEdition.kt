// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.model

/**
 * The user's local news edition (city + region), e.g. Washington, D.C. / US.
 */
data class LocalEdition(
    val city: String,
    val region: String,
)
