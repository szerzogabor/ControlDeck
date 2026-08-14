package com.controlldeck.app.state

import com.controlldeck.domain.ActionSpec
import com.controlldeck.domain.ConnectionState
import com.controlldeck.domain.DeviceCapabilities
import com.controlldeck.domain.DeviceId
import com.controlldeck.domain.DeviceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * The in-memory "State Manager" from docs/ARCHITECTURE.md §2: this
 * device's last-known view of every peer's connection + brightness/
 * volume/muted/media state, plus their advertised capabilities. Pure
 * in-memory bookkeeping — no I/O, safe to unit test, driven entirely by
 * events the transport/action layers push into it.
 */
class DeviceStateManager {

    private val _deviceStates = MutableStateFlow<Map<DeviceId, DeviceState>>(emptyMap())
    val deviceStates: StateFlow<Map<DeviceId, DeviceState>> = _deviceStates

    private val _capabilities = MutableStateFlow<Map<DeviceId, DeviceCapabilities>>(emptyMap())
    val capabilities: StateFlow<Map<DeviceId, DeviceCapabilities>> = _capabilities

    fun markOnline(deviceId: DeviceId) {
        _deviceStates.update { it + (deviceId to (it[deviceId]?.copy(connection = ConnectionState.ONLINE) ?: DeviceState(deviceId, ConnectionState.ONLINE))) }
    }

    fun markOffline(deviceId: DeviceId) {
        _deviceStates.update { it + (deviceId to (it[deviceId]?.copy(connection = ConnectionState.OFFLINE) ?: DeviceState(deviceId, ConnectionState.OFFLINE))) }
    }

    fun updateCapabilities(caps: DeviceCapabilities) {
        _capabilities.update { it + (caps.deviceId to caps) }
    }

    fun applyStateFragment(deviceId: DeviceId, action: ActionSpec) {
        _deviceStates.update { current ->
            val existing = current[deviceId] ?: DeviceState(deviceId, ConnectionState.ONLINE)
            val updated = when (action) {
                is ActionSpec.BrightnessSet -> existing.copy(brightness = action.value)
                is ActionSpec.VolumeSet -> existing.copy(volume = action.value)
                is ActionSpec.SetMuted -> existing.copy(muted = action.muted)
                is ActionSpec.MediaSetState -> existing.copy(mediaState = action.state)
                ActionSpec.MediaNext, ActionSpec.MediaPrevious -> existing
                is ActionSpec.AppLaunch -> existing
            }
            current + (deviceId to updated)
        }
    }

    fun snapshot(deviceId: DeviceId): DeviceState? = _deviceStates.value[deviceId]

    fun isOnline(deviceId: DeviceId): Boolean = _deviceStates.value[deviceId]?.connection == ConnectionState.ONLINE
}
