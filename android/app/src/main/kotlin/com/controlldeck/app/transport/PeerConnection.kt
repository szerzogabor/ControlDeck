package com.controlldeck.app.transport

import com.controlldeck.app.identity.DeviceIdentity
import com.controlldeck.app.logging.Logger
import com.controlldeck.domain.AppRegistryEntry
import com.controlldeck.domain.Capability
import com.controlldeck.domain.Dashboard
import com.controlldeck.domain.DeviceId
import com.controlldeck.protocol.ActionPayload
import com.controlldeck.protocol.ActionResultPayload
import com.controlldeck.protocol.AuthPayload
import com.controlldeck.protocol.AuthProof
import com.controlldeck.protocol.AuthResultPayload
import com.controlldeck.protocol.CapabilitiesPayload
import com.controlldeck.protocol.DashboardAckPayload
import com.controlldeck.protocol.DashboardSyncPayload
import com.controlldeck.protocol.DeviceInfoPayload
import com.controlldeck.protocol.Envelope
import com.controlldeck.protocol.ErrorCode
import com.controlldeck.protocol.ErrorPayload
import com.controlldeck.protocol.HelloPayload
import com.controlldeck.protocol.MalformedPayloadException
import com.controlldeck.protocol.MessageDispatcher
import com.controlldeck.protocol.MessageTypes
import com.controlldeck.protocol.PROTOCOL_VERSION
import com.controlldeck.protocol.PairRejectReason
import com.controlldeck.protocol.PairRequestPayload
import com.controlldeck.protocol.PairResponsePayload
import com.controlldeck.protocol.ParsedMessage
import com.controlldeck.protocol.ProtocolJson
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "PeerConnection"
private const val PING_INTERVAL_MS = 15_000L
private const val MAX_MISSED_PONGS = 3
private const val HANDSHAKE_TIMEOUT_MS = 10_000L

/** What the initiator already knows before opening a fresh (unpaired) connection for pairing. */
data class OutgoingPairingIntent(val pairingToken: String)

/**
 * Everything a [PeerConnection] needs from the rest of the app, supplied
 * once at construction so the connection itself has zero knowledge of
 * Room/DataStore/etc — purely a protocol state machine over a socket.
 */
class PeerConnectionContext(
    val selfIdentity: DeviceIdentity,
    val localCapabilities: Set<Capability>,
    val localApps: List<AppRegistryEntry>,
    val isPaired: suspend (DeviceId) -> Boolean,
    val getSharedSecret: suspend (DeviceId) -> String?,
    /** Called on the acceptor side to validate an inbound PIN/QR token; null token means "reject". */
    val validatePairingToken: suspend (String) -> Boolean,
    /** Called on the acceptor side once a PAIR_REQUEST is accepted, to persist the new peer + secret. */
    val onPaired: suspend (deviceId: DeviceId, deviceName: String, platformRaw: String, sharedSecretBase64: String) -> Unit,
    val ownedDashboards: suspend () -> List<Dashboard>,
    val toWireDashboard: (Dashboard) -> DashboardSyncPayload,
)

/**
 * One WebSocket connection to a peer, driving the full lifecycle from
 * protocol/PROTOCOL.md §4: HELLO -> [PAIR_REQUEST/RESPONSE | AUTH] ->
 * DEVICE_INFO/CAPABILITIES -> steady state -> PING/PONG liveness.
 *
 * Works uniformly for both the accepting (server) and initiating (client)
 * side — Ktor's server and client WebSocket sessions both implement
 * [WebSocketSession].
 */
