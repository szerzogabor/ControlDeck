package com.controlldeck.app.mapper

import com.controlldeck.domain.ActionSpec
import com.controlldeck.domain.AppId
import com.controlldeck.domain.AppRegistryEntry
import com.controlldeck.domain.Capability
import com.controlldeck.domain.Dashboard
import com.controlldeck.domain.DashboardId
import com.controlldeck.domain.DeviceId
import com.controlldeck.domain.GridPosition
import com.controlldeck.domain.GridSize
import com.controlldeck.domain.Group
import com.controlldeck.domain.GroupId
import com.controlldeck.domain.GroupKind
import com.controlldeck.domain.MediaPlaybackState
import com.controlldeck.domain.Platform
import com.controlldeck.domain.ReconnectPolicy
import com.controlldeck.domain.Widget
import com.controlldeck.domain.WidgetId
import com.controlldeck.domain.WidgetType
import com.controlldeck.protocol.ActionDto
import com.controlldeck.protocol.AppRegistryEntryDto
import com.controlldeck.protocol.CapabilityValues
import com.controlldeck.protocol.DashboardDto
import com.controlldeck.protocol.GridPositionDto
import com.controlldeck.protocol.GridSizeDto
import com.controlldeck.protocol.GroupDto
import com.controlldeck.protocol.MediaStateValues
import com.controlldeck.protocol.WidgetDto

/**
 * Bidirectional mapping between :domain models and :protocol wire DTOs.
 * Kept out of both lower modules per docs/ARCHITECTURE.md's "no
 * cross-module leakage" principle — :protocol never depends on :domain.
 */
object ProtocolMappers {

    // ---- Platform ----

    fun platformToWire(platform: Platform): String = platform.name

    fun platformFromWire(value: String): Platform = runCatching { Platform.valueOf(value) }.getOrDefault(Platform.ANDROID)

    // ---- Capability ----

    fun capabilityToWire(capability: Capability): String = capability.name

    /** Unknown wire capability strings are silently dropped, per protocol/PROTOCOL.md §3.4/§6. */
    fun capabilitiesFromWire(values: List<String>): Set<Capability> =
        values.filter { it in CapabilityValues.known }.mapNotNull { runCatching { Capability.valueOf(it) }.getOrNull() }.toSet()

    // ---- ActionSpec <-> ActionDto ----

    fun actionToWire(action: ActionSpec): ActionDto = when (action) {
        is ActionSpec.BrightnessSet -> ActionDto.BrightnessSet(action.value)
        is ActionSpec.VolumeSet -> ActionDto.VolumeSet(action.value)
        is ActionSpec.SetMuted -> ActionDto.SetMuted(action.muted)
        is ActionSpec.MediaSetState -> ActionDto.MediaSetState(mediaStateToWire(action.state))
        ActionSpec.MediaNext -> ActionDto.MediaNext
        ActionSpec.MediaPrevious -> ActionDto.MediaPrevious
        is ActionSpec.AppLaunch -> ActionDto.AppLaunch(action.appId.value)
    }

    /** Returns null for a DTO this device doesn't understand (forward-compat safe default). */
    fun actionFromWire(dto: ActionDto): ActionSpec? = when (dto) {
        is ActionDto.BrightnessSet -> ActionSpec.BrightnessSet(dto.value)
        is ActionDto.VolumeSet -> ActionSpec.VolumeSet(dto.value)
        is ActionDto.SetMuted -> ActionSpec.SetMuted(dto.muted)
        is ActionDto.MediaSetState -> mediaStateFromWire(dto.state)?.let { ActionSpec.MediaSetState(it) }
        ActionDto.MediaNext -> ActionSpec.MediaNext
        ActionDto.MediaPrevious -> ActionSpec.MediaPrevious
        is ActionDto.AppLaunch -> ActionSpec.AppLaunch(AppId(dto.appId))
    }

    fun mediaStateToWire(state: MediaPlaybackState): String = when (state) {
        MediaPlaybackState.PLAYING -> MediaStateValues.PLAYING
        MediaPlaybackState.PAUSED -> MediaStateValues.PAUSED
    }

    fun mediaStateFromWire(value: String): MediaPlaybackState? = when (value) {
        MediaStateValues.PLAYING -> MediaPlaybackState.PLAYING
        MediaStateValues.PAUSED -> MediaPlaybackState.PAUSED
        else -> null
    }

    // ---- AppRegistryEntry ----

    fun appEntryToWire(entry: AppRegistryEntry): AppRegistryEntryDto = AppRegistryEntryDto(entry.appId.value, entry.displayName)

    // ---- Widget / Group / Dashboard <-> DTOs ----

    fun widgetToWire(widget: Widget): WidgetDto = WidgetDto(
        id = widget.id.value,
        type = widget.type.name,
        position = GridPositionDto(widget.position.x, widget.position.y),
        size = GridSizeDto(widget.size.width, widget.size.height),
        targetDeviceId = widget.targetDeviceId.value,
        action = actionToWire(widget.action),
        configuration = widget.configuration,
    )

    fun widgetFromWire(dto: WidgetDto): Widget? {
        val type = runCatching { WidgetType.valueOf(dto.type) }.getOrNull() ?: return null
        val action = actionFromWire(dto.action) ?: return null
        return Widget(
            id = WidgetId(dto.id),
            type = type,
            position = GridPosition(dto.position.x, dto.position.y),
            size = GridSize(dto.size.width, dto.size.height),
            targetDeviceId = DeviceId(dto.targetDeviceId),
            action = action,
            configuration = dto.configuration,
        )
    }

    fun groupToWire(group: Group): GroupDto = GroupDto(
        id = group.id.value,
        name = group.name,
        kind = group.kind.name,
        memberWidgetIds = group.memberWidgetIds.map { it.value },
        reconnectPolicy = group.reconnectPolicy.name,
    )

    fun groupFromWire(dto: GroupDto): Group? {
        val kind = runCatching { GroupKind.valueOf(dto.kind) }.getOrNull() ?: return null
        val policy = runCatching { ReconnectPolicy.valueOf(dto.reconnectPolicy) }.getOrDefault(ReconnectPolicy.SYNC_GROUP_STATE)
        return Group(
            id = GroupId(dto.id),
            name = dto.name,
            kind = kind,
            memberWidgetIds = dto.memberWidgetIds.map { WidgetId(it) },
            reconnectPolicy = policy,
        )
    }

    fun dashboardToWire(dashboard: Dashboard): DashboardDto = DashboardDto(
        id = dashboard.id.value,
        name = dashboard.name,
        version = dashboard.version,
        widgets = dashboard.widgets.map { widgetToWire(it) },
        groups = dashboard.groups.map { groupToWire(it) },
    )

    fun dashboardFromWire(dto: DashboardDto): Dashboard = Dashboard(
        id = DashboardId(dto.id),
        name = dto.name,
        version = dto.version,
        widgets = dto.widgets.mapNotNull { widgetFromWire(it) },
        groups = dto.groups.mapNotNull { groupFromWire(it) },
    )
}
