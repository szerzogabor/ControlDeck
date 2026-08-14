package com.controlldeck.app.persistence

import com.controlldeck.app.persistence.db.AppRegistryDao
import com.controlldeck.app.persistence.db.AppRegistryEntryEntity
import com.controlldeck.domain.AppId
import com.controlldeck.domain.AppRegistryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** appId -> package name entry, editable via Settings, seeded with a few common defaults. */
data class LocalAppRegistryEntry(
    val appId: AppId,
    val displayName: String,
    val packageName: String,
)

/**
 * Local app registry: `appId` -> Android package name, resolved only on
 * this device at ACTION execution time (protocol/PROTOCOL.md §8 — never
 * a filesystem path over the wire, and package names never cross the
 * wire either, only the opaque `appId`).
 */
class AppRegistryRepository(private val dao: AppRegistryDao) {

    companion object {
        val DEFAULT_ENTRIES = listOf(
            LocalAppRegistryEntry(AppId("spotify"), "Spotify", "com.spotify.music"),
            LocalAppRegistryEntry(AppId("discord"), "Discord", "com.discord"),
            LocalAppRegistryEntry(AppId("chrome"), "Chrome", "com.android.chrome"),
        )
    }

    suspend fun seedDefaultsIfEmpty() {
        if (dao.count() == 0) {
            DEFAULT_ENTRIES.forEach { dao.upsert(it.toEntity()) }
        }
    }

    fun observeEntries(): Flow<List<LocalAppRegistryEntry>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getEntry(appId: AppId): LocalAppRegistryEntry? = dao.getById(appId.value)?.toDomain()

    suspend fun upsert(entry: LocalAppRegistryEntry) = dao.upsert(entry.toEntity())

    suspend fun remove(appId: AppId) = dao.delete(appId.value)

    /** The advertised `apps` list for CAPABILITIES (protocol/PROTOCOL.md §3.4). */
    suspend fun toWireAppList(): List<AppRegistryEntry> =
        dao.observeAll().first().map { AppRegistryEntry(AppId(it.appId), it.displayName) }
}

private fun LocalAppRegistryEntry.toEntity(): AppRegistryEntryEntity =
    AppRegistryEntryEntity(appId.value, displayName, packageName)

private fun AppRegistryEntryEntity.toDomain(): LocalAppRegistryEntry =
    LocalAppRegistryEntry(AppId(appId), displayName, packageName)