class PeerConnection(
    private val session: WebSocketSession,
    private val role: ConnectionRole,
    private val context: PeerConnectionContext,
    private val logger: Logger,
    private val events: MutableSharedFlow<TransportEvent>,
    private val outgoingPairingIntent: OutgoingPairingIntent? = null,
    private val onReady: suspend (DeviceId) -> Unit,
    private val onClosed: suspend (DeviceId?) -> Unit,
) {
    @Volatile var state: SessionState = SessionState.CONNECTING
        private set

    @Volatile var peerDeviceId: DeviceId? = null
        private set

    private val json = ProtocolJson.instance
    private val sendMutex = Mutex()
    private val missedPongs = AtomicInteger(0)
    private val pendingActionResults = MutableSharedFlow<ParsedMessage.ActionResult>(extraBufferCapacity = 16)

    /** Drives the full handshake + steady-state loop. Suspends until the connection closes. */
    suspend fun run(scope: CoroutineScope) {
        try {
            val selfHello = envelope(MessageTypes.HELLO, targetDeviceId = null).withPayload(
                HelloPayload.serializer(),
                HelloPayload(PROTOCOL_VERSION, context.selfIdentity.deviceId.value, context.selfIdentity.deviceName, "ANDROID", context.selfIdentity.appVersion),
            )

            if (role == ConnectionRole.INITIATOR) send(selfHello)
            val peerHello = withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) { receiveExpected<ParsedMessage.Hello>() }
                ?: run { logger.w(TAG, "handshake timed out waiting for HELLO"); close(); return }
            if (role == ConnectionRole.ACCEPTOR) send(selfHello)

            if (peerHello.payload.protocolVersion != PROTOCOL_VERSION) {
                sendError(ErrorCode.PROTOCOL_VERSION_MISMATCH, "peer protocolVersion=${peerHello.payload.protocolVersion}")
                close()
                return
            }

            val peerId = DeviceId(peerHello.payload.deviceId)
            peerDeviceId = peerId
            state = SessionState.HELLO_EXCHANGED
            logger.i(TAG, "HELLO exchanged with ${peerId.value} ($role)")

            val authenticated = if (context.isPaired(peerId)) {
                performAuth(peerId)
            } else {
                performPairing(peerId, peerHello.payload.deviceName, peerHello.payload.platform)
            }

            if (!authenticated) {
                close()
                return
            }

            state = SessionState.READY
            exchangeDeviceInfoAndCapabilities(peerId, peerHello.payload)
            pushOwnedDashboards(peerId)
            onReady(peerId)
            events.emit(TransportEvent.PeerOnline(peerId, DeviceInfoPayload(peerId.value, peerHello.payload.deviceName, peerHello.payload.platform, peerHello.payload.appVersion)))

            scope.launch(Dispatchers.IO) { pingLoop() }
            steadyStateLoop(peerId)
        } catch (e: ClosedReceiveChannelException) {
            logger.i(TAG, "connection closed by peer ${peerDeviceId?.value}")
        } catch (e: Exception) {
            logger.w(TAG, "connection loop terminated with error", e)
        } finally {
            state = SessionState.CLOSED
            val id = peerDeviceId
            if (id != null) events.emit(TransportEvent.PeerOffline(id))
            onClosed(id)
        }
    }

    // ---- Pairing / Auth ----

    private suspend fun performAuth(peerId: DeviceId): Boolean {
        state = SessionState.AUTHENTICATING
        val secret = context.getSharedSecret(peerId)
        if (secret == null) {
            sendError(ErrorCode.NOT_PAIRED, "no shared secret on file for ${peerId.value}")
            return false
        }

        if (role == ConnectionRole.INITIATOR) {
            val messageId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            val proof = AuthProof.compute(secret, messageId, timestamp)
            send(envelope(MessageTypes.AUTH, messageId = messageId, timestamp = timestamp).withPayload(AuthPayload.serializer(), AuthPayload(context.selfIdentity.deviceId.value, proof)))
            val result = withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) { receiveExpected<ParsedMessage.AuthResult>() } ?: return false
            return result.payload.accepted
        } else {
            val auth = withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) { receiveExpected<ParsedMessage.Auth>() } ?: return false
            val valid = AuthProof.verify(secret, auth.envelope.messageId, auth.envelope.timestamp, auth.payload.proof)
            send(envelope(MessageTypes.AUTH_RESULT).withPayload(AuthResultPayload.serializer(), AuthResultPayload(valid, if (valid) null else "AUTH_FAILED")))
            if (!valid) logger.w(TAG, "AUTH proof rejected for ${peerId.value}")
            return valid
        }
    }

    private suspend fun performPairing(peerId: DeviceId, peerName: String, peerPlatform: String): Boolean {
        state = SessionState.AWAITING_PAIRING
        return if (role == ConnectionRole.INITIATOR) {
            val token = outgoingPairingIntent?.pairingToken
            if (token == null) {
                logger.w(TAG, "no pairing intent for unpaired peer ${peerId.value}")
                return false
            }
            send(
                envelope(MessageTypes.PAIR_REQUEST).withPayload(
                    PairRequestPayload.serializer(),
                    PairRequestPayload(context.selfIdentity.deviceId.value, context.selfIdentity.deviceName, "ANDROID", token),
                ),
            )
            val response = withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) { receiveExpected<ParsedMessage.PairResponse>() } ?: return false
            if (!response.payload.accepted || response.payload.sharedSecret == null) {
                logger.w(TAG, "pairing rejected by ${peerId.value}: ${response.payload.reason}")
                return false
            }
            context.onPaired(peerId, response.payload.deviceName, response.payload.platform, response.payload.sharedSecret)
            true
        } else {
            val request = withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) { receiveExpected<ParsedMessage.PairRequest>() } ?: return false
            val tokenValid = context.validatePairingToken(request.payload.pairingToken)
            if (!tokenValid) {
                send(
                    envelope(MessageTypes.PAIR_RESPONSE).withPayload(
                        PairResponsePayload.serializer(),
                        PairResponsePayload(false, PairRejectReason.TOKEN_INVALID, context.selfIdentity.deviceId.value, context.selfIdentity.deviceName, "ANDROID", null),
                    ),
                )
                logger.i(TAG, "rejected pairing from ${peerId.value}: invalid/expired token")
                return false
            }
            val secretBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val secret = Base64.getEncoder().encodeToString(secretBytes)
            context.onPaired(peerId, request.payload.requesterDeviceName, request.payload.requesterPlatform, secret)
            send(
                envelope(MessageTypes.PAIR_RESPONSE).withPayload(
                    PairResponsePayload.serializer(),
                    PairResponsePayload(true, null, context.selfIdentity.deviceId.value, context.selfIdentity.deviceName, "ANDROID", secret),
                ),
            )
            logger.redactedEvent(TAG, "pairing accepted, shared secret issued to ${peerId.value}")
            true
        }
    }

    private suspend fun exchangeDeviceInfoAndCapabilities(peerId: DeviceId, peerHello: HelloPayload) {
        send(
            envelope(MessageTypes.DEVICE_INFO).withPayload(
                DeviceInfoPayload.serializer(),
                DeviceInfoPayload(context.selfIdentity.deviceId.value, context.selfIdentity.deviceName, "ANDROID", context.selfIdentity.appVersion),
            ),
        )
        send(
            envelope(MessageTypes.CAPABILITIES).withPayload(
                CapabilitiesPayload.serializer(),
                CapabilitiesPayload(
                    context.selfIdentity.deviceId.value,
                    context.localCapabilities.map { it.name },
                    context.localApps.map { com.controlldeck.protocol.AppRegistryEntryDto(it.appId.value, it.displayName) },
                ),
            ),
        )
    }

    private suspend fun pushOwnedDashboards(peerId: DeviceId) {
        context.ownedDashboards().forEach { dashboard ->
            send(envelope(MessageTypes.DASHBOARD_SYNC).withPayload(DashboardSyncPayload.serializer(), context.toWireDashboard(dashboard)))
        }
    }

    // ---- Steady state ----

    private suspend fun steadyStateLoop(peerId: DeviceId) {
        for (frame in session.incoming) {
            if (frame !is Frame.Text) continue
            val raw = frame.readText()
            val message = try {
                MessageDispatcher.parse(raw, json)
            } catch (e: MalformedPayloadException) {
                sendError(ErrorCode.MALFORMED_PAYLOAD, e.message ?: "malformed payload")
                continue
            }
            handleSteadyStateMessage(peerId, message)
        }
    }

    private suspend fun handleSteadyStateMessage(peerId: DeviceId, message: ParsedMessage) {
        when (message) {
            is ParsedMessage.Ping -> send(envelope(MessageTypes.PONG))
            is ParsedMessage.Pong -> missedPongs.set(0)
            is ParsedMessage.Action -> events.emit(
                TransportEvent.ActionReceived(peerId, message.envelope.messageId, message.payload.action) { success, errorCode, resultingState ->
                    send(
                        envelope(MessageTypes.ACTION_RESULT).withPayload(
                            ActionResultPayload.serializer(),
                            ActionResultPayload(message.envelope.messageId, success, errorCode, resultingState),
                        ),
                    )
                },
            )
            is ParsedMessage.ActionResult -> {
                pendingActionResults.emit(message)
                events.emit(TransportEvent.ActionResultReceived(peerId, message.payload.correlatesTo, message.payload.success, message.payload.errorCode, message.payload.resultingState))
            }
            is ParsedMessage.StateUpdate -> events.emit(TransportEvent.StateUpdateReceived(peerId, message.payload.state))
            is ParsedMessage.DashboardSync -> {
                events.emit(
                    TransportEvent.DashboardSyncReceived(peerId, message.envelope.timestamp, message.payload) { replyPayload ->
                        send(envelope(MessageTypes.DASHBOARD_SYNC).withPayload(DashboardSyncPayload.serializer(), replyPayload))
                    },
                )
            }
            is ParsedMessage.DashboardAck -> logger.d(TAG, "DASHBOARD_ACK from ${peerId.value}: applied v${message.payload.appliedVersion}")
            is ParsedMessage.Error -> events.emit(TransportEvent.Error(peerId, message.payload.code, message.payload.message))
            is ParsedMessage.Unknown -> sendError(ErrorCode.UNSUPPORTED_MESSAGE_TYPE, "unrecognized type '${message.envelope.type}'")
            is ParsedMessage.Capabilities -> events.emit(TransportEvent.CapabilitiesReceived(peerId, message.payload))
            else -> Unit
        }
    }

    private suspend fun pingLoop() {
        while (state == SessionState.READY) {
            delay(PING_INTERVAL_MS)
            if (missedPongs.get() >= MAX_MISSED_PONGS) {
                logger.w(TAG, "3 missed PONGs from ${peerDeviceId?.value}, closing connection")
                close()
                return
            }
            missedPongs.incrementAndGet()
            runCatching { send(envelope(MessageTypes.PING)) }
        }
    }

    // ---- Frame plumbing ----

    private suspend inline fun <reified T : ParsedMessage> receiveExpected(): T? {
        for (frame in session.incoming) {
            if (frame !is Frame.Text) continue
            val raw = frame.readText()
            val message = try {
                MessageDispatcher.parse(raw, json)
            } catch (e: MalformedPayloadException) {
                logger.w(TAG, "malformed payload during handshake", e)
                continue
            }
            if (message is T) return message
            if (message is ParsedMessage.Error) {
                logger.w(TAG, "peer sent ERROR during handshake: ${message.payload.code} ${message.payload.message}")
                return null
            }
        }
        return null
    }

    private fun envelope(type: String, messageId: String = UUID.randomUUID().toString(), targetDeviceId: String? = peerDeviceId?.value, timestamp: Long = System.currentTimeMillis()): Envelope =
        Envelope(type = type, messageId = messageId, sourceDeviceId = context.selfIdentity.deviceId.value, targetDeviceId = targetDeviceId, timestamp = timestamp)

    private fun <T> Envelope.withPayload(serializer: kotlinx.serialization.KSerializer<T>, payload: T): Envelope =
        copy(payload = json.encodeToJsonElement(serializer, payload))

    private suspend fun send(envelope: Envelope) {
        sendMutex.withLock {
            runCatching { session.send(Frame.Text(json.encodeToString(Envelope.serializer(), envelope))) }
                .onFailure { logger.w(TAG, "send failed", it) }
        }
    }

    private suspend fun sendError(code: String, message: String) {
        runCatching { send(envelope(MessageTypes.ERROR).withPayload(ErrorPayload.serializer(), ErrorPayload(code, message, null))) }
    }

    suspend fun close() {
        runCatching { session.close() }
    }

    /** Sends an ACTION to this peer and returns the correlated ACTION_RESULT, or null on timeout. */
    suspend fun sendAction(action: com.controlldeck.protocol.ActionDto, timeoutMs: Long = 5000): ParsedMessage.ActionResult? {
        val messageId = UUID.randomUUID().toString()
        send(envelope(MessageTypes.ACTION, messageId = messageId).withPayload(ActionPayload.serializer(), ActionPayload(action)))
        return withTimeoutOrNull(timeoutMs) {
            kotlinx.coroutines.flow.first(pendingActionResults) { it.payload.correlatesTo == messageId }
        }
    }

    suspend fun sendStateUpdate(state: com.controlldeck.protocol.ActionDto) {
        send(
            envelope(MessageTypes.STATE_UPDATE).withPayload(
                com.controlldeck.protocol.StateUpdatePayload.serializer(),
                com.controlldeck.protocol.StateUpdatePayload(context.selfIdentity.deviceId.value, state),
            ),
        )
    }

    suspend fun sendDashboardSync(payload: DashboardSyncPayload) {
        send(envelope(MessageTypes.DASHBOARD_SYNC).withPayload(DashboardSyncPayload.serializer(), payload))
    }

    suspend fun sendDashboardAck(dashboardId: String, appliedVersion: Long) {
        send(envelope(MessageTypes.DASHBOARD_ACK).withPayload(DashboardAckPayload.serializer(), DashboardAckPayload(dashboardId, appliedVersion)))
    }
}
