package com.lumo.hub.data

import android.content.Context
import com.lumo.hub.security.ApiKeyStore
import com.lumo.hub.network.ModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

class SettingsRepository(context: Context? = null) {

    private val preferences = context?.getSharedPreferences("provider_settings", Context.MODE_PRIVATE)
    private val keyStore = context?.let { ApiKeyStore(it) }

    private val _state =
        MutableStateFlow(
            SettingsUiState(
                provider =
                    ProviderProfile(
                        baseUrl = preferences?.getString("base_url", null) ?: "https://api.openai.com/v1",
                        apiKey = keyStore?.get().orEmpty(),
                        model = preferences?.getString("model", null) ?: "gpt-4o-mini",
                    ),
            ),
        )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _state.value = _state.value.copy(themeMode = mode)
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
        preferences?.edit()?.putString("base_url", provider.baseUrl)?.putString("model", provider.model)?.apply()
        keyStore?.put(provider.apiKey)
    }

    fun toggleApiKeyVisibility() {
        _state.value = _state.value.copy(apiKeyVisible = !_state.value.apiKeyVisible)
    }
}
