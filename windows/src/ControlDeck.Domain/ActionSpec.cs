namespace ControlDeck.Domain;

/// <summary>
/// Discriminated union of the actions defined in protocol/PROTOCOL.md §3.5.
/// This is a closed hierarchy: the base constructor is `private protected`,
/// which allows the sealed records declared in this file (same assembly) to
/// derive from it while preventing any other assembly from adding new cases
/// — callers must exhaustively pattern-match on the known set.
/// </summary>
public abstract record ActionSpec
{
    private protected ActionSpec()
    {
    }
}

/// <summary>Absolute brightness set, 0-100.</summary>
public sealed record BrightnessSet(int Value) : ActionSpec;

/// <summary>Absolute volume set, 0-100.</summary>
public sealed record VolumeSet(int Value) : ActionSpec;

/// <summary>Absolute mute state.</summary>
public sealed record SetMuted(bool Muted) : ActionSpec;

/// <summary>Absolute media play/pause state, resolved locally via PLAY/PAUSE key.</summary>
public sealed record MediaSetState(MediaState State) : ActionSpec;

/// <summary>Edge-triggered "next track".</summary>
public sealed record MediaNext : ActionSpec;

/// <summary>Edge-triggered "previous track".</summary>
public sealed record MediaPrevious : ActionSpec;

/// <summary>Launch a locally-registered application by opaque id.</summary>
public sealed record AppLaunch(string AppId) : ActionSpec;
