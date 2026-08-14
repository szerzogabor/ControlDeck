using System.IO;

namespace ControlDeck.Agent.Persistence;

/// <summary>Centralizes the %LOCALAPPDATA%\ControlDeck\ file layout so no repository hardcodes a path itself.</summary>
public static class PersistencePaths
{
    public static string RootDirectory => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "ControlDeck");

    public static string IdentityFile => Path.Combine(RootDirectory, "identity.json");
    public static string DashboardsDirectory => Path.Combine(RootDirectory, "dashboards");
    public static string PairedDevicesFile => Path.Combine(RootDirectory, "paired_devices.json");

    /// <summary>DPAPI-protected (user-scope) blob — shared secrets never touch disk in plaintext.</summary>
    public static string SecretsFile => Path.Combine(RootDirectory, "secrets.protected");

    public static string AppRegistryFile => Path.Combine(RootDirectory, "app_registry.json");
    public static string PreferencesFile => Path.Combine(RootDirectory, "preferences.json");
    public static string LogFile => Path.Combine(RootDirectory, "logs", "agent.log");

    public static void EnsureRootExists() => Directory.CreateDirectory(RootDirectory);
}
