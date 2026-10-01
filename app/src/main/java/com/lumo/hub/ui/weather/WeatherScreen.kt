package com.lumo.hub.ui.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumo.hub.data.WeatherRepository
import com.lumo.hub.network.ForecastResponse
import com.lumo.hub.network.WeatherLocation
import kotlinx.coroutines.launch
import java.util.Locale

private sealed interface WeatherState {
    data object Empty : WeatherState
    data object Loading : WeatherState
    data class Success(val forecast: ForecastResponse, val location: WeatherLocation) : WeatherState
    data class Error(val message: String) : WeatherState
}

@Composable
fun WeatherScreen(repository: WeatherRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var city by remember { mutableStateOf(repository.savedLocation()?.name.orEmpty()) }
    var state by remember { mutableStateOf<WeatherState>(WeatherState.Empty) }
    var rainEnabled by remember { mutableStateOf(repository.notificationsEnabled()) }
    var frostEnabled by remember { mutableStateOf(repository.notificationsEnabled()) }

    fun load(query: String) {
        scope.launch {
            state = WeatherState.Loading
            state = runCatching {
                val location = repository.searchCity(query.trim()).firstOrNull()
                    ?: error("Город не найден")
                repository.saveLocation(location)
                WeatherState.Success(repository.forecast() ?: error("Прогноз недоступен"), location)
            }.getOrElse { WeatherState.Error(it.message ?: "Не удалось загрузить прогноз") }
        }
    }

    LaunchedEffect(Unit) {
        repository.savedLocation()?.let { saved ->
            state = WeatherState.Loading
            state = runCatching { WeatherState.Success(repository.forecast() ?: error("Прогноз недоступен"), saved) }
                .getOrElse { WeatherState.Error(it.message ?: "Не удалось загрузить прогноз") }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Назад") }
            Text("Погода", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = city,
            onValueChange = { city = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Город") },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { load(city) }, enabled = city.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Показать прогноз")
        }
        Spacer(Modifier.height(20.dp))
        when (val current = state) {
            WeatherState.Empty -> WeatherEmpty()
            WeatherState.Loading -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Загружаем прогноз…", modifier = Modifier.padding(top = 12.dp))
            }
            is WeatherState.Error -> WeatherError(current.message) { load(city) }
            is WeatherState.Success -> WeatherContent(current, rainEnabled, frostEnabled,
                onRainChange = { rainEnabled = it; repository.setNotificationsEnabled(it || frostEnabled) },
                onFrostChange = { frostEnabled = it; repository.setNotificationsEnabled(it || rainEnabled) },
            )
        }
    }
}

@Composable private fun WeatherEmpty() {
    Text("Введите город, чтобы получить актуальные данные.", style = MaterialTheme.typography.bodyLarge)
    Text("Поиск выполняется через GeoNames, прогноз предоставляет Open-Meteo.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
}

@Composable private fun WeatherError(message: String, retry: () -> Unit) {
    Text("Не удалось получить прогноз", style = MaterialTheme.typography.titleMedium)
    Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
    Button(onClick = retry, modifier = Modifier.padding(top = 12.dp)) {
        Icon(Icons.Outlined.Refresh, contentDescription = null)
        Spacer(Modifier.padding(horizontal = 4.dp))
        Text("Повторить")
    }
}

@Composable
private fun WeatherContent(
    state: WeatherState.Success,
    rainEnabled: Boolean,
    frostEnabled: Boolean,
    onRainChange: (Boolean) -> Unit,
    onFrostChange: (Boolean) -> Unit,
) {
    val current = state.forecast.current
    val hourly = state.forecast.hourly
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(state.location.name, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(12.dp))
            Text("${current?.temperatureCelsius?.formatOne() ?: ""} °C", style = MaterialTheme.typography.displaySmall)
            current?.apparentTemperatureCelsius?.let { Text("Ощущается как ${it.formatOne()} °C") }
            current?.windSpeedKmh?.let { Text("Ветер ${it.formatOne()} км/ч") }
            Spacer(Modifier.height(16.dp))
            Text("Ближайшие часы", style = MaterialTheme.typography.titleMedium)
            val hours = hourly?.let { minOf(6, it.time.size, it.temperatureCelsius.size, it.precipitationProbability.size, it.precipitationMm.size) } ?: 0
            if (hours == 0) Text("Данные прогноза недоступны")
            else Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                repeat(hours) { index ->
                    Text("${hourly!!.time[index].substringAfter('T')}  ${hourly.temperatureCelsius[index].formatOne()} °C, осадки ${hourly.precipitationProbability[index]}%")
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text("Уведомления", style = MaterialTheme.typography.titleMedium)
    NotificationRow("Дождь", rainEnabled, onRainChange)
    NotificationRow("Заморозки", frostEnabled, onFrostChange)
    Spacer(Modifier.height(12.dp))
    Text("Прогноз: Open-Meteo. Поиск города: GeoNames.", style = MaterialTheme.typography.bodySmall)
}

@Composable private fun NotificationRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun Double.formatOne() = String.format(Locale.US, "%.1f", this)
