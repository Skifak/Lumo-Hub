package com.lumo.hub.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeocodingResponse(val results: List<GeocodingResult> = emptyList())

@Serializable
data class GeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    @SerialName("admin1") val region: String? = null,
)

@Serializable
data class ForecastResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val current: CurrentWeather? = null,
    val hourly: HourlyWeather? = null,
)

@Serializable
data class CurrentWeather(
    val time: String,
    @SerialName("temperature_2m") val temperatureCelsius: Double,
    @SerialName("apparent_temperature") val apparentTemperatureCelsius: Double? = null,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("wind_speed_10m") val windSpeedKmh: Double? = null,
)

@Serializable
data class HourlyWeather(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m") val temperatureCelsius: List<Double> = emptyList(),
    @SerialName("precipitation_probability") val precipitationProbability: List<Int> = emptyList(),
    @SerialName("precipitation") val precipitationMm: List<Double> = emptyList(),
    @SerialName("weather_code") val weatherCodes: List<Int> = emptyList(),
)

data class WeatherLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val region: String? = null,
)

data class WeatherAlert(val type: Type, val title: String, val body: String) {
    enum class Type { RAIN, FROST }
}
