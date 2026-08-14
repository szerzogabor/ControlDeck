using ControlDeck.Agent.PlatformActions;
using ControlDeck.Domain;

namespace ControlDeck.Agent.Tests;

/// <summary>
/// In-memory fakes for every platform-action interface. These let
/// ActionDispatcher/CapabilityRegistry be tested deterministically without
/// touching real Core Audio/WMI/SendInput/Process.Start — per the spec,
/// this test project exercises the ABSTRACTIONS only.
/// </summary>
public sealed class FakeVolumeController : IVolumeController
{
    public bool IsAvailable { get; set; } = true;
    public int Volume { get; set; } = 50;
    public bool Muted { get; set; }

    public event EventHandler? VolumeOrMuteChanged;

    public int GetVolume() => Volume;

    public void SetVolume(int value)
    {
        Volume = Math.Clamp(value, 0, 100);
        VolumeOrMuteChanged?.Invoke(this, EventArgs.Empty);
    }

    public bool GetMuted() => Muted;

    public void SetMuted(bool muted)
    {
        Muted = muted;
        VolumeOrMuteChanged?.Invoke(this, EventArgs.Empty);
    }
}

public sealed class FakeBrightnessController : IBrightnessController
{
    public bool IsAvailable { get; set; } = true;
    public int Brightness { get; set; } = 50;

    public int GetBrightness() => Brightness;

    public void SetBrightness(int value) => Brightness = Math.Clamp(value, 0, 100);
}

public sealed class FakeMediaController : IMediaController
{
    public MediaState? LastSetState { get; private set; }
    public int NextCount { get; private set; }
    public int PreviousCount { get; private set; }

    public void SetState(MediaState desiredState) => LastSetState = desiredState;

    public void Next() => NextCount++;

    public void Previous() => PreviousCount++;
}

public sealed class FakeAppLauncher : IAppLauncher
{
    private readonly Dictionary<string, AppLaunchResult> _results = new();

    public List<string> LaunchedAppIds { get; } = new();

    public void ConfigureResult(string appId, AppLaunchResult result) => _results[appId] = result;

    public AppLaunchResult Launch(string appId)
    {
        LaunchedAppIds.Add(appId);
        return _results.TryGetValue(appId, out var result) ? result : AppLaunchResult.Launched;
    }
}

public sealed class ThrowingVolumeController : IVolumeController
{
    public bool IsAvailable => true;

    public event EventHandler? VolumeOrMuteChanged { add { } remove { } }

    public int GetVolume() => 0;

    public void SetVolume(int value) => throw new InvalidOperationException("simulated platform failure");

    public bool GetMuted() => false;

    public void SetMuted(bool muted) => throw new InvalidOperationException("simulated platform failure");
}
