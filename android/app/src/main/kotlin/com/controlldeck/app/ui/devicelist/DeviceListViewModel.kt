package com.controlldeck.app.ui.devicelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlldeck.app.discovery.DiscoveredDevice
import com.controlldeck.app.discovery.NsdDiscoveryService
import com.controlldeck.app.persistence.PairedDevice
import com.controlldeck.app.persistence.PairedDeviceRepository
import com.controlldeck.app.state.DeviceStateManager
import com.controlldeck.app.transport.ConnectionManager
import com.controlldeck.domain.ConnectionState
import com.controlldeck.domain.DeviceId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class PairedDeviceUi(val device: PairedDevice, val online: Boolean)

data class DeviceListUiState(
    val pairedDevices: List<PairedDeviceUi> = emptyList(),
    val discoveredUnpaired: List<DiscoveredDevice> = emptyList(),
)

class DeviceListViewModel(
    private val pairedDeviceRepository: PairedDeviceRepository,
    private val nsdDiscoveryService: NsdDiscoveryService,
    private val deviceStateManager: DeviceStateManager,
    private val selfDeviceId: DeviceId,
    private val connectionManager: ConnectionManager,
) : ViewModel() {

    private val _quickConnectStatus = MutableStateFlow<String?>(null)
    val quickConnectStatus: StateFlow<String?> = _quickConnectStatus

    val uiState: StateFlow<DeviceListUiState> = combine(
        pairedDeviceRepository.observePairedDevices(),
        nsdDiscoveryService.discoveredDevices,
        deviceStateManager.deviceStates,
    ) { paired, discovered, states ->
        val pairedIds = paired.map { it.deviceId }.toSet()
        DeviceListUiState(
            pairedDevices = paired.map { PairedDeviceUi(it, states[it.deviceId]?.connection == ConnectionState.ONLINE) },
            // Android's NsdManager commonly reports a device's own advertised
            // service back through discovery callbacks, so this device's own
            // deviceId must be excluded explicitly, not just paired ones.
            discoveredUnpaired = discovered.filter { it.deviceId !in pairedIds && it.deviceId != selfDeviceId },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeviceListUiState())

    fun forgetDevice(deviceId: DeviceId) {
        viewModelScope.launch { pairedDeviceRepository.forget(deviceId) }
    }

    /**
     * One-click testing convenience (see PairingScreen's "Auto-accept
     * pairing"): fires a tokenless pairing request and waits for the
     * device to actually show up as paired, since [ConnectionManager]'s
     * connect calls are otherwise fire-and-forget with no result signal.
     */
    fun quickConnect(device: DiscoveredDevice) {
        val host = device.host.hostAddress
        if (host == null) {
            _quickConnectStatus.value = "Could not resolve ${device.deviceName}'s address."
            return
        }

        _quickConnectStatus.value = "Connecting to ${device.deviceName}…"
        connectionManager.connectForPairing(host, device.port, "")

        viewModelScope.launch {
            val paired = withTimeoutOrNull(8000) {
                pairedDeviceRepository.observePairedDevices().first { list -> list.any { it.deviceId == device.deviceId } }
            }
            _quickConnectStatus.value = if (paired != null) {
                null
            } else {
                "Quick connect to ${device.deviceName} failed — enable \"Auto-accept pairing\" in its Settings, or use Scan QR / Enter PIN instead."
            }
        }
    }
}
