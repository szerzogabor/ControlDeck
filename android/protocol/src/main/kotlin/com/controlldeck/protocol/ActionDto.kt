package com.controlldeck.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire representation of an Action, discriminated by `type` per the table
 * in protocol/PROTOCOL.md §3.5. kotlinx.serialization's built-in sealed
 * class support uses `type` as the default class discriminator key, which
 * matches the wire format exactly (no custom SerializersModule needed).
 */
@Serializable
sealed class ActionDto {

    @Serializable
    @SerialName("BRIGHTNESS_SET")
    data class BrightnessSet(val value: Int) : ActionDto()

    @Serializable
    @SerialName("VOLUME_SET")
    data class VolumeSet(val value: Int) : ActionDto()

    @Serializable
    @SerialName("SET_MUTED")
    data class SetMuted(val muted: Boolean) : ActionDto()

    @Serializable
    @SerialName("MEDIA_SET_STATE")
    data class MediaSetState(val state: String) : ActionDto()

    @Serializable
    @SerialName("MEDIA_NEXT")
    data object MediaNext : ActionDto()

    @Serializable
    @SerialName("MEDIA_PREVIOUS")
    data object MediaPrevious : ActionDto()

    @Serializable
    @SerialName("APP_LAUNCH")
    data class AppLaunch(val appId: String) : ActionDto()
}

/** Valid values for [ActionDto.MediaSetState.state]. */
object MediaStateValues {
    const val PLAYING = "PLAYING"
    const val PAUSED = "PAUSED"
}
