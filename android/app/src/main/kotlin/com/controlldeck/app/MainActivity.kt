package com.controlldeck.app

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.controlldeck.app.di.ServiceLocator
import com.controlldeck.app.mapper.ProtocolMappers
import com.controlldeck.app.ui.dashboard.DashboardScreen
import com.controlldeck.app.ui.dashboard.DashboardViewModel
import com.controlldeck.app.ui.dashboardeditor.DashboardEditorScreen
import com.controlldeck.app.ui.dashboardeditor.DashboardEditorViewModel
import com.controlldeck.app.ui.dashboardeditor.TargetableDevice
import com.controlldeck.app.ui.dashboardeditor.WidgetConfigDialog
import com.controlldeck.app.ui.dashboardlist.DashboardListScreen
import com.controlldeck.app.ui.devicelist.DeviceListScreen
import com.controlldeck.app.ui.devicelist.DeviceListViewModel
import com.controlldeck.app.ui.groupeditor.GroupEditorScreen
import com.controlldeck.app.ui.groupeditor.GroupEditorViewModel
import com.controlldeck.app.ui.nav.Screen
import com.controlldeck.app.ui.pairing.PairingScreen
import com.controlldeck.app.ui.pairing.PairingViewModel
import com.controlldeck.app.ui.settings.SettingsScreen
import com.controlldeck.app.ui.settings.SettingsViewModel
import com.controlldeck.app.ui.theme.ControlDeckTheme
import com.controlldeck.domain.GridPosition
import com.controlldeck.domain.Widget
import com.controlldeck.protocol.DashboardSyncPayload
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {

    private val serviceLocator get() = (application as ControlDeckApplication).serviceLocator

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission.launch(android.Manifest.permission.CAMERA)
        }

        setContent {
            ControlDeckTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ControlDeckApp(serviceLocator)
                }
            }
        }
    }
}

/** Broadcasts a local dashboard edit to every connected peer, per docs/ARCHITECTURE.md §6. */
private suspend fun ServiceLocator.broadcastEdit(dashboard: com.controlldeck.domain.Dashboard) {
    connectionManager.broadcastDashboardSync(DashboardSyncPayload(ProtocolMappers.dashboardToWire(dashboard)))
}

