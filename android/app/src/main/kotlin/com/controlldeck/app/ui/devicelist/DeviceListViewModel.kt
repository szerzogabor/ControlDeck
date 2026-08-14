package com.controlldeck.app.ui.devicelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlldeck.app.discovery.DiscoveredDevice
import com.controlldeck.app.discovery.NsdDiscoveryService
import com.controlldeck.app.persistence.PairedDevice
import com.controlldeck.app.persistence.PairedDeviceRepository
import com.controlldeck.app.state.DeviceStateManager
import com.controlldeck.domain.ConnectionState
import com.controlldeck.domain.DeviceId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PairedDeviceUi(val device: PairedDevice, val online: Boolean)

data class DeviceListUiState(
    val pairedDevices: List<PairedDeviceUi> = emptyList(),
    val discoveredUnpaired: List<DiscoveredDevice> = emptyList(),
)

class DeviceListViewModel(
    private val pairedDeviceRepository: PairedDeviceRepository,
    private val nsdDiscoveryService: NsdDiscoveryService,
    private val deviceStateManager: DeviceStateManager,
) : ViewModel() {

    val uiState: StateFlow<DeviceListUiState> = combine(
        pairedDeviceRepository.observePairedDevices(),
        nsdDiscoveryService.discoveredDevices,
        deviceStateManager.deviceStates,
    ) { paired, discovered, states ->
        val pairedIds = paired.map { it.deviceId }.toSet()
        DeviceListUiState(
            pairedDevices = paired.map { PairedDeviceUi(it, states[it.deviceId]?.connection == ConnectionState.ONLINE) },
            discoveredUnpaired = discovered.filter { it.deviceId !in pairedIds },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeviceListUiState())

    fun forgetDevice(deviceId: DeviceId) {
        viewModelScope.launch { pairedDeviceRepository.forget(deviceId) }
    }
}
