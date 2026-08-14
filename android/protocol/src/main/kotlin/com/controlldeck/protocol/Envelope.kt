package com.controlldeck.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** The single wire-format protocol version this codec speaks. protocol/PROTOCOL.md. */
const val PROTOCOL_VERSION: Int = 1

/**
 * The common envelope shared by every ControlDeck message
 * (protocol/PROTOCOL.md §2). [payload] is kept as a raw [JsonElement] here
 * so the envelope itself can always be parsed even when [type] is unknown
 * or [payload] doesn't match the expected shape for [type] — per-type
 * decoding happens afterwards, in [MessageDispatcher].
 */
@Serializable
data class Envelope(
    val protocolVersion: Int = PROTOCOL_VERSION,
    val type: String,
    val messageId: String,
    val sourceDeviceId: String,
    val targetDeviceId: String? = null,
    val timestamp: Long,
    val payload: JsonElement = JsonObject(emptyMap()),
)

/** All message `type` discriminator values from protocol/PROTOCOL.md §3. */
object MessageTypes {
    const val HELLO = "HELLO"
    const val PAIR_REQUEST = "PAIR_REQUEST"
    const val PAIR_RESPONSE = "PAIR_RESPONSE"
    const val AUTH = "AUTH"
    const val AUTH_RESULT = "AUTH_RESULT"
    const val DEVICE_INFO = "DEVICE_INFO"
    const val CAPABILITIES = "CAPABILITIES"
    const val ACTION = "ACTION"
    const val ACTION_RESULT = "ACTION_RESULT"
    const val STATE_UPDATE = "STATE_UPDATE"
    const val DASHBOARD_SYNC = "DASHBOARD_SYNC"
    const val DASHBOARD_ACK = "DASHBOARD_ACK"
    const val ERROR = "ERROR"
    const val PING = "PING"
    const val PONG = "PONG"
}
