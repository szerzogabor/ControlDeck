using System.Text.Json;
using System.Text.Json.Serialization;

namespace ControlDeck.Protocol;

/// <summary>
/// Single entry point for (de)serializing ControlDeck wire messages. Always
/// use this rather than calling <see cref="JsonSerializer"/> directly so the
/// shared <see cref="Options"/> (naming policy, enum casing, converters) and
/// the malformed-payload -&gt; <see cref="ProtocolDecodeException"/>
/// translation stay centralized.
/// </summary>
public static class ProtocolCodec
{
    /// <summary>
    /// - camelCase property names to match protocol/PROTOCOL.md's JSON examples.
    /// - Enum values serialize as UPPER_SNAKE_CASE (e.g. WirePlatform.Windows -> "WINDOWS",
    ///   WireMediaState.Playing -> "PLAYING") to match the wire literals in the spec.
    /// - Unknown JSON properties are ignored by default (System.Text.Json's default
    ///   UnmappedMemberHandling is Skip), satisfying §2's forward-compat requirement.
    /// - Null optional fields are omitted on write for a smaller wire payload.
    /// </summary>
    public static readonly JsonSerializerOptions Options = CreateOptions();

    private static JsonSerializerOptions CreateOptions()
    {
        var options = new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
            DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
            WriteIndented = false,
            ReadCommentHandling = JsonCommentHandling.Skip,
        };

        options.Converters.Add(new JsonStringEnumConverter(JsonNamingPolicy.SnakeCaseUpper));

        return options;
    }

    public static string Encode(Envelope envelope) => JsonSerializer.Serialize(envelope, Options);

    /// <summary>
    /// Decodes a raw JSON text frame into an <see cref="Envelope"/>.
    /// Throws <see cref="ProtocolDecodeException"/> (never a raw
    /// <see cref="JsonException"/> or any other unhandled exception) on
    /// malformed JSON or a payload missing a required field — callers should
    /// catch this and respond with ERROR/MALFORMED_PAYLOAD per
    /// protocol/PROTOCOL.md §3.8 rather than letting it crash the connection.
    /// </summary>
    public static Envelope Decode(string json)
    {
        try
        {
            return JsonSerializer.Deserialize<Envelope>(json, Options)
                ?? throw new ProtocolDecodeException("Envelope JSON decoded to null.");
        }
        catch (ProtocolDecodeException)
        {
            throw;
        }
        catch (JsonException ex)
        {
            throw new ProtocolDecodeException($"Malformed ControlDeck protocol message: {ex.Message}", ex);
        }
    }

    /// <summary>Non-throwing variant of <see cref="Decode"/> for hot paths that don't want exception overhead.</summary>
    public static bool TryDecode(string json, out Envelope? envelope, out string? error)
    {
        try
        {
            envelope = Decode(json);
            error = null;
            return true;
        }
        catch (ProtocolDecodeException ex)
        {
            envelope = null;
            error = ex.Message;
            return false;
        }
    }
}
