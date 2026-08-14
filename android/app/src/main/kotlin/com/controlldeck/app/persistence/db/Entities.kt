package com.controlldeck.app.persistence.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "dashboards")
data class DashboardEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: Long,
)

@Entity(
    tableName = "widgets",
    foreignKeys = [
        ForeignKey(
            entity = DashboardEntity::class,
            parentColumns = ["id"],
            childColumns = ["dashboardId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("dashboardId")],
)
data class WidgetEntity(
    @PrimaryKey val id: String,
    val dashboardId: String,
    val type: String,
    val positionX: Int,
    val positionY: Int,
    val sizeWidth: Int,
    val sizeHeight: Int,
    val targetDeviceId: String,
    /** Pipe-encoded ActionSpec, see [com.controlldeck.app.persistence.db.ActionSpecCodec]. */
    val actionEncoded: String,
    /** `key=value` pairs joined by `` (unit separator), URL-encoded per entry. */
    val configurationEncoded: String,
)

@Entity(
    tableName = "groups",
    foreignKeys = [
        ForeignKey(
            entity = DashboardEntity::class,
            parentColumns = ["id"],
            childColumns = ["dashboardId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("dashboardId")],
)
data class GroupEntity(
    @PrimaryKey val id: String,
    val dashboardId: String,
    val name: String,
    val kind: String,
    /** Widget ids joined by ``. */
    val memberWidgetIdsEncoded: String,
    val reconnectPolicy: String,
)

@Entity(tableName = "paired_devices")
data class PairedDeviceEntity(
    @PrimaryKey val deviceId: String,
    val deviceName: String,
    val platform: String,
    val pairedAtEpochMs: Long,
)

@Entity(tableName = "app_registry")
data class AppRegistryEntryEntity(
    @PrimaryKey val appId: String,
    val displayName: String,
    /** Android package name this appId resolves to on this device. */
    val packageName: String,
)
