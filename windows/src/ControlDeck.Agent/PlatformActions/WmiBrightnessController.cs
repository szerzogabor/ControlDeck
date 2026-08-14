using System.Management;
using System.Runtime.Versioning;
using Microsoft.Extensions.Logging;

namespace ControlDeck.Agent.PlatformActions;

/// <summary>
/// Reads/sets brightness via the `root\wmi` WMI classes
/// `WmiMonitorBrightness` (current level + supported levels) and
/// `WmiMonitorBrightnessMethods` (`WmiSetBrightness`).
///
/// KNOWN LIMITATION: this only works for laptop/internal panels or
/// DDC/CI-capable external monitors whose driver exposes these WMI classes —
/// many external desktop monitors do not. `IsAvailable` reflects a real WMI
/// query done at startup (`root\wmi` / `SELECT * FROM WmiMonitorBrightness`)
/// rather than being hardcoded true, so BRIGHTNESS is only advertised as a
/// supported capability when this device can actually act on it.
/// </summary>
[SupportedOSPlatform("windows")]
public sealed class WmiBrightnessController : IBrightnessController
{
    private const string Scope = @"root\wmi";
    private readonly ILogger<WmiBrightnessController> _logger;
    private readonly bool _available;

    public WmiBrightnessController(ILogger<WmiBrightnessController> logger)
    {
        _logger = logger;
        _available = ProbeAvailability();
    }

    public bool IsAvailable => _available;

    public int GetBrightness()
    {
        if (!_available)
        {
            return 0;
        }

        try
        {
            using var searcher = new ManagementObjectSearcher(Scope, "SELECT * FROM WmiMonitorBrightness");
            foreach (ManagementObject instance in searcher.Get())
            {
                using (instance)
                {
                    return Convert.ToInt32(instance["CurrentBrightness"]);
                }
            }
        }
        catch (ManagementException ex)
        {
            _logger.LogWarning(ex, "Failed to read brightness via WMI.");
        }

        return 0;
    }

    public void SetBrightness(int value)
    {
        if (!_available)
        {
            return;
        }

        var clamped = Math.Clamp(value, 0, 100);

        try
        {
            using var searcher = new ManagementObjectSearcher(Scope, "SELECT * FROM WmiMonitorBrightnessMethods");
            foreach (ManagementObject instance in searcher.Get())
            {
                using (instance)
                {
                    // WmiSetBrightness(UInt32 Timeout, Byte Brightness)
                    instance.InvokeMethod("WmiSetBrightness", new object[] { 0u, (byte)clamped });
                }
            }
        }
        catch (ManagementException ex)
        {
            _logger.LogWarning(ex, "Failed to set brightness to {Value} via WMI.", clamped);
        }
    }

    private bool ProbeAvailability()
    {
        try
        {
            using var searcher = new ManagementObjectSearcher(Scope, "SELECT * FROM WmiMonitorBrightness");
            using var results = searcher.Get();
            foreach (ManagementObject instance in results)
            {
                instance.Dispose();
                return true; // at least one brightness-capable display was found
            }

            return false;
        }
        catch (ManagementException ex)
        {
            _logger.LogInformation(ex, "WmiMonitorBrightness is not available on this machine; BRIGHTNESS capability will not be advertised.");
            return false;
        }
    }
}
