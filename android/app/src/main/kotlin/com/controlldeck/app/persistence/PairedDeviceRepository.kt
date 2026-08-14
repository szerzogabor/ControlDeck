package com.controlldeck.app.persistence

import com.controlldeck.app.persistence.db.PairedDeviceDao
import com.controlldeck.app.persistence.db.PairedDeviceEntity
import com.controlldeck.app.security.SecretStore
import com.controlldeck.domain.DeviceId
import com.controlldeck.domain.Platform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A paired peer's identity metadata (never the raw IP — see docs/ARCHITECTURE.md). */
data class PairedDevice(
    val deviceId: DeviceId,
    val deviceName: String,
    val platform: Platform,
    val pairedAtEpochMs: Long,
)

/**
 * Persists paired-peer metadata (Room) and delegates the shared secret to
 * [SecretStore] (EncryptedSharedPreferences) — the secret itself never
 * touches this table or any plaintext store.
 */
class PairedDeviceRepository(
    private val dao: PairedDeviceDao,
    private val secretStore: SecretStore,
) {
    fun observePairedDevices(): Flow<List<PairedDevice>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getPairedDevice(deviceId: DeviceId): PairedDevice? = dao.getById(deviceId.value)?.toDomain()

    suspend fun isPaired(deviceId: DeviceId): Boolean =
        dao.getById(deviceId.value) != null && secretStore.hasSharedSecret(deviceId)

    suspend fun savePairing(device: PairedDevice, sharedSecretBase64: String) {
        dao.upsert(
            PairedDeviceEntity(
                deviceId = device.deviceId.value,
                deviceName = device.deviceName,
                platform = device.platform.name,
                pairedAtEpochMs = device.pairedAtEpochMs,
            ),
        )
        secretStore.putSharedSecret(device.deviceId, sharedSecretBase64)
    }

    fun getSharedSecret(deviceId: DeviceId): String? = secretStore.getSharedSecret(deviceId)

    suspend fun forget(deviceId: DeviceId) {
        dao.deleteById(deviceId.value)
        secretStore.removeSharedSecret(deviceId)
    }
}

private fun PairedDeviceEntity.toDomain(): PairedDevice = PairedDevice(
    deviceId = DeviceId(deviceId),
    deviceName = deviceName,
    platform = runCatching { Platform.valueOf(platform) }.getOrDefault(Platform.ANDROID),
    pairedAtEpochMs = pairedAtEpochMs,
)
