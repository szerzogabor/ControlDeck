package com.controlldeck.app.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.controlldeck.domain.ReconnectPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPrefsDataStore by preferencesDataStore(name = "user_prefs")

data class UserPreferences(
    val defaultReconnectPolicy: ReconnectPolicy = ReconnectPolicy.SYNC_GROUP_STATE,
    // Testing-only convenience: when true, this device accepts any incoming
    // PAIR_REQUEST without checking the PIN/QR token (see PairingManager,
    // ServiceLocator.validatePairingToken). Off by default — enabling it
    // means anything on the LAN speaking the protocol can pair with this
    // device with no human confirmation.
    val autoAcceptPairing: Boolean = false,
)

/** General user-editable app preferences (not device identity, not secrets). */
class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val DEFAULT_RECONNECT_POLICY = stringPreferencesKey("default_reconnect_policy")
        val AUTO_ACCEPT_PAIRING = booleanPreferencesKey("auto_accept_pairing")
    }

    val preferences: Flow<UserPreferences> = context.userPrefsDataStore.data.map { prefs ->
        val policy = prefs[Keys.DEFAULT_RECONNECT_POLICY]?.let { raw ->
            runCatching { ReconnectPolicy.valueOf(raw) }.getOrNull()
        } ?: ReconnectPolicy.SYNC_GROUP_STATE
        UserPreferences(
            defaultReconnectPolicy = policy,
            autoAcceptPairing = prefs[Keys.AUTO_ACCEPT_PAIRING] ?: false,
        )
    }

    suspend fun setDefaultReconnectPolicy(policy: ReconnectPolicy) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_RECONNECT_POLICY] = policy.name
        }
    }

    suspend fun setAutoAcceptPairing(enabled: Boolean) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[Keys.AUTO_ACCEPT_PAIRING] = enabled
        }
    }
}
