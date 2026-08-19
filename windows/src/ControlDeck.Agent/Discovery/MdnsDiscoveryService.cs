using System.Collections.Concurrent;
using System.Runtime.Versioning;
using ControlDeck.Domain;
using Makaretu.Dns;
using Microsoft.Extensions.Logging;

namespace ControlDeck.Agent.Discovery;

/// <summary>
/// mDNS/DNS-SD advertise + browse for `_controlldeck._tcp.local.`, per
/// protocol/PROTOCOL.md §5, built on the `Makaretu.Dns.Multicast` package
/// (a pure-.NET, actively-maintained mDNS/DNS-SD implementation).
///
/// TXT records carry `id`, `name`, `platform`, `version`, `port` as specified;
/// `id` (the advertised deviceId) is what callers key discovered devices on
/// — never the resolved IP/hostname, which can change at any time.
/// </summary>
[SupportedOSPlatform("windows")]
public sealed class MdnsDiscoveryService : IDiscoveryService
{
    private const string ServiceName = "_controlldeck._tcp";

    private readonly ILogger<MdnsDiscoveryService> _logger;
    private readonly ConcurrentDictionary<string, DiscoveredDevice> _known = new();

    private static readonly TimeSpan QueryInterval = TimeSpan.FromSeconds(10);

    private MulticastService? _mdns;
    private ServiceDiscovery? _serviceDiscovery;
    private string? _selfDeviceId;
    private Timer? _queryTimer;

    public event EventHandler<DiscoveredDevice>? DeviceFound;
    public event EventHandler<string>? DeviceLost;

    public IReadOnlyList<DiscoveredDevice> KnownDevices => _known.Values.ToList();

    public MdnsDiscoveryService(ILogger<MdnsDiscoveryService> logger)
    {
        _logger = logger;
    }

    public void Start(string deviceId, string deviceName, Platform platform, string appVersion, int port)
    {
        _selfDeviceId = deviceId;

        _mdns = new MulticastService
        {
            // IPv6 multicast is unreliably supported across consumer Wi-Fi/mobile-hotspot
            // gear and Android network stacks — Android's own mDNS discovery is
            // effectively IPv4-only in practice. Dual-stacking here (the library's
            // default) can leave this device invisible to Android peers even though
            // Android-to-Android discovery works fine over IPv4. Force IPv4-only so
            // Windows and Android reliably see each other on the same multicast group.
            UseIpv6 = false,
        };
        _serviceDiscovery = new ServiceDiscovery(_mdns);

        var profile = new ServiceProfile(deviceId, ServiceName, (ushort)port);
        profile.AddProperty("id", deviceId);
        profile.AddProperty("name", deviceName);
        profile.AddProperty("platform", platform == Platform.Windows ? "WINDOWS" : "ANDROID");
        profile.AddProperty("version", appVersion);
        profile.AddProperty("port", port.ToString());

        _serviceDiscovery.Advertise(profile);

        _serviceDiscovery.ServiceInstanceDiscovered += OnServiceInstanceDiscovered;
        _serviceDiscovery.ServiceInstanceShutdown += OnServiceInstanceShutdown;
        _mdns.NetworkInterfaceDiscovered += (_, _) => _serviceDiscovery.QueryServiceInstances(ServiceName);

        try
        {
            _mdns.Start();
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to start mDNS multicast service; discovery will be unavailable.");
        }

        // NetworkInterfaceDiscovered only fires once per interface at startup, so without a
        // repeat query, a peer that wasn't already advertising at that exact moment (e.g. its
        // app was opened afterward) would never be found. Re-query periodically like a normal
        // continuous mDNS browse (Android's NsdManager does this internally).
        _queryTimer = new Timer(
            _ => { try { _serviceDiscovery?.QueryServiceInstances(ServiceName); } catch (Exception ex) { _logger.LogWarning(ex, "Periodic mDNS query failed."); } },
            null,
            QueryInterval,
            QueryInterval);
    }

    private void OnServiceInstanceDiscovered(object? sender, ServiceInstanceDiscoveryEventArgs e)
    {
        try
        {
            var device = ParseDiscoveredDevice(e);
            if (device is null || device.DeviceId == _selfDeviceId)
            {
                return;
            }

            _known[device.DeviceId] = device;
            DeviceFound?.Invoke(this, device);
        }
        catch (Exception ex)
        {
            // A malformed/foreign TXT record on the LAN must never crash discovery.
            _logger.LogWarning(ex, "Failed to parse a discovered mDNS service instance; ignoring it.");
        }
    }

    private void OnServiceInstanceShutdown(object? sender, ServiceInstanceShutdownEventArgs e)
    {
        var instanceName = e.ServiceInstanceName?.ToString();
        var lost = _known.Values.FirstOrDefault(d => instanceName?.Contains(d.DeviceId, StringComparison.OrdinalIgnoreCase) == true);
        if (lost is not null && _known.TryRemove(lost.DeviceId, out _))
        {
            DeviceLost?.Invoke(this, lost.DeviceId);
        }
    }

    private static DiscoveredDevice? ParseDiscoveredDevice(ServiceInstanceDiscoveryEventArgs e)
    {
        var txt = e.Message.AdditionalRecords.OfType<TXTRecord>().FirstOrDefault()
                  ?? e.Message.Answers.OfType<TXTRecord>().FirstOrDefault();
        var srv = e.Message.AdditionalRecords.OfType<SRVRecord>().FirstOrDefault()
                  ?? e.Message.Answers.OfType<SRVRecord>().FirstOrDefault();
        var address = e.Message.AdditionalRecords.OfType<ARecord>().FirstOrDefault()?.Address.ToString()
                      ?? srv?.Target?.ToString();

        if (txt is null)
        {
            return null;
        }

        var props = ParseTxtStrings(txt.Strings);

        if (!props.TryGetValue("id", out var id) || string.IsNullOrWhiteSpace(id))
        {
            return null;
        }

        var name = props.GetValueOrDefault("name", "Unknown Device");
        var platformToken = props.GetValueOrDefault("platform", "WINDOWS");
        var platform = platformToken == "ANDROID" ? Platform.Android : Platform.Windows;
        var version = props.GetValueOrDefault("version", "0.0.0");
        var port = int.TryParse(props.GetValueOrDefault("port"), out var parsedPort)
            ? parsedPort
            : srv?.Port ?? 47531;

        return new DiscoveredDevice(id, Uri.UnescapeDataString(name), platform, version, address ?? "", port);
    }

    private static Dictionary<string, string> ParseTxtStrings(IEnumerable<string> strings)
    {
        var result = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        foreach (var entry in strings)
        {
            var separatorIndex = entry.IndexOf('=');
            if (separatorIndex <= 0)
            {
                continue;
            }

            result[entry[..separatorIndex]] = entry[(separatorIndex + 1)..];
        }

        return result;
    }

    public void Stop()
    {
        _queryTimer?.Dispose();
        _queryTimer = null;

        if (_serviceDiscovery is not null)
        {
            _serviceDiscovery.ServiceInstanceDiscovered -= OnServiceInstanceDiscovered;
            _serviceDiscovery.ServiceInstanceShutdown -= OnServiceInstanceShutdown;
            _serviceDiscovery.Dispose();
            _serviceDiscovery = null;
        }

        _mdns?.Stop();
        _mdns?.Dispose();
        _mdns = null;
    }

    public void Dispose() => Stop();
}
