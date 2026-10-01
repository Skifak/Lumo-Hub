package com.lumo.hub.network

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WeatherClient(private val httpClient: OkHttpClient = OkHttpClient()) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun searchCity(query: String, language: String = "en"): List<WeatherLocation> = withContext(Dispatchers.IO) {
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", query).addQueryParameter("count", "5")
            .addQueryParameter("language", language).addQueryParameter("format", "json").build()
        execute(url.toString(), GeocodingResponse.serializer()).results.map {
            WeatherLocation(it.name, it.latitude, it.longitude, it.country, it.region)
        }
    }

    suspend fun forecast(location: WeatherLocation): ForecastResponse = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", location.latitude.toString())
            .addQueryParameter("longitude", location.longitude.toString())
            .addQueryParameter("current", "temperature_2m,apparent_temperature,weather_code,wind_speed_10m")
            .addQueryParameter("hourly", "temperature_2m,precipitation_probability,precipitation,weather_code")
            .addQueryParameter("forecast_days", "2").addQueryParameter("timezone", "auto").build()
        execute(url.toString(), ForecastResponse.serializer())
    }

    private fun <T> execute(url: String, serializer: kotlinx.serialization.KSerializer<T>): T {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Open-Meteo вернул код ${response.code}")
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) error("Open-Meteo вернул пустой ответ")
            json.decodeFromString(serializer, body)
        }
    }
}
