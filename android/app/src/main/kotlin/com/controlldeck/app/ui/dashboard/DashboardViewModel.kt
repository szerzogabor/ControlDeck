package com.controlldeck.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlldeck.app.action.ActionEngine
import com.controlldeck.app.group.GroupManager
import com.controlldeck.app.state.DeviceStateManager
import com.controlldeck.app.ui.dashboardeditor.DashboardStore
import com.controlldeck.domain.ActionSpec
import com.controlldeck.domain.ConnectionState
import com.controlldeck.domain.DashboardId
import com.controlldeck.domain.DeviceState
import com.controlldeck.domain.Group
import com.controlldeck.domain.GroupUserInput
import com.controlldeck.domain.MediaPlaybackState
import com.controlldeck.domain.Widget
import com.controlldeck.domain.WidgetType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WidgetUiState(
    val widget: Widget,
    val online: Boolean,
    val currentValue: Int?,
    val muted: Boolean?,
    val mediaState: MediaPlaybackState?,
)

data class DashboardUiState(
    val dashboardName: String = "",
    val widgets: List<WidgetUiState> = emptyList(),
    val groups: List<Group> = emptyList(),
)

/**
 * Renders and drives interaction for one dashboard's live view: sliders,
 * mute/media buttons, app-launch tiles. Every user interaction is routed
 * through [ActionEngine] which itself routes grouped widgets through
 * [GroupManager] (docs/ARCHITECTURE.md §4's "UI never computes deltas").
 * Widgets targeting an offline device are surfaced as `online = false`
 * (docs/ARCHITECTURE.md §7) rather than removed from the list.
 */
class DashboardViewModel(
    private val dashboardId: DashboardId,
    private val store: DashboardStore,
    private val deviceStateManager: DeviceStateManager,
    private val groupManager: GroupManager,
    private val actionEngine: ActionEngine,
    private val onLocalEditBroadcast: suspend (com.controlldeck.domain.Dashboard) -> Unit = {},
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(store.observeDashboards(), deviceStateManager.deviceStates) { dashboards, states ->
        val dashboard = dashboards.find { it.id == dashboardId }
        DashboardUiState(
            dashboardName = dashboard?.name.orEmpty(),
            widgets = dashboard?.widgets.orEmpty().map { widget -> widget.toUiState(states) },
            groups = dashboard?.groups.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    fun onSliderChanged(widget: Widget, oldValue: Int, newValue: Int) {
        dispatchInteraction(widget) { group, widgets, states ->
            groupManager.apply(group, widgets, states, GroupUserInput.SliderMoved(widget.id, oldValue, newValue))
        } ?: viewModelScope.launch {
            val action = if (widget.type == WidgetType.SLIDER_BRIGHTNESS) ActionSpec.BrightnessSet(newValue) else ActionSpec.VolumeSet(newValue)
            actionEngine.dispatchSingle(widget.targetDeviceId, action)
        }
    }

    fun onMutePressed(widget: Widget) {
        dispatchInteraction(widget) { group, widgets, states ->
            groupManager.apply(group, widgets, states, GroupUserInput.ToggleActivated(widget.id))
        } ?: viewModelScope.launch {
            val currentlyMuted = deviceStateManager.snapshot(widget.targetDeviceId)?.muted ?: false
            actionEngine.dispatchSingle(widget.targetDeviceId, ActionSpec.SetMuted(!currentlyMuted))
        }
    }

    fun onMediaPlayPause(widget: Widget) {
        dispatchInteraction(widget) { group, widgets, states ->
            groupManager.apply(group, widgets, states, GroupUserInput.MediaToggle(widget.id))
        } ?: viewModelScope.launch {
            val current = deviceStateManager.snapshot(widget.targetDeviceId)?.mediaState
            val desired = if (current == MediaPlaybackState.PLAYING) MediaPlaybackState.PAUSED else MediaPlaybackState.PLAYING
            actionEngine.dispatchSingle(widget.targetDeviceId, ActionSpec.MediaSetState(desired))
        }
    }

    fun onMediaEdge(widget: Widget, action: ActionSpec) {
        dispatchInteraction(widget) { group, widgets, states ->
            groupManager.apply(group, widgets, states, GroupUserInput.MediaEdge(widget.id, action))
        } ?: viewModelScope.launch { actionEngine.dispatchSingle(widget.targetDeviceId, action) }
    }

    fun onAppLaunch(widget: Widget) {
        viewModelScope.launch { actionEngine.dispatchSingle(widget.targetDeviceId, widget.action) }
    }

    /** Returns non-null (and launches dispatch) only if [widget] belongs to a group; null means "caller should dispatch a single action". */
    private fun dispatchInteraction(
        widget: Widget,
        compute: (Group, List<Widget>, List<DeviceState>) -> List<com.controlldeck.domain.GroupDispatch>,
    ): Unit? {
        val dashboardWidgets = uiState.value.widgets.map { it.widget }
        val group = uiState.value.groups.find { widget.id in it.memberWidgetIds } ?: return null
        viewModelScope.launch {
            val states = deviceStateManager.deviceStates.value.values.toList()
            val dispatches = compute(group, dashboardWidgets, states)
            actionEngine.dispatchGroup(dispatches)
        }
        return Unit
    }
}

private fun Widget.toUiState(states: Map<com.controlldeck.domain.DeviceId, DeviceState>): WidgetUiState {
    val state = states[targetDeviceId]
    val value = when (type) {
        WidgetType.SLIDER_BRIGHTNESS -> state?.brightness
        WidgetType.SLIDER_VOLUME -> state?.volume
        else -> null
    }
    return WidgetUiState(
        widget = this,
        online = state?.connection == ConnectionState.ONLINE,
        currentValue = value,
        muted = state?.muted,
        mediaState = state?.mediaState,
    )
}
