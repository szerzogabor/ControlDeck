package com.controlldeck.app.ui.dashboardeditor

import com.controlldeck.domain.Dashboard
import com.controlldeck.domain.DashboardId
import kotlinx.coroutines.flow.Flow

/**
 * What [DashboardEditorViewModel] needs from persistence, abstracted so
 * tests can supply an in-memory fake instead of the real Room-backed
 * [com.controlldeck.app.persistence.DashboardRepository] (which needs an
 * Android Context and is not JVM-unit-testable in this environment).
 * The real repository's method signatures already match this shape
 * exactly.
 */
interface DashboardStore {
    fun observeDashboards(): Flow<List<Dashboard>>
    suspend fun getDashboard(id: DashboardId): Dashboard?
    fun createDashboard(name: String): Dashboard
    suspend fun persistNew(dashboard: Dashboard)
    suspend fun persistLocalEdit(dashboard: Dashboard): Dashboard
    suspend fun deleteDashboard(id: DashboardId)
}
