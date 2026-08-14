namespace ControlDeck.Domain;

/// <summary>Device platform, per protocol/PROTOCOL.md §3.1 ("ANDROID" | "WINDOWS").</summary>
public enum Platform
{
    Android,
    Windows
}

/// <summary>
/// Closed capability enum for the MVP, per protocol/PROTOCOL.md §3.4. Receivers
/// must tolerate unknown future values elsewhere on the wire (handled in the
/// Protocol layer); this enum only needs to represent the values this codebase
/// understands.
/// </summary>
public enum Capability
{
    Brightness,
    Volume,
    Mute,
    MediaPlayPause,
    MediaNext,
    MediaPrevious,
    AppLaunch
}

/// <summary>Widget kinds, per docs/ARCHITECTURE.md §3.</summary>
public enum WidgetType
{
    SliderBrightness,
    SliderVolume,
    ButtonMute,
    ButtonMediaPlayPause,
    ButtonMediaNext,
    ButtonMediaPrevious,
    AppLaunch
}

/// <summary>Group semantics, per docs/ARCHITECTURE.md §4.</summary>
public enum GroupKind
{
    RelativeSlider,
    AbsoluteToggle,
    AbsoluteMedia
}

/// <summary>Reconnect behavior for a group member, per docs/ARCHITECTURE.md §5.</summary>
public enum ReconnectPolicy
{
    SyncGroupState,
    KeepDeviceState,
    NoAction
}

/// <summary>Per-device online/offline state, per docs/ARCHITECTURE.md §3/§7.</summary>
public enum ConnectionState
{
    Online,
    Offline
}

/// <summary>Media playback state, per protocol/PROTOCOL.md §3.5.</summary>
public enum MediaState
{
    Playing,
    Paused
}
