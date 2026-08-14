package com.controlldeck.app.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.controlldeck.domain.ReconnectPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPrefsDataStore by preferencesDataStore(name = "user_prefs")

data class UserPreferences(
    val defaultReconnectPolicy: ReconnectPolicy = ReconnectPolicy.SYNC_GROUP_STATE,
)

/** General user-editable app preferences (not device identity, not secrets). */
class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val DEFAULT_RECONNECT_POLICY = stringPreferencesKey("default_reconnect_policy")
    }

    val preferences: Flow<UserPreferences> = context.userPrefsDataStore.data.map { prefs ->
        val policy = prefs[Keys.DEFAULT_RECONNECT_POLICY]?.let { raw ->
            runCatching { ReconnectPolicy.valueOf(raw) }.getOrNull()
        } ?: ReconnectPolicy.SYNC_GROUP_STATE
        UserPreferences(defaultReconnectPolicy = policy)
    }

    suspend fun setDefaultReconnectPolicy(policy: ReconnectPolicy) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_RECONNECT_POLICY] = policy.name
        }
    }
}
