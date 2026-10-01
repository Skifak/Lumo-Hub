package com.lumo.hub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.lumo.hub.data.SettingsRepository
import com.lumo.hub.data.ThemeMode
import com.lumo.hub.theme.LumoTheme
import com.lumo.hub.ui.navigation.LumoNavHost
import com.lumo.hub.ui.navigation.rememberThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LumoRoot()
        }
    }
}

@Composable
private fun LumoRoot() {
    val context = LocalContext.current
    val settingsRepository = remember(context) { SettingsRepository(context) }
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
            LumoNavHost(settingsRepository = settingsRepository)
        }
    }
}
