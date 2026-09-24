package oms.umitaf.service

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import oms.umitaf.dto.GeocodeAddressResponse
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Resolves an address only when a user explicitly asks for it. The provider URL
 * may be switched through GEOCODER_URL without changing the application code.
 */
class GeocodingService(
    private val baseUrl: String = System.getenv("GEOCODER_URL")?.trim()?.takeIf(String::isNotEmpty)
        ?: "https://nominatim.openstreetmap.org/search"
) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, GeocodeAddressResponse?>()
    private var lastRequestAt = 0L

    @Synchronized
    fun geocode(address: String, city: String, region: String): GeocodeAddressResponse? {
        val query = composeGeocodingAddress(address, city, region)
        require(query != "Україна") { "Address, city, or region is required for coordinate lookup." }
        cache[query]?.let { return it }

        val elapsed = System.currentTimeMillis() - lastRequestAt
        if (elapsed < MIN_REQUEST_INTERVAL_MS) Thread.sleep(MIN_REQUEST_INTERVAL_MS - elapsed)

        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val separator = if ('?' in baseUrl) '&' else '?'
        val request = HttpRequest.newBuilder(
            URI.create("$baseUrl${separator}format=jsonv2&limit=1&countrycodes=ua&q=$encodedQuery")
        )
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(12))
            .header("User-Agent", "OMS-UMITAF/1.0 (manual address coordinate lookup)")
            .GET()
            .build()
        lastRequestAt = System.currentTimeMillis()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Geocoding service returned HTTP ${response.statusCode()}.")
        }
        val result = json.decodeFromString(ListSerializer(NominatimPlace.serializer()), response.body())
            .firstOrNull()
            ?.let { place ->
                val latitude = place.lat.toDoubleOrNull()
                val longitude = place.lon.toDoubleOrNull()
                if (latitude != null && longitude != null && latitude in -90.0..90.0 && longitude in -180.0..180.0)
                    GeocodeAddressResponse(latitude, longitude) else null
            }
        cache[query] = result
        return result
    }

    @Serializable
    private data class NominatimPlace(val lat: String, val lon: String)

    private companion object {
        const val MIN_REQUEST_INTERVAL_MS = 1_000L
    }
}

internal fun composeGeocodingAddress(address: String, city: String, region: String): String {
    fun clean(value: String) = value.replace('\u00a0', ' ').replace(Regex("\\s+"), " ").trim()
        .replace(Regex("^(м\\.|місто|с\\.|село|смт\\.?)\\s*", RegexOption.IGNORE_CASE), "")
    val parts = (address.split(',') + city + region).map(::clean).filter(String::isNotEmpty)
        .filterNot { it.equals("Україна", true) || it.equals("Ukraine", true) }
        .distinctBy { it.lowercase().replace(Regex("\\s+(область|обл\\.|region|oblast)$"), "") }
    return (parts + "Україна").joinToString(", ")
}
