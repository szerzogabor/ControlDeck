using ControlDeck.Domain;

namespace ControlDeck.Agent.PlatformActions;

/// <summary>
/// Determines which capabilities this device actually supports right now,
/// by asking each platform-action controller rather than hardcoding a fixed
/// list — in particular BRIGHTNESS is only included when
/// <see cref="IBrightnessController.IsAvailable"/> is true (per the spec's
/// explicit requirement not to hardcode it as always-supported), and
/// VOLUME/MUTE are only included when a default audio endpoint exists.
/// MEDIA_* and APP_LAUNCH are always available since they don't depend on
/// enumerable hardware.
/// </summary>
public sealed class CapabilityRegistry
{
    private readonly IVolumeController _volume;
    private readonly IBrightnessController _brightness;

    public CapabilityRegistry(IVolumeController volume, IBrightnessController brightness)
    {
        _volume = volume;
        _brightness = brightness;
    }

    public IReadOnlySet<Capability> CurrentCapabilities()
    {
        var caps = new HashSet<Capability>
        {
            Capability.MediaPlayPause,
            Capability.MediaNext,
            Capability.MediaPrevious,
            Capability.AppLaunch,
        };

        if (_volume.IsAvailable)
        {
            caps.Add(Capability.Volume);
            caps.Add(Capability.Mute);
        }

        if (_brightness.IsAvailable)
        {
            caps.Add(Capability.Brightness);
        }

        return caps;
    }

    public static string ToWireToken(Capability capability) => capability switch
    {
        Capability.Brightness => "BRIGHTNESS",
        Capability.Volume => "VOLUME",
        Capability.Mute => "MUTE",
        Capability.MediaPlayPause => "MEDIA_PLAY_PAUSE",
        Capability.MediaNext => "MEDIA_NEXT",
        Capability.MediaPrevious => "MEDIA_PREVIOUS",
        Capability.AppLaunch => "APP_LAUNCH",
        _ => throw new ArgumentOutOfRangeException(nameof(capability))
    };

    /// <summary>Maps a wire capability token to a Domain Capability; unrecognized tokens (forward compat) return null rather than throwing.</summary>
    public static Capability? FromWireToken(string token) => token switch
    {
        "BRIGHTNESS" => Capability.Brightness,
        "VOLUME" => Capability.Volume,
        "MUTE" => Capability.Mute,
        "MEDIA_PLAY_PAUSE" => Capability.MediaPlayPause,
        "MEDIA_NEXT" => Capability.MediaNext,
        "MEDIA_PREVIOUS" => Capability.MediaPrevious,
        "APP_LAUNCH" => Capability.AppLaunch,
        _ => null
    };
}
