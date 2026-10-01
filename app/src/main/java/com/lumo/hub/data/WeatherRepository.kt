package com.lumo.hub.data

import android.content.Context
import com.lumo.hub.network.ForecastResponse
import com.lumo.hub.network.WeatherAlert
import com.lumo.hub.network.WeatherClient
import com.lumo.hub.network.WeatherLocation
import java.util.Locale

class WeatherRepository(context: Context, private val client: WeatherClient = WeatherClient()) {
    private val preferences = context.applicationContext.getSharedPreferences("weather_settings", Context.MODE_PRIVATE)

    fun savedLocation(): WeatherLocation? {
        val name = preferences.getString(KEY_NAME, null) ?: return null
        return WeatherLocation(name, preferences.getFloat(KEY_LAT, Float.NaN).toDouble(), preferences.getFloat(KEY_LON, Float.NaN).toDouble(), preferences.getString(KEY_COUNTRY, null), preferences.getString(KEY_REGION, null))
            .takeUnless { it.latitude.isNaN() || it.longitude.isNaN() }
    }

    fun saveLocation(location: WeatherLocation) { preferences.edit().putString(KEY_NAME, location.name).putFloat(KEY_LAT, location.latitude.toFloat()).putFloat(KEY_LON, location.longitude.toFloat()).putString(KEY_COUNTRY, location.country).putString(KEY_REGION, location.region).apply() }
    fun notificationsEnabled(): Boolean = preferences.getBoolean(KEY_NOTIFICATIONS, false)
    fun setNotificationsEnabled(enabled: Boolean) { preferences.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply() }

    suspend fun searchCity(query: String): List<WeatherLocation> = client.searchCity(query)
    suspend fun forecast(): ForecastResponse? = savedLocation()?.let { client.forecast(it) }

    fun alerts(forecast: ForecastResponse): List<WeatherAlert> {
        val hourly = forecast.hourly ?: return emptyList()
        val hours = minOf(hourly.time.size, hourly.temperatureCelsius.size, hourly.precipitationProbability.size, hourly.precipitationMm.size)
        val rain = (0 until hours).firstOrNull { hourly.precipitationProbability[it] >= 60 && hourly.precipitationMm[it] >= 0.5 }
        val frost = (0 until hours).firstOrNull { hourly.temperatureCelsius[it] <= 0.0 }
        return buildList {
            rain?.let { add(WeatherAlert(WeatherAlert.Type.RAIN, "Возможен дождь", "Вероятность осадков ${hourly.precipitationProbability[it]}% в ближайшие часы.")) }
            frost?.let { add(WeatherAlert(WeatherAlert.Type.FROST, "Возможны заморозки", "Температура опустится до ${format(hourly.temperatureCelsius[it])} °C.")) }
        }
    }

    private fun format(value: Double) = String.format(Locale.US, "%.1f", value)
    private companion object { const val KEY_NAME = "location_name"; const val KEY_LAT = "location_lat"; const val KEY_LON = "location_lon"; const val KEY_COUNTRY = "location_country"; const val KEY_REGION = "location_region"; const val KEY_NOTIFICATIONS = "notifications_enabled" }
}
