package com.controlldeck.app.transport

import com.controlldeck.domain.DeviceId
import com.controlldeck.protocol.ActionDto
import com.controlldeck.protocol.CapabilitiesPayload
import com.controlldeck.protocol.DashboardSyncPayload
import com.controlldeck.protocol.DeviceInfoPayload
import com.controlldeck.protocol.PairRequestPayload

/** The role this device plays in a given connection — every device can be both simultaneously across different peers. */
enum class ConnectionRole { INITIATOR, ACCEPTOR }

enum class SessionState {
    CONNECTING,
    HELLO_EXCHANGED,
    AWAITING_PAIRING,
    AUTHENTICATING,
    READY,
    CLOSED,
}

/** Events bubbled up from the transport layer to the rest of the app. */
sealed class TransportEvent {
    data class PeerOnline(val deviceId: DeviceId, val deviceInfo: DeviceInfoPayload) : TransportEvent()
    data class PeerOffline(val deviceId: DeviceId) : TransportEvent()
    data class CapabilitiesReceived(val deviceId: DeviceId, val payload: CapabilitiesPayload) : TransportEvent()
    data class ActionReceived(val fromDeviceId: DeviceId, val messageId: String, val action: ActionDto, val replyTo: suspend (Boolean, String?, ActionDto?) -> Unit) : TransportEvent()
    data class ActionResultReceived(val fromDeviceId: DeviceId, val correlatesTo: String, val success: Boolean, val errorCode: String?, val resultingState: ActionDto?) : TransportEvent()
    data class StateUpdateReceived(val fromDeviceId: DeviceId, val state: ActionDto) : TransportEvent()
    data class DashboardSyncReceived(val fromDeviceId: DeviceId, val timestamp: Long, val payload: DashboardSyncPayload, val reply: suspend (DashboardSyncPayload) -> Unit) : TransportEvent()
    data class IncomingPairRequest(val fromDeviceId: DeviceId, val payload: PairRequestPayload, val respond: suspend (accepted: Boolean, sharedSecretIfAccepted: String?) -> Unit) : TransportEvent()
    data class Error(val fromDeviceId: DeviceId?, val code: String, val message: String) : TransportEvent()
}
