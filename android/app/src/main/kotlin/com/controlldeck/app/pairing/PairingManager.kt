package com.controlldeck.app.pairing

import com.controlldeck.app.logging.Logger
import com.controlldeck.app.ui.pairing.PairingTokenSource
import com.controlldeck.protocol.ProtocolJson
import kotlinx.serialization.Serializable
import java.security.SecureRandom

private const val TAG = "Pairing"
private const val PIN_VALIDITY_MS = 2 * 60 * 1000L

/** The payload encoded into this device's own pairing QR code (protocol/PROTOCOL.md §3.2 "QR flow"). */
@Serializable
data class QrPairingPayload(
    val deviceId: String,
    val pairingToken: String,
    val port: Int,
)

private data class PendingToken(val token: String, val expiresAtEpochMs: Long)

/**
 * Generates and validates the short-lived pairing token used by both the
 * QR and PIN flows (protocol/PROTOCOL.md §3.2 — "both flows use the same
 * message shape, the PIN is simply a low-entropy, human-typeable
 * pairingToken"). Never logs the token/PIN value itself.
 */
class PairingManager(private val logger: Logger) : PairingTokenSource {

    @Volatile private var pending: PendingToken? = null

    /** Starts a 2-minute PIN pairing window; returns the 6-digit PIN to display. */
    override fun startPinPairing(): String {
        val pin = (100000 + SecureRandom().nextInt(900000)).toString()
        pending = PendingToken(pin, System.currentTimeMillis() + PIN_VALIDITY_MS)
        logger.redactedEvent(TAG, "PIN pairing window started")
        return pin
    }

    /** Starts a 2-minute QR pairing window; returns the payload to encode into the QR code. */
    override fun startQrPairing(deviceId: String, port: Int): QrPairingPayload {
        val token = generateHighEntropyToken()
        pending = PendingToken(token, System.currentTimeMillis() + PIN_VALIDITY_MS)
        logger.redactedEvent(TAG, "QR pairing window started")
        return QrPairingPayload(deviceId, token, port)
    }

    override fun cancelPairingWindow() {
        pending = null
    }

    /** Called by the acceptor side ([com.controlldeck.app.transport.PeerConnectionContext.validatePairingToken]). */
    fun validateToken(candidate: String): Boolean {
        val current = pending ?: return false
        if (System.currentTimeMillis() > current.expiresAtEpochMs) {
            pending = null
            return false
        }
        // Constant-time-ish comparison to avoid trivial PIN-guessing timing signal.
        return java.security.MessageDigest.isEqual(current.token.toByteArray(), candidate.toByteArray())
    }

    override fun remainingValiditySeconds(): Long {
        val current = pending ?: return 0
        return ((current.expiresAtEpochMs - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
    }

    override fun encodeQrPayload(payload: QrPairingPayload): String =
        ProtocolJson.instance.encodeToString(QrPairingPayload.serializer(), payload)

    fun decodeQrPayload(raw: String): QrPairingPayload? =
        runCatching { ProtocolJson.instance.decodeFromString(QrPairingPayload.serializer(), raw) }.getOrNull()

    private fun generateHighEntropyToken(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
