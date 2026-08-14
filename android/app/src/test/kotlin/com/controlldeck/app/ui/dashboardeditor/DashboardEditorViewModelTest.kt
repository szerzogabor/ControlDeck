package com.controlldeck.app.ui.dashboardeditor

import com.controlldeck.domain.Dashboard
import com.controlldeck.domain.DashboardId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * In-memory fake standing in for the Room-backed
 * [com.controlldeck.app.persistence.DashboardRepository], which needs an
 * Android Context and is not JVM-unit-testable in this environment. This
 * fake conforms to the exact same [DashboardStore] interface the real
 * repository implements, so [DashboardEditorViewModel] under test is
 * exercised through the identical seam production code uses.
 */
private class FakeDashboardStore : DashboardStore {
    private val state = MutableStateFlow<List<Dashboard>>(emptyList())

    override fun observeDashboards(): StateFlow<List<Dashboard>> = state

    override suspend fun getDashboard(id: DashboardId): Dashboard? = state.value.find { it.id == id }

    override fun createDashboard(name: String): Dashboard = Dashboard(DashboardId(UUID.randomUUID().toString()), name, version = 1)

    override suspend fun persistNew(dashboard: Dashboard) {
        state.value = state.value + dashboard
    }

    override suspend fun persistLocalEdit(dashboard: Dashboard): Dashboard {
        val bumped = dashboard.copy(version = dashboard.version + 1)
        state.value = state.value.map { if (it.id == bumped.id) bumped else it }
        return bumped
    }

    override suspend fun deleteDashboard(id: DashboardId) {
        state.value = state.value.filterNot { it.id == id }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    // Passed directly as the ViewModel's coroutineScope instead of relying on
    // Dispatchers.setMain + viewModelScope: viewModelScope's job is independent
    // of runTest's TestScope job, so a Dispatchers.Main-routed
    // stateIn(..., SharingStarted.Eagerly, ...) collector is never guaranteed to
    // share the same TestCoroutineScheduler as testScheduler.advanceUntilIdle()
    // below — that mismatch previously hung this test run in CI. Using the same
    // TestScope(dispatcher) instance for both eliminates the ambiguity.
    private val testScope = TestScope(dispatcher)
    private lateinit var store: FakeDashboardStore
    private lateinit var viewModel: DashboardEditorViewModel
    private val broadcasted = mutableListOf<Dashboard>()

    @BeforeEach
    fun setUp() {
        store = FakeDashboardStore()
        broadcasted.clear()
        viewModel = DashboardEditorViewModel(store, onLocalEditBroadcast = { dashboard -> broadcasted.add(dashboard) }, coroutineScope = testScope)
    }

    @Test
    fun `create adds a new dashboard and selects it`() = runTest(dispatcher) {
        viewModel.createDashboard("Gaming")
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.dashboards.size)
        assertEquals("Gaming", state.dashboards.single().name)
        assertEquals(state.dashboards.single().id, state.selectedDashboardId)
    }

    @Test
    fun `create with blank name sets an error and does not create`() = runTest(dispatcher) {
        viewModel.createDashboard("   ")
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.dashboards.isEmpty())
        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun `rename updates the dashboard name and bumps version, then broadcasts`() = runTest(dispatcher) {
        viewModel.createDashboard("Gaming")
        testScheduler.advanceUntilIdle()
        val id = viewModel.uiState.value.dashboards.single().id

        viewModel.renameDashboard(id, "Office")
        testScheduler.advanceUntilIdle()

        val renamed = viewModel.uiState.value.dashboards.single()
        assertEquals("Office", renamed.name)
        assertEquals(2, renamed.version) // v1 on create, bumped to v2 on rename
        assertEquals(1, broadcasted.size)
        assertEquals("Office", broadcasted.single().name)
    }

    @Test
    fun `delete removes the dashboard and clears selection if it was selected`() = runTest(dispatcher) {
        viewModel.createDashboard("Gaming")
        testScheduler.advanceUntilIdle()
        val id = viewModel.uiState.value.dashboards.single().id

        viewModel.deleteDashboard(id)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.dashboards.isEmpty())
        assertNull(viewModel.uiState.value.selectedDashboardId)
    }

    @Test
    fun `switchTo changes the selected dashboard without mutating any dashboard`() = runTest(dispatcher) {
        viewModel.createDashboard("Gaming")
        testScheduler.advanceUntilIdle()
        viewModel.createDashboard("Office")
        testScheduler.advanceUntilIdle()

        val gaming = viewModel.uiState.value.dashboards.find { it.name == "Gaming" }!!
        viewModel.switchTo(gaming.id)

        assertEquals(gaming.id, viewModel.uiState.value.selectedDashboardId)
        assertEquals("Gaming", viewModel.uiState.value.selectedDashboard?.name)
        assertEquals(2, viewModel.uiState.value.dashboards.size)
    }

    @Test
    fun `addWidget on the selected dashboard persists and broadcasts an edit`() = runTest(dispatcher) {
        viewModel.createDashboard("Gaming")
        testScheduler.advanceUntilIdle()
        val widget = com.controlldeck.domain.Widget(
            id = com.controlldeck.domain.WidgetId("w1"),
            type = com.controlldeck.domain.WidgetType.SLIDER_VOLUME,
            position = com.controlldeck.domain.GridPosition(0, 0),
            size = com.controlldeck.domain.GridSize(1, 1),
            targetDeviceId = com.controlldeck.domain.DeviceId("dev1"),
            action = com.controlldeck.domain.ActionSpec.VolumeSet(50),
        )

        viewModel.addWidget(widget)
        testScheduler.advanceUntilIdle()

        val dashboard = viewModel.uiState.value.selectedDashboard!!
        assertEquals(1, dashboard.widgets.size)
        assertEquals(2, dashboard.version)
        assertEquals(1, broadcasted.size)
    }
}
