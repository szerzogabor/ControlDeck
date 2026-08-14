package com.controlldeck.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Thrown when an [Envelope]'s `type` is recognized but its `payload` does
 * not decode into the expected shape (missing required field, wrong JSON
 * type, etc). Maps to protocol/PROTOCOL.md §3.8 `MALFORMED_PAYLOAD`.
 */
class MalformedPayloadException(val messageType: String, cause: Throwable) :
    Exception("Malformed payload for message type '$messageType': ${cause.message}", cause)

/**
 * A fully-typed, decoded ControlDeck message. Every branch carries the raw
 * [Envelope] (for messageId/sourceDeviceId/timestamp/targetDeviceId) plus
 * its strongly-typed payload. [Unknown] is the explicit, non-throwing
 * representation of an unrecognized `type`
 * (protocol/PROTOCOL.md §2: "Unknown type values MUST produce an ERROR
 * response ... rather than crashing the connection") — callers are
 * expected to reply with an [ErrorPayload] carrying
 * `code = UNSUPPORTED_MESSAGE_TYPE` when they see this case.
 */
sealed class ParsedMessage {
    abstract val envelope: Envelope

    data class Hello(override val envelope: Envelope, val payload: HelloPayload) : ParsedMessage()
    data class PairRequest(override val envelope: Envelope, val payload: PairRequestPayload) : ParsedMessage()
    data class PairResponse(override val envelope: Envelope, val payload: PairResponsePayload) : ParsedMessage()
    data class Auth(override val envelope: Envelope, val payload: AuthPayload) : ParsedMessage()
    data class AuthResult(override val envelope: Envelope, val payload: AuthResultPayload) : ParsedMessage()
    data class DeviceInfo(override val envelope: Envelope, val payload: DeviceInfoPayload) : ParsedMessage()
    data class Capabilities(override val envelope: Envelope, val payload: CapabilitiesPayload) : ParsedMessage()
    data class Action(override val envelope: Envelope, val payload: ActionPayload) : ParsedMessage()
    data class ActionResult(override val envelope: Envelope, val payload: ActionResultPayload) : ParsedMessage()
    data class StateUpdate(override val envelope: Envelope, val payload: StateUpdatePayload) : ParsedMessage()
    data class DashboardSync(override val envelope: Envelope, val payload: DashboardSyncPayload) : ParsedMessage()
    data class DashboardAck(override val envelope: Envelope, val payload: DashboardAckPayload) : ParsedMessage()
    data class Error(override val envelope: Envelope, val payload: ErrorPayload) : ParsedMessage()
    data class Ping(override val envelope: Envelope) : ParsedMessage()
    data class Pong(override val envelope: Envelope) : ParsedMessage()

    /** Unrecognized `type`. Decoding never throws for this case — see class doc. */
    data class Unknown(override val envelope: Envelope) : ParsedMessage()
}

/**
 * Decodes raw envelopes/JSON text into [ParsedMessage]s and encodes typed
 * payloads back into [Envelope]s, per protocol/PROTOCOL.md.
 */
object MessageDispatcher {

    fun parse(raw: String, json: Json = ProtocolJson.instance): ParsedMessage {
        val envelope = try {
            json.decodeFromString(Envelope.serializer(), raw)
        } catch (e: SerializationException) {
            throw MalformedPayloadException("<envelope>", e)
        }
        return parse(envelope, json)
    }

