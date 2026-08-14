namespace ControlDeck.Protocol;

/// <summary>Wire values: "ANDROID" | "WINDOWS" (protocol/PROTOCOL.md §3.1).</summary>
public enum WirePlatform
{
    Android,
    Windows
}

/// <summary>Wire values: "PLAYING" | "PAUSED" (protocol/PROTOCOL.md §3.5).</summary>
public enum WireMediaState
{
    Playing,
    Paused
}
