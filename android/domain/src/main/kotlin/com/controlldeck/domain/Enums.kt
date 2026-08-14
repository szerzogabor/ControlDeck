package com.controlldeck.domain

/** Platform a device runs on. Mirrors protocol/PROTOCOL.md HELLO.platform. */
enum class Platform {
    ANDROID,
    WINDOWS,
}

/**
 * Capability enum mirrors protocol/PROTOCOL.md §3.4. Closed set for the MVP;
 * unknown wire values are tolerated at the protocol layer and never reach
 * this enum (they're dropped before mapping into the domain).
 */
enum class Capability {
    BRIGHTNESS,
    VOLUME,
    MUTE,
    MEDIA_PLAY_PAUSE,
    MEDIA_NEXT,
    MEDIA_PREVIOUS,
    APP_LAUNCH,
}

/** Widget kinds per docs/ARCHITECTURE.md §3. */
enum class WidgetType {
    SLIDER_BRIGHTNESS,
    SLIDER_VOLUME,
    BUTTON_MUTE,
    BUTTON_MEDIA_PLAY_PAUSE,
    BUTTON_MEDIA_NEXT,
    BUTTON_MEDIA_PREVIOUS,
    APP_LAUNCH,
}

/** The capability a widget type requires on its target device. */
fun WidgetType.requiredCapability(): Capability = when (this) {
    WidgetType.SLIDER_BRIGHTNESS -> Capability.BRIGHTNESS
    WidgetType.SLIDER_VOLUME -> Capability.VOLUME
    WidgetType.BUTTON_MUTE -> Capability.MUTE
    WidgetType.BUTTON_MEDIA_PLAY_PAUSE -> Capability.MEDIA_PLAY_PAUSE
    WidgetType.BUTTON_MEDIA_NEXT -> Capability.MEDIA_NEXT
    WidgetType.BUTTON_MEDIA_PREVIOUS -> Capability.MEDIA_PREVIOUS
    WidgetType.APP_LAUNCH -> Capability.APP_LAUNCH
}

/** Group semantics kind. See docs/ARCHITECTURE.md §4. */
enum class GroupKind {
    RELATIVE_SLIDER,
    ABSOLUTE_TOGGLE,
    ABSOLUTE_MEDIA,
}

/** Reconnect behavior for a group member. See docs/ARCHITECTURE.md §5. */
enum class ReconnectPolicy {
    SYNC_GROUP_STATE,
    KEEP_DEVICE_STATE,
    NO_ACTION,
}

/** Device connection state as tracked by the local State Manager. */
enum class ConnectionState {
    ONLINE,
    OFFLINE,
}

/** Media playback state. */
enum class MediaPlaybackState {
    PLAYING,
    PAUSED,
}
