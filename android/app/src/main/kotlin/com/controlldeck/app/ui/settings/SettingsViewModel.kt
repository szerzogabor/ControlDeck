package com.controlldeck.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlldeck.app.identity.DeviceIdentityRepository
import com.controlldeck.app.persistence.AppRegistryRepository
import com.controlldeck.app.persistence.LocalAppRegistryEntry
import com.controlldeck.app.prefs.UserPreferencesRepository
import com.controlldeck.domain.AppId
import com.controlldeck.domain.ReconnectPolicy
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val deviceName: String = "",
    val defaultReconnectPolicy: ReconnectPolicy = ReconnectPolicy.SYNC_GROUP_STATE,
    val appRegistry: List<LocalAppRegistryEntry> = emptyList(),
    val autoAcceptPairing: Boolean = false,
)

class SettingsViewModel(
    private val deviceIdentityRepository: DeviceIdentityRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val appRegistryRepository: AppRegistryRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        deviceIdentityRepository.identity,
        userPreferencesRepository.preferences,
        appRegistryRepository.observeEntries(),
    ) { identity, prefs, apps ->
        SettingsUiState(identity.deviceName, prefs.defaultReconnectPolicy, apps, prefs.autoAcceptPairing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setDeviceName(name: String) {
        viewModelScope.launch { deviceIdentityRepository.setDeviceName(name) }
    }

    fun setDefaultReconnectPolicy(policy: ReconnectPolicy) {
        viewModelScope.launch { userPreferencesRepository.setDefaultReconnectPolicy(policy) }
    }

    fun setAutoAcceptPairing(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setAutoAcceptPairing(enabled) }
    }

    fun addOrUpdateApp(appId: String, displayName: String, packageName: String) {
        if (appId.isBlank() || packageName.isBlank()) return
        viewModelScope.launch { appRegistryRepository.upsert(LocalAppRegistryEntry(AppId(appId.trim()), displayName.ifBlank { appId }, packageName.trim())) }
    }

    fun removeApp(appId: AppId) {
        viewModelScope.launch { appRegistryRepository.remove(appId) }
    }
}
