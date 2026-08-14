package com.controlldeck.protocol

import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthProofTest {

    private val secret = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })

    @Test
    fun `compute is deterministic for the same inputs`() {
        val a = AuthProof.compute(secret, "msg-1", 1000)
        val b = AuthProof.compute(secret, "msg-1", 1000)
        assertEquals(a, b)
    }

    @Test
    fun `compute differs when messageId or timestamp changes`() {
        val base = AuthProof.compute(secret, "msg-1", 1000)
        assertNotEquals(base, AuthProof.compute(secret, "msg-2", 1000))
        assertNotEquals(base, AuthProof.compute(secret, "msg-1", 1001))
    }

    @Test
    fun `verify accepts a matching proof and rejects a tampered one`() {
        val proof = AuthProof.compute(secret, "msg-1", 1000)
        assertTrue(AuthProof.verify(secret, "msg-1", 1000, proof))
        assertFalse(AuthProof.verify(secret, "msg-1", 1000, proof.reversed()))
        assertFalse(AuthProof.verify(secret, "msg-1", 1001, proof))
    }

    @Test
    fun `different shared secrets produce different proofs`() {
        val otherSecret = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 1).toByte() })
        val a = AuthProof.compute(secret, "msg-1", 1000)
        val b = AuthProof.compute(otherSecret, "msg-1", 1000)
        assertNotEquals(a, b)
    }
}
