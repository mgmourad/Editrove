// SPDX-License-Identifier: AGPL-3.0-only
package news.openwire.i18n

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import news.openwire.domain.engine.TopicRegistry
import news.openwire.domain.model.LocalEdition

/**
 * Finds the reader's city from the phone's approximate (network) location, once, when they
 * ask. Nothing is stored or sent anywhere by Editrove; the phone's own geocoder names the
 * place, and without one the nearest major city is picked offline.
 */
object LocationDetector {

    sealed interface Result {
        data class Found(val edition: LocalEdition, val supportedCountry: Boolean) : Result
        data object NoFix : Result
    }

    @SuppressLint("MissingPermission") // callers ask for ACCESS_COARSE_LOCATION first
    suspend fun detect(context: Context): Result {
        val loc = locate(context) ?: return Result.NoFix
        val named = withContext(Dispatchers.IO) { geocode(context, loc) }
        val edition = named ?: nearest(loc.latitude, loc.longitude) ?: return Result.NoFix
        val supported = TopicRegistry.COUNTRIES.any { it.code == edition.country }
        return Result.Found(if (supported) edition else edition.copy(country = "US"), supported)
    }

    @SuppressLint("MissingPermission")
    private suspend fun locate(context: Context): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER); add(LocationManager.PASSIVE_PROVIDER)
        }.filter { runCatching { lm.isProviderEnabled(it) || it == LocationManager.PASSIVE_PROVIDER }.getOrDefault(false) }
        val recent = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.takeIf { System.currentTimeMillis() - it.time < 6 * 60 * 60_000L }
        if (recent != null) return recent
        val provider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER } ?: return null
        if (Build.VERSION.SDK_INT < 30) return null
        return withTimeoutOrNull(12_000) {
            suspendCancellableCoroutine { cont ->
                val cancel = android.os.CancellationSignal()
                cont.invokeOnCancellation { cancel.cancel() }
                runCatching {
                    lm.getCurrentLocation(provider, cancel, context.mainExecutor) { cont.resume(it) }
                }.onFailure { cont.resume(null) }
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun geocode(context: Context, loc: Location): LocalEdition? {
        if (!Geocoder.isPresent()) return null
        val g = Geocoder(context, Locale.getDefault())
        val address = runCatching {
            if (Build.VERSION.SDK_INT >= 33) withTimeoutOrNull(8_000) {
                suspendCancellableCoroutine { cont ->
                    g.getFromLocation(loc.latitude, loc.longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(list: MutableList<android.location.Address>) { cont.resume(list.firstOrNull()) }
                        override fun onError(message: String?) { cont.resume(null) }
                    })
                }
            } else g.getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
        }.getOrNull() ?: return null
        val city = address.locality ?: address.subAdminArea ?: address.adminArea ?: return null
        val country = address.countryCode?.uppercase() ?: return null
        return LocalEdition(city, address.adminArea ?: city, country)
    }

    /** Offline fallback: the closest major city within ~150 km. */
    private fun nearest(lat: Double, lon: Double): LocalEdition? {
        val out = FloatArray(1)
        return CITIES.minByOrNull { c -> Location.distanceBetween(lat, lon, c.lat, c.lon, out); out[0] }
            ?.takeIf { c -> Location.distanceBetween(lat, lon, c.lat, c.lon, out); out[0] < 150_000f }
            ?.edition
    }

    private class City(val edition: LocalEdition, val lat: Double, val lon: Double)
    private fun c(city: String, region: String, country: String, lat: Double, lon: Double) = City(LocalEdition(city, region, country), lat, lon)

    private val CITIES = listOf(
        c("Washington, D.C.", "District of Columbia", "US", 38.90, -77.04), c("New York", "New York", "US", 40.71, -74.01),
        c("Los Angeles", "California", "US", 34.05, -118.24), c("San Francisco", "California", "US", 37.77, -122.42),
        c("Seattle", "Washington", "US", 47.61, -122.33), c("Chicago", "Illinois", "US", 41.88, -87.63),
        c("London", "England", "GB", 51.51, -0.13), c("Toronto", "Ontario", "CA", 43.65, -79.38),
        c("Sydney", "New South Wales", "AU", -33.87, 151.21), c("Dublin", "Leinster", "IE", 53.35, -6.26),
        c("Cairo", "Cairo", "EG", 30.04, 31.24), c("Alexandria", "Alexandria", "EG", 31.20, 29.92),
        c("Riyadh", "Riyadh", "SA", 24.71, 46.68), c("Jeddah", "Makkah", "SA", 21.49, 39.19),
        c("Dubai", "Dubai", "AE", 25.20, 55.27), c("Abu Dhabi", "Abu Dhabi", "AE", 24.45, 54.38),
        c("Beirut", "Beirut", "LB", 33.89, 35.50), c("Casablanca", "Casablanca-Settat", "MA", 33.57, -7.59),
        c("Paris", "Île-de-France", "FR", 48.86, 2.35), c("Lyon", "Auvergne-Rhône-Alpes", "FR", 45.76, 4.84),
        c("Brussels", "Brussels", "BE", 50.85, 4.35), c("Berlin", "Berlin", "DE", 52.52, 13.40),
        c("Munich", "Bavaria", "DE", 48.14, 11.58), c("Vienna", "Vienna", "AT", 48.21, 16.37),
        c("Zurich", "Zurich", "CH", 47.38, 8.54), c("Madrid", "Madrid", "ES", 40.42, -3.70),
        c("Barcelona", "Catalonia", "ES", 41.39, 2.17), c("Mexico City", "CDMX", "MX", 19.43, -99.13),
        c("Buenos Aires", "Buenos Aires", "AR", -34.60, -58.38), c("Bogotá", "Bogotá", "CO", 4.71, -74.07),
        c("Santiago", "Santiago", "CL", -33.45, -70.67), c("Rome", "Lazio", "IT", 41.90, 12.50),
        c("Tokyo", "Tokyo", "JP", 35.68, 139.69), c("São Paulo", "São Paulo", "BR", -23.55, -46.63),
        c("Mumbai", "Maharashtra", "IN", 19.08, 72.88), c("Singapore", "Singapore", "SG", 1.35, 103.82),
    )
}
