package com.controlldeck.app.action

import com.controlldeck.app.capability.LocalActionOutcome
import com.controlldeck.app.capability.LocalCapabilityController
import com.controlldeck.app.logging.Logger
import com.controlldeck.app.mapper.ProtocolMappers
import com.controlldeck.app.state.DeviceStateManager
import com.controlldeck.app.transport.ConnectionManager
import com.controlldeck.app.transport.TransportEvent
import com.controlldeck.domain.ActionSpec
import com.controlldeck.domain.DeviceId
import com.controlldeck.domain.GroupDispatch
import com.controlldeck.protocol.ActionErrorCode
import kotlinx.coroutines.flow.filterIsInstance

private const val TAG = "ActionEngine"

/**
 * The Action Engine from docs/ARCHITECTURE.md §2: dispatches incoming
 * ACTION messages to the right platform effect, and sends
 * locally-originated actions (single widget or [GroupDispatch] list) out
 * to their targets — routing to [LocalCapabilityController] directly when
 * the target is this device, or over the transport otherwise.
 */
class ActionEngine(
    private val selfDeviceId: DeviceId,
    private val localCapabilityController: LocalCapabilityController,
    private val connectionManager: ConnectionManager,
    private val deviceStateManager: DeviceStateManager,
    private val logger: Logger,
) {

    /** Call once at startup to route incoming ACTION/STATE_UPDATE events into this engine. */
    suspend fun observeTransportEvents() {
        connectionManager.events.filterIsInstance<TransportEvent.ActionReceived>().collect { event ->
            handleIncomingAction(event)
        }
    }

    suspend fun handleIncomingAction(event: TransportEvent.ActionReceived) {
        val actionSpec = ProtocolMappers.actionFromWire(event.action)
        if (actionSpec == null) {
            event.replyTo(false, ActionErrorCode.UNSUPPORTED_CAPABILITY, null)
            return
        }
        when (val outcome = localCapabilityController.execute(actionSpec)) {
            is LocalActionOutcome.Success -> {
                deviceStateManager.applyStateFragment(selfDeviceId, outcome.resultingState)
                event.replyTo(true, null, ProtocolMappers.actionToWire(outcome.resultingState))
                connectionManager.broadcastStateUpdate(ProtocolMappers.actionToWire(outcome.resultingState))
            }
            is LocalActionOutcome.Failure -> {
                logger.w(TAG, "action execution failed: ${outcome.errorCode}")
                event.replyTo(false, outcome.errorCode, null)
            }
        }
    }

    fun handleIncomingStateUpdate(event: TransportEvent.StateUpdateReceived) {
        val actionSpec = ProtocolMappers.actionFromWire(event.state) ?: return
        deviceStateManager.applyStateFragment(event.fromDeviceId, actionSpec)
    }

    /** Sends a single (non-grouped) widget action to its target device, local or remote. */
    suspend fun dispatchSingle(targetDeviceId: DeviceId, action: ActionSpec) {
        if (targetDeviceId == selfDeviceId) {
            executeLocallyAndBroadcast(action)
        } else {
            connectionManager.sendAction(targetDeviceId, ProtocolMappers.actionToWire(action))
        }
    }

    /** Sends every dispatch computed by [com.controlldeck.app.group.GroupManager]. */
    suspend fun dispatchGroup(dispatches: List<GroupDispatch>) {
        dispatches.forEach { dispatch ->
            dispatchSingle(dispatch.targetDeviceId, dispatch.action)
        }
    }

    private suspend fun executeLocallyAndBroadcast(action: ActionSpec) {
        when (val outcome = localCapabilityController.execute(action)) {
            is LocalActionOutcome.Success -> {
                deviceStateManager.applyStateFragment(selfDeviceId, outcome.resultingState)
                connectionManager.broadcastStateUpdate(ProtocolMappers.actionToWire(outcome.resultingState))
            }
            is LocalActionOutcome.Failure -> logger.w(TAG, "local action execution failed: ${outcome.errorCode}")
        }
    }
}
