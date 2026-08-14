using System.Reflection;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace ControlDeck.Protocol;

/// <summary>
/// The common envelope shared by every ControlDeck message, per
/// protocol/PROTOCOL.md §2. `Payload`'s concrete type is resolved from the
/// sibling `Type` string discriminator by <see cref="EnvelopeJsonConverter"/>.
/// </summary>
[JsonConverter(typeof(EnvelopeJsonConverter))]
public sealed record Envelope(
    int ProtocolVersion,
    string Type,
    string MessageId,
    string? SourceDeviceId,
    string? TargetDeviceId,
    long Timestamp,
    MessagePayload Payload
)
{
    public static Envelope Create(
        string type,
        MessagePayload payload,
        string sourceDeviceId,
        string? targetDeviceId = null,
        int protocolVersion = ProtocolConstants.CurrentVersion,
        string? messageId = null,
        long? timestamp = null) =>
        new(
            protocolVersion,
            type,
            messageId ?? Guid.NewGuid().ToString(),
            sourceDeviceId,
            targetDeviceId,
            timestamp ?? DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
            payload);
}

public static class ProtocolConstants
{
    public const int CurrentVersion = 1;
}

/// <summary>
/// The known envelope `type` string discriminators, per
/// protocol/PROTOCOL.md §3. Kept as plain string constants (not an enum) so
/// the wire representation and the "unknown type" fallback path share one
/// obviously-correct source of truth.
/// </summary>
public static class MessageType
{
    public const string Hello = "HELLO";
    public const string PairRequest = "PAIR_REQUEST";
    public const string PairResponse = "PAIR_RESPONSE";
    public const string Auth = "AUTH";
    public const string AuthResult = "AUTH_RESULT";
    public const string DeviceInfo = "DEVICE_INFO";
    public const string Capabilities = "CAPABILITIES";
    public const string Action = "ACTION";
    public const string ActionResult = "ACTION_RESULT";
    public const string StateUpdate = "STATE_UPDATE";
    public const string DashboardSync = "DASHBOARD_SYNC";
    public const string DashboardAck = "DASHBOARD_ACK";
    public const string Error = "ERROR";
    public const string Ping = "PING";
    public const string Pong = "PONG";
}

/// <summary>
/// Decodes/encodes the <see cref="Envelope"/> shape. On read, peeks at the
/// `type` field to decide which concrete <see cref="MessagePayload"/> to
/// deserialize `payload` into (per protocol/PROTOCOL.md §2 — this is why it's
/// a custom converter rather than `[JsonPolymorphic]`: the discriminator
/// lives in the *enclosing* object, not inside `payload` itself).
///
/// Unrecognized `type` values decode to <see cref="UnknownPayload"/> rather
/// than throwing (§2's forward-compat requirement). Malformed/missing
/// required fields throw <see cref="JsonException"/>, which
/// <see cref="ProtocolCodec"/> translates into a <see cref="ProtocolDecodeException"/>
/// for callers to catch — never an unhandled crash.
/// </summary>
public sealed class EnvelopeJsonConverter : JsonConverter<Envelope>
{
    public override Envelope Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        using var doc = JsonDocument.ParseValue(ref reader);
        var root = doc.RootElement;

        if (!root.TryGetProperty("protocolVersion", out var protocolVersionEl))
        {
            throw new JsonException("Envelope is missing required field \"protocolVersion\".");
        }

        if (!root.TryGetProperty("type", out var typeEl) || typeEl.GetString() is not { Length: > 0 } type)
        {
            throw new JsonException("Envelope is missing required field \"type\".");
        }

        if (!root.TryGetProperty("messageId", out var messageIdEl) || messageIdEl.GetString() is not { Length: > 0 } messageId)
        {
            throw new JsonException("Envelope is missing required field \"messageId\".");
        }

        if (!root.TryGetProperty("timestamp", out var timestampEl))
        {
            throw new JsonException("Envelope is missing required field \"timestamp\".");
        }

        var sourceDeviceId = TryGetOptionalString(root, "sourceDeviceId");
        var targetDeviceId = TryGetOptionalString(root, "targetDeviceId");

        var payloadElement = root.TryGetProperty("payload", out var payloadEl) ? payloadEl : default;

        var payload = DecodePayload(type, payloadElement, options);

