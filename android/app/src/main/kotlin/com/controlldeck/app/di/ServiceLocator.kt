package com.controlldeck.app.di

import android.content.Context
import com.controlldeck.app.action.ActionEngine
import com.controlldeck.app.capability.LocalCapabilityController
import com.controlldeck.app.discovery.NsdDiscoveryService
import com.controlldeck.app.group.GroupManager
import com.controlldeck.app.identity.DeviceIdentity
import com.controlldeck.app.identity.DeviceIdentityRepository
import com.controlldeck.app.logging.AndroidLogger
import com.controlldeck.app.logging.Logger
import com.controlldeck.app.mapper.ProtocolMappers
import com.controlldeck.app.pairing.PairingManager
import com.controlldeck.app.persistence.AppRegistryRepository
import com.controlldeck.app.persistence.DashboardRepository
import com.controlldeck.app.persistence.PairedDeviceRepository
import com.controlldeck.app.persistence.db.AppDatabase
import com.controlldeck.app.prefs.UserPreferencesRepository
import com.controlldeck.app.security.SecretStore
import com.controlldeck.app.state.DeviceStateManager
import com.controlldeck.app.transport.ConnectionManager
import com.controlldeck.app.transport.buildPeerConnectionContext
import com.controlldeck.domain.DeviceId
import com.controlldeck.app.sync.DashboardSyncCoordinator
import com.controlldeck.app.sync.PeerConnectionCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Minimal manual dependency container (no Hilt/Koin — see task notes: the
 * annotation processor overhead of Hilt can't be verified without an
 * Android SDK in this environment, so a small explicit graph is more
 * robust). Constructed once in [com.controlldeck.app.ControlDeckApplication]
 * and handed to Activities/ViewModel factories.
 */
class ServiceLocator(context: Context) {

    val logger: Logger = AndroidLogger

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database = AppDatabase.get(context)

    val deviceIdentityRepository = DeviceIdentityRepository(context)
    val userPreferencesRepository = UserPreferencesRepository(context)
    val secretStore = SecretStore(context, logger)
    val pairedDeviceRepository = PairedDeviceRepository(database.pairedDeviceDao(), secretStore)
    val dashboardRepository = DashboardRepository(database.dashboardDao())
    val appRegistryRepository = AppRegistryRepository(database.appRegistryDao())

    val localCapabilityController = LocalCapabilityController(context, appRegistryRepository, logger)
    val deviceStateManager = DeviceStateManager()
    val groupManager = GroupManager()
    val pairingManager = PairingManager(logger)
    val nsdDiscoveryService = NsdDiscoveryService(context, logger)

    /**
     * The device identity is resolved once, synchronously, at construction
     * time (DataStore ensureInitialized is a fast local read/write). This
     * keeps every downstream dependency (ConnectionManager, ActionEngine)
     * free of "identity not loaded yet" nullability.
     */
    val selfIdentity: DeviceIdentity = runBlocking { deviceIdentityRepository.ensureInitialized() }

    val connectionManager: ConnectionManager = ConnectionManager(logger) {
        buildPeerConnectionContext(
            selfIdentity = selfIdentity,
            localCapabilities = localCapabilityController.localCapabilities(),
            localApps = runBlocking { appRegistryRepository.toWireAppList() },
            isPaired = { deviceId -> pairedDeviceRepository.isPaired(deviceId) },
            getSharedSecret = { deviceId -> pairedDeviceRepository.getSharedSecret(deviceId) },
            // Testing-only convenience: bypass PIN/QR validation entirely when
            // this device has "Auto-accept pairing" enabled in Settings.
            validatePairingToken = { token ->
                if (userPreferencesRepository.preferences.first().autoAcceptPairing) true
                else pairingManager.validateToken(token)
            },
            onPaired = { deviceId, name, platformRaw, secret ->
                pairedDeviceRepository.savePairing(
                    com.controlldeck.app.persistence.PairedDevice(
                        deviceId = deviceId,
                        deviceName = name,
                        platform = ProtocolMappers.platformFromWire(platformRaw),
                        pairedAtEpochMs = System.currentTimeMillis(),
                    ),
                    secret,
                )
            },
            ownedDashboards = { dashboardRepository.observeDashboardsSnapshot() },
            toWireDashboard = { dashboard -> com.controlldeck.protocol.DashboardSyncPayload(ProtocolMappers.dashboardToWire(dashboard)) },
        )
    }

    val actionEngine = ActionEngine(
        selfDeviceId = selfIdentity.deviceId,
        localCapabilityController = localCapabilityController,
        connectionManager = connectionManager,
        deviceStateManager = deviceStateManager,
        logger = logger,
    )

    private val dashboardSyncCoordinator = DashboardSyncCoordinator(dashboardRepository, connectionManager, logger)
    private val peerConnectionCoordinator = PeerConnectionCoordinator(deviceStateManager, connectionManager, groupManager, dashboardRepository, actionEngine, logger)

    fun start() {
        connectionManager.startServer()
        nsdDiscoveryService.startAdvertising(selfIdentity.deviceId, selfIdentity.deviceName, selfIdentity.appVersion, connectionManager.boundPort)
        nsdDiscoveryService.startDiscovery()
        applicationScope.launch { appRegistryRepository.seedDefaultsIfEmpty() }
        applicationScope.launch { actionEngine.observeTransportEvents() }
        applicationScope.launch { dashboardSyncCoordinator.observe() }
        applicationScope.launch { peerConnectionCoordinator.observe() }
    }

    fun shutdown() {
        nsdDiscoveryService.stopAdvertising()
        nsdDiscoveryService.stopDiscovery()
        connectionManager.shutdown()
    }

    companion object {
        @Volatile private var instance: ServiceLocator? = null

        fun getOrCreate(context: Context): ServiceLocator = instance ?: synchronized(this) {
            instance ?: ServiceLocator(context.applicationContext).also { instance = it }
        }
    }
}