    fun parse(envelope: Envelope, json: Json = ProtocolJson.instance): ParsedMessage = try {
        when (envelope.type) {
            MessageTypes.HELLO -> ParsedMessage.Hello(envelope, json.decodeFromJsonElement(HelloPayload.serializer(), envelope.payload))
            MessageTypes.PAIR_REQUEST -> ParsedMessage.PairRequest(envelope, json.decodeFromJsonElement(PairRequestPayload.serializer(), envelope.payload))
            MessageTypes.PAIR_RESPONSE -> ParsedMessage.PairResponse(envelope, json.decodeFromJsonElement(PairResponsePayload.serializer(), envelope.payload))
            MessageTypes.AUTH -> ParsedMessage.Auth(envelope, json.decodeFromJsonElement(AuthPayload.serializer(), envelope.payload))
            MessageTypes.AUTH_RESULT -> ParsedMessage.AuthResult(envelope, json.decodeFromJsonElement(AuthResultPayload.serializer(), envelope.payload))
            MessageTypes.DEVICE_INFO -> ParsedMessage.DeviceInfo(envelope, json.decodeFromJsonElement(DeviceInfoPayload.serializer(), envelope.payload))
            MessageTypes.CAPABILITIES -> ParsedMessage.Capabilities(envelope, json.decodeFromJsonElement(CapabilitiesPayload.serializer(), envelope.payload))
            MessageTypes.ACTION -> ParsedMessage.Action(envelope, json.decodeFromJsonElement(ActionPayload.serializer(), envelope.payload))
            MessageTypes.ACTION_RESULT -> ParsedMessage.ActionResult(envelope, json.decodeFromJsonElement(ActionResultPayload.serializer(), envelope.payload))
            MessageTypes.STATE_UPDATE -> ParsedMessage.StateUpdate(envelope, json.decodeFromJsonElement(StateUpdatePayload.serializer(), envelope.payload))
            MessageTypes.DASHBOARD_SYNC -> ParsedMessage.DashboardSync(envelope, json.decodeFromJsonElement(DashboardSyncPayload.serializer(), envelope.payload))
            MessageTypes.DASHBOARD_ACK -> ParsedMessage.DashboardAck(envelope, json.decodeFromJsonElement(DashboardAckPayload.serializer(), envelope.payload))
            MessageTypes.ERROR -> ParsedMessage.Error(envelope, json.decodeFromJsonElement(ErrorPayload.serializer(), envelope.payload))
            MessageTypes.PING -> ParsedMessage.Ping(envelope)
            MessageTypes.PONG -> ParsedMessage.Pong(envelope)
            else -> ParsedMessage.Unknown(envelope)
        }
    } catch (e: SerializationException) {
        throw MalformedPayloadException(envelope.type, e)
    } catch (e: IllegalArgumentException) {
        // kotlinx.serialization throws IllegalArgumentException for some malformed
        // polymorphic/sealed-class payloads (e.g. missing class discriminator).
        throw MalformedPayloadException(envelope.type, e)
    }

    fun serialize(message: ParsedMessage, json: Json = ProtocolJson.instance): String {
        val envelope = toEnvelope(message, json)
        return json.encodeToString(Envelope.serializer(), envelope)
    }

    private fun toEnvelope(message: ParsedMessage, json: Json): Envelope = when (message) {
        is ParsedMessage.Hello -> withPayload(message.envelope, json.encodeToJsonElement(HelloPayload.serializer(), message.payload))
        is ParsedMessage.PairRequest -> withPayload(message.envelope, json.encodeToJsonElement(PairRequestPayload.serializer(), message.payload))
        is ParsedMessage.PairResponse -> withPayload(message.envelope, json.encodeToJsonElement(PairResponsePayload.serializer(), message.payload))
        is ParsedMessage.Auth -> withPayload(message.envelope, json.encodeToJsonElement(AuthPayload.serializer(), message.payload))
        is ParsedMessage.AuthResult -> withPayload(message.envelope, json.encodeToJsonElement(AuthResultPayload.serializer(), message.payload))
        is ParsedMessage.DeviceInfo -> withPayload(message.envelope, json.encodeToJsonElement(DeviceInfoPayload.serializer(), message.payload))
        is ParsedMessage.Capabilities -> withPayload(message.envelope, json.encodeToJsonElement(CapabilitiesPayload.serializer(), message.payload))
        is ParsedMessage.Action -> withPayload(message.envelope, json.encodeToJsonElement(ActionPayload.serializer(), message.payload))
        is ParsedMessage.ActionResult -> withPayload(message.envelope, json.encodeToJsonElement(ActionResultPayload.serializer(), message.payload))
        is ParsedMessage.StateUpdate -> withPayload(message.envelope, json.encodeToJsonElement(StateUpdatePayload.serializer(), message.payload))
        is ParsedMessage.DashboardSync -> withPayload(message.envelope, json.encodeToJsonElement(DashboardSyncPayload.serializer(), message.payload))
        is ParsedMessage.DashboardAck -> withPayload(message.envelope, json.encodeToJsonElement(DashboardAckPayload.serializer(), message.payload))
        is ParsedMessage.Error -> withPayload(message.envelope, json.encodeToJsonElement(ErrorPayload.serializer(), message.payload))
        is ParsedMessage.Ping -> withPayload(message.envelope, JsonObject(emptyMap()))
        is ParsedMessage.Pong -> withPayload(message.envelope, JsonObject(emptyMap()))
        is ParsedMessage.Unknown -> message.envelope
    }

    private fun withPayload(envelope: Envelope, payload: kotlinx.serialization.json.JsonElement): Envelope =
        envelope.copy(payload = payload)
}
