namespace ControlDeck.Protocol;

/// <summary>
/// Thrown by <see cref="ProtocolCodec"/> when a message cannot be decoded —
/// malformed JSON, a payload missing a required field, or any other
/// deserialization failure. Callers are expected to catch this (e.g. to
/// respond with an ERROR envelope, code MALFORMED_PAYLOAD, per
/// protocol/PROTOCOL.md §3.8) rather than let it crash the connection.
/// </summary>
public sealed class ProtocolDecodeException : Exception
{
    public ProtocolDecodeException(string message) : base(message)
    {
    }

    public ProtocolDecodeException(string message, Exception innerException) : base(message, innerException)
    {
    }
}
