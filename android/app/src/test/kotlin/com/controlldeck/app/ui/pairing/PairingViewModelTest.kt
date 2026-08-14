package com.controlldeck.app.ui.pairing

import com.controlldeck.app.pairing.QrPairingPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Deterministic fake for [PairingTokenSource] — the real
 * [com.controlldeck.app.pairing.PairingManager] is already pure JVM
 * (java.security only) and separately unit tested; this fake lets
 * [PairingViewModel]'s state machine be tested against a fixed clock
 * instead of real wall-clock expiry.
 */
private class FakePairingTokenSource : PairingTokenSource {
    var now: Long = 0L
    private var expiresAt: Long = -1L
    var lastPin: String = "111111"

    override fun startPinPairing(): String {
        expiresAt = now + PAIRING_WINDOW_MS
        return lastPin
    }

    override fun startQrPairing(deviceId: String, port: Int): QrPairingPayload {
        expiresAt = now + PAIRING_WINDOW_MS
        return QrPairingPayload(deviceId, "qr-token", port)
    }

    override fun cancelPairingWindow() {
        expiresAt = -1L
    }

    override fun remainingValiditySeconds(): Long =
        if (expiresAt < 0) 0 else ((expiresAt - now) / 1000).coerceAtLeast(0)

    override fun encodeQrPayload(payload: QrPairingPayload): String = "${payload.deviceId}|${payload.pairingToken}|${payload.port}"
}

private class FakePairingConnector : PairingConnector {
    data class Call(val host: String, val port: Int, val token: String)
    val calls = mutableListOf<Call>()

    override fun connectForPairing(host: String, port: Int, pairingToken: String) {
        calls += Call(host, port, pairingToken)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PairingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var tokenSource: FakePairingTokenSource
    private lateinit var connector: FakePairingConnector
    private lateinit var viewModel: PairingViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        tokenSource = FakePairingTokenSource()
        connector = FakePairingConnector()
        viewModel = PairingViewModel(tokenSource, connector, selfDeviceId = "self-id", boundPort = 47531, tickIntervalMs = 1000)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `PIN format validation accepts exactly 6 digits`() {
        assertTrue(PairingViewModel.validatePinFormat("123456"))
        assertFalse(PairingViewModel.validatePinFormat("12345"))
        assertFalse(PairingViewModel.validatePinFormat("1234567"))
        assertFalse(PairingViewModel.validatePinFormat("12a456"))
        assertFalse(PairingViewModel.validatePinFormat(""))
    }

    @Test
    fun `updateEnteredPin strips non-digits and truncates to 6 characters`() {
        viewModel.updateEnteredPin("12-34ab56789")
        assertEquals("123456", viewModel.uiState.value.enteredPin)
    }

    @Test
    fun `startPinPairing shows the generated PIN and starts a countdown`() = runTest(dispatcher) {
        // The PIN/mode/initial remaining-seconds are set synchronously inside
        // startPinPairing() itself, before the background countdown coroutine
        // ever runs — so no scheduler advance is needed (and none is safe here:
        // the countdown is an unbounded `while(true)` loop that would make
        // advanceUntilIdle() spin forever chasing its own re-scheduled delay).
        viewModel.startPinPairing()

        val state = viewModel.uiState.value
        assertEquals(PairingMode.SHOWING_PIN, state.mode)
        assertEquals("111111", state.pin)
        assertEquals(120, state.remainingSeconds)
    }

    @Test
    fun `countdown reaching zero transitions to EXPIRED`() = runTest(dispatcher) {
        viewModel.startPinPairing()
        // Expire the fake token BEFORE driving the scheduler, so the countdown
        // coroutine's very first tick observes remaining <= 0 and returns —
        // bounding the loop instead of spinning advanceUntilIdle() forever.
        tokenSource.now += PAIRING_WINDOW_MS
        testScheduler.advanceTimeBy(1_500)
        testScheduler.runCurrent()

        assertEquals(PairingMode.EXPIRED, viewModel.uiState.value.mode)
    }

    @Test
    fun `submitEnteredPin with an invalid format sets an error and does not connect`() {
        viewModel.beginEnteringPin()
        viewModel.updateEnteredPin("123")

        viewModel.submitEnteredPin("192.168.1.5", 47531)

        assertNotNull(viewModel.uiState.value.error)
        assertTrue(connector.calls.isEmpty())
    }

    @Test
    fun `submitEnteredPin with a valid format connects via the connector`() {
        viewModel.beginEnteringPin()
        viewModel.updateEnteredPin("482910")

        viewModel.submitEnteredPin("192.168.1.5", 47531)

        assertEquals(1, connector.calls.size)
        val call = connector.calls.single()
        assertEquals("192.168.1.5", call.host)
        assertEquals(47531, call.port)
        assertEquals("482910", call.token)
        assertTrue(viewModel.uiState.value.connecting)
    }

    @Test
    fun `cancel resets to idle and clears the pairing window`() = runTest(dispatcher) {
        viewModel.startPinPairing()
        // Deliberately not advancing the scheduler here — cancel() must work
        // correctly even before the countdown coroutine has run at all.

        viewModel.cancel()

        assertEquals(PairingMode.IDLE, viewModel.uiState.value.mode)
        assertEquals(0, tokenSource.remainingValiditySeconds())
    }
}
