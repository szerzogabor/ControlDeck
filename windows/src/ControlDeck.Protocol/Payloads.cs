namespace ControlDeck.Protocol;

// protocol/PROTOCOL.md §3.1
public sealed record HelloPayload(
    int ProtocolVersion,
    string DeviceId,
    string DeviceName,
    WirePlatform Platform,
    string AppVersion,
    bool Secure
) : MessagePayload;

// protocol/PROTOCOL.md §3.2
public sealed record PairRequestPayload(
    string RequesterDeviceId,
    string RequesterDeviceName,
    WirePlatform RequesterPlatform,
    string PairingToken
) : MessagePayload;

public sealed record PairResponsePayload(
    bool Accepted,
    string? Reason,
    string DeviceId,
    string DeviceName,
    WirePlatform Platform,
    string? SharedSecret
) : MessagePayload;

// protocol/PROTOCOL.md §3.3
public sealed record AuthPayload(
    string DeviceId,
    string Proof
) : MessagePayload;

public sealed record AuthResultPayload(
    bool Accepted,
    string? Reason
) : MessagePayload;

// protocol/PROTOCOL.md §3.4
public sealed record DeviceInfoPayload(
    string DeviceId,
    string DeviceName,
    WirePlatform Platform,
    string AppVersion
) : MessagePayload;

public sealed record AppRegistryEntry(
    string AppId,
    string DisplayName
);

/// <summary>
/// `Capabilities` is intentionally `IReadOnlyList&lt;string&gt;` (raw wire
/// tokens) rather than a strict enum list: protocol/PROTOCOL.md §3.4/§6
/// requires receivers to tolerate unknown future capability values without
/// crashing. Mapping known tokens to <c>ControlDeck.Domain.Capability</c>
/// (skipping anything unrecognized) is the caller's job.
/// </summary>
public sealed record CapabilitiesPayload(
    string DeviceId,
    IReadOnlyList<string> Capabilities,
    IReadOnlyList<AppRegistryEntry> Apps
) : MessagePayload;

// protocol/PROTOCOL.md §3.5
public sealed record ActionPayload(
    ActionSpecDto Action
) : MessagePayload;

public sealed record ActionResultPayload(
    string CorrelatesTo,
    bool Success,
    string? ErrorCode,
    ActionSpecDto? ResultingState
) : MessagePayload;

// protocol/PROTOCOL.md §3.6 — wire shape is exactly the resultingState-shaped
// ActionSpecDto object (no extra wrapper field); see EnvelopeJsonConverter
// for how this is flattened on read/write.
public sealed record StateUpdatePayload(
    ActionSpecDto State
) : MessagePayload;

// protocol/PROTOCOL.md §3.7
public sealed record DashboardSyncPayload(
    DashboardDto Dashboard
) : MessagePayload;

public sealed record DashboardAckPayload(
    string DashboardId,
    long AppliedVersion
) : MessagePayload;

// protocol/PROTOCOL.md §3.8
public sealed record ErrorPayload(
    string Code,
    string Message,
    string? CorrelatesTo
) : MessagePayload;

/// <summary>PING/PONG carry no payload beyond the envelope (protocol/PROTOCOL.md §3.8).</summary>
public sealed record EmptyPayload : MessagePayload
{
    public static readonly EmptyPayload Instance = new();
}

/// <summary>
/// Fallback for an envelope `type` this build doesn't recognize. Decoding
/// succeeds (protocol/PROTOCOL.md §2: "Unknown `type` values MUST produce an
/// ERROR response ... rather than crashing the connection") — the caller
/// inspects <see cref="RawType"/> and <see cref="RawJson"/> to build that
/// ERROR response.
/// </summary>
public sealed record UnknownPayload(
    string RawType,
    string RawJson
) : MessagePayload;
