using System.Diagnostics;
using System.Runtime.Versioning;
using ControlDeck.Agent.Persistence;
using Microsoft.Extensions.Logging;

namespace ControlDeck.Agent.PlatformActions;

/// <summary>
/// Resolves `appId` -> executable path or shell target (e.g.
/// `shell:AppsFolder\PackageFamilyName!AppId`) via <see cref="IAppRegistryRepository"/>,
/// then launches it with <see cref="Process.Start"/>. Filesystem paths never
/// cross the wire (protocol/PROTOCOL.md §8) — only the opaque `appId` does;
/// resolution is entirely local.
/// </summary>
[SupportedOSPlatform("windows")]
public sealed class ProcessAppLauncher : IAppLauncher
{
    private readonly IAppRegistryRepository _appRegistry;
    private readonly ILogger<ProcessAppLauncher> _logger;

    public ProcessAppLauncher(IAppRegistryRepository appRegistry, ILogger<ProcessAppLauncher> logger)
    {
        _appRegistry = appRegistry;
        _logger = logger;
    }

    public AppLaunchResult Launch(string appId)
    {
        var entry = _appRegistry.GetByAppId(appId);
        if (entry is null || string.IsNullOrWhiteSpace(entry.Target))
        {
            _logger.LogWarning("APP_LAUNCH requested unknown or unconfigured appId \"{AppId}\".", appId);
            return AppLaunchResult.AppNotFound;
        }

        try
        {
            using var process = Process.Start(new ProcessStartInfo
            {
                FileName = entry.Target,
                UseShellExecute = true,
            });

            return process is null ? AppLaunchResult.PlatformError : AppLaunchResult.Launched;
        }
        catch (Exception ex) when (ex is System.ComponentModel.Win32Exception or InvalidOperationException or System.IO.FileNotFoundException)
        {
            _logger.LogWarning(ex, "Failed to launch appId \"{AppId}\" (target \"{Target}\").", appId, entry.Target);
            return AppLaunchResult.PlatformError;
        }
    }
}
