// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.domain.market

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One instrument on the market-watch bar. */
data class MarketQuote(
    val symbol: String,
    val label: String,
    val name: String,
    val price: Double,
    val previousClose: Double,
    val dayHigh: Double?,
    val dayLow: Double?,
    val currency: String?,
    /** Today's intraday closes (for the sparkline). */
    val series: List<Double>,
) {
    val change: Double get() = price - previousClose
    val changePct: Double get() = if (previousClose == 0.0) 0.0 else change / previousClose * 100
    val up: Boolean get() = change >= 0
}

/** The watch list and the parser for Yahoo Finance's public chart endpoint (no key needed). */
object Markets {
    data class Instrument(val symbol: String, val label: String)

    val WATCH = listOf(
        Instrument("^GSPC", "S&P 500"),
        Instrument("^DJI", "Dow"),
        Instrument("^IXIC", "Nasdaq"),
        Instrument("^RUT", "Russell 2000"),
        Instrument("^VIX", "VIX"),
        Instrument("^TNX", "10-yr yield"),
        Instrument("CL=F", "Crude oil"),
        Instrument("GC=F", "Gold"),
        Instrument("EURUSD=X", "EUR/USD"),
        Instrument("BTC-USD", "Bitcoin"),
    )

    const val YAHOO_MARKETS = "https://finance.yahoo.com/markets/"

    fun chartUrl(symbol: String) =
        "https://query1.finance.yahoo.com/v8/finance/chart/" + java.net.URLEncoder.encode(symbol, "UTF-8") + "?range=1d&interval=5m"

    fun quotePage(symbol: String) = "https://finance.yahoo.com/quote/" + java.net.URLEncoder.encode(symbol, "UTF-8") + "/"

    private val json = Json { ignoreUnknownKeys = true }

    /** Parses one chart response; null if it isn't a usable quote. */
    fun parse(body: String, instrument: Instrument): MarketQuote? = runCatching {
        val result = json.parseToJsonElement(body).jsonObject["chart"]!!.jsonObject["result"]!!.jsonArray[0].jsonObject
        val meta = result["meta"]!!.jsonObject
        fun d(o: JsonObject, k: String) = o[k]?.jsonPrimitive?.doubleOrNull
        val price = d(meta, "regularMarketPrice") ?: return null
        val prev = d(meta, "chartPreviousClose") ?: d(meta, "previousClose") ?: return null
        val closes = result["indicators"]?.jsonObject?.get("quote")?.jsonArray?.firstOrNull()?.jsonObject?.get("close")?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.doubleOrNull }.orEmpty()
        MarketQuote(
            symbol = instrument.symbol, label = instrument.label,
            name = meta["shortName"]?.jsonPrimitive?.contentOrNull ?: instrument.label,
            price = price, previousClose = prev,
            dayHigh = d(meta, "regularMarketDayHigh"), dayLow = d(meta, "regularMarketDayLow"),
            currency = meta["currency"]?.jsonPrimitive?.contentOrNull,
            series = closes,
        )
    }.getOrNull()

    /** "7,416.59", "4.21%", "1.0832" — digits suited to the instrument. */
    fun formatPrice(q: MarketQuote): String {
        val decimals = when { q.symbol.endsWith("=X") -> 4; q.price >= 1000 -> 2; else -> 2 }
        val s = String.format(java.util.Locale.US, "%,.${decimals}f", q.price)
        return if (q.symbol == "^TNX") "$s%" else s
    }

    fun formatChange(q: MarketQuote): String =
        String.format(java.util.Locale.US, "%s%.2f%%", if (q.up) "+" else "−", kotlin.math.abs(q.changePct))
}
