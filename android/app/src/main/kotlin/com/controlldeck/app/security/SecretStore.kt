package com.controlldeck.app.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.controlldeck.app.logging.Logger
import com.controlldeck.domain.DeviceId

/**
 * Stores each paired peer's Base64 shared secret using an Android
 * Keystore-backed [EncryptedSharedPreferences]. Never stored in plaintext
 * DataStore, never logged (per protocol/PROTOCOL.md §7 and top-level
 * spec §24-25).
 */
class SecretStore(context: Context, private val logger: Logger) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "controlldeck_secrets",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun putSharedSecret(peerDeviceId: DeviceId, sharedSecretBase64: String) {
        prefs.edit().putString(peerDeviceId.value, sharedSecretBase64).apply()
        logger.redactedEvent("SecretStore", "shared secret stored for peer ${peerDeviceId.value}")
    }

    fun getSharedSecret(peerDeviceId: DeviceId): String? = prefs.getString(peerDeviceId.value, null)

    fun hasSharedSecret(peerDeviceId: DeviceId): Boolean = prefs.contains(peerDeviceId.value)

    fun removeSharedSecret(peerDeviceId: DeviceId) {
        prefs.edit().remove(peerDeviceId.value).apply()
        logger.i("SecretStore", "shared secret removed for peer ${peerDeviceId.value}")
    }
}
