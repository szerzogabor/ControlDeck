package com.controlldeck.app.sync

import com.controlldeck.app.action.ActionEngine
import com.controlldeck.app.group.GroupManager
import com.controlldeck.app.logging.Logger
import com.controlldeck.app.mapper.ProtocolMappers
import com.controlldeck.app.persistence.DashboardRepository
import com.controlldeck.app.state.DeviceStateManager
import com.controlldeck.app.transport.ConnectionManager
import com.controlldeck.app.transport.TransportEvent
import com.controlldeck.domain.DeviceCapabilities

private const val TAG = "PeerConnCoordinator"

/**
 * Bridges transport-level connection lifecycle events into the app's
 * State Manager and Group Manager: marks devices online/offline, records
 * advertised capabilities, forwards STATE_UPDATE into the Action Engine,
 * and runs reconnect-policy corrections (docs/ARCHITECTURE.md §5) whenever
 * a device that is a member of one or more groups comes back online.
 */
class PeerConnectionCoordinator(
    private val deviceStateManager: DeviceStateManager,
    private val connectionManager: ConnectionManager,
    private val groupManager: GroupManager,
    private val dashboardRepository: DashboardRepository,
    private val actionEngine: ActionEngine,
    private val logger: Logger,
) {
    suspend fun observe() {
        connectionManager.events.collect { event ->
            when (event) {
                is TransportEvent.PeerOnline -> {
                    deviceStateManager.markOnline(event.deviceId)
                    runReconnectCorrections(event.deviceId)
                }
                is TransportEvent.PeerOffline -> deviceStateManager.markOffline(event.deviceId)
                is TransportEvent.CapabilitiesReceived -> deviceStateManager.updateCapabilities(
                    DeviceCapabilities(
                        deviceId = event.deviceId,
                        capabilities = ProtocolMappers.capabilitiesFromWire(event.payload.capabilities),
                        apps = event.payload.apps.map { com.controlldeck.domain.AppRegistryEntry(com.controlldeck.domain.AppId(it.appId), it.displayName) },
                    ),
                )
                is TransportEvent.StateUpdateReceived -> actionEngine.handleIncomingStateUpdate(event)
                is TransportEvent.Error -> logger.w(TAG, "peer ${event.fromDeviceId?.value} reported error ${event.code}: ${event.message}")
                else -> Unit
            }
        }
    }

    private suspend fun runReconnectCorrections(reconnectedDeviceId: com.controlldeck.domain.DeviceId) {
        val dashboards = dashboardRepository.observeDashboardsSnapshot()
        val deviceStates = deviceStateManager.deviceStates.value.values.toList()

        dashboards.forEach { dashboard ->
            val dispatches = groupManager.onDeviceReconnected(dashboard.groups, dashboard.widgets, deviceStates, reconnectedDeviceId)
            if (dispatches.isNotEmpty()) {
                logger.i(TAG, "reconnect policy sending ${dispatches.size} corrective action(s) to ${reconnectedDeviceId.value}")
                actionEngine.dispatchGroup(dispatches)
            }
        }
    }
}
