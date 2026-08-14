using System.IO;
using System.Reflection;
using System.Text.Json;
using System.Text.Json.Serialization;
using ControlDeck.Domain;
using Microsoft.Extensions.Logging;

namespace ControlDeck.Agent.Identity;

/// <summary>Persisted local device identity, stored plaintext (non-secret) under %LOCALAPPDATA%\ControlDeck\identity.json.</summary>
public sealed record DeviceIdentity(
    string DeviceId,
    string DeviceName
);

/// <summary>
/// Loads/creates the persistent device identity. `DeviceId` is generated
/// once (a GUID) and never changes thereafter; `DeviceName` defaults to
/// `Environment.MachineName` but is user-editable.
/// </summary>
public sealed class DeviceIdentityStore
{
    private readonly string _filePath;
    private readonly ILogger<DeviceIdentityStore> _logger;
    private readonly object _lock = new();
    private DeviceIdentity? _cached;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
    };

    public DeviceIdentityStore(ILogger<DeviceIdentityStore> logger)
        : this(DefaultFilePath(), logger)
    {
    }

    public DeviceIdentityStore(string filePath, ILogger<DeviceIdentityStore> logger)
    {
        _filePath = filePath;
        _logger = logger;
    }

    public static string DefaultFilePath() => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "ControlDeck",
        "identity.json");

    public DeviceIdentity Load()
    {
        lock (_lock)
        {
            if (_cached is not null)
            {
                return _cached;
            }

            if (File.Exists(_filePath))
            {
                try
                {
                    var json = File.ReadAllText(_filePath);
                    var loaded = JsonSerializer.Deserialize<DeviceIdentity>(json, JsonOptions);
                    if (loaded is not null && !string.IsNullOrWhiteSpace(loaded.DeviceId))
                    {
                        _cached = loaded;
                        return loaded;
                    }
                }
                catch (Exception ex) when (ex is IOException or JsonException)
                {
                    _logger.LogWarning(ex, "Failed to read identity.json; generating a new identity.");
                }
            }

            var created = new DeviceIdentity(DeviceId.NewId().Value, Environment.MachineName);
            Save(created);
            _cached = created;
            return created;
        }
    }

    public void SaveDeviceName(string newName)
    {
        lock (_lock)
        {
            var current = Load();
            var updated = current with { DeviceName = newName };
            Save(updated);
            _cached = updated;
        }
    }

    private void Save(DeviceIdentity identity)
    {
        var directory = Path.GetDirectoryName(_filePath);
        if (!string.IsNullOrEmpty(directory))
        {
            Directory.CreateDirectory(directory);
        }

        var json = JsonSerializer.Serialize(identity, JsonOptions);
        File.WriteAllText(_filePath, json);
    }

    /// <summary>Current app version, read from the assembly informational/file version.</summary>
    public static string AppVersion =>
        Assembly.GetExecutingAssembly().GetName().Version?.ToString() ?? "0.1.0";
}
