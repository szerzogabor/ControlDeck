package com.controlldeck.domain

/** Position of a widget on the dashboard grid. */
data class GridPosition(val x: Int, val y: Int)

/** Size of a widget on the dashboard grid, in grid cells. */
data class GridSize(val width: Int, val height: Int)

/**
 * A single control on a dashboard. docs/ARCHITECTURE.md §3.
 *
 * [action] mirrors the ActionSpec this widget issues when interacted with
 * directly (i.e. when it is not a member of a group, or is the origin of a
 * group interaction). [configuration] holds free-form widget-specific
 * settings such as a display label.
 */
data class Widget(
    val id: WidgetId,
    val type: WidgetType,
    val position: GridPosition,
    val size: GridSize,
    val targetDeviceId: DeviceId,
    val action: ActionSpec,
    val configuration: Map<String, String> = emptyMap(),
)

/**
 * A set of widgets whose actions are dispatched together per one of the
 * [GroupKind] algorithms in docs/ARCHITECTURE.md §4.
 */
data class Group(
    val id: GroupId,
    val name: String,
    val kind: GroupKind,
    val memberWidgetIds: List<WidgetId>,
    val reconnectPolicy: ReconnectPolicy = ReconnectPolicy.SYNC_GROUP_STATE,
)

/** A named, versioned collection of widgets and groups. */
data class Dashboard(
    val id: DashboardId,
    val name: String,
    val version: Long,
    val widgets: List<Widget> = emptyList(),
    val groups: List<Group> = emptyList(),
)

/** Last-known state of a device, as tracked locally by the State Manager. */
data class DeviceState(
    val deviceId: DeviceId,
    val connection: ConnectionState,
    val brightness: Int? = null,
    val volume: Int? = null,
    val muted: Boolean? = null,
    val mediaState: MediaPlaybackState? = null,
)

/** A device's advertised set of capabilities, as tracked by the Capability Registry. */
data class DeviceCapabilities(
    val deviceId: DeviceId,
    val capabilities: Set<Capability>,
    val apps: List<AppRegistryEntry> = emptyList(),
)

data class AppRegistryEntry(val appId: AppId, val displayName: String)
