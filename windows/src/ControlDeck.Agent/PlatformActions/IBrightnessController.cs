namespace ControlDeck.Agent.PlatformActions;

/// <summary>Controls display brightness (laptop/internal panel or DDC/CI-capable monitor).</summary>
public interface IBrightnessController
{
    /// <summary>
    /// True only if a WMI `WmiMonitorBrightness` instance was actually found
    /// at startup — BRIGHTNESS must NOT be advertised as a capability
    /// (protocol/PROTOCOL.md §3.4) on hardware that doesn't expose it.
    /// </summary>
    bool IsAvailable { get; }

    /// <summary>Current brightness, 0-100.</summary>
    int GetBrightness();

    /// <summary>Sets brightness, clamped to 0-100.</summary>
    void SetBrightness(int value);
}
