package com.lumo.hub

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.lumo.hub.data.SettingsRepository
import com.lumo.hub.data.ChatRepository
import com.lumo.hub.data.ThemeMode
import com.lumo.hub.theme.LumoTheme
import com.lumo.hub.ui.navigation.LumoNavHost
import com.lumo.hub.ui.navigation.rememberThemeMode
import com.lumo.hub.weather.WeatherCheckWorker
import com.lumo.hub.weather.WeatherNotifications

class MainActivity : ComponentActivity() {
    private val settingsRepository: SettingsRepository by viewModels {
        SettingsRepository.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WeatherNotifications.createChannel(this)
        WeatherCheckWorker.schedule(this)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        setContent {
            LumoRoot(settingsRepository)
        }
    }
}

@Composable
private fun LumoRoot(settingsRepository: SettingsRepository) {
    val context = LocalContext.current
    val chatRepository = remember(context) { ChatRepository.get(context) }
    val mode = rememberThemeMode(settingsRepository)
    val dark =
        when (mode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }
    LumoTheme(darkTheme = dark) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
             LumoNavHost(settingsRepository = settingsRepository, chatRepository = chatRepository)
        }
    }
}
