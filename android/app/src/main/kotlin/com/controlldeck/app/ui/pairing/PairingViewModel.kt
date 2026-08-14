package com.controlldeck.app.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlldeck.app.pairing.QrPairingPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long a generated PIN/QR pairing token stays valid, per protocol/PROTOCOL.md §3.2. */
const val PAIRING_WINDOW_MS = 120_000L

enum class PairingMode { IDLE, SHOWING_PIN, SHOWING_QR, ENTERING_PIN, EXPIRED }

data class PairingUiState(
    val mode: PairingMode = PairingMode.IDLE,
    val pin: String? = null,
    val qrPayload: QrPairingPayload? = null,
    val remainingSeconds: Long = 0,
    val enteredPin: String = "",
    val error: String? = null,
    val connecting: Boolean = false,
)

/**
 * Abstraction over what [PairingViewModel] needs from the transport layer,
 * so tests can supply a fake instead of a real Ktor-backed
 * [com.controlldeck.app.transport.ConnectionManager] (which needs an
 * Android Context and is not JVM-unit-testable in this environment).
 */
interface PairingConnector {
    fun connectForPairing(host: String, port: Int, pairingToken: String)
}

/**
 * Abstraction over PIN/QR token generation, matching
 * [com.controlldeck.app.pairing.PairingManager]'s public surface. That
 * class itself is pure JVM (java.security only) so real instances are
 * already unit-testable; this interface exists so [PairingViewModel] can
 * also be tested with a fully deterministic fake (fixed PIN/expiry).
 */
interface PairingTokenSource {
    fun startPinPairing(): String
    fun startQrPairing(deviceId: String, port: Int): QrPairingPayload
    fun cancelPairingWindow()
    fun remainingValiditySeconds(): Long
    fun encodeQrPayload(payload: QrPairingPayload): String
}

/**
 * Drives the pairing screen's state machine: generating/displaying a
 * PIN or QR, validating a manually-entered PIN's *format* (not the
 * cryptographic match, which only the acceptor's [PairingManager] can
 * verify), and expiring the window after [PAIRING_WINDOW_MS].
 *
 * Entirely free of Android framework classes in its logic (only
 * [ViewModel]/[viewModelScope] from androidx.lifecycle, which do not pull
 * in android.* APIs) — safe to unit test against [PairingTokenSource] and
 * [PairingConnector] fakes without Robolectric.
 *
 * [coroutineScope] defaults to [viewModelScope] in production; tests inject
 * their own `TestScope` instead, since [viewModelScope]'s job is
 * independent of a test's `TestScope` job — see the equivalent note on
 * [com.controlldeck.app.ui.dashboardeditor.DashboardEditorViewModel] for
 * why that matters for the countdown coroutine below.
 */
class PairingViewModel(
    private val tokenSource: PairingTokenSource,
    private val connector: PairingConnector,
    private val selfDeviceId: String,
    private val boundPort: Int,
    private val tickIntervalMs: Long = 1000L,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {

    private val scope: CoroutineScope = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState

    private var countdownJob: kotlinx.coroutines.Job? = null

    fun startPinPairing() {
        val pin = tokenSource.startPinPairing()
        _uiState.value = PairingUiState(mode = PairingMode.SHOWING_PIN, pin = pin, remainingSeconds = tokenSource.remainingValiditySeconds())
        startCountdown()
    }

    fun startQrPairing() {
        val payload = tokenSource.startQrPairing(selfDeviceId, boundPort)
        _uiState.value = PairingUiState(mode = PairingMode.SHOWING_QR, qrPayload = payload, remainingSeconds = tokenSource.remainingValiditySeconds())
        startCountdown()
    }

    fun beginEnteringPin() {
        countdownJob?.cancel()
        _uiState.value = PairingUiState(mode = PairingMode.ENTERING_PIN)
    }

    fun updateEnteredPin(value: String) {
        val digitsOnly = value.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(enteredPin = digitsOnly, error = null) }
    }

    fun isEnteredPinValidFormat(): Boolean = validatePinFormat(_uiState.value.enteredPin)

    fun encodeQrContent(payload: QrPairingPayload): String = tokenSource.encodeQrPayload(payload)

    /** Attempts to connect to a peer discovered at [host]:[port] using the currently entered PIN. */
    fun submitEnteredPin(host: String, port: Int) {
        val pin = _uiState.value.enteredPin
        if (!validatePinFormat(pin)) {
            _uiState.update { it.copy(error = "PIN must be exactly 6 digits") }
            return
        }
        _uiState.update { it.copy(connecting = true, error = null) }
        connector.connectForPairing(host, port, pin)
    }

    fun connectWithQrPayload(payload: QrPairingPayload, host: String) {
        _uiState.update { it.copy(connecting = true, error = null) }
        connector.connectForPairing(host, payload.port, payload.pairingToken)
    }

    fun cancel() {
        countdownJob?.cancel()
        tokenSource.cancelPairingWindow()
        _uiState.value = PairingUiState()
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            while (true) {
                val remaining = tokenSource.remainingValiditySeconds()
                if (remaining <= 0) {
                    _uiState.update { it.copy(mode = PairingMode.EXPIRED, remainingSeconds = 0) }
                    return@launch
                }
                _uiState.update { it.copy(remainingSeconds = remaining) }
                delay(tickIntervalMs)
            }
        }
    }

    companion object {
        fun validatePinFormat(candidate: String): Boolean = candidate.length == 6 && candidate.all { it.isDigit() }
    }
}
