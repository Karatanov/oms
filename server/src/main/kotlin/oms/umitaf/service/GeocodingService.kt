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

/**
 * Resolves an address only when a user explicitly asks for it. The provider URL
 * may be switched through GEOCODER_URL without changing the application code.
 */
class GeocodingService {
    private val client = HttpClient.newBuilder().build()
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, GeocodeAddressResponse?>()
    private var lastRequestAt = 0L

    @Synchronized
    fun geocode(address: String, city: String, region: String): GeocodeAddressResponse? {
        val query = listOf(address, city, region, "Україна")
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(", ")
        require(query != "Україна") { "Address, city, or region is required for coordinate lookup." }
        cache[query]?.let { return it }

        val elapsed = System.currentTimeMillis() - lastRequestAt
        if (elapsed < MIN_REQUEST_INTERVAL_MS) Thread.sleep(MIN_REQUEST_INTERVAL_MS - elapsed)

        val baseUrl = System.getenv("GEOCODER_URL")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: "https://nominatim.openstreetmap.org/search"
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val separator = if ('?' in baseUrl) '&' else '?'
        val request = HttpRequest.newBuilder(
            URI.create("$baseUrl${separator}format=jsonv2&limit=1&countrycodes=ua&q=$encodedQuery")
        )
            .header("Accept", "application/json")
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
                if (latitude != null && longitude != null) GeocodeAddressResponse(latitude, longitude) else null
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
