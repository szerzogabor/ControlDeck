namespace ControlDeck.Domain;

/// <summary>What a receiving device should do with an incoming DASHBOARD_SYNC.</summary>
public enum SyncOutcome
{
    /// <summary>Apply the incoming dashboard as the new local copy.</summary>
    ApplyIncoming,

    /// <summary>Discard the incoming copy; it is strictly older than local.</summary>
    KeepLocal,

    /// <summary>
    /// Discard the incoming copy AND reply with the local (newer) dashboard so
    /// the sender catches up, per docs/ARCHITECTURE.md §6.
    /// </summary>
    KeepLocalAndReplyWithLocal
}

/// <summary>
/// Implements the last-write-wins rule from docs/ARCHITECTURE.md §6 exactly:
///
/// - No local copy exists yet -> always apply incoming.
/// - incoming.version &gt; local.version -> apply incoming outright.
/// - incoming.version &lt; local.version -> discard, reply with local so the
///   sender catches up.
/// - Equal versions (rare concurrent-edit race) -> tie-break by the enclosing
///   message's `timestamp` (higher wins), then by `sourceDeviceId` string
///   ordering (ordinal / StringComparer.Ordinal) as a final deterministic
///   fallback: the lexicographically GREATER sourceDeviceId wins. Both
///   platforms must implement this identical rule so they converge without
///   further negotiation.
///
/// This is a pure function: no I/O, no mutation. Callers own actually
/// applying the incoming dashboard, sending DASHBOARD_ACK, or re-sending the
/// local DASHBOARD_SYNC.
/// </summary>
public static class DashboardSyncResolver
{
    public static SyncOutcome Resolve(
        bool localDashboardExists,
        long incomingVersion,
        long localVersion,
        long incomingTimestamp,
        long localTimestamp,
        string incomingSourceDeviceId,
        string localSourceDeviceId)
    {
        if (!localDashboardExists)
        {
            return SyncOutcome.ApplyIncoming;
        }

        if (incomingVersion > localVersion)
        {
            return SyncOutcome.ApplyIncoming;
        }

        if (incomingVersion < localVersion)
        {
            return SyncOutcome.KeepLocalAndReplyWithLocal;
        }

        // Equal version: tie-break by timestamp, then by sourceDeviceId ordinal ordering.
        if (incomingTimestamp > localTimestamp)
        {
            return SyncOutcome.ApplyIncoming;
        }

        if (incomingTimestamp < localTimestamp)
        {
            return SyncOutcome.KeepLocalAndReplyWithLocal;
        }

        var comparison = string.CompareOrdinal(incomingSourceDeviceId, localSourceDeviceId);
        return comparison > 0 ? SyncOutcome.ApplyIncoming : SyncOutcome.KeepLocalAndReplyWithLocal;
    }
}
