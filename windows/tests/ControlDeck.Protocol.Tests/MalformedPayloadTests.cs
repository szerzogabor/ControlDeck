using ControlDeck.Protocol;
using Xunit;

namespace ControlDeck.Protocol.Tests;

/// <summary>
/// Malformed JSON and payloads missing a required field must produce a
/// clear, catchable <see cref="ProtocolDecodeException"/> — never an
/// unhandled crash.
/// </summary>
public class MalformedPayloadTests
{
    [Fact]
    public void NotJson_ThrowsProtocolDecodeException()
    {
        var ex = Assert.Throws<ProtocolDecodeException>(() => ProtocolCodec.Decode("this is not json"));
        Assert.NotNull(ex.Message);
    }

    [Fact]
    public void MissingType_ThrowsProtocolDecodeException()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "messageId": "msg-1",
          "sourceDeviceId": "pc-id",
          "timestamp": 1723600000000,
          "payload": {}
        }
        """;

        Assert.Throws<ProtocolDecodeException>(() => ProtocolCodec.Decode(json));
    }

    [Fact]
    public void MissingMessageId_ThrowsProtocolDecodeException()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "PING",
          "sourceDeviceId": "pc-id",
          "timestamp": 1723600000000
        }
        """;

        Assert.Throws<ProtocolDecodeException>(() => ProtocolCodec.Decode(json));
    }

    [Fact]
    public void HelloPayload_MissingRequiredDeviceId_ThrowsProtocolDecodeException()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "HELLO",
          "messageId": "msg-2",
          "sourceDeviceId": "pc-id",
          "timestamp": 1723600000000,
          "payload": {
            "protocolVersion": 1,
            "deviceName": "Gaming PC",
            "platform": "WINDOWS",
            "appVersion": "0.1.0",
            "secure": false
          }
        }
        """;

        var ex = Assert.Throws<ProtocolDecodeException>(() => ProtocolCodec.Decode(json));
        Assert.Contains("DeviceId", ex.Message);
    }

    [Fact]
    public void ActionPayload_MissingPayloadField_ThrowsProtocolDecodeException()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "ACTION",
          "messageId": "msg-3",
          "sourceDeviceId": "tablet-id",
          "targetDeviceId": "pc-id",
          "timestamp": 1723600000000
        }
        """;

        Assert.Throws<ProtocolDecodeException>(() => ProtocolCodec.Decode(json));
    }

    [Fact]
    public void ActionSpec_MissingTypeDiscriminator_ThrowsProtocolDecodeException()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "ACTION",
          "messageId": "msg-4",
          "sourceDeviceId": "tablet-id",
          "targetDeviceId": "pc-id",
          "timestamp": 1723600000000,
          "payload": { "action": { "value": 50 } }
        }
        """;

        Assert.Throws<ProtocolDecodeException>(() => ProtocolCodec.Decode(json));
    }

    [Fact]
    public void ActionSpec_MissingRequiredValueField_ThrowsProtocolDecodeException()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "ACTION",
          "messageId": "msg-5",
          "sourceDeviceId": "tablet-id",
          "targetDeviceId": "pc-id",
          "timestamp": 1723600000000,
          "payload": { "action": { "type": "VOLUME_SET" } }
        }
        """;

        Assert.Throws<ProtocolDecodeException>(() => ProtocolCodec.Decode(json));
    }

    [Fact]
    public void TryDecode_ReturnsFalseWithMessage_InsteadOfThrowing()
    {
        var ok = ProtocolCodec.TryDecode("not json at all", out var envelope, out var error);

        Assert.False(ok);
        Assert.Null(envelope);
        Assert.NotNull(error);
    }

    [Fact]
    public void TryDecode_ReturnsTrue_ForValidMessage()
    {
        const string json = """
        {
          "protocolVersion": 1,
          "type": "PING",
          "messageId": "msg-6",
          "sourceDeviceId": "pc-id",
          "timestamp": 1723600000000
        }
        """;

        var ok = ProtocolCodec.TryDecode(json, out var envelope, out var error);

        Assert.True(ok);
        Assert.NotNull(envelope);
        Assert.Null(error);
    }
}
