package com.controlldeck.app.ui.dashboardeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlldeck.domain.Dashboard
import com.controlldeck.domain.DashboardId
import com.controlldeck.domain.GridPosition
import com.controlldeck.domain.GridSize
import com.controlldeck.domain.Widget
import com.controlldeck.domain.WidgetId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardEditorUiState(
    val dashboards: List<Dashboard> = emptyList(),
    val selectedDashboardId: DashboardId? = null,
    val error: String? = null,
) {
    val selectedDashboard: Dashboard? get() = dashboards.find { it.id == selectedDashboardId }
}

/**
 * Owns dashboard list CRUD + which dashboard is currently selected/being
 * edited, and the grid-editing operations (add/remove/move/resize widget)
 * on the selected dashboard. Broadcasts every local edit via
 * [onLocalEditBroadcast] (wired to
 * [com.controlldeck.app.sync.DashboardSyncCoordinator.broadcastLocalEdit]
 * in production) per docs/ARCHITECTURE.md §6.
 *
 * Depends only on [DashboardStore] (not the concrete Room-backed
 * repository), so it is unit-testable against an in-memory fake without
 * an Android SDK/Robolectric.
 */
class DashboardEditorViewModel(
    private val store: DashboardStore,
    private val onLocalEditBroadcast: suspend (Dashboard) -> Unit = {},
) : ViewModel() {

    private val _selectedId = MutableStateFlow<DashboardId?>(null)
    private val _error = MutableStateFlow<String?>(null)

    // Eagerly (not WhileSubscribed): this state must reflect store writes made by
    // createDashboard/addWidget/etc immediately, including from callers (and unit
    // tests) that read `.value` without an active Compose collector.
    val uiState: StateFlow<DashboardEditorUiState> = combine(store.observeDashboards(), _selectedId, _error) { dashboards, selectedId, error ->
        DashboardEditorUiState(dashboards, selectedId, error)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DashboardEditorUiState())

    // ---- List CRUD / switch ----

    fun createDashboard(name: String) {
        if (name.isBlank()) {
            _error.value = "Dashboard name cannot be empty"
            return
        }
        viewModelScope.launch {
            val dashboard = store.createDashboard(name.trim())
            store.persistNew(dashboard)
            _selectedId.value = dashboard.id
            _error.value = null
        }
    }

    fun renameDashboard(id: DashboardId, newName: String) {
        if (newName.isBlank()) {
            _error.value = "Dashboard name cannot be empty"
            return
        }
        viewModelScope.launch {
            val current = store.getDashboard(id) ?: return@launch
            val edited = store.persistLocalEdit(current.copy(name = newName.trim()))
            onLocalEditBroadcast(edited)
        }
    }

    fun deleteDashboard(id: DashboardId) {
        viewModelScope.launch {
            store.deleteDashboard(id)
            if (_selectedId.value == id) _selectedId.value = null
        }
    }

    fun switchTo(id: DashboardId) {
        _selectedId.value = id
    }

    // ---- Grid editing on the selected dashboard ----

    fun addWidget(widget: Widget) = editSelected { it.copy(widgets = it.widgets + widget) }

    fun removeWidget(widgetId: WidgetId) = editSelected { dashboard ->
        dashboard.copy(
            widgets = dashboard.widgets.filterNot { it.id == widgetId },
            groups = dashboard.groups.map { it.copy(memberWidgetIds = it.memberWidgetIds.filterNot { id -> id == widgetId }) }
                .filter { it.memberWidgetIds.isNotEmpty() },
        )
    }

    fun moveWidget(widgetId: WidgetId, newPosition: GridPosition) = editSelected { dashboard ->
        dashboard.copy(widgets = dashboard.widgets.map { if (it.id == widgetId) it.copy(position = newPosition) else it })
    }

    fun resizeWidget(widgetId: WidgetId, newSize: GridSize) = editSelected { dashboard ->
        dashboard.copy(widgets = dashboard.widgets.map { if (it.id == widgetId) it.copy(size = newSize) else it })
    }

    fun replaceWidget(updated: Widget) = editSelected { dashboard ->
        dashboard.copy(widgets = dashboard.widgets.map { if (it.id == updated.id) updated else it })
    }

    private fun editSelected(transform: (Dashboard) -> Dashboard) {
        val current = uiState.value.selectedDashboard ?: return
        viewModelScope.launch {
            val edited = store.persistLocalEdit(transform(current))
            onLocalEditBroadcast(edited)
        }
    }
}
