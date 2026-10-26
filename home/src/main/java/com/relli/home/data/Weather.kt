package com.relli.home.data

import com.relli.home.HomeApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * Weather from Open-Meteo: free, no account or key, and it only receives your city's rough
 * coordinates (never anything about you).
 */
object Weather {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable data class Place(val name: String, val latitude: Double, val longitude: Double, val admin1: String? = null, @SerialName("country_code") val country: String? = null)
    @Serializable private data class Places(val results: List<Place> = emptyList())
    @Serializable private data class Current(@SerialName("temperature_2m") val temp: Double, @SerialName("weather_code") val code: Int, @SerialName("is_day") val isDay: Int = 1)
    @Serializable private data class Daily(@SerialName("temperature_2m_max") val max: List<Double> = emptyList(), @SerialName("temperature_2m_min") val min: List<Double> = emptyList())
    @Serializable private data class Forecast(val current: Current, val daily: Daily? = null)

    data class Now(val temp: Int, val high: Int?, val low: Int?, val label: String, val emoji: String)

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        HomeApp.instance.http.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string().orEmpty() }
    }

    suspend fun find(city: String): Place? {
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", city).addQueryParameter("count", "1").addQueryParameter("language", "en").build().toString()
        return json.decodeFromString<Places>(get(url)).results.firstOrNull()
    }

    suspend fun now(lat: Double, lon: Double, fahrenheit: Boolean): Now {
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", "%.3f".format(lat)).addQueryParameter("longitude", "%.3f".format(lon))
            .addQueryParameter("current", "temperature_2m,weather_code,is_day")
            .addQueryParameter("daily", "temperature_2m_max,temperature_2m_min").addQueryParameter("forecast_days", "1")
            .addQueryParameter("temperature_unit", if (fahrenheit) "fahrenheit" else "celsius")
            .addQueryParameter("timezone", "auto").build().toString()
        val f = json.decodeFromString<Forecast>(get(url))
        val (label, emoji) = describe(f.current.code, f.current.isDay == 1)
        return Now(Math.round(f.current.temp).toInt(), f.daily?.max?.firstOrNull()?.let { Math.round(it).toInt() }, f.daily?.min?.firstOrNull()?.let { Math.round(it).toInt() }, label, emoji)
    }

    /** WMO weather codes → words. */
    private fun describe(code: Int, day: Boolean): Pair<String, String> = when (code) {
        0 -> "Clear" to if (day) "☀" else "☾"
        1, 2 -> "Partly cloudy" to if (day) "⛅" else "☁"
        3 -> "Cloudy" to "☁"
        45, 48 -> "Fog" to "🌫"
        51, 53, 55, 56, 57 -> "Drizzle" to "🌦"
        61, 63, 65, 66, 67, 80, 81, 82 -> "Rain" to "🌧"
        71, 73, 75, 77, 85, 86 -> "Snow" to "❄"
        95, 96, 99 -> "Storms" to "⛈"
        else -> "—" to "☁"
    }
}
