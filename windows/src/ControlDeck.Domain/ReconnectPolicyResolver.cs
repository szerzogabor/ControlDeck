namespace ControlDeck.Domain;

/// <summary>
/// Implements docs/ARCHITECTURE.md §5. Evaluated whenever a device
/// transitions OFFLINE -> ONLINE and is a member of one or more groups.
/// Pure function: given the group's policy and its current authoritative
/// action (the "group's current desired state" for the relevant group kind),
/// decide whether to correct the reconnecting member.
/// </summary>
public static class ReconnectPolicyResolver
{
    /// <summary>
    /// Returns a corrective <see cref="GroupDispatch"/> to send to the
    /// reconnecting member, or null if no ACTION should be sent.
    ///
    /// - SYNC_GROUP_STATE: overwrite the reconnecting member with the group's
    ///   authoritative value/state -> emits a dispatch.
    /// - KEEP_DEVICE_STATE: the member keeps its drifted value; the group's
    ///   aggregate view is refreshed from the member's own STATE_UPDATE
    ///   instead (handled by the caller, outside this pure function) -> no
    ///   dispatch.
    /// - NO_ACTION: nothing sent, and the aggregate view is not recomputed
    ///   from this member until the next explicit user interaction -> no
    ///   dispatch.
    /// </summary>
    public static GroupDispatch? Resolve(
        ReconnectPolicy policy,
        DeviceId reconnectingDeviceId,
        WidgetId reconnectingWidgetId,
        ActionSpec groupAuthoritativeAction)
    {
        return policy switch
        {
            ReconnectPolicy.SyncGroupState =>
                new GroupDispatch(reconnectingDeviceId, reconnectingWidgetId, groupAuthoritativeAction),
            ReconnectPolicy.KeepDeviceState => null,
            ReconnectPolicy.NoAction => null,
            _ => null
        };
    }
}
