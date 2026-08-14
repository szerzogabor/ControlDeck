using System.IO;
using System.Text.Json;
using System.Text.Json.Serialization;
using ControlDeck.Domain;
using Microsoft.Extensions.Logging;

namespace ControlDeck.Agent.Persistence;

/// <summary>User preferences that aren't identity and aren't a dashboard document.</summary>
public sealed record Preferences(
    int WebSocketPort = 47531,
    ReconnectPolicy DefaultReconnectPolicy = ReconnectPolicy.SyncGroupState,
    bool StartMinimizedToTray = false
);

public interface IPreferencesRepository
{
    Preferences Load();

    void Save(Preferences preferences);
}

public sealed class JsonPreferencesRepository : IPreferencesRepository
{
    private readonly string _filePath;
    private readonly ILogger<JsonPreferencesRepository> _logger;
    private readonly object _lock = new();

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        Converters = { new JsonStringEnumConverter() },
    };

    public JsonPreferencesRepository(ILogger<JsonPreferencesRepository> logger)
        : this(PersistencePaths.PreferencesFile, logger)
    {
    }

    public JsonPreferencesRepository(string filePath, ILogger<JsonPreferencesRepository> logger)
    {
        _filePath = filePath;
        _logger = logger;
        var directory = Path.GetDirectoryName(_filePath);
        if (!string.IsNullOrEmpty(directory))
        {
            Directory.CreateDirectory(directory);
        }
    }

    public Preferences Load()
    {
        lock (_lock)
        {
            if (!File.Exists(_filePath))
            {
                return new Preferences();
            }

            try
            {
                var json = File.ReadAllText(_filePath);
                return JsonSerializer.Deserialize<Preferences>(json, JsonOptions) ?? new Preferences();
            }
            catch (Exception ex) when (ex is IOException or JsonException)
            {
                _logger.LogWarning(ex, "Failed to read preferences file; using defaults.");
                return new Preferences();
            }
        }
    }

    public void Save(Preferences preferences)
    {
        lock (_lock)
        {
            var json = JsonSerializer.Serialize(preferences, JsonOptions);
            var tempPath = _filePath + ".tmp";
            File.WriteAllText(tempPath, json);
            File.Move(tempPath, _filePath, overwrite: true);
        }
    }
}
