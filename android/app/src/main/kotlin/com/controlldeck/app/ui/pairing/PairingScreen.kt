package com.controlldeck.app.ui.pairing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.controlldeck.app.pairing.QrCodeUtil
import com.controlldeck.app.pairing.QrPairingPayload

/**
 * Pairing UI: shows this device's own QR/PIN for a peer to scan/enter, or
 * lets the user enter a PIN scanned/typed from a peer that's displaying
 * one. [targetHost]/[targetPort] identify the specific discovered peer
 * this screen was opened for (picked on the device list screen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    viewModel: PairingViewModel,
    targetHost: String?,
    targetPort: Int,
    scannedQr: QrPairingPayload? = null,
    onDone: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(scannedQr, targetHost) {
        if (scannedQr != null && targetHost != null) {
            viewModel.connectWithQrPayload(scannedQr, targetHost)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Pair device") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.connecting) {
                Text("Connecting...", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
            }
            when (state.mode) {
                PairingMode.IDLE -> IdleContent(viewModel)
                PairingMode.SHOWING_PIN -> PinContent(state)
                PairingMode.SHOWING_QR -> QrContent(viewModel, state)
                PairingMode.ENTERING_PIN -> EnterPinContent(viewModel, state, targetHost, targetPort)
                PairingMode.EXPIRED -> ExpiredContent(viewModel)
            }

            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(onClick = { viewModel.cancel(); onDone() }) { Text("Close") }
        }
    }
}

@Composable
private fun IdleContent(viewModel: PairingViewModel) {
    Text("Pair with another ControlDeck device", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = { viewModel.startPinPairing() }, modifier = Modifier.fillMaxWidth()) { Text("Show my PIN") }
    Spacer(modifier = Modifier.height(8.dp))
    Button(onClick = { viewModel.startQrPairing() }, modifier = Modifier.fillMaxWidth()) { Text("Show my QR code") }
    Spacer(modifier = Modifier.height(8.dp))
    Button(onClick = { viewModel.beginEnteringPin() }, modifier = Modifier.fillMaxWidth()) { Text("Enter a peer's PIN") }
}

@Composable
private fun PinContent(state: PairingUiState) {
    Text("Enter this PIN on the other device", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(16.dp))
    Text(state.pin ?: "------", style = MaterialTheme.typography.displayMedium)
    Spacer(modifier = Modifier.height(8.dp))
    Text("Expires in ${state.remainingSeconds}s", style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun QrContent(viewModel: PairingViewModel, state: PairingUiState) {
    Text("Scan this code on the other device", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(16.dp))
    val payload = state.qrPayload
    if (payload != null) {
        val content = remember(payload) { viewModel.encodeQrContent(payload) }
        val bitmap = remember(content) { QrCodeUtil.encodeToBitmap(content) }
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Pairing QR code", modifier = Modifier.size(256.dp))
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text("Expires in ${state.remainingSeconds}s", style = MaterialTheme.typography.bodyMedium)
}

/** Default ControlDeck WebSocket port, per protocol/PROTOCOL.md §1. */
private const val DEFAULT_PORT = "47531"

@Composable
private fun EnterPinContent(viewModel: PairingViewModel, state: PairingUiState, host: String?, port: Int) {
    // host/port are only pre-filled when pairing was started by tapping a
    // specific discovered device row. Entered generically (e.g. the device
    // list's standalone "Enter PIN" button), neither is known yet, so the
    // user must supply the peer's address themselves.
    var manualHost by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf(if (port > 0) port.toString() else DEFAULT_PORT) }

    Text("Enter the peer's 6-digit PIN", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(16.dp))
    if (host == null) {
        OutlinedTextField(
            value = manualHost,
            onValueChange = { manualHost = it },
            label = { Text("Peer IP address") },
            isError = state.error != null,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = manualPort,
            onValueChange = { manualPort = it.filter(Char::isDigit).take(5) },
            label = { Text("Port") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
    OutlinedTextField(
        value = state.enteredPin,
        onValueChange = { viewModel.updateEnteredPin(it) },
        label = { Text("PIN") },
        isError = state.error != null,
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.error != null) {
        Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(modifier = Modifier.height(16.dp))

    val resolvedHost = host ?: manualHost.trim().ifBlank { null }
    val resolvedPort = if (host != null) port else manualPort.toIntOrNull()

    Button(
        onClick = { if (resolvedHost != null && resolvedPort != null) viewModel.submitEnteredPin(resolvedHost, resolvedPort) },
        enabled = viewModel.isEnteredPinValidFormat() && resolvedHost != null && resolvedPort != null && !state.connecting,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (state.connecting) "Connecting..." else "Connect")
    }
}

@Composable
private fun ExpiredContent(viewModel: PairingViewModel) {
    Text("Pairing window expired", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = { viewModel.startPinPairing() }) { Text("Try again") }
}
