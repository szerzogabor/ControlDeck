package com.controlldeck.protocol

import kotlinx.serialization.Serializable

// ---- 3.1 HELLO ----

@Serializable
data class HelloPayload(
    val protocolVersion: Int,
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val appVersion: String,
    val secure: Boolean = false,
)

// ---- 3.2 Pairing ----

@Serializable
data class PairRequestPayload(
    val requesterDeviceId: String,
    val requesterDeviceName: String,
    val requesterPlatform: String,
    val pairingToken: String,
)

@Serializable
data class PairResponsePayload(
    val accepted: Boolean,
    val reason: String? = null,
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val sharedSecret: String? = null,
)

/** [PairResponsePayload.reason] values when [PairResponsePayload.accepted] is false. */
object PairRejectReason {
    const val TOKEN_INVALID = "TOKEN_INVALID"
    const val TOKEN_EXPIRED = "TOKEN_EXPIRED"
    const val DECLINED = "DECLINED"
}

// ---- 3.3 AUTH ----

@Serializable
data class AuthPayload(
    val deviceId: String,
    val proof: String,
)

@Serializable
data class AuthResultPayload(
    val accepted: Boolean,
    val reason: String? = null,
)

// ---- 3.4 DEVICE_INFO / CAPABILITIES ----

@Serializable
data class DeviceInfoPayload(
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val appVersion: String,
)

@Serializable
data class AppRegistryEntryDto(
    val appId: String,
    val displayName: String,
)

@Serializable
data class CapabilitiesPayload(
    val deviceId: String,
    val capabilities: List<String>,
    val apps: List<AppRegistryEntryDto> = emptyList(),
)

/** The closed capability enum from protocol/PROTOCOL.md §3.4 — receivers must tolerate unknown values. */
object CapabilityValues {
    const val BRIGHTNESS = "BRIGHTNESS"
    const val VOLUME = "VOLUME"
    const val MUTE = "MUTE"
    const val MEDIA_PLAY_PAUSE = "MEDIA_PLAY_PAUSE"
    const val MEDIA_NEXT = "MEDIA_NEXT"
    const val MEDIA_PREVIOUS = "MEDIA_PREVIOUS"
    const val APP_LAUNCH = "APP_LAUNCH"

    val known: Set<String> = setOf(BRIGHTNESS, VOLUME, MUTE, MEDIA_PLAY_PAUSE, MEDIA_NEXT, MEDIA_PREVIOUS, APP_LAUNCH)
}

// ---- 3.5 ACTION / ACTION_RESULT ----

@Serializable
data class ActionPayload(
    val action: ActionDto,
)

@Serializable
data class ActionResultPayload(
    val correlatesTo: String,
    val success: Boolean,
    val errorCode: String? = null,
    val resultingState: ActionDto? = null,
)

/** [ActionResultPayload.errorCode] values when [ActionResultPayload.success] is false. */
object ActionErrorCode {
    const val UNSUPPORTED_CAPABILITY = "UNSUPPORTED_CAPABILITY"
    const val APP_NOT_FOUND = "APP_NOT_FOUND"
    const val PLATFORM_ERROR = "PLATFORM_ERROR"
    const val INVALID_VALUE = "INVALID_VALUE"
}

// ---- 3.6 STATE_UPDATE ----

/**
 * Unsolicited push of a device's own current state. Same payload shape as
 * [ActionResultPayload.resultingState] (protocol/PROTOCOL.md §3.6) but
 * carries the reporting [deviceId] and no `correlatesTo`.
 */
@Serializable
data class StateUpdatePayload(
    val deviceId: String,
    val state: ActionDto,
)

// ---- 3.7 DASHBOARD_SYNC / DASHBOARD_ACK ----

@Serializable
data class GridPositionDto(val x: Int, val y: Int)

@Serializable
data class GridSizeDto(val width: Int, val height: Int)

@Serializable
data class WidgetDto(
    val id: String,
    val type: String,
    val position: GridPositionDto,
    val size: GridSizeDto,
    val targetDeviceId: String,
    val action: ActionDto,
    val configuration: Map<String, String> = emptyMap(),
)

@Serializable
data class GroupDto(
    val id: String,
    val name: String,
    val kind: String,
    val memberWidgetIds: List<String>,
    val reconnectPolicy: String = "SYNC_GROUP_STATE",
)

@Serializable
data class DashboardDto(
    val id: String,
    val name: String,
    val version: Long,
    val widgets: List<WidgetDto> = emptyList(),
    val groups: List<GroupDto> = emptyList(),
)

@Serializable
data class DashboardSyncPayload(
    val dashboard: DashboardDto,
)

@Serializable
data class DashboardAckPayload(
    val dashboardId: String,
    val appliedVersion: Long,
)

// ---- 3.8 ERROR / PING / PONG ----

@Serializable
data class ErrorPayload(
    val code: String,
    val message: String,
    val correlatesTo: String? = null,
)

/** protocol/PROTOCOL.md §3.8 error codes. */
object ErrorCode {
    const val PROTOCOL_VERSION_MISMATCH = "PROTOCOL_VERSION_MISMATCH"
    const val UNSUPPORTED_MESSAGE_TYPE = "UNSUPPORTED_MESSAGE_TYPE"
    const val MALFORMED_PAYLOAD = "MALFORMED_PAYLOAD"
    const val NOT_PAIRED = "NOT_PAIRED"
    const val AUTH_FAILED = "AUTH_FAILED"
    const val UNSUPPORTED_CAPABILITY = "UNSUPPORTED_CAPABILITY"
    const val INVALID_ACTION = "INVALID_ACTION"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
}

/** PING/PONG carry no payload beyond the envelope. */
@Serializable
class EmptyPayload