@Composable
private fun ControlDeckApp(serviceLocator: ServiceLocator) {
    var screen by remember { mutableStateOf<Screen>(Screen.DeviceList) }
    val scope = rememberCoroutineScope()

    val qrScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents ?: return@rememberLauncherForActivityResult
        val payload = serviceLocator.pairingManager.decodeQrPayload(raw) ?: return@rememberLauncherForActivityResult
        // The QR only carries deviceId/token/port (never an IP, per
        // protocol/PROTOCOL.md §5) — resolve the current LAN host for that
        // deviceId from whatever mDNS has already discovered.
        scope.launch {
            val matchingList = withTimeoutOrNull(8000) {
                serviceLocator.nsdDiscoveryService.discoveredDevices.first { list -> list.any { it.deviceId.value == payload.deviceId } }
            }
            val host = matchingList?.first { it.deviceId.value == payload.deviceId }?.host?.hostAddress
            screen = Screen.Pairing(host = host, port = payload.port, scannedQr = payload)
        }
    }

    val dashboardEditorViewModel: DashboardEditorViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DashboardEditorViewModel(serviceLocator.dashboardRepository) { dashboard -> serviceLocator.broadcastEdit(dashboard) } }
        },
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = screen is Screen.DeviceList,
                    onClick = { screen = Screen.DeviceList },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Devices") },
                    label = { Text("Devices") },
                )
                NavigationBarItem(
                    selected = screen is Screen.DashboardList || screen is Screen.Dashboard || screen is Screen.DashboardEditor || screen is Screen.GroupEditor,
                    onClick = { screen = Screen.DashboardList },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Dashboards") },
                    label = { Text("Dashboards") },
                )
                NavigationBarItem(
                    selected = screen is Screen.Settings,
                    onClick = { screen = Screen.Settings },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                )
            }
        },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val current = screen) {
                is Screen.DeviceList -> {
                    val deviceListViewModel: DeviceListViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer { DeviceListViewModel(serviceLocator.pairedDeviceRepository, serviceLocator.nsdDiscoveryService, serviceLocator.deviceStateManager) }
                        },
                    )
                    DeviceListScreen(
                        viewModel = deviceListViewModel,
                        onPairWithDiscovered = { device -> screen = Screen.Pairing(device.host.hostAddress, device.port) },
                        onScanQr = { qrScanLauncher.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false)) },
                        onEnterPin = { screen = Screen.Pairing(null, 0) },
                        onForget = { deviceListViewModel.forgetDevice(it) },
                    )
                }

                is Screen.DashboardList -> DashboardListScreen(
                    viewModel = dashboardEditorViewModel,
                    onOpenDashboard = { id -> screen = Screen.Dashboard(id) },
                    onEditDashboard = { id -> screen = Screen.DashboardEditor(id) },
                )

                is Screen.Dashboard -> {
                    val dashboardViewModel: DashboardViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                DashboardViewModel(
                                    current.dashboardId,
                                    serviceLocator.dashboardRepository,
                                    serviceLocator.deviceStateManager,
                                    serviceLocator.groupManager,
                                    serviceLocator.actionEngine,
                                )
                            }
                        },
                    )
                    DashboardScreen(dashboardViewModel)
                }

                is Screen.DashboardEditor -> {
                    var showWidgetDialog by remember { mutableStateOf(false) }
                    var editingWidget by remember { mutableStateOf<Widget?>(null) }
                    androidx.compose.runtime.LaunchedEffect(current.dashboardId) { dashboardEditorViewModel.switchTo(current.dashboardId) }
                    val editorState by dashboardEditorViewModel.uiState.collectAsState()
                    val pairedDevices by serviceLocator.pairedDeviceRepository.observePairedDevices().collectAsState(initial = emptyList())

                    DashboardEditorScreen(
                        viewModel = dashboardEditorViewModel,
                        onAddWidget = { editingWidget = null; showWidgetDialog = true },
                        onConfigureWidget = { widget -> editingWidget = widget; showWidgetDialog = true },
                        onManageGroups = { screen = Screen.GroupEditor(current.dashboardId) },
                    )

                    if (showWidgetDialog) {
                        val targetable = buildList {
                            add(TargetableDevice(serviceLocator.selfIdentity.deviceId, "${serviceLocator.selfIdentity.deviceName} (this device)"))
                            pairedDevices.forEach { add(TargetableDevice(it.deviceId, it.deviceName)) }
                        }
                        WidgetConfigDialog(
                            availableDevices = targetable,
                            existing = editingWidget,
                            nextGridPosition = GridPosition(0, editorState.selectedDashboard?.widgets?.size ?: 0),
                            onDismiss = { showWidgetDialog = false },
                            onSave = { widget ->
                                if (editingWidget == null) dashboardEditorViewModel.addWidget(widget) else dashboardEditorViewModel.replaceWidget(widget)
                                showWidgetDialog = false
                            },
                        )
                    }
                }

                is Screen.GroupEditor -> {
                    val groupEditorViewModel: GroupEditorViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                GroupEditorViewModel(serviceLocator.dashboardRepository, current.dashboardId) { dashboard -> serviceLocator.broadcastEdit(dashboard) }
                            }
                        },
                    )
                    GroupEditorScreen(groupEditorViewModel)
                }

                is Screen.Pairing -> {
                    val pairingViewModel: PairingViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                PairingViewModel(
                                    serviceLocator.pairingManager,
                                    serviceLocator.connectionManager,
                                    serviceLocator.selfIdentity.deviceId.value,
                                    serviceLocator.connectionManager.boundPort,
                                )
                            }
                        },
                    )
                    PairingScreen(
                        viewModel = pairingViewModel,
                        targetHost = current.host,
                        targetPort = current.port,
                        scannedQr = current.scannedQr,
                        onDone = { screen = Screen.DeviceList },
                    )
                }

                is Screen.Settings -> {
                    val settingsViewModel: SettingsViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer { SettingsViewModel(serviceLocator.deviceIdentityRepository, serviceLocator.userPreferencesRepository, serviceLocator.appRegistryRepository) }
                        },
                    )
                    SettingsScreen(settingsViewModel)
                }
            }
        }
    }
}
