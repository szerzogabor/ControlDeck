namespace ControlDeck.Protocol;

/// <summary>
/// Base of the closed payload hierarchy for every envelope `type` in
/// protocol/PROTOCOL.md §3. Base constructor is `private protected` so only
/// the sealed records declared in this assembly can be a payload.
/// <see cref="UnknownPayload"/> is the explicit fallback for an
/// unrecognized `type` (never throw on decode; see §2/§6).
/// </summary>
public abstract record MessagePayload
{
    private protected MessagePayload()
    {
    }
}
