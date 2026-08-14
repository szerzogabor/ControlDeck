package com.controlldeck.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun volumeWidget(id: String, deviceId: String): Widget = Widget(
    id = WidgetId(id),
    type = WidgetType.SLIDER_VOLUME,
    position = GridPosition(0, 0),
    size = GridSize(1, 1),
    targetDeviceId = DeviceId(deviceId),
    action = ActionSpec.VolumeSet(0),
)

private fun deviceState(deviceId: String, connection: ConnectionState, volume: Int? = null) =
    DeviceState(DeviceId(deviceId), connection, volume = volume)

class ReconnectPolicyResolverTest {

    @Test
    fun `SYNC_GROUP_STATE brings drifted device back to group baseline`() {
        val drifted = volumeWidget("drifted", "drifted-dev")
        val a = volumeWidget("a", "a-dev")
        val b = volumeWidget("b", "b-dev")
        val group = Group(
            GroupId("g1"), "Volume", GroupKind.RELATIVE_SLIDER,
            listOf(drifted.id, a.id, b.id), reconnectPolicy = ReconnectPolicy.SYNC_GROUP_STATE,
        )
        val context = GroupContext.of(
            listOf(drifted, a, b),
            listOf(
                deviceState("drifted-dev", ConnectionState.ONLINE, volume = 35),
                deviceState("a-dev", ConnectionState.ONLINE, volume = 60),
                deviceState("b-dev", ConnectionState.ONLINE, volume = 60),
            ),
        )

        val dispatches = ReconnectPolicyResolver.resolve(group, DeviceId("drifted-dev"), context)

        assertEquals(1, dispatches.size)
        assertEquals(drifted.id, dispatches.single().widgetId)
        assertEquals(ActionSpec.VolumeSet(60), dispatches.single().action)
    }

    @Test
    fun `SYNC_GROUP_STATE sends nothing when reconnecting device already matches baseline`() {
        val drifted = volumeWidget("drifted", "drifted-dev")
        val a = volumeWidget("a", "a-dev")
        val group = Group(GroupId("g1"), "Volume", GroupKind.RELATIVE_SLIDER, listOf(drifted.id, a.id))
        val context = GroupContext.of(
            listOf(drifted, a),
            listOf(deviceState("drifted-dev", ConnectionState.ONLINE, volume = 60), deviceState("a-dev", ConnectionState.ONLINE, volume = 60)),
        )

        val dispatches = ReconnectPolicyResolver.resolve(group, DeviceId("drifted-dev"), context)

        assertTrue(dispatches.isEmpty())
    }

    @Test
    fun `KEEP_DEVICE_STATE never sends a corrective action`() {
        val drifted = volumeWidget("drifted", "drifted-dev")
        val a = volumeWidget("a", "a-dev")
        val group = Group(
            GroupId("g1"), "Volume", GroupKind.RELATIVE_SLIDER,
            listOf(drifted.id, a.id), reconnectPolicy = ReconnectPolicy.KEEP_DEVICE_STATE,
        )
        val context = GroupContext.of(
            listOf(drifted, a),
            listOf(deviceState("drifted-dev", ConnectionState.ONLINE, volume = 35), deviceState("a-dev", ConnectionState.ONLINE, volume = 60)),
        )

        val dispatches = ReconnectPolicyResolver.resolve(group, DeviceId("drifted-dev"), context)

        assertTrue(dispatches.isEmpty())
    }

    @Test
    fun `NO_ACTION never sends a corrective action`() {
        val drifted = volumeWidget("drifted", "drifted-dev")
        val a = volumeWidget("a", "a-dev")
        val group = Group(
            GroupId("g1"), "Volume", GroupKind.RELATIVE_SLIDER,
            listOf(drifted.id, a.id), reconnectPolicy = ReconnectPolicy.NO_ACTION,
        )
        val context = GroupContext.of(
            listOf(drifted, a),
            listOf(deviceState("drifted-dev", ConnectionState.ONLINE, volume = 35), deviceState("a-dev", ConnectionState.ONLINE, volume = 60)),
        )

        val dispatches = ReconnectPolicyResolver.resolve(group, DeviceId("drifted-dev"), context)

        assertTrue(dispatches.isEmpty())
    }

    @Test
    fun `single member group has no other members to derive a baseline from`() {
        val drifted = volumeWidget("drifted", "drifted-dev")
        val group = Group(GroupId("g1"), "Solo", GroupKind.RELATIVE_SLIDER, listOf(drifted.id))
        val context = GroupContext.of(listOf(drifted), listOf(deviceState("drifted-dev", ConnectionState.ONLINE, volume = 35)))

        val dispatches = ReconnectPolicyResolver.resolve(group, DeviceId("drifted-dev"), context)

        assertTrue(dispatches.isEmpty())
    }

    @Test
    fun `empty group produces no dispatches`() {
        val group = Group(GroupId("g1"), "Empty", GroupKind.RELATIVE_SLIDER, emptyList())
        val context = GroupContext.of(emptyList(), emptyList())

        val dispatches = ReconnectPolicyResolver.resolve(group, DeviceId("anything"), context)

        assertTrue(dispatches.isEmpty())
    }
}
