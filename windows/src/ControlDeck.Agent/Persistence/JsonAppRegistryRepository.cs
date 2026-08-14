using System.IO;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Extensions.Logging;

namespace ControlDeck.Agent.Persistence;

/// <summary>
/// User-editable appId -> launch-target registry. Seeded with a few common
/// placeholders on first run (spotify/discord/chrome) whose `Target` is left
/// blank when a well-known install path can't be found — install locations
/// vary too much to hardcode reliably, so the settings screen is where the
/// user actually points these at their real executables.
/// </summary>
public sealed class JsonAppRegistryRepository : IAppRegistryRepository
{
    private readonly string _filePath;
    private readonly ILogger<JsonAppRegistryRepository> _logger;
    private readonly object _lock = new();

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
    };

    public JsonAppRegistryRepository(ILogger<JsonAppRegistryRepository> logger)
        : this(PersistencePaths.AppRegistryFile, logger)
    {
    }

    public JsonAppRegistryRepository(string filePath, ILogger<JsonAppRegistryRepository> logger)
    {
        _filePath = filePath;
        _logger = logger;
        var directory = Path.GetDirectoryName(_filePath);
        if (!string.IsNullOrEmpty(directory))
        {
            Directory.CreateDirectory(directory);
        }
    }

    public IReadOnlyList<AppRegistryEntry> GetAll()
    {
        lock (_lock)
        {
            return LoadAll();
        }
    }

    public AppRegistryEntry? GetByAppId(string appId)
    {
        lock (_lock)
        {
            return LoadAll().FirstOrDefault(e => e.AppId == appId);
        }
    }

    public void Upsert(AppRegistryEntry entry)
    {
        lock (_lock)
        {
            var all = LoadAll().Where(e => e.AppId != entry.AppId).ToList();
            all.Add(entry);
            SaveAll(all);
        }
    }

    public void Remove(string appId)
    {
        lock (_lock)
        {
            var all = LoadAll().Where(e => e.AppId != appId).ToList();
            SaveAll(all);
        }
    }

    private List<AppRegistryEntry> LoadAll()
    {
        if (!File.Exists(_filePath))
        {
            var defaults = DefaultRegistry();
            SaveAll(defaults);
            return defaults;
        }

        try
        {
            var json = File.ReadAllText(_filePath);
            return JsonSerializer.Deserialize<List<AppRegistryEntry>>(json, JsonOptions) ?? new List<AppRegistryEntry>();
        }
        catch (Exception ex) when (ex is IOException or JsonException)
        {
            _logger.LogWarning(ex, "Failed to read app registry file; treating as empty.");
            return new List<AppRegistryEntry>();
        }
    }

    private void SaveAll(List<AppRegistryEntry> entries)
    {
        var json = JsonSerializer.Serialize(entries, JsonOptions);
        var tempPath = _filePath + ".tmp";
        File.WriteAllText(tempPath, json);
        File.Move(tempPath, _filePath, overwrite: true);
    }

    /// <summary>
    /// A handful of common apps, left with a placeholder/empty `Target` for
    /// the user to fill in via the settings screen since install paths vary
    /// by machine (per-user vs per-machine installs, custom drive letters,
    /// MSIX vs classic installers, etc.) — hardcoding a guessed path would
    /// silently fail on most machines.
    /// </summary>
    private static List<AppRegistryEntry> DefaultRegistry() => new()
    {
        new AppRegistryEntry("spotify", "Spotify", string.Empty),
        new AppRegistryEntry("discord", "Discord", string.Empty),
        new AppRegistryEntry("chrome", "Google Chrome", string.Empty),
    };
}
