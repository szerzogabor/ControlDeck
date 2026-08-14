package com.controlldeck.app.ui.groupeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlldeck.app.ui.dashboardeditor.DashboardStore
import com.controlldeck.domain.DashboardId
import com.controlldeck.domain.Group
import com.controlldeck.domain.GroupId
import com.controlldeck.domain.GroupKind
import com.controlldeck.domain.ReconnectPolicy
import com.controlldeck.domain.Widget
import com.controlldeck.domain.WidgetId
import com.controlldeck.domain.WidgetType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class GroupEditorUiState(
    val widgets: List<Widget> = emptyList(),
    val groups: List<Group> = emptyList(),
    val selectedWidgetIds: Set<WidgetId> = emptySet(),
    val error: String? = null,
)

/** Which [GroupKind] applies to a set of widgets, or null if they're not group-compatible (mixed action types). */
fun inferGroupKind(widgets: List<Widget>): GroupKind? {
    if (widgets.isEmpty()) return null
    val types = widgets.map { it.type }.toSet()
    return when {
        types.all { it == WidgetType.SLIDER_BRIGHTNESS } || types.all { it == WidgetType.SLIDER_VOLUME } -> GroupKind.RELATIVE_SLIDER
        types.all { it == WidgetType.BUTTON_MUTE } -> GroupKind.ABSOLUTE_TOGGLE
        types.all { it in setOf(WidgetType.BUTTON_MEDIA_PLAY_PAUSE, WidgetType.BUTTON_MEDIA_NEXT, WidgetType.BUTTON_MEDIA_PREVIOUS) } -> GroupKind.ABSOLUTE_MEDIA
        else -> null
    }
}

/** Group management: create a group from selected widgets, pick [GroupKind] (auto-inferred), set [ReconnectPolicy]. */
class GroupEditorViewModel(
    private val store: DashboardStore,
    private val dashboardId: DashboardId,
    private val onLocalEditBroadcast: suspend (com.controlldeck.domain.Dashboard) -> Unit = {},
) : ViewModel() {

    private val _selectedWidgetIds = MutableStateFlow<Set<WidgetId>>(emptySet())
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<GroupEditorUiState> = store.observeDashboards()
        .map { dashboards -> dashboards.find { it.id == dashboardId } }
        .map { dashboard ->
            GroupEditorUiState(
                widgets = dashboard?.widgets.orEmpty(),
                groups = dashboard?.groups.orEmpty(),
                selectedWidgetIds = _selectedWidgetIds.value,
                error = _error.value,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GroupEditorUiState())

    fun toggleWidgetSelected(widgetId: WidgetId) {
        _selectedWidgetIds.value = if (widgetId in _selectedWidgetIds.value) {
            _selectedWidgetIds.value - widgetId
        } else {
            _selectedWidgetIds.value + widgetId
        }
    }

    fun createGroup(name: String, reconnectPolicy: ReconnectPolicy) {
        val selectedIds = _selectedWidgetIds.value
        if (selectedIds.size < 2) {
            _error.value = "Select at least two widgets to form a group"
            return
        }
        viewModelScope.launch {
            val dashboard = store.getDashboard(dashboardId) ?: return@launch
            val selectedWidgets = dashboard.widgets.filter { it.id in selectedIds }
            val kind = inferGroupKind(selectedWidgets)
            if (kind == null) {
                _error.value = "Selected widgets must share a compatible action type"
                return@launch
            }
            val group = Group(GroupId(UUID.randomUUID().toString()), name, kind, selectedIds.toList(), reconnectPolicy)
            val edited = store.persistLocalEdit(dashboard.copy(groups = dashboard.groups + group))
            onLocalEditBroadcast(edited)
            _selectedWidgetIds.value = emptySet()
            _error.value = null
        }
    }

    fun deleteGroup(groupId: GroupId) {
        viewModelScope.launch {
            val dashboard = store.getDashboard(dashboardId) ?: return@launch
            val edited = store.persistLocalEdit(dashboard.copy(groups = dashboard.groups.filterNot { it.id == groupId }))
            onLocalEditBroadcast(edited)
        }
    }

    fun setReconnectPolicy(groupId: GroupId, policy: ReconnectPolicy) {
        viewModelScope.launch {
            val dashboard = store.getDashboard(dashboardId) ?: return@launch
            val edited = store.persistLocalEdit(
                dashboard.copy(groups = dashboard.groups.map { if (it.id == groupId) it.copy(reconnectPolicy = policy) else it }),
            )
            onLocalEditBroadcast(edited)
        }
    }
}
