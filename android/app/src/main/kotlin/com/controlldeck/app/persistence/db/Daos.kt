package com.controlldeck.app.persistence.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class DashboardWithContents(
    val dashboard: DashboardEntity,
    val widgets: List<WidgetEntity>,
    val groups: List<GroupEntity>,
)

@Dao
interface DashboardDao {
    @Query("SELECT * FROM dashboards ORDER BY name")
    fun observeAll(): Flow<List<DashboardEntity>>

    @Query("SELECT * FROM dashboards WHERE id = :id")
    suspend fun getById(id: String): DashboardEntity?

    @Query("SELECT * FROM widgets WHERE dashboardId = :dashboardId")
    suspend fun getWidgets(dashboardId: String): List<WidgetEntity>

    @Query("SELECT * FROM groups WHERE dashboardId = :dashboardId")
    suspend fun getGroups(dashboardId: String): List<GroupEntity>

    @Transaction
    suspend fun getWithContents(dashboardId: String): DashboardWithContents? {
        val dashboard = getById(dashboardId) ?: return null
        return DashboardWithContents(dashboard, getWidgets(dashboardId), getGroups(dashboardId))
    }

    @Upsert
    suspend fun upsertDashboard(dashboard: DashboardEntity)

    @Query("DELETE FROM widgets WHERE dashboardId = :dashboardId")
    suspend fun clearWidgets(dashboardId: String)

    @Query("DELETE FROM groups WHERE dashboardId = :dashboardId")
    suspend fun clearGroups(dashboardId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWidgets(widgets: List<WidgetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<GroupEntity>)

    /** Replaces a dashboard's full contents (widgets + groups) atomically — used by editor saves and DASHBOARD_SYNC apply. */
    @Transaction
    suspend fun replaceContents(dashboard: DashboardEntity, widgets: List<WidgetEntity>, groups: List<GroupEntity>) {
        upsertDashboard(dashboard)
        clearWidgets(dashboard.id)
        clearGroups(dashboard.id)
        insertWidgets(widgets)
        insertGroups(groups)
    }

    @Query("DELETE FROM dashboards WHERE id = :id")
    suspend fun deleteDashboard(id: String)
}

@Dao
interface PairedDeviceDao {
    @Query("SELECT * FROM paired_devices ORDER BY deviceName")
    fun observeAll(): Flow<List<PairedDeviceEntity>>

    @Query("SELECT * FROM paired_devices WHERE deviceId = :deviceId")
    suspend fun getById(deviceId: String): PairedDeviceEntity?

    @Upsert
    suspend fun upsert(device: PairedDeviceEntity)

    @Delete
    suspend fun delete(device: PairedDeviceEntity)

    @Query("DELETE FROM paired_devices WHERE deviceId = :deviceId")
    suspend fun deleteById(deviceId: String)
}

@Dao
interface AppRegistryDao {
    @Query("SELECT * FROM app_registry ORDER BY displayName")
    fun observeAll(): Flow<List<AppRegistryEntryEntity>>

    @Query("SELECT * FROM app_registry WHERE appId = :appId")
    suspend fun getById(appId: String): AppRegistryEntryEntity?

    @Upsert
    suspend fun upsert(entry: AppRegistryEntryEntity)

    @Query("DELETE FROM app_registry WHERE appId = :appId")
    suspend fun delete(appId: String)

    @Query("SELECT COUNT(*) FROM app_registry")
    suspend fun count(): Int
}
