package com.controlldeck.domain

/** A DASHBOARD_SYNC as seen by the resolver: the dashboard plus the enclosing envelope metadata. */
data class DashboardSyncMessage(
    val dashboard: Dashboard,
    val timestamp: Long,
    val sourceDeviceId: DeviceId,
)

/** Result of resolving an incoming DASHBOARD_SYNC against local state. */
sealed class DashboardSyncOutcome {
    /** Incoming wins; caller should persist [dashboard] as the new local copy. */
    data class Applied(val dashboard: Dashboard) : DashboardSyncOutcome()

    /** Local is newer/wins the tie-break; caller should send its own DASHBOARD_SYNC back to the sender. */
    data class ReplyWithLocal(val local: Dashboard) : DashboardSyncOutcome()

    /** Same version, identical content — nothing to do. */
    data object NoChange : DashboardSyncOutcome()
}

/**
 * Implements the exact last-write-wins rule from docs/ARCHITECTURE.md §6:
 *
 * 1. Apply incoming iff `incoming.version > local.version` (or no local copy exists).
 * 2. If `incoming.version < local.version`, reply with the local (newer) copy.
 * 3. Equal versions: tie-break by message `timestamp`, then by `sourceDeviceId`
 *    string ordering, deterministically on both sides.
 */
object DashboardSyncResolver {

    fun resolve(local: DashboardSyncMessage?, incoming: DashboardSyncMessage): DashboardSyncOutcome {
        if (local == null) return DashboardSyncOutcome.Applied(incoming.dashboard)

        val localVersion = local.dashboard.version
        val incomingVersion = incoming.dashboard.version

        return when {
            incomingVersion > localVersion -> DashboardSyncOutcome.Applied(incoming.dashboard)
            incomingVersion < localVersion -> DashboardSyncOutcome.ReplyWithLocal(local.dashboard)
            else -> resolveTie(local, incoming)
        }
    }

    private fun resolveTie(local: DashboardSyncMessage, incoming: DashboardSyncMessage): DashboardSyncOutcome {
        if (local.dashboard == incoming.dashboard) return DashboardSyncOutcome.NoChange

        return when {
            incoming.timestamp > local.timestamp -> DashboardSyncOutcome.Applied(incoming.dashboard)
            incoming.timestamp < local.timestamp -> DashboardSyncOutcome.ReplyWithLocal(local.dashboard)
            else -> {
                // Final deterministic fallback: sourceDeviceId string ordering. The
                // lexicographically greater id "wins" — arbitrary but symmetric, so
                // both peers independently compute the same winner.
                val incomingWins = incoming.sourceDeviceId.value > local.sourceDeviceId.value
                if (incomingWins) {
                    DashboardSyncOutcome.Applied(incoming.dashboard)
                } else {
                    DashboardSyncOutcome.ReplyWithLocal(local.dashboard)
                }
            }
        }
    }
}
