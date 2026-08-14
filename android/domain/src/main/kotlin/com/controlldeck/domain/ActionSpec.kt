package com.controlldeck.domain

/**
 * Mirrors the protocol Action payloads (protocol/PROTOCOL.md §3.5) but lives
 * in the domain layer so widgets/groups can be reasoned about without any
 * dependency on the wire format. Mapping to/from the protocol DTOs happens
 * outside :domain (in :app), per docs/ARCHITECTURE.md's "no cross-module
 * leakage" principle.
 */
sealed class ActionSpec {
    data class BrightnessSet(val value: Int) : ActionSpec()
    data class VolumeSet(val value: Int) : ActionSpec()
    data class SetMuted(val muted: Boolean) : ActionSpec()
    data class MediaSetState(val state: MediaPlaybackState) : ActionSpec()
    data object MediaNext : ActionSpec()
    data object MediaPrevious : ActionSpec()
    data class AppLaunch(val appId: AppId) : ActionSpec()
}

/** Clamp helper used throughout group math. Defined once per ARCHITECTURE.md §4.1. */
fun clampPercent(value: Int): Int = value.coerceIn(0, 100)
