package com.jarvis.assistant.search

import com.jarvis.assistant.data.Net
import com.jarvis.assistant.data.SecureStore
import kotlinx.serialization.Serializable
import retrofit2.HttpException
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url
import java.io.IOException

@Serializable data class SearchItem(val title: String = "", val link: String = "", val snippet: String = "")
@Serializable data class SearchResponse(val items: List<SearchItem> = emptyList())
@Serializable data class GeoResult(val name: String = "", val latitude: Double = 0.0, val longitude: Double = 0.0, val country: String = "")
@Serializable data class GeoResponse(val results: List<GeoResult> = emptyList())
@Serializable data class CurrentW(
    val temperature_2m: Double = 0.0, val apparent_temperature: Double = 0.0,
    val weather_code: Int = 0, val wind_speed_10m: Double = 0.0
)
@Serializable data class WeatherResponse(val current: CurrentW? = null)

interface WebApi {
    @GET suspend fun google(
        @Url url: String, @Query("key") key: String, @Query("cx") cx: String,
        @Query("q") q: String, @Query("num") num: Int = 5
    ): SearchResponse

    @GET suspend fun geocode(@Url url: String, @Query("name") name: String, @Query("count") count: Int = 1): GeoResponse

    @GET suspend fun forecast(
        @Url url: String, @Query("latitude") lat: Double, @Query("longitude") lon: Double,
        @Query("current") current: String = "temperature_2m,apparent_temperature,weather_code,wind_speed_10m"
    ): WeatherResponse
}

class SearchError(msg: String) : Exception(msg)

class SearchRepository(private val secure: SecureStore) {
    private val api = Net.retrofit.create(WebApi::class.java)

    /** Google Programmable Search JSON API. Needs your own API key + search engine id (cx). */
    suspend fun search(q: String, cx: String): List<SearchItem> {
        val key = secure.get(SecureStore.SEARCH)
        if (key.isNullOrBlank() || cx.isBlank())
            throw SearchError("Web search configure nahi hai. Settings -> Web search mein Google Programmable Search key + engine ID (cx) daalo.")
        try {
            return api.google("https://www.googleapis.com/customsearch/v1", key, cx, q).items
        } catch (e: HttpException) {
            throw SearchError("Search API error ${e.code()} (key, cx ya daily quota check karo).")
        } catch (e: IOException) {
            throw SearchError("Network problem: ${e.message}")
        }
    }

    /** Open-Meteo needs no API key. */
    suspend fun weather(city: String): String {
        try {
            val g = api.geocode("https://geocoding-api.open-meteo.com/v1/search", city).results.firstOrNull()
                ?: return "City '$city' nahi mili."
            val w = api.forecast("https://api.open-meteo.com/v1/forecast", g.latitude, g.longitude).current
                ?: return "Weather data available nahi hai."
            return "Weather in ${g.name}, ${g.country}: ${w.temperature_2m}°C (feels like ${w.apparent_temperature}°C), " +
                "${describe(w.weather_code)}, wind ${w.wind_speed_10m} km/h.\n(Source: Open-Meteo)"
        } catch (e: HttpException) {
            throw SearchError("Weather API error ${e.code()}")
        } catch (e: IOException) {
            throw SearchError("Network problem: ${e.message}")
        }
    }

    private fun describe(c: Int) = when (c) {
        0 -> "clear sky"; 1, 2, 3 -> "partly cloudy"; 45, 48 -> "fog"
        in 51..57 -> "drizzle"; in 61..67 -> "rain"; in 71..77 -> "snow"
        in 80..82 -> "rain showers"; in 95..99 -> "thunderstorm"; else -> "code $c"
    }
}
