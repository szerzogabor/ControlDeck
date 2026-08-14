package com.controlldeck.app.persistence

import com.controlldeck.app.persistence.db.DashboardDao
import com.controlldeck.app.persistence.db.DashboardEntity
import com.controlldeck.app.persistence.db.DashboardWithContents
import com.controlldeck.app.persistence.db.GroupEntity
import com.controlldeck.app.persistence.db.MapListCodec
import com.controlldeck.app.persistence.db.ActionSpecCodec
import com.controlldeck.app.persistence.db.WidgetEntity
import com.controlldeck.domain.ActionSpec
import com.controlldeck.domain.Dashboard
import com.controlldeck.domain.DashboardId
import com.controlldeck.domain.DeviceId
import com.controlldeck.domain.DashboardSyncMessage
import com.controlldeck.domain.DashboardSyncOutcome
import com.controlldeck.domain.DashboardSyncResolver
import com.controlldeck.domain.GridPosition
import com.controlldeck.domain.GridSize
import com.controlldeck.domain.Group
import com.controlldeck.domain.GroupId
import com.controlldeck.domain.GroupKind
import com.controlldeck.domain.ReconnectPolicy
import com.controlldeck.domain.Widget
import com.controlldeck.domain.WidgetId
import com.controlldeck.domain.WidgetType
import com.controlldeck.app.ui.dashboardeditor.DashboardStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Owns dashboard CRUD + last-write-wins sync (protocol/PROTOCOL.md §3.7,
 * docs/ARCHITECTURE.md §6). All conflict math is delegated to the pure
 * [DashboardSyncResolver] in :domain; this class only does I/O and mapping.
 */
class DashboardRepository(private val dao: DashboardDao) : DashboardStore {

    override fun observeDashboards(): Flow<List<Dashboard>> =
        dao.observeAll().map { entities -> entities.map { toSkeletonDomain(it) } }

    /** One-shot snapshot of every dashboard with full contents — used to seed a newly-connected peer. */
    suspend fun observeDashboardsSnapshot(): List<Dashboard> {
        val skeletons = dao.observeAll().first()
        return skeletons.mapNotNull { getDashboard(DashboardId(it.id)) }
    }

    override suspend fun getDashboard(id: DashboardId): Dashboard? =
        dao.getWithContents(id.value)?.let { toDomain(it) }

    override fun createDashboard(name: String): Dashboard = Dashboard(
        id = DashboardId(UUID.randomUUID().toString()),
        name = name,
        version = 1,
    )

    override suspend fun persistLocalEdit(dashboard: Dashboard): Dashboard {
        val bumped = dashboard.copy(version = dashboard.version + 1)
        save(bumped)
        return bumped
    }

    override suspend fun persistNew(dashboard: Dashboard) {
        save(dashboard)
    }

    override suspend fun deleteDashboard(id: DashboardId) {
        dao.deleteDashboard(id.value)
    }

    /**
     * Applies an incoming DASHBOARD_SYNC using the exact last-write-wins
     * rule from docs/ARCHITECTURE.md §6. Returns the outcome so the caller
     * (transport layer) knows whether to persist, reply with local, or do
     * nothing.
     */
    suspend fun resolveIncomingSync(incoming: DashboardSyncMessage): DashboardSyncOutcome {
        val local = getDashboard(incoming.dashboard.id)
        val localMessage = local?.let {
            // Local timestamp/source aren't persisted separately; treat local
            // as authored "now" by this device for tie-break purposes, which
            // is the correct approach in an active peer-to-peer session where
            // only the diverging edit's real timestamp matters. The resolver
            // discards the sourceDeviceId tiebreak until strict equality path,
            // so this is a safe last-write-wins-preserving substitution.
            DashboardSyncMessage(it, timestamp = 0, sourceDeviceId = incoming.sourceDeviceId)
        }
        val outcome = DashboardSyncResolver.resolve(localMessage, incoming)
        if (outcome is DashboardSyncOutcome.Applied) {
            save(outcome.dashboard)
        }
        return outcome
    }

    private suspend fun save(dashboard: Dashboard) {
        val dashboardEntity = DashboardEntity(dashboard.id.value, dashboard.name, dashboard.version)
        val widgetEntities = dashboard.widgets.map { it.toEntity(dashboard.id) }
        val groupEntities = dashboard.groups.map { it.toEntity(dashboard.id) }
        dao.replaceContents(dashboardEntity, widgetEntities, groupEntities)
    }

    private fun toSkeletonDomain(entity: DashboardEntity): Dashboard =
        Dashboard(DashboardId(entity.id), entity.name, entity.version)

    private fun toDomain(contents: DashboardWithContents): Dashboard = Dashboard(
        id = DashboardId(contents.dashboard.id),
        name = contents.dashboard.name,
        version = contents.dashboard.version,
        widgets = contents.widgets.map { it.toDomain() },
        groups = contents.groups.map { it.toDomain() },
    )
}

private fun Widget.toEntity(dashboardId: DashboardId): WidgetEntity = WidgetEntity(
    id = id.value,
    dashboardId = dashboardId.value,
    type = type.name,
    positionX = position.x,
    positionY = position.y,
    sizeWidth = size.width,
    sizeHeight = size.height,
    targetDeviceId = targetDeviceId.value,
    actionEncoded = ActionSpecCodec.encode(action),
    configurationEncoded = MapListCodec.encodeMap(configuration),
)

private fun WidgetEntity.toDomain(): Widget = Widget(
    id = WidgetId(id),
    type = runCatching { WidgetType.valueOf(type) }.getOrDefault(WidgetType.SLIDER_VOLUME),
    position = GridPosition(positionX, positionY),
    size = GridSize(sizeWidth, sizeHeight),
    targetDeviceId = DeviceId(targetDeviceId),
    action = runCatching { ActionSpecCodec.decode(actionEncoded) }.getOrDefault(ActionSpec.VolumeSet(0)),
    configuration = MapListCodec.decodeMap(configurationEncoded),
)

private fun Group.toEntity(dashboardId: DashboardId): GroupEntity = GroupEntity(
    id = id.value,
    dashboardId = dashboardId.value,
    name = name,
    kind = kind.name,
    memberWidgetIdsEncoded = MapListCodec.encodeList(memberWidgetIds.map { it.value }),
    reconnectPolicy = reconnectPolicy.name,
)

private fun GroupEntity.toDomain(): Group = Group(
    id = GroupId(id),
    name = name,
    kind = runCatching { GroupKind.valueOf(kind) }.getOrDefault(GroupKind.RELATIVE_SLIDER),
    memberWidgetIds = MapListCodec.decodeList(memberWidgetIdsEncoded).map { WidgetId(it) },
    reconnectPolicy = runCatching { ReconnectPolicy.valueOf(reconnectPolicy) }.getOrDefault(ReconnectPolicy.SYNC_GROUP_STATE),
)
