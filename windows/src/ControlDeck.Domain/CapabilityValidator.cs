namespace ControlDeck.Domain;

/// <summary>
/// Maps widget/action shapes to the capability a target device must advertise
/// in order to execute them. Used to flag an ACTION as UNSUPPORTED_CAPABILITY
/// (protocol/PROTOCOL.md §3.5) before ever attempting to send/execute it.
/// </summary>
public static class CapabilityValidator
{
    public static Capability RequiredCapability(WidgetType widgetType) => widgetType switch
    {
        WidgetType.SliderBrightness => Capability.Brightness,
        WidgetType.SliderVolume => Capability.Volume,
        WidgetType.ButtonMute => Capability.Mute,
        WidgetType.ButtonMediaPlayPause => Capability.MediaPlayPause,
        WidgetType.ButtonMediaNext => Capability.MediaNext,
        WidgetType.ButtonMediaPrevious => Capability.MediaPrevious,
        WidgetType.AppLaunch => Capability.AppLaunch,
        _ => throw new ArgumentOutOfRangeException(nameof(widgetType), widgetType, "Unknown widget type")
    };

    public static Capability RequiredCapability(ActionSpec action) => action switch
    {
        BrightnessSet => Capability.Brightness,
        VolumeSet => Capability.Volume,
        SetMuted => Capability.Mute,
        MediaSetState => Capability.MediaPlayPause,
        MediaNext => Capability.MediaNext,
        MediaPrevious => Capability.MediaPrevious,
        AppLaunch => Capability.AppLaunch,
        _ => throw new ArgumentOutOfRangeException(nameof(action), action, "Unknown action")
    };

    public static bool IsSupported(ActionSpec action, IReadOnlySet<Capability> targetCapabilities) =>
        targetCapabilities.Contains(RequiredCapability(action));

    public static bool IsSupported(Widget widget, IReadOnlySet<Capability> targetCapabilities) =>
        targetCapabilities.Contains(RequiredCapability(widget.Type));
}
