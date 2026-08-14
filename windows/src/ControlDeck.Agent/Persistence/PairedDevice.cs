using ControlDeck.Domain;

namespace ControlDeck.Agent.Persistence;

/// <summary>
/// Non-secret metadata about a paired peer. The shared secret itself lives
/// only in <see cref="ISecretStore"/>'s DPAPI-protected file — never here.
/// </summary>
public sealed record PairedDevice(
    string DeviceId,
    string DeviceName,
    Platform Platform,
    DateTimeOffset PairedAtUtc,
    ReconnectPolicy DefaultReconnectPolicy = ReconnectPolicy.SyncGroupState
);

public interface IPairedDeviceRepository
{
    IReadOnlyList<PairedDevice> GetAll();

    PairedDevice? GetById(string deviceId);

    void Upsert(PairedDevice device);

    void Remove(string deviceId);
}
