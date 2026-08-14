using System.IO;
using System.Runtime.Versioning;
using System.Security.Cryptography;
using System.Text.Json;
using Microsoft.Extensions.Logging;

namespace ControlDeck.Agent.Persistence;

/// <summary>
/// Persists paired-device shared secrets (protocol/PROTOCOL.md §3.2/§7) in a
/// single file, encrypted at rest with Windows DPAPI
/// (<see cref="ProtectedData"/>, user scope) — so the plaintext secret is
/// only ever reconstituted in memory for the current Windows user account,
/// never written to disk or logged. The on-disk container format is:
/// JSON `{ deviceId: base64(secretBytes) }` -&gt; UTF8 bytes -&gt;
/// <see cref="ProtectedData.Protect"/> -&gt; raw file bytes.
/// </summary>
[SupportedOSPlatform("windows")]
public sealed class DpapiSecretStore : ISecretStore
{
    private readonly string _filePath;
    private readonly ILogger<DpapiSecretStore> _logger;
    private readonly object _lock = new();
    private Dictionary<string, string>? _cache; // deviceId -> base64(secret), decrypted in memory only

    public DpapiSecretStore(ILogger<DpapiSecretStore> logger)
        : this(PersistencePaths.SecretsFile, logger)
    {
    }

    public DpapiSecretStore(string filePath, ILogger<DpapiSecretStore> logger)
    {
        _filePath = filePath;
        _logger = logger;
        var directory = Path.GetDirectoryName(_filePath);
        if (!string.IsNullOrEmpty(directory))
        {
            Directory.CreateDirectory(directory);
        }
    }

    public void Set(string deviceId, byte[] sharedSecret)
    {
        lock (_lock)
        {
            var map = LoadDecrypted();
            map[deviceId] = Convert.ToBase64String(sharedSecret);
            SaveEncrypted(map);
        }
    }

    public bool TryGet(string deviceId, out byte[]? sharedSecret)
    {
        lock (_lock)
        {
            var map = LoadDecrypted();
            if (map.TryGetValue(deviceId, out var base64))
            {
                sharedSecret = Convert.FromBase64String(base64);
                return true;
            }

            sharedSecret = null;
            return false;
        }
    }

    public void Remove(string deviceId)
    {
        lock (_lock)
        {
            var map = LoadDecrypted();
            if (map.Remove(deviceId))
            {
                SaveEncrypted(map);
            }
        }
    }

    private Dictionary<string, string> LoadDecrypted()
    {
        if (_cache is not null)
        {
            return _cache;
        }

        if (!File.Exists(_filePath))
        {
            _cache = new Dictionary<string, string>();
            return _cache;
        }

        try
        {
            var protectedBytes = File.ReadAllBytes(_filePath);
            var plainBytes = ProtectedData.Unprotect(protectedBytes, optionalEntropy: null, DataProtectionScope.CurrentUser);
            var json = System.Text.Encoding.UTF8.GetString(plainBytes);
            _cache = JsonSerializer.Deserialize<Dictionary<string, string>>(json) ?? new Dictionary<string, string>();
        }
        catch (Exception ex) when (ex is IOException or CryptographicException or JsonException)
        {
            // NEVER log secret bytes/content here — only the fact that the
            // store was unreadable (e.g. moved to a different Windows user
            // profile, or corrupted). Peers will simply need to re-pair.
            _logger.LogWarning(ex, "Failed to decrypt secrets store; treating it as empty. Paired peers will need to re-pair.");
            _cache = new Dictionary<string, string>();
        }

        return _cache;
    }

    private void SaveEncrypted(Dictionary<string, string> map)
    {
        var json = JsonSerializer.Serialize(map);
        var plainBytes = System.Text.Encoding.UTF8.GetBytes(json);
        var protectedBytes = ProtectedData.Protect(plainBytes, optionalEntropy: null, DataProtectionScope.CurrentUser);

        var tempPath = _filePath + ".tmp";
        File.WriteAllBytes(tempPath, protectedBytes);
        File.Move(tempPath, _filePath, overwrite: true);

        _cache = map;
    }
}
