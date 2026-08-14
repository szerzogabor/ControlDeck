package com.controlldeck.app.logging

import android.util.Log

/**
 * Thin abstraction over [android.util.Log] used across the whole app so
 * logging policy (what never gets logged) lives in one place.
 *
 * Hard rule (top-level spec §24-25): shared secrets and pairing PINs are
 * NEVER passed to any of these methods. Call sites log the *fact* that a
 * secret/PIN was generated/used, never the value itself — see
 * [redactedEvent] for the sanctioned pattern.
 */
interface Logger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)

    /** Logs that a sensitive event happened without ever including the sensitive value. */
    fun redactedEvent(tag: String, event: String) = i(tag, "$event (value redacted)")
}

object AndroidLogger : Logger {
    override fun d(tag: String, message: String) {
        Log.d("ControlDeck.$tag", message)
    }

    override fun i(tag: String, message: String) {
        Log.i("ControlDeck.$tag", message)
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        Log.w("ControlDeck.$tag", message, throwable)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        Log.e("ControlDeck.$tag", message, throwable)
    }
}
