package com.controlldeck.app.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.controlldeck.app.logging.Logger
import com.controlldeck.domain.DeviceId
import com.controlldeck.domain.Platform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.net.InetAddress
import java.nio.charset.StandardCharsets

private const val SERVICE_TYPE = "_controlldeck._tcp."
private const val TAG = "NsdDiscovery"

/** A peer discovered via mDNS, per protocol/PROTOCOL.md §5 TXT records. */
data class DiscoveredDevice(
    val deviceId: DeviceId,
    val deviceName: String,
    val platform: Platform,
    val appVersion: String,
    val host: InetAddress,
    val port: Int,
)

/**
 * Wraps [NsdManager] to both advertise this device's own WebSocket server
 * and browse/resolve peers, exposing peers as a [Flow]. All NSD callbacks
 * are funneled through Kotlin coroutine primitives so callers never touch
 * the raw Android callback API. Never propagates NSD failures as
 * exceptions (docs/ARCHITECTURE.md §7) — failures are logged and simply
 * omit the affected peer/registration.
 */
class NsdDiscoveryService(private val context: Context, private val logger: Logger) {

    private val nsdManager: NsdManager by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }

    // Without holding this, many Wi-Fi chipsets silently drop *incoming*
    // multicast packets (including mDNS) to save power — the device can
    // advertise itself but never receive other peers' announcements. Not
    // reference-counted: acquire()/release() are called in lockstep from
    // startDiscovery()/stopDiscovery() below, guarded by isHeld.
    private val multicastLock: WifiManager.MulticastLock by lazy {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiManager.createMulticastLock("controldeck-mdns").apply { setReferenceCounted(false) }
    }

    private val _discoveredDevices = MutableStateFlow<Map<String, DiscoveredDevice>>(emptyMap())

    /** Keyed by mDNS service name internally; exposed as a flat list, deduplicated by deviceId. */
    val discoveredDevices: Flow<List<DiscoveredDevice>> = _discoveredDevices.map { byServiceName ->
        byServiceName.values.distinctBy { it.deviceId }
    }

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    /**
     * Advertises this device on the LAN. [port] must be the actual bound
     * WebSocket server port (may differ from the default if it was busy).
     */
    fun startAdvertising(deviceId: DeviceId, deviceName: String, appVersion: String, port: Int) {
        stopAdvertising()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "ControlDeck-${deviceId.value.take(8)}"
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("id", deviceId.value)
            setAttribute("name", deviceName)
            setAttribute("platform", "ANDROID")
            setAttribute("version", appVersion)
            setAttribute("port", port.toString())
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                logger.i(TAG, "advertising registered as ${info.serviceName} on port $port")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                logger.e(TAG, "advertising registration failed: errorCode=$errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                logger.i(TAG, "advertising unregistered")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                logger.w(TAG, "advertising unregistration failed: errorCode=$errorCode")
            }
        }
        registrationListener = listener
        runCatching { nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { logger.e(TAG, "registerService threw", it) }
    }

    fun stopAdvertising() {
        registrationListener?.let { listener ->
            runCatching { nsdManager.unregisterService(listener) }
                .onFailure { logger.w(TAG, "unregisterService threw", it) }
        }
        registrationListener = null
    }

    fun startDiscovery() {
        stopDiscovery()

        runCatching { if (!multicastLock.isHeld) multicastLock.acquire() }
            .onFailure { logger.w(TAG, "failed to acquire Wi-Fi multicast lock; peer announcements may not be received", it) }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                logger.i(TAG, "discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType != SERVICE_TYPE && !service.serviceType.startsWith(SERVICE_TYPE)) return
                resolve(service)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                // We key discovered devices by mDNS service name until resolved; best-effort removal.
                _discoveredDevices.value = _discoveredDevices.value.filterKeys { it != service.serviceName }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                logger.i(TAG, "discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                logger.e(TAG, "start discovery failed: errorCode=$errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                logger.w(TAG, "stop discovery failed: errorCode=$errorCode")
            }
        }
        discoveryListener = listener
        runCatching { nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { logger.e(TAG, "discoverServices threw", it) }
    }

    fun stopDiscovery() {
        discoveryListener?.let { listener ->
            runCatching { nsdManager.stopServiceDiscovery(listener) }
                .onFailure { logger.w(TAG, "stopServiceDiscovery threw", it) }
        }
        discoveryListener = null

        runCatching { if (multicastLock.isHeld) multicastLock.release() }
            .onFailure { logger.w(TAG, "failed to release Wi-Fi multicast lock", it) }
    }

    private fun resolve(service: NsdServiceInfo) {
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                logger.w(TAG, "resolve failed for ${info.serviceName}: errorCode=$errorCode")
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                val attrs = info.attributes
                val id = attrs["id"]?.toText() ?: return
                val name = attrs["name"]?.toText() ?: info.serviceName
                val platform = runCatching { Platform.valueOf(attrs["platform"]?.toText() ?: "ANDROID") }.getOrDefault(Platform.ANDROID)
                val version = attrs["version"]?.toText() ?: "0.0.0"
                val port = attrs["port"]?.toText()?.toIntOrNull() ?: info.port
                val host = info.host ?: return

                val device = DiscoveredDevice(DeviceId(id), name, platform, version, host, port)
                _discoveredDevices.value = _discoveredDevices.value + (service.serviceName to device)
            }
        }
        runCatching { nsdManager.resolveService(service, listener) }
            .onFailure { logger.w(TAG, "resolveService threw", it) }
    }

    private fun ByteArray.toText(): String = String(this, StandardCharsets.UTF_8)
}
