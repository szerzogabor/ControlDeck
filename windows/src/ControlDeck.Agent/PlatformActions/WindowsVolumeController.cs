using System.Runtime.InteropServices;
using System.Runtime.Versioning;
using Microsoft.Extensions.Logging;
using NAudio.CoreAudioApi;

namespace ControlDeck.Agent.PlatformActions;

/// <summary>
/// Controls the default Windows playback endpoint's master volume/mute via
/// NAudio's Core Audio API wrapper (WASAPI `IAudioEndpointVolume`).
/// Subscribes to endpoint-volume notifications so externally-driven changes
/// (user moves the system volume slider, another app changes it) surface as
/// <see cref="VolumeOrMuteChanged"/> -&gt; a STATE_UPDATE broadcast
/// (protocol/PROTOCOL.md §3.6). NAudio's `AudioEndpointVolume` handles the
/// underlying `IAudioEndpointVolumeCallback` COM registration internally —
/// consumers just add a plain delegate to its `OnVolumeNotification` event.
/// </summary>
[SupportedOSPlatform("windows")]
public sealed class WindowsVolumeController : IVolumeController, IDisposable
{
    private readonly ILogger<WindowsVolumeController> _logger;
    private readonly MMDeviceEnumerator _enumerator = new();
    private MMDevice? _device;

    public event EventHandler? VolumeOrMuteChanged;

    public WindowsVolumeController(ILogger<WindowsVolumeController> logger)
    {
        _logger = logger;
        TryBindToDefaultDevice();
    }

    public bool IsAvailable => _device is not null;

    public int GetVolume()
    {
        if (!TryEnsureDevice())
        {
            return 0;
        }

        try
        {
            return (int)Math.Round(_device!.AudioEndpointVolume.MasterVolumeLevelScalar * 100);
        }
        catch (Exception ex) when (IsRecoverableAudioException(ex))
        {
            _logger.LogWarning(ex, "Failed to read master volume; device may have been removed.");
            return 0;
        }
    }

    public void SetVolume(int value)
    {
        var clamped = Math.Clamp(value, 0, 100);
        if (!TryEnsureDevice())
        {
            return;
        }

        try
        {
            _device!.AudioEndpointVolume.MasterVolumeLevelScalar = clamped / 100f;
        }
        catch (Exception ex) when (IsRecoverableAudioException(ex))
        {
            _logger.LogWarning(ex, "Failed to set master volume to {Value}.", clamped);
        }
    }

    public bool GetMuted()
    {
        if (!TryEnsureDevice())
        {
            return false;
        }

        try
        {
            return _device!.AudioEndpointVolume.Mute;
        }
        catch (Exception ex) when (IsRecoverableAudioException(ex))
        {
            _logger.LogWarning(ex, "Failed to read mute state.");
            return false;
        }
    }

    public void SetMuted(bool muted)
    {
        if (!TryEnsureDevice())
        {
            return;
        }

        try
        {
            _device!.AudioEndpointVolume.Mute = muted;
        }
        catch (Exception ex) when (IsRecoverableAudioException(ex))
        {
            _logger.LogWarning(ex, "Failed to set mute to {Muted}.", muted);
        }
    }

    /// <summary>NAudio invokes this on the Core Audio notification thread whenever the endpoint volume/mute changes for any reason.</summary>
    private void OnEndpointVolumeNotification(AudioVolumeNotificationData data) =>
        VolumeOrMuteChanged?.Invoke(this, EventArgs.Empty);

    private bool TryEnsureDevice()
    {
        if (_device is not null)
        {
            return true;
        }

        TryBindToDefaultDevice();
        return _device is not null;
    }

    private void TryBindToDefaultDevice()
    {
        try
        {
            _device = _enumerator.GetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia);
            _device.AudioEndpointVolume.OnVolumeNotification += OnEndpointVolumeNotification;
        }
        catch (Exception ex) when (IsRecoverableAudioException(ex))
        {
            // No default playback device (e.g. all audio devices disabled) —
            // VOLUME/MUTE capabilities are simply not advertised; not a crash.
            _logger.LogWarning(ex, "No default audio playback endpoint available.");
            _device = null;
        }
    }

    private static bool IsRecoverableAudioException(Exception ex) =>
        ex is COMException or InvalidOperationException or NullReferenceException;

    public void Dispose()
    {
        try
        {
            if (_device is not null)
            {
                _device.AudioEndpointVolume.OnVolumeNotification -= OnEndpointVolumeNotification;
            }
        }
        catch (COMException)
        {
            // Device already gone; nothing to clean up.
        }

        _device?.Dispose();
        _enumerator.Dispose();
    }
}
