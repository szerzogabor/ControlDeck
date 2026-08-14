package com.controlldeck.app.identity

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.controlldeck.app.BuildConfig
import com.controlldeck.domain.DeviceId
import com.controlldeck.domain.Platform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.identityDataStore by preferencesDataStore(name = "device_identity")

/** This device's identity as advertised over mDNS and used in every Envelope.sourceDeviceId. */
data class DeviceIdentity(
    val deviceId: DeviceId,
    val deviceName: String,
    val platform: Platform,
    val appVersion: String,
)

/**
 * Persists the device's stable [DeviceId] (generated once, per
 * protocol/PROTOCOL.md §5 "the advertised deviceId is what the UI keys off
 * of — never the resolved IP/hostname") and the user-editable device name.
 */
class DeviceIdentityRepository(private val context: Context) {

    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DEVICE_NAME = stringPreferencesKey("device_name")
    }

    val identity: Flow<DeviceIdentity> = context.identityDataStore.data.map { prefs ->
        DeviceIdentity(
            deviceId = DeviceId(prefs[Keys.DEVICE_ID] ?: ""),
            deviceName = prefs[Keys.DEVICE_NAME] ?: defaultDeviceName(),
            platform = Platform.ANDROID,
            appVersion = BuildConfig.VERSION_NAME,
        )
    }

    /** Ensures a deviceId/name exist, generating them on first launch. Safe to call repeatedly. */
    suspend fun ensureInitialized(): DeviceIdentity {
        context.identityDataStore.edit { prefs ->
            if (prefs[Keys.DEVICE_ID].isNullOrBlank()) {
                prefs[Keys.DEVICE_ID] = UUID.randomUUID().toString()
            }
            if (prefs[Keys.DEVICE_NAME].isNullOrBlank()) {
                prefs[Keys.DEVICE_NAME] = defaultDeviceName()
            }
        }
        return identity.first()
    }

    suspend fun setDeviceName(name: String) {
        context.identityDataStore.edit { prefs ->
            prefs[Keys.DEVICE_NAME] = name.trim().ifBlank { defaultDeviceName() }
        }
    }

    private fun defaultDeviceName(): String = Build.MODEL ?: "Android Device"
}
