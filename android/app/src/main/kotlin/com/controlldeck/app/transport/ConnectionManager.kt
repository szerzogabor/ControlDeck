package com.controlldeck.app.transport

import com.controlldeck.app.identity.DeviceIdentity
import com.controlldeck.app.logging.Logger
import com.controlldeck.domain.AppRegistryEntry
import com.controlldeck.domain.Capability
import com.controlldeck.domain.Dashboard
import com.controlldeck.domain.DeviceId
import com.controlldeck.protocol.ActionDto
import com.controlldeck.protocol.DashboardSyncPayload
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.install
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.net.ServerSocket
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "ConnectionManager"
const val DEFAULT_PORT = 47531

/**
 * Owns the WebSocket server (accepting inbound peer connections) and the
 * WebSocket client (dialing out to peers), per protocol/PROTOCOL.md §1 —
 * "every device can also act as a WebSocket client and connect out to
 * peers it has paired with". Every open [PeerConnection] is tracked by
 * deviceId so the rest of the app can address a specific peer.
 */
class ConnectionManager(
    private val logger: Logger,
    private val contextFactory: () -> PeerConnectionContext,
) : com.controlldeck.app.ui.pairing.PairingConnector {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<TransportEvent> = _events

    private val connections = ConcurrentHashMap<String, PeerConnection>()

    private var server: io.ktor.server.engine.ApplicationEngine? = null
    private val httpClient by lazy {
        HttpClient(ClientCIO) {
            install(ClientWebSockets) {
                pingInterval = Duration.ofSeconds(15)
            }
        }
    }

    var boundPort: Int = DEFAULT_PORT
        private set

    /** Starts the WebSocket server, falling back to an ephemeral port if [DEFAULT_PORT] is busy. */
    fun startServer() {
        val port = pickAvailablePort(DEFAULT_PORT)
        boundPort = port
        server = embeddedServer(ServerCIO, port = port) {
            install(ServerWebSockets)
            routing {
                webSocket("/") {
                    lateinit var connection: PeerConnection
                    connection = PeerConnection(
                        session = this,
                        role = ConnectionRole.ACCEPTOR,
                        context = contextFactory(),
                        logger = logger,
                        events = _events,
                        onReady = { id -> connections[id.value] = connection },
                        onClosed = { id -> id?.let { connections.remove(it.value) } },
                    )
                    connection.run(scope)
                }
            }
        }.also { it.start(wait = false) }
        logger.i(TAG, "WebSocket server listening on port $port")
    }

    fun stopServer() {
        server?.stop(gracePeriodMillis = 200, timeoutMillis = 1000)
        server = null
    }

    /** Opens an authenticated (already-paired) connection to a peer, e.g. after discovery or on reconnect. */
    fun connectAuthenticated(host: String, port: Int) {
        scope.launch {
            runCatching {
                httpClient.webSocket(host = host, port = port, path = "/") {
                    lateinit var connection: PeerConnection
                    connection = PeerConnection(
                        session = this,
                        role = ConnectionRole.INITIATOR,
                        context = contextFactory(),
                        logger = logger,
                        events = _events,
                        onReady = { id -> connections[id.value] = connection },
                        onClosed = { id -> id?.let { connections.remove(it.value) } },
                    )
                    connection.run(scope)
                }
            }.onFailure { logger.w(TAG, "connectAuthenticated to $host:$port failed", it) }
        }
    }

    /** Opens a fresh (unpaired) connection to complete a QR/PIN pairing handshake as the initiator. */
    override fun connectForPairing(host: String, port: Int, pairingToken: String) {
        scope.launch {
            runCatching {
                httpClient.webSocket(host = host, port = port, path = "/") {
                    lateinit var connection: PeerConnection
                    connection = PeerConnection(
                        session = this,
                        role = ConnectionRole.INITIATOR,
                        context = contextFactory(),
                        logger = logger,
                        events = _events,
                        outgoingPairingIntent = OutgoingPairingIntent(pairingToken),
                        onReady = { id -> connections[id.value] = connection },
                        onClosed = { id -> id?.let { connections.remove(it.value) } },
                    )
                    connection.run(scope)
                }
            }.onFailure { logger.w(TAG, "connectForPairing to $host:$port failed", it) }
        }
    }

    fun isConnected(deviceId: DeviceId): Boolean = connections.containsKey(deviceId.value)

    fun connectedDeviceIds(): Set<DeviceId> = connections.keys.map { DeviceId(it) }.toSet()

    suspend fun sendAction(deviceId: DeviceId, action: ActionDto) = connections[deviceId.value]?.sendAction(action)

    suspend fun sendStateUpdate(deviceId: DeviceId, state: ActionDto) {
        connections[deviceId.value]?.sendStateUpdate(state)
    }

    suspend fun broadcastStateUpdate(state: ActionDto) {
        connections.values.forEach { it.sendStateUpdate(state) }
    }

    suspend fun sendDashboardSync(deviceId: DeviceId, payload: DashboardSyncPayload) {
        connections[deviceId.value]?.sendDashboardSync(payload)
    }

    suspend fun broadcastDashboardSync(payload: DashboardSyncPayload) {
        connections.values.forEach { it.sendDashboardSync(payload) }
    }

    suspend fun sendDashboardAck(deviceId: DeviceId, dashboardId: String, appliedVersion: Long) {
        connections[deviceId.value]?.sendDashboardAck(dashboardId, appliedVersion)
    }

    fun shutdown() {
        scope.launch { connections.values.forEach { it.close() } }
        stopServer()
        httpClient.close()
    }

    private fun pickAvailablePort(preferred: Int): Int = try {
        ServerSocket(preferred).use { it.localPort }
    } catch (e: Exception) {
        logger.w(TAG, "default port $preferred busy, falling back to an ephemeral port", e)
        ServerSocket(0).use { it.localPort }
    }
}

/** Convenience constructor bundle so ServiceLocator doesn't need to know PeerConnectionContext's shape. */
fun buildPeerConnectionContext(
    selfIdentity: DeviceIdentity,
    localCapabilities: Set<Capability>,
    localApps: List<AppRegistryEntry>,
    isPaired: suspend (DeviceId) -> Boolean,
    getSharedSecret: suspend (DeviceId) -> String?,
    validatePairingToken: suspend (String) -> Boolean,
    onPaired: suspend (DeviceId, String, String, String) -> Unit,
    ownedDashboards: suspend () -> List<Dashboard>,
    toWireDashboard: (Dashboard) -> DashboardSyncPayload,
): PeerConnectionContext = PeerConnectionContext(
    selfIdentity, localCapabilities, localApps, isPaired, getSharedSecret, validatePairingToken, onPaired, ownedDashboards, toWireDashboard,
)
