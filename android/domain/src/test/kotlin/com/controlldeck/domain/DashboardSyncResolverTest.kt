package com.controlldeck.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun dashboard(version: Long, name: String = "Gaming") = Dashboard(
    id = DashboardId("dash-1"),
    name = name,
    version = version,
)

class DashboardSyncResolverTest {

    @Test
    fun `no local copy - incoming is always applied`() {
        val incoming = DashboardSyncMessage(dashboard(1), timestamp = 1000, sourceDeviceId = DeviceId("phone"))

        val outcome = DashboardSyncResolver.resolve(local = null, incoming = incoming)

        assertEquals(DashboardSyncOutcome.Applied(dashboard(1)), outcome)
    }

    @Test
    fun `sequential edits - v14 from phone wins over local v13 from tablet`() {
        // base v12 -> tablet edits to v13 (local) -> phone concurrently edits base to v14 (incoming)
        val local = DashboardSyncMessage(dashboard(13), timestamp = 5000, sourceDeviceId = DeviceId("tablet"))
        val incoming = DashboardSyncMessage(dashboard(14), timestamp = 5500, sourceDeviceId = DeviceId("phone"))

        val outcome = DashboardSyncResolver.resolve(local, incoming)

        assertEquals(DashboardSyncOutcome.Applied(dashboard(14)), outcome)
    }

    @Test
    fun `incoming older version - local is replied back`() {
        val local = DashboardSyncMessage(dashboard(14), timestamp = 5500, sourceDeviceId = DeviceId("phone"))
        val incoming = DashboardSyncMessage(dashboard(13), timestamp = 5000, sourceDeviceId = DeviceId("tablet"))

        val outcome = DashboardSyncResolver.resolve(local, incoming)

        assertEquals(DashboardSyncOutcome.ReplyWithLocal(dashboard(14)), outcome)
    }

    @Test
    fun `equal version different content - later timestamp wins tie-break`() {
        val local = DashboardSyncMessage(dashboard(13, name = "Local"), timestamp = 1000, sourceDeviceId = DeviceId("tablet"))
        val incoming = DashboardSyncMessage(dashboard(13, name = "Incoming"), timestamp = 2000, sourceDeviceId = DeviceId("phone"))

        val outcome = DashboardSyncResolver.resolve(local, incoming)

        assertTrue(outcome is DashboardSyncOutcome.Applied)
        assertEquals("Incoming", (outcome as DashboardSyncOutcome.Applied).dashboard.name)
    }

    @Test
    fun `equal version equal timestamp - deterministic sourceDeviceId string ordering tie-break`() {
        val local = DashboardSyncMessage(dashboard(13, name = "Local"), timestamp = 1000, sourceDeviceId = DeviceId("aaa-device"))
        val incoming = DashboardSyncMessage(dashboard(13, name = "Incoming"), timestamp = 1000, sourceDeviceId = DeviceId("zzz-device"))

        // "zzz-device" > "aaa-device" lexicographically -> incoming wins.
        val outcome = DashboardSyncResolver.resolve(local, incoming)
        assertTrue(outcome is DashboardSyncOutcome.Applied)
        assertEquals("Incoming", (outcome as DashboardSyncOutcome.Applied).dashboard.name)

        // Symmetric: swap roles, same rule must hold so both sides agree on the same winner.
        val outcomeSwapped = DashboardSyncResolver.resolve(local = incoming, incoming = local)
        assertTrue(outcomeSwapped is DashboardSyncOutcome.ReplyWithLocal)
        assertEquals("Incoming", (outcomeSwapped as DashboardSyncOutcome.ReplyWithLocal).local.name)
    }

    @Test
    fun `equal version identical content - no change`() {
        val d = dashboard(13)
        val local = DashboardSyncMessage(d, timestamp = 1000, sourceDeviceId = DeviceId("tablet"))
        val incoming = DashboardSyncMessage(d, timestamp = 9999, sourceDeviceId = DeviceId("phone"))

        val outcome = DashboardSyncResolver.resolve(local, incoming)

        assertEquals(DashboardSyncOutcome.NoChange, outcome)
    }
}
