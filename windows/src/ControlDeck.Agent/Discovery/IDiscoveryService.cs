using ControlDeck.Domain;

namespace ControlDeck.Agent.Discovery;

/// <summary>A peer found via mDNS/DNS-SD (protocol/PROTOCOL.md §5). `DeviceId` — never the resolved IP/hostname — is what the UI keys off of.</summary>
public sealed record DiscoveredDevice(
    string DeviceId,
    string DeviceName,
    Platform Platform,
    string AppVersion,
    string HostOrAddress,
    int Port
);

/// <summary>
/// mDNS/DNS-SD advertise + browse for `_controlldeck._tcp.local.`
/// (protocol/PROTOCOL.md §5). Kept behind an interface so
/// higher layers (and tests) never depend on the concrete mDNS library.
/// </summary>
public interface IDiscoveryService : IDisposable
{
    event EventHandler<DiscoveredDevice>? DeviceFound;
    event EventHandler<string>? DeviceLost;

    /// <summary>Starts advertising this device on the LAN and browsing for peers.</summary>
    void Start(string deviceId, string deviceName, Platform platform, string appVersion, int port);

    void Stop();

    IReadOnlyList<DiscoveredDevice> KnownDevices { get; }
}
