package com.controlldeck.app.sync

import com.controlldeck.app.logging.Logger
import com.controlldeck.app.mapper.ProtocolMappers
import com.controlldeck.app.persistence.DashboardRepository
import com.controlldeck.app.transport.ConnectionManager
import com.controlldeck.app.transport.TransportEvent
import com.controlldeck.domain.DashboardSyncMessage
import com.controlldeck.domain.DashboardSyncOutcome
import com.controlldeck.domain.DeviceId
import com.controlldeck.protocol.DashboardSyncPayload
import kotlinx.coroutines.flow.filterIsInstance

private const val TAG = "DashboardSync"

/**
 * Wires incoming DASHBOARD_SYNC transport events to the pure last-write-
 * wins resolver in [DashboardRepository]/:domain (docs/ARCHITECTURE.md §6),
 * and reacts to the resulting [DashboardSyncOutcome].
 */
class DashboardSyncCoordinator(
    private val dashboardRepository: DashboardRepository,
    private val connectionManager: ConnectionManager,
    private val logger: Logger,
) {
    suspend fun observe() {
        connectionManager.events.filterIsInstance<TransportEvent.DashboardSyncReceived>().collect { event ->
            handle(event)
        }
    }

    private suspend fun handle(event: TransportEvent.DashboardSyncReceived) {
        val incomingDashboard = ProtocolMappers.dashboardFromWire(event.payload.dashboard)
        val message = DashboardSyncMessage(incomingDashboard, event.timestamp, event.fromDeviceId)

        when (val outcome = dashboardRepository.resolveIncomingSync(message)) {
            is DashboardSyncOutcome.Applied -> {
                logger.i(TAG, "applied DASHBOARD_SYNC v${outcome.dashboard.version} from ${event.fromDeviceId.value}")
                connectionManager.sendDashboardAck(event.fromDeviceId, outcome.dashboard.id.value, outcome.dashboard.version)
            }
            is DashboardSyncOutcome.ReplyWithLocal -> {
                logger.i(TAG, "local dashboard newer, replying to ${event.fromDeviceId.value}")
                event.reply(DashboardSyncPayload(ProtocolMappers.dashboardToWire(outcome.local)))
            }
            DashboardSyncOutcome.NoChange -> Unit
        }
    }

    /** Broadcasts a local edit to every connected peer, per docs/ARCHITECTURE.md §6 "broadcasts DASHBOARD_SYNC to every currently-connected paired peer". */
    suspend fun broadcastLocalEdit(dashboard: com.controlldeck.domain.Dashboard) {
        connectionManager.broadcastDashboardSync(DashboardSyncPayload(ProtocolMappers.dashboardToWire(dashboard)))
    }
}
