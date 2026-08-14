package com.controlldeck.app.ui.nav

import com.controlldeck.app.pairing.QrPairingPayload
import com.controlldeck.domain.DashboardId

/** Simple, explicit navigation state — no NavHost needed for this MVP's screen count. */
sealed class Screen {
    data object DeviceList : Screen()
    data object DashboardList : Screen()
    data class DashboardEditor(val dashboardId: DashboardId) : Screen()
    data class GroupEditor(val dashboardId: DashboardId) : Screen()
    data class Dashboard(val dashboardId: DashboardId) : Screen()
    data class Pairing(val host: String?, val port: Int, val scannedQr: QrPairingPayload? = null) : Screen()
    data object Settings : Screen()
}
