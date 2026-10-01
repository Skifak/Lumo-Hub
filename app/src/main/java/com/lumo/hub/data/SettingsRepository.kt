package com.lumo.hub.data

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lumo.hub.security.ApiKeyStore
import com.lumo.hub.network.ModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

/** Режим темы: системная / светлая / тёмная (DESIGN.md §4 Settings). */
enum class ThemeMode(val label: String) {
    SYSTEM("Системная"),
    LIGHT("Светлая"),
    DARK("Тёмная"),
}

data class ProviderProfile(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
)

sealed interface ModelsState {
    data object Idle : ModelsState
    data object Loading : ModelsState
    data class Ready(val models: List<ModelInfo>) : ModelsState
    data class Error(val message: String) : ModelsState
}

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val provider: ProviderProfile = ProviderProfile(),
    val apiKeyVisible: Boolean = false,
    val modelsState: ModelsState = ModelsState.Idle,
)

class SettingsRepository(context: Context? = null) : ViewModel() {

    private val appContext = context?.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        // Android Keystore access (including API-key decryption) can block, so do
        // not perform it while Compose is creating the activity content.
        appContext?.let { context ->
            scope.launch {
                val preferences = context.getSharedPreferences("provider_settings", Context.MODE_PRIVATE)
                val provider = ProviderProfile(
                    baseUrl = preferences.getString("base_url", null) ?: "https://api.openai.com/v1",
                    apiKey = ApiKeyStore(context).get().orEmpty(),
                    model = preferences.getString("model", null) ?: "gpt-4o-mini",
                )
                _state.value = _state.value.copy(provider = provider, themeMode = readTheme(preferences))
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _state.value = _state.value.copy(themeMode = mode)
        persist { it.edit().putString("theme_mode", mode.name).apply() }
    }

    fun updateProvider(transform: (ProviderProfile) -> ProviderProfile) {
        val provider = transform(_state.value.provider)
        _state.value = _state.value.copy(provider = provider)
    }

    fun setModelsLoading() { _state.value = _state.value.copy(modelsState = ModelsState.Loading) }

    fun setModelsResult(result: Result<List<ModelInfo>>) {
        _state.value = _state.value.copy(
            modelsState = result.fold(
                { ModelsState.Ready(it.sortedBy(ModelInfo::id)) },
                { ModelsState.Error(it.message ?: "Не удалось получить список моделей") },
            ),
        )
    }

    fun saveProvider() {
        val provider = _state.value.provider
        val context = appContext ?: return
        scope.launch {
            context.getSharedPreferences("provider_settings", Context.MODE_PRIVATE).edit()
                .putString("base_url", provider.baseUrl)
                .putString("model", provider.model)
                .apply()
            ApiKeyStore(context).put(provider.apiKey)
        }
    }

    fun toggleApiKeyVisibility() {
        _state.value = _state.value.copy(apiKeyVisible = !_state.value.apiKeyVisible)
    }

    private fun persist(action: (android.content.SharedPreferences) -> Unit) {
        val context = appContext ?: return
        scope.launch {
            action(context.getSharedPreferences("provider_settings", Context.MODE_PRIVATE))
        }
    }

    private fun readTheme(preferences: android.content.SharedPreferences): ThemeMode =
        runCatching { ThemeMode.valueOf(preferences.getString("theme_mode", null).orEmpty()) }
            .getOrDefault(ThemeMode.SYSTEM)

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsRepository(context.applicationContext) as T
    }
}
