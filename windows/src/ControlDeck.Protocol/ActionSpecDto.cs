using System.Text.Json;
using System.Text.Json.Serialization;

namespace ControlDeck.Protocol;

/// <summary>
/// Wire shape of an Action / ActionResult.resultingState / StateUpdate value,
/// per protocol/PROTOCOL.md §3.5-3.6. Closed hierarchy: base constructor is
/// `private protected` so only the sealed records declared in this file can
/// derive from it.
/// </summary>
[JsonConverter(typeof(ActionSpecDtoJsonConverter))]
public abstract record ActionSpecDto
{
    private protected ActionSpecDto()
    {
    }
}

public sealed record BrightnessSetDto(int Value) : ActionSpecDto;

public sealed record VolumeSetDto(int Value) : ActionSpecDto;

public sealed record SetMutedDto(bool Muted) : ActionSpecDto;

public sealed record MediaSetStateDto(WireMediaState State) : ActionSpecDto;

public sealed record MediaNextDto : ActionSpecDto;

public sealed record MediaPreviousDto : ActionSpecDto;

public sealed record AppLaunchDto(string AppId) : ActionSpecDto;

/// <summary>
/// Fallback for an action `type` this build doesn't recognize (forward
/// compatibility, protocol/PROTOCOL.md §6). Callers should treat this the
/// same way an unsupported capability is treated — decode succeeds, dispatch
/// fails cleanly (e.g. ACTION_RESULT with errorCode UNSUPPORTED_CAPABILITY)
/// — rather than the connection crashing on an unrecognized action.
/// </summary>
public sealed record UnknownActionDto(string RawType) : ActionSpecDto;

/// <summary>
/// (De)serializes the ACTION_TYPE-discriminated <see cref="ActionSpecDto"/>
/// union, e.g. <c>{ "type": "BRIGHTNESS_SET", "value": 70 }</c>. This is a
/// nested polymorphic shape (the discriminator lives alongside the payload's
/// own fields, not in an enclosing envelope), so it's handled with its own
/// converter distinct from <see cref="EnvelopeJsonConverter"/>.
/// </summary>
public sealed class ActionSpecDtoJsonConverter : JsonConverter<ActionSpecDto>
{
    public override ActionSpecDto Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        using var doc = JsonDocument.ParseValue(ref reader);
        return ReadFromElement(doc.RootElement, options);
    }

    internal static ActionSpecDto ReadFromElement(JsonElement element, JsonSerializerOptions options)
    {
        if (element.ValueKind != JsonValueKind.Object || !element.TryGetProperty("type", out var typeProp))
        {
            throw new JsonException("Action object is missing required \"type\" field.");
        }

        var type = typeProp.GetString() ?? throw new JsonException("Action \"type\" field must be a string.");

        return type switch
        {
            "BRIGHTNESS_SET" => RequireField(element, "value", v => new BrightnessSetDto(v.GetInt32())),
            "VOLUME_SET" => RequireField(element, "value", v => new VolumeSetDto(v.GetInt32())),
            "SET_MUTED" => RequireField(element, "muted", v => new SetMutedDto(v.GetBoolean())),
            "MEDIA_SET_STATE" => RequireField(element, "state", v => new MediaSetStateDto(ParseMediaState(v))),
            "MEDIA_NEXT" => new MediaNextDto(),
            "MEDIA_PREVIOUS" => new MediaPreviousDto(),
            "APP_LAUNCH" => RequireField(element, "appId", v =>
                new AppLaunchDto(v.GetString() ?? throw new JsonException("\"appId\" must be a string."))),
            _ => new UnknownActionDto(type)
        };
    }

    private static WireMediaState ParseMediaState(JsonElement value)
    {
        var raw = value.GetString() ?? throw new JsonException("\"state\" must be a string.");
        return raw switch
        {
            "PLAYING" => WireMediaState.Playing,
            "PAUSED" => WireMediaState.Paused,
            _ => throw new JsonException($"Unknown media state \"{raw}\".")
        };
    }

    private static T RequireField<T>(JsonElement element, string fieldName, Func<JsonElement, T> project)
    {
        if (!element.TryGetProperty(fieldName, out var value))
        {
            throw new JsonException($"Action is missing required field \"{fieldName}\".");
        }

        return project(value);
    }

    public override void Write(Utf8JsonWriter writer, ActionSpecDto value, JsonSerializerOptions options)
    {
        writer.WriteStartObject();

        switch (value)
        {
            case BrightnessSetDto d:
                writer.WriteString("type", "BRIGHTNESS_SET");
                writer.WriteNumber("value", d.Value);
                break;
            case VolumeSetDto d:
                writer.WriteString("type", "VOLUME_SET");
                writer.WriteNumber("value", d.Value);
                break;
            case SetMutedDto d:
                writer.WriteString("type", "SET_MUTED");
                writer.WriteBoolean("muted", d.Muted);
                break;
            case MediaSetStateDto d:
                writer.WriteString("type", "MEDIA_SET_STATE");
                writer.WriteString("state", d.State == WireMediaState.Playing ? "PLAYING" : "PAUSED");
                break;
            case MediaNextDto:
                writer.WriteString("type", "MEDIA_NEXT");
                break;
            case MediaPreviousDto:
                writer.WriteString("type", "MEDIA_PREVIOUS");
                break;
            case AppLaunchDto d:
                writer.WriteString("type", "APP_LAUNCH");
                writer.WriteString("appId", d.AppId);
                break;
            case UnknownActionDto d:
                writer.WriteString("type", d.RawType);
                break;
            default:
                throw new JsonException($"Unknown ActionSpecDto subtype: {value.GetType().Name}");
        }

        writer.WriteEndObject();
    }
}
