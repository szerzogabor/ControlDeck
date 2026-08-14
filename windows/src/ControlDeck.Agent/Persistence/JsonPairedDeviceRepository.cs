using System.IO;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Extensions.Logging;

namespace ControlDeck.Agent.Persistence;

/// <summary>Plaintext (non-secret) paired-device metadata as a single JSON array file.</summary>
public sealed class JsonPairedDeviceRepository : IPairedDeviceRepository
{
    private readonly string _filePath;
    private readonly ILogger<JsonPairedDeviceRepository> _logger;
    private readonly object _lock = new();

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
        Converters = { new JsonStringEnumConverter() },
    };

    public JsonPairedDeviceRepository(ILogger<JsonPairedDeviceRepository> logger)
        : this(PersistencePaths.PairedDevicesFile, logger)
    {
    }

    public JsonPairedDeviceRepository(string filePath, ILogger<JsonPairedDeviceRepository> logger)
    {
        _filePath = filePath;
        _logger = logger;
        var directory = Path.GetDirectoryName(_filePath);
        if (!string.IsNullOrEmpty(directory))
        {
            Directory.CreateDirectory(directory);
        }
    }

    public IReadOnlyList<PairedDevice> GetAll()
    {
        lock (_lock)
        {
            return LoadAll();
        }
    }

    public PairedDevice? GetById(string deviceId)
    {
        lock (_lock)
        {
            return LoadAll().FirstOrDefault(d => d.DeviceId == deviceId);
        }
    }

    public void Upsert(PairedDevice device)
    {
        lock (_lock)
        {
            var all = LoadAll().Where(d => d.DeviceId != device.DeviceId).ToList();
            all.Add(device);
            SaveAll(all);
        }
    }

    public void Remove(string deviceId)
    {
        lock (_lock)
        {
            var all = LoadAll().Where(d => d.DeviceId != deviceId).ToList();
            SaveAll(all);
        }
    }

    private List<PairedDevice> LoadAll()
    {
        if (!File.Exists(_filePath))
        {
            return new List<PairedDevice>();
        }

        try
        {
            var json = File.ReadAllText(_filePath);
            return JsonSerializer.Deserialize<List<PairedDevice>>(json, JsonOptions) ?? new List<PairedDevice>();
        }
        catch (Exception ex) when (ex is IOException or JsonException)
        {
            _logger.LogWarning(ex, "Failed to read paired devices file; treating as empty.");
            return new List<PairedDevice>();
        }
    }

    private void SaveAll(List<PairedDevice> devices)
    {
        var json = JsonSerializer.Serialize(devices, JsonOptions);
        var tempPath = _filePath + ".tmp";
        File.WriteAllText(tempPath, json);
        File.Move(tempPath, _filePath, overwrite: true);
    }
}
