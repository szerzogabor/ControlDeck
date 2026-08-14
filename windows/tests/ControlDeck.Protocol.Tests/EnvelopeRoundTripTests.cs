using ControlDeck.Protocol;
using Xunit;

namespace ControlDeck.Protocol.Tests;

/// <summary>
/// Round-trip tests for every message type in protocol/PROTOCOL.md §3, using
/// JSON fixtures that mirror the literal examples in that document.
/// </summary>
public class EnvelopeRoundTripTests
{
    // protocol/PROTOCOL.md §3.1
    [Fact]
    public void Hello_RoundTrips()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "HELLO",
          "messageId": "b3d6e2b0-0000-0000-0000-000000000001",
          "sourceDeviceId": "pc-uuid",
          "timestamp": 1723600000000,
          "payload": {
            "protocolVersion": 1,
            "deviceId": "pc-uuid",
            "deviceName": "Gaming PC",
            "platform": "WINDOWS",
            "appVersion": "0.1.0",
            "secure": false
          }
        }
        """;

        var envelope = ProtocolCodec.Decode(json);

        Assert.Equal(MessageType.Hello, envelope.Type);
        Assert.Equal("b3d6e2b0-0000-0000-0000-000000000001", envelope.MessageId);
        var hello = Assert.IsType<HelloPayload>(envelope.Payload);
        Assert.Equal("pc-uuid", hello.DeviceId);
        Assert.Equal("Gaming PC", hello.DeviceName);
        Assert.Equal(WirePlatform.Windows, hello.Platform);
        Assert.Equal("0.1.0", hello.AppVersion);
        Assert.False(hello.Secure);

        RoundTripThroughEncode(envelope);
    }

    // protocol/PROTOCOL.md §3.2
    [Fact]
    public void PairRequest_RoundTrips()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "PAIR_REQUEST",
          "messageId": "msg-1",
          "sourceDeviceId": "tablet-uuid",
          "timestamp": 1723600000000,
          "payload": {
            "requesterDeviceId": "tablet-uuid",
            "requesterDeviceName": "Living Room Tablet",
            "requesterPlatform": "ANDROID",
            "pairingToken": "481927"
          }
        }
        """;

        var envelope = ProtocolCodec.Decode(json);
        var payload = Assert.IsType<PairRequestPayload>(envelope.Payload);
        Assert.Equal("tablet-uuid", payload.RequesterDeviceId);
        Assert.Equal(WirePlatform.Android, payload.RequesterPlatform);
        Assert.Equal("481927", payload.PairingToken);

        RoundTripThroughEncode(envelope);
    }

    [Fact]
    public void PairResponse_Accepted_RoundTrips()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "PAIR_RESPONSE",
          "messageId": "msg-2",
          "sourceDeviceId": "pc-uuid",
          "timestamp": 1723600000000,
          "payload": {
            "accepted": true,
            "reason": null,
            "deviceId": "pc-uuid",
            "deviceName": "Gaming PC",
            "platform": "WINDOWS",
            "sharedSecret": "base64-32-random-bytes"
          }
        }
        """;

        var envelope = ProtocolCodec.Decode(json);
        var payload = Assert.IsType<PairResponsePayload>(envelope.Payload);
        Assert.True(payload.Accepted);
        Assert.Null(payload.Reason);
        Assert.Equal("base64-32-random-bytes", payload.SharedSecret);

        RoundTripThroughEncode(envelope);
    }

    [Fact]
    public void PairResponse_Rejected_RoundTrips()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "PAIR_RESPONSE",
          "messageId": "msg-3",
          "sourceDeviceId": "pc-uuid",
          "timestamp": 1723600000000,
          "payload": {
            "accepted": false,
            "reason": "TOKEN_EXPIRED",
            "deviceId": "pc-uuid",
            "deviceName": "Gaming PC",
            "platform": "WINDOWS",
            "sharedSecret": null
          }
        }
        """;

        var envelope = ProtocolCodec.Decode(json);
        var payload = Assert.IsType<PairResponsePayload>(envelope.Payload);
        Assert.False(payload.Accepted);
        Assert.Equal("TOKEN_EXPIRED", payload.Reason);
        Assert.Null(payload.SharedSecret);
    }

    // protocol/PROTOCOL.md §3.3
    [Fact]
    public void Auth_RoundTrips()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "AUTH",
          "messageId": "msg-4",
          "sourceDeviceId": "tablet-uuid",
          "timestamp": 1723600000000,
          "payload": {
            "deviceId": "tablet-uuid",
            "proof": "base64-hmac"
          }
        }
        """;

        var envelope = ProtocolCodec.Decode(json);
        var payload = Assert.IsType<AuthPayload>(envelope.Payload);
        Assert.Equal("tablet-uuid", payload.DeviceId);
        Assert.Equal("base64-hmac", payload.Proof);

        RoundTripThroughEncode(envelope);
    }

    [Fact]
    public void AuthResult_RoundTrips()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "AUTH_RESULT",
          "messageId": "msg-5",
          "sourceDeviceId": "pc-uuid",
          "timestamp": 1723600000000,
          "payload": { "accepted": true, "reason": null }
        }
        """;

        var envelope = ProtocolCodec.Decode(json);
        var payload = Assert.IsType<AuthResultPayload>(envelope.Payload);
        Assert.True(payload.Accepted);

        RoundTripThroughEncode(envelope);
    }

    // protocol/PROTOCOL.md §3.4
    [Fact]
    public void DeviceInfo_RoundTrips()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "DEVICE_INFO",
          "messageId": "msg-6",
          "sourceDeviceId": "pc-uuid",
          "timestamp": 1723600000000,
          "payload": { "deviceId": "pc-uuid", "deviceName": "Gaming PC", "platform": "WINDOWS", "appVersion": "0.1.0" }
        }
        """;

        var envelope = ProtocolCodec.Decode(json);
        var payload = Assert.IsType<DeviceInfoPayload>(envelope.Payload);
        Assert.Equal("Gaming PC", payload.DeviceName);

        RoundTripThroughEncode(envelope);
    }

    [Fact]
    public void Capabilities_RoundTrips()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "CAPABILITIES",
          "messageId": "msg-7",
          "sourceDeviceId": "pc-uuid",
          "timestamp": 1723600000000,
          "payload": {
            "deviceId": "pc-uuid",
            "capabilities": ["VOLUME", "MUTE", "MEDIA_PLAY_PAUSE", "MEDIA_NEXT", "MEDIA_PREVIOUS", "APP_LAUNCH"],
            "apps": [
              { "appId": "spotify", "displayName": "Spotify" },
              { "appId": "discord", "displayName": "Discord" }
            ]
          }
        }
        """;

        var envelope = ProtocolCodec.Decode(json);
        var payload = Assert.IsType<CapabilitiesPayload>(envelope.Payload);
        Assert.Equal(6, payload.Capabilities.Count);
        Assert.Contains("VOLUME", payload.Capabilities);
        Assert.Equal(2, payload.Apps.Count);
        Assert.Equal("spotify", payload.Apps[0].AppId);
        Assert.Equal("Spotify", payload.Apps[0].DisplayName);

        RoundTripThroughEncode(envelope);
    }

    // protocol/PROTOCOL.md §3.5
    [Fact]
    public void Action_BrightnessSet_RoundTrips()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "ACTION",
          "messageId": "msg-8",
          "sourceDeviceId": "tablet-id",
          "targetDeviceId": "pc-id",
          "timestamp": 1723600000000,
          "payload": {
            "action": { "type": "BRIGHTNESS_SET", "value": 70 }
          }
        }
        """;

        var envelope = ProtocolCodec.Decode(json);
        Assert.Equal("pc-id", envelope.TargetDeviceId);
        var payload = Assert.IsType<ActionPayload>(envelope.Payload);
        var action = Assert.IsType<BrightnessSetDto>(payload.Action);
        Assert.Equal(70, action.Value);

        RoundTripThroughEncode(envelope);
    }

    [Theory]
    [InlineData("VOLUME_SET", """{ "type": "VOLUME_SET", "value": 55 }""")]
    [InlineData("SET_MUTED", """{ "type": "SET_MUTED", "muted": true }""")]
    [InlineData("MEDIA_SET_STATE", """{ "type": "MEDIA_SET_STATE", "state": "PLAYING" }""")]
    [InlineData("MEDIA_NEXT", """{ "type": "MEDIA_NEXT" }""")]
    [InlineData("MEDIA_PREVIOUS", """{ "type": "MEDIA_PREVIOUS" }""")]
    [InlineData("APP_LAUNCH", """{ "type": "APP_LAUNCH", "appId": "spotify" }""")]
    public void Action_EveryActionType_RoundTrips(string discriminator, string actionJson)
    {
        const string template = """
        {
          "protocolVersion": 1,
          "type": "ACTION",
          "messageId": "msg-9",
          "sourceDeviceId": "tablet-id",
          "targetDeviceId": "pc-id",
          "timestamp": 1723600000000,
          "payload": { "action": __ACTION__ }
        }
        """;
        var json = template.Replace("__ACTION__", actionJson);

        var envelope = ProtocolCodec.Decode(json);
        var payload = Assert.IsType<ActionPayload>(envelope.Payload);
        Assert.NotNull(payload.Action);

        var reEncoded = ProtocolCodec.Encode(envelope);
        Assert.Contains($"\"type\":\"{discriminator}\"", reEncoded);

        var redecoded = ProtocolCodec.Decode(reEncoded);
        Assert.Equal(envelope.Payload, redecoded.Payload);
    }

    [Fact]
    public void ActionResult_Success_RoundTrips()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "ACTION_RESULT",
          "messageId": "msg-10",
          "sourceDeviceId": "pc-id",
          "targetDeviceId": "tablet-id",
          "timestamp": 1723600000000,
          "payload": {
            "correlatesTo": "msg-8",
            "success": true,
            "errorCode": null,
            "resultingState": { "type": "BRIGHTNESS_SET", "value": 70 }
          }
        }
        """;

        var envelope = ProtocolCodec.Decode(json);
        var payload = Assert.IsType<ActionResultPayload>(envelope.Payload);
        Assert.Equal("msg-8", payload.CorrelatesTo);
        Assert.True(payload.Success);
        Assert.Null(payload.ErrorCode);
        var resultingState = Assert.IsType<BrightnessSetDto>(payload.ResultingState);
        Assert.Equal(70, resultingState.Value);

        RoundTripThroughEncode(envelope);
    }

    [Fact]
    public void ActionResult_Failure_RoundTrips()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "ACTION_RESULT",
          "messageId": "msg-11",
          "sourceDeviceId": "pc-id",
          "targetDeviceId": "tablet-id",
          "timestamp": 1723600000000,
          "payload": {
            "correlatesTo": "msg-8",
            "success": false,
            "errorCode": "UNSUPPORTED_CAPABILITY",
            "resultingState": null
          }
        }
        """;

        var envelope = ProtocolCodec.Decode(json);
        var payload = Assert.IsType<ActionResultPayload>(envelope.Payload);
        Assert.False(payload.Success);
        Assert.Equal("UNSUPPORTED_CAPABILITY", payload.ErrorCode);
        Assert.Null(payload.ResultingState);
    }

    // protocol/PROTOCOL.md §3.6 — payload is the resultingState-shaped object directly.
    [Fact]
    public void StateUpdate_RoundTrips()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "STATE_UPDATE",
          "messageId": "msg-12",
          "sourceDeviceId": "pc-id",
          "timestamp": 1723600000000,
          "payload": { "type": "VOLUME_SET", "value": 40 }
        }
        """;

        var envelope = ProtocolCodec.Decode(json);
        var payload = Assert.IsType<StateUpdatePayload>(envelope.Payload);
        var state = Assert.IsType<VolumeSetDto>(payload.State);
        Assert.Equal(40, state.Value);

        var reEncoded = ProtocolCodec.Encode(envelope);
        Assert.Contains("\"type\":\"VOLUME_SET\"", reEncoded);
        Assert.Contains("\"value\":40", reEncoded);
        RoundTripThroughEncode(envelope);
    }

    // protocol/PROTOCOL.md §3.7
    [Fact]
    public void DashboardSync_RoundTrips()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "DASHBOARD_SYNC",
          "messageId": "msg-13",
          "sourceDeviceId": "pc-id",
          "timestamp": 1723600000000,
          "payload": {
            "dashboard": {
              "id": "dash-1",
              "name": "Gaming",
              "version": 14,
              "widgets": [
                {
                  "id": "w1",
                  "type": "SLIDER_VOLUME",
                  "position": { "x": 0, "y": 0 },
                  "size": { "width": 2, "height": 1 },
                  "targetDeviceId": "pc-id",
                  "action": { "type": "VOLUME_SET", "value": 50 },
                  "configuration": { "label": "PC Volume" }
                }
              ],
              "groups": [
                {
                  "id": "g1",
                  "name": "Speakers",
                  "kind": "RELATIVE_SLIDER",
                  "memberWidgetIds": ["w1"],
                  "reconnectPolicy": "SYNC_GROUP_STATE"
                }
              ]
            }
          }
        }
        """;

        var envelope = ProtocolCodec.Decode(json);
        var payload = Assert.IsType<DashboardSyncPayload>(envelope.Payload);
        Assert.Equal("dash-1", payload.Dashboard.Id);
        Assert.Equal(14, payload.Dashboard.Version);
        Assert.Single(payload.Dashboard.Widgets);
        Assert.Single(payload.Dashboard.Groups);
        Assert.Equal("PC Volume", payload.Dashboard.Widgets[0].Configuration?["label"]);

        RoundTripThroughEncode(envelope);
    }

    [Fact]
    public void DashboardAck_RoundTrips()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "DASHBOARD_ACK",
          "messageId": "msg-14",
          "sourceDeviceId": "tablet-id",
          "timestamp": 1723600000000,
          "payload": { "dashboardId": "dash-1", "appliedVersion": 14 }
        }
        """;

        var envelope = ProtocolCodec.Decode(json);
        var payload = Assert.IsType<DashboardAckPayload>(envelope.Payload);
        Assert.Equal("dash-1", payload.DashboardId);
        Assert.Equal(14, payload.AppliedVersion);

        RoundTripThroughEncode(envelope);
    }

    // protocol/PROTOCOL.md §3.8
    [Fact]
    public void Error_RoundTrips()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "ERROR",
          "messageId": "msg-15",
          "timestamp": 1723600000000,
          "payload": { "code": "UNSUPPORTED_MESSAGE_TYPE", "message": "unknown type", "correlatesTo": null }
        }
        """;

        var envelope = ProtocolCodec.Decode(json);
        var payload = Assert.IsType<ErrorPayload>(envelope.Payload);
        Assert.Equal("UNSUPPORTED_MESSAGE_TYPE", payload.Code);

        RoundTripThroughEncode(envelope);
    }

    [Fact]
    public void Ping_RoundTrips_WithNoPayloadField()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "PING",
          "messageId": "msg-16",
          "sourceDeviceId": "pc-id",
          "timestamp": 1723600000000
        }
        """;

        var envelope = ProtocolCodec.Decode(json);
        Assert.IsType<EmptyPayload>(envelope.Payload);

        RoundTripThroughEncode(envelope);
    }

    [Fact]
    public void Pong_RoundTrips_WithEmptyPayloadObject()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "PONG",
          "messageId": "msg-17",
          "sourceDeviceId": "tablet-id",
          "timestamp": 1723600000000,
          "payload": {}
        }
        """;

        var envelope = ProtocolCodec.Decode(json);
        Assert.IsType<EmptyPayload>(envelope.Payload);
    }

    // messageId / deviceId round-trip through Envelope.Create + Encode + Decode.
    [Fact]
    public void EnvelopeCreate_PreservesMessageIdAndDeviceIds()
    {
        var envelope = Envelope.Create(
            MessageType.Action,
            new ActionPayload(new VolumeSetDto(30)),
            sourceDeviceId: "controller-device-id",
            targetDeviceId: "target-device-id");

        var json = ProtocolCodec.Encode(envelope);
        var decoded = ProtocolCodec.Decode(json);

        Assert.Equal(envelope.MessageId, decoded.MessageId);
        Assert.Equal("controller-device-id", decoded.SourceDeviceId);
        Assert.Equal("target-device-id", decoded.TargetDeviceId);
    }

    /// <summary>
    /// Proves encode(decode(json)) is stable by decoding, re-encoding, decoding
    /// again, and re-encoding again — the two JSON strings must match exactly.
    /// (Deliberately not `Assert.Equal(envelope.Payload, redecoded.Payload)`:
    /// several payloads carry `List&lt;T&gt;`/`Dictionary` fields, and record-
    /// generated equality falls back to reference equality for those field
    /// types, which would make two independently-deserialized instances
    /// compare unequal even when their content is identical.)
    /// </summary>
    private static void RoundTripThroughEncode(Envelope envelope)
    {
        var json1 = ProtocolCodec.Encode(envelope);
        var redecoded = ProtocolCodec.Decode(json1);
        var json2 = ProtocolCodec.Encode(redecoded);

        Assert.Equal(envelope.Type, redecoded.Type);
        Assert.Equal(envelope.MessageId, redecoded.MessageId);
        Assert.Equal(json1, json2);
    }
}
