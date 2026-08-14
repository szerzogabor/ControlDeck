using System.IO;
using ControlDeck.Domain;
using ControlDeck.Protocol;
using Microsoft.Extensions.Logging;

namespace ControlDeck.Agent.Persistence;

/// <summary>
/// One JSON file per dashboard under %LOCALAPPDATA%\ControlDeck\dashboards\{id}.json,
/// using the same DTO shape (and therefore the same battle-tested
/// (de)serialization, via <see cref="ProtocolCodec"/>'s options) as the wire
/// format. All disk I/O is wrapped so a corrupt/unreadable file never
/// crashes the app — it's logged and treated as "dashboard not found".
/// </summary>
public sealed class JsonDashboardRepository : IDashboardRepository
{
    private readonly string _directory;
    private readonly ILogger<JsonDashboardRepository> _logger;
    private readonly object _lock = new();

    public JsonDashboardRepository(ILogger<JsonDashboardRepository> logger)
        : this(PersistencePaths.DashboardsDirectory, logger)
    {
    }

    public JsonDashboardRepository(string directory, ILogger<JsonDashboardRepository> logger)
    {
        _directory = directory;
        _logger = logger;
        Directory.CreateDirectory(_directory);
    }

    public IReadOnlyList<Dashboard> GetAll()
    {
        lock (_lock)
        {
            var results = new List<Dashboard>();
            foreach (var file in SafeEnumerateFiles())
            {
                var dashboard = TryLoad(file);
                if (dashboard is not null)
                {
                    results.Add(dashboard);
                }
            }

            return results;
        }
    }

    public Dashboard? GetById(DashboardId id)
    {
        lock (_lock)
        {
            var path = PathFor(id);
            return File.Exists(path) ? TryLoad(path) : null;
        }
    }

    public void Upsert(Dashboard dashboard)
    {
        lock (_lock)
        {
            var dto = DashboardMapper.ToDto(dashboard);
            var json = ProtocolCodecJsonForDashboards.Serialize(dto);
            var path = PathFor(dashboard.Id);
            var tempPath = path + ".tmp";

            // Write-to-temp-then-move keeps a crash mid-write from corrupting the existing file.
            File.WriteAllText(tempPath, json);
            File.Move(tempPath, path, overwrite: true);
        }
    }

    public void Delete(DashboardId id)
    {
        lock (_lock)
        {
            var path = PathFor(id);
            if (File.Exists(path))
            {
                File.Delete(path);
            }
        }
    }

    private string PathFor(DashboardId id) => Path.Combine(_directory, $"{id.Value}.json");

    private IEnumerable<string> SafeEnumerateFiles()
    {
        try
        {
            return Directory.EnumerateFiles(_directory, "*.json");
        }
        catch (IOException ex)
        {
            _logger.LogWarning(ex, "Failed to enumerate dashboards directory {Directory}.", _directory);
            return Array.Empty<string>();
        }
    }

    private Dashboard? TryLoad(string path)
    {
        try
        {
            var json = File.ReadAllText(path);
            var dto = ProtocolCodecJsonForDashboards.Deserialize(json);
            return DashboardMapper.ToDomain(dto);
        }
        catch (Exception ex) when (ex is IOException or InvalidOperationException || ex.GetType().Name == "JsonException")
        {
            _logger.LogWarning(ex, "Failed to load dashboard file {Path}; skipping it.", path);
            return null;
        }
    }
}

/// <summary>Thin wrapper reusing <see cref="ProtocolCodec"/>'s JSON options for standalone DashboardDto (de)serialization on disk (not wrapped in an Envelope).</summary>
internal static class ProtocolCodecJsonForDashboards
{
    public static string Serialize(DashboardDto dto) =>
        System.Text.Json.JsonSerializer.Serialize(dto, ProtocolCodec.Options);

    public static DashboardDto Deserialize(string json) =>
        System.Text.Json.JsonSerializer.Deserialize<DashboardDto>(json, ProtocolCodec.Options)
        ?? throw new InvalidOperationException("Dashboard JSON decoded to null.");
}
