namespace ControlDeck.Domain;

/// <summary>
/// Per-device last-known state, held by the State Manager. See
/// docs/ARCHITECTURE.md §3/§7 — an offline device retains its last-known
/// values (they are simply not authoritative / not actionable) rather than
/// being cleared, so the UI can still show a plausible last state alongside
/// the "offline" badge.
/// </summary>
public sealed record DeviceState(
    DeviceId DeviceId,
    ConnectionState Connection,
    int? Brightness,
    int? Volume,
    bool? Muted,
    MediaState? MediaState
)
{
    public static DeviceState OfflineUnknown(DeviceId deviceId) =>
        new(deviceId, ConnectionState.Offline, null, null, null, null);
}
