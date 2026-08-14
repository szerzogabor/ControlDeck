using System.Collections.Concurrent;
using ControlDeck.Domain;

namespace ControlDeck.Agent.Transport;

/// <summary>
/// The State Manager (docs/ARCHITECTURE.md §2): per-device last-known state
/// (brightness/volume/muted/media, ONLINE/OFFLINE). A device with no open,
/// authenticated connection is OFFLINE for every peer (docs/ARCHITECTURE.md §7)
/// — this class is the single source of truth other layers (UI, group
/// reconnect logic) read from.
/// </summary>
public sealed class DeviceStateManager
{
    private readonly ConcurrentDictionary<string, DeviceState> _states = new();

    public event EventHandler<DeviceState>? StateChanged;

    public DeviceState Get(string deviceId) =>
        _states.TryGetValue(deviceId, out var state) ? state : DeviceState.OfflineUnknown(new DeviceId(deviceId));

    public IReadOnlyCollection<DeviceState> GetAll() => _states.Values.ToList();

    public void MarkOnline(string deviceId)
    {
        var current = Get(deviceId);
        Set(current with { Connection = ConnectionState.Online });
    }

    /// <summary>
    /// Marks a device OFFLINE but deliberately keeps its last-known
    /// brightness/volume/muted/media values rather than clearing them, so
    /// the UI can still show a plausible last state under the "offline"
    /// badge (docs/ARCHITECTURE.md §7).
    /// </summary>
    public void MarkOffline(string deviceId)
    {
        var current = Get(deviceId);
        Set(current with { Connection = ConnectionState.Offline });
    }

    public void ApplyBrightness(string deviceId, int value) =>
        Set(Get(deviceId) with { Brightness = value, Connection = ConnectionState.Online });

    public void ApplyVolume(string deviceId, int value) =>
        Set(Get(deviceId) with { Volume = value, Connection = ConnectionState.Online });

    public void ApplyMuted(string deviceId, bool muted) =>
        Set(Get(deviceId) with { Muted = muted, Connection = ConnectionState.Online });

    public void ApplyMediaState(string deviceId, MediaState state) =>
        Set(Get(deviceId) with { MediaState = state, Connection = ConnectionState.Online });

    private void Set(DeviceState state)
    {
        _states[state.DeviceId.Value] = state;
        StateChanged?.Invoke(this, state);
    }
}
