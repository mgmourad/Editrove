// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.engine

/**
 * Shopping, deals and sponsored pieces: not news, and never read in a briefing. Judged by the
 * headline and, once fetched, by the text (affiliate disclosures, "buy now", coupon codes).
 */
object Promotional {
    private val TITLE = Regex(
        "(?i)\\b((black friday|cyber monday|prime day|holiday|early) (deals?|sales?|offers?|discounts?)|best (?:\\w+ ){0,3}deals?|deals? (on|for) (?:the )?\\w+|" +
            "coupon|promo code|\\d+% off|on sale (now|for)|sale ends|lowest price|price drop|save \\$\\d+|" +
            "best (?:\\w+ ){0,3}to buy|buying guide|gift guide|gifts? for (him|her|kids|dad|mom|everyone)|sponsored|partner content|paid post|advertorial|shop (now|the sale))\\b" +
            "|تخفيضات|كوبون خصم|برعاية|bons plans|code promo|rebajas|cupón|gutschein|anzeige:",
    )
    private val TEXT = listOf(
        "affiliate link", "affiliate commission", "we may earn a commission", "may earn a commission", "earn a small commission",
        "commission from links", "if you buy something", "when you buy through", "buy through links", "click on the link below",
        "click the link below", "use code", "promo code", "this post is sponsored", "sponsored by", "paid partnership", "shop now",
        "رابط الشراء", "عمولة", "lien affilié", "enlace de afiliado", "affiliate-link", "provision",
    )

    fun isPromotionalTitle(title: String): Boolean = TITLE.containsMatchIn(title)

    /** True when the text is a sales pitch: two or more commerce signals, or an affiliate disclosure. */
    fun isPromotionalText(text: String): Boolean {
        val l = text.lowercase()
        val hits = TEXT.count { it in l }
        return hits >= 2 || "affiliate" in l && ("commission" in l || "links" in l)
    }
}
