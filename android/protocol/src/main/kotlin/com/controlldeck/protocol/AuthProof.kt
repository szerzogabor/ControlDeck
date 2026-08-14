package com.controlldeck.protocol

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Computes/verifies the AUTH `proof` field from protocol/PROTOCOL.md §3.3:
 * `HMAC-SHA256(sharedSecret, messageId + timestamp)`, Base64-encoded.
 *
 * Pure JVM (`javax.crypto`, part of the standard library on both the JVM
 * and Android runtimes) — no Android-specific APIs, so this can live in
 * :protocol and be unit tested without an SDK.
 */
object AuthProof {
    private const val ALGORITHM = "HmacSHA256"

    /** [sharedSecretBase64] is the Base64-encoded 256-bit secret established during pairing. */
    fun compute(sharedSecretBase64: String, messageId: String, timestamp: Long): String {
        val keyBytes = Base64.getDecoder().decode(sharedSecretBase64)
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(keyBytes, ALGORITHM))
        val macBytes = mac.doFinal((messageId + timestamp).toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(macBytes)
    }

    fun verify(sharedSecretBase64: String, messageId: String, timestamp: Long, proof: String): Boolean =
        // Constant-time-ish comparison: MessageDigest.isEqual avoids short-circuit timing leaks.
        java.security.MessageDigest.isEqual(
            compute(sharedSecretBase64, messageId, timestamp).toByteArray(Charsets.UTF_8),
            proof.toByteArray(Charsets.UTF_8),
        )
}