        return new Envelope(
            protocolVersionEl.GetInt32(),
            type,
            messageId,
            sourceDeviceId,
            targetDeviceId,
            timestampEl.GetInt64(),
            payload);
    }

    public override void Write(Utf8JsonWriter writer, Envelope value, JsonSerializerOptions options)
    {
        writer.WriteStartObject();
        writer.WriteNumber("protocolVersion", value.ProtocolVersion);
        writer.WriteString("type", value.Type);
        writer.WriteString("messageId", value.MessageId);

        if (value.SourceDeviceId is not null)
        {
            writer.WriteString("sourceDeviceId", value.SourceDeviceId);
        }

        if (value.TargetDeviceId is not null)
        {
            writer.WriteString("targetDeviceId", value.TargetDeviceId);
        }

        writer.WriteNumber("timestamp", value.Timestamp);

        writer.WritePropertyName("payload");
        WritePayload(writer, value.Payload, options);

        writer.WriteEndObject();
    }

    private static void WritePayload(Utf8JsonWriter writer, MessagePayload payload, JsonSerializerOptions options)
    {
        switch (payload)
        {
            case StateUpdatePayload stateUpdate:
                // Flattened per protocol/PROTOCOL.md §3.6: the payload IS the
                // resultingState-shaped object, no extra wrapper.
                JsonSerializer.Serialize(writer, stateUpdate.State, options);
                break;
            case EmptyPayload:
                writer.WriteStartObject();
                writer.WriteEndObject();
                break;
            case UnknownPayload unknown:
                using (var doc = JsonDocument.Parse(unknown.RawJson))
                {
                    doc.RootElement.WriteTo(writer);
                }

                break;
            default:
                JsonSerializer.Serialize(writer, payload, payload.GetType(), options);
                break;
        }
    }

    private static MessagePayload DecodePayload(string type, JsonElement payloadElement, JsonSerializerOptions options)
    {
        return type switch
        {
            MessageType.Hello => Deserialize<HelloPayload>(type, payloadElement, options),
            MessageType.PairRequest => Deserialize<PairRequestPayload>(type, payloadElement, options),
            MessageType.PairResponse => Deserialize<PairResponsePayload>(type, payloadElement, options),
            MessageType.Auth => Deserialize<AuthPayload>(type, payloadElement, options),
            MessageType.AuthResult => Deserialize<AuthResultPayload>(type, payloadElement, options),
            MessageType.DeviceInfo => Deserialize<DeviceInfoPayload>(type, payloadElement, options),
            MessageType.Capabilities => Deserialize<CapabilitiesPayload>(type, payloadElement, options),
            MessageType.Action => Deserialize<ActionPayload>(type, payloadElement, options),
            MessageType.ActionResult => Deserialize<ActionResultPayload>(type, payloadElement, options),
            MessageType.StateUpdate => new StateUpdatePayload(RequirePayload(type, payloadElement, el => ActionSpecDtoJsonConverter.ReadFromElement(el, options))),
            MessageType.DashboardSync => Deserialize<DashboardSyncPayload>(type, payloadElement, options),
            MessageType.DashboardAck => Deserialize<DashboardAckPayload>(type, payloadElement, options),
            MessageType.Error => Deserialize<ErrorPayload>(type, payloadElement, options),
            MessageType.Ping => EmptyPayload.Instance,
            MessageType.Pong => EmptyPayload.Instance,
            _ => new UnknownPayload(
                type,
                payloadElement.ValueKind == JsonValueKind.Undefined ? "{}" : payloadElement.GetRawText())
        };
    }

    private static T Deserialize<T>(string type, JsonElement element, JsonSerializerOptions options) where T : MessagePayload
    {
        return RequirePayload(type, element, el =>
        {
            var result = el.Deserialize<T>(options)
                ?? throw new JsonException($"Message of type \"{type}\" has a \"payload\" that decoded to null.");
            RequiredFieldValidator.Validate(result);
            return result;
        });
    }

    private static T RequirePayload<T>(string type, JsonElement element, Func<JsonElement, T> project)
    {
        if (element.ValueKind == JsonValueKind.Undefined)
        {
            throw new JsonException($"Message of type \"{type}\" is missing its required \"payload\" field.");
        }

        return project(element);
    }

    private static string? TryGetOptionalString(JsonElement root, string propertyName)
    {
        if (!root.TryGetProperty(propertyName, out var el) || el.ValueKind == JsonValueKind.Null)
        {
            return null;
        }

        return el.GetString();
    }
}

/// <summary>
/// Generic "no required field is silently null" guard. Positional records
/// don't enforce presence of `string` constructor arguments during JSON
/// deserialization (a missing field just binds `null!`), so this reflects
/// over the decoded payload and rejects any non-nullable `string` property
/// that came back null — turning a silent data-integrity bug into an
/// immediate, catchable <see cref="JsonException"/> (wrapped by
/// <see cref="ProtocolCodec"/> into <see cref="ProtocolDecodeException"/>).
/// </summary>
internal static class RequiredFieldValidator
{
    private static readonly NullabilityInfoContext NullabilityContext = new();

    public static void Validate(object instance)
    {
        foreach (var property in instance.GetType().GetProperties(BindingFlags.Public | BindingFlags.Instance))
        {
            if (property.PropertyType != typeof(string))
            {
                continue;
            }

            var nullability = NullabilityContext.Create(property);
            if (nullability.WriteState == NullabilityState.NotNull && property.GetValue(instance) is null)
            {
                throw new JsonException(
                    $"{instance.GetType().Name}.{property.Name} is required but was missing from the payload.");
            }
        }
    }
}
