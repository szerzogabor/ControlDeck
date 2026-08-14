namespace ControlDeck.Agent.PlatformActions;

/// <summary>
/// Controls (and reports) the default Windows audio endpoint's volume/mute.
/// Kept behind an interface so ActionDispatcher/tests can use a fake instead
/// of touching real Core Audio.
/// </summary>
public interface IVolumeController
{
    /// <summary>True if a default audio render endpoint is available (i.e. this capability should be advertised).</summary>
    bool IsAvailable { get; }

    /// <summary>Current volume, 0-100.</summary>
    int GetVolume();

    /// <summary>Sets volume, clamped to 0-100.</summary>
    void SetVolume(int value);

    bool GetMuted();

    void SetMuted(bool muted);

    /// <summary>Raised when the volume/mute changes for any reason (including changes made outside ControlDeck).</summary>
    event EventHandler? VolumeOrMuteChanged;
}
