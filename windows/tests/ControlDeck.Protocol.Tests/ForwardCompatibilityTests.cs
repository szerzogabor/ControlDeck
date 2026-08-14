using ControlDeck.Protocol;
using Xunit;

namespace ControlDeck.Protocol.Tests;

/// <summary>
/// protocol/PROTOCOL.md §2/§6: unknown JSON fields must be ignored, unknown
/// `type` values must decode to something usable rather than throwing, and
/// unknown enum-ish string values (e.g. a future capability) must not crash
/// decoding either.
/// </summary>
public class ForwardCompatibilityTests
{
    [Fact]
    public void UnknownExtraFields_AreIgnored()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "DEVICE_INFO",
          "messageId": "msg-1",
          "sourceDeviceId": "pc-id",
          "timestamp": 1723600000000,
          "fromTheFuture": { "nested": true },
          "payload": {
            "deviceId": "pc-id",
            "deviceName": "Gaming PC",
            "platform": "WINDOWS",
            "appVersion": "0.1.0",
            "unexpectedField": 42,
            "anotherOne": [1, 2, 3]
          }
        }
        """;

        var envelope = ProtocolCodec.Decode(json);

        var payload = Assert.IsType<DeviceInfoPayload>(envelope.Payload);
        Assert.Equal("pc-id", payload.DeviceId);
        Assert.Equal("Gaming PC", payload.DeviceName);
    }

    [Fact]
    public void UnrecognizedMessageType_DecodesToUnknownPayload_InsteadOfThrowing()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "FUTURE_MESSAGE_TYPE",
          "messageId": "msg-2",
          "sourceDeviceId": "pc-id",
          "timestamp": 1723600000000,
          "payload": { "someFutureField": "someFutureValue" }
        }
        """;

        var envelope = ProtocolCodec.Decode(json);

        Assert.Equal("FUTURE_MESSAGE_TYPE", envelope.Type);
        var payload = Assert.IsType<UnknownPayload>(envelope.Payload);
        Assert.Equal("FUTURE_MESSAGE_TYPE", payload.RawType);
        Assert.Contains("someFutureField", payload.RawJson);
    }

    [Fact]
    public void UnrecognizedActionType_DecodesToUnknownActionDto_InsteadOfThrowing()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "ACTION",
          "messageId": "msg-3",
          "sourceDeviceId": "tablet-id",
          "targetDeviceId": "pc-id",
          "timestamp": 1723600000000,
          "payload": { "action": { "type": "FUTURE_ACTION", "someField": 1 } }
        }
        """;

        var envelope = ProtocolCodec.Decode(json);

        var payload = Assert.IsType<ActionPayload>(envelope.Payload);
        var action = Assert.IsType<UnknownActionDto>(payload.Action);
        Assert.Equal("FUTURE_ACTION", action.RawType);
    }

    [Fact]
    public void UnrecognizedCapabilityToken_DoesNotCrashDecode()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "CAPABILITIES",
          "messageId": "msg-4",
          "sourceDeviceId": "pc-id",
          "timestamp": 1723600000000,
          "payload": {
            "deviceId": "pc-id",
            "capabilities": ["VOLUME", "HOLOGRAM_PROJECTION"],
            "apps": []
          }
        }
        """;

        var envelope = ProtocolCodec.Decode(json);

        var payload = Assert.IsType<CapabilitiesPayload>(envelope.Payload);
        Assert.Contains("VOLUME", payload.Capabilities);
        Assert.Contains("HOLOGRAM_PROJECTION", payload.Capabilities); // preserved raw; mapper decides to ignore it
    }

    [Fact]
    public void MissingSourceAndTargetDeviceId_AreTreatedAsOptional()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "PING",
          "messageId": "msg-5",
          "timestamp": 1723600000000
        }
        """;

        var envelope = ProtocolCodec.Decode(json);

        Assert.Null(envelope.SourceDeviceId);
        Assert.Null(envelope.TargetDeviceId);
    }
}
