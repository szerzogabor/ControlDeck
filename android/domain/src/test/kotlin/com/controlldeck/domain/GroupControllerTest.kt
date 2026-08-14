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

private fun muteWidget(id: String, deviceId: String): Widget = Widget(
    id = WidgetId(id),
    type = WidgetType.BUTTON_MUTE,
    position = GridPosition(0, 0),
    size = GridSize(1, 1),
    targetDeviceId = DeviceId(deviceId),
    action = ActionSpec.SetMuted(false),
)

private fun mediaWidget(id: String, deviceId: String): Widget = Widget(
    id = WidgetId(id),
    type = WidgetType.BUTTON_MEDIA_PLAY_PAUSE,
    position = GridPosition(0, 0),
    size = GridSize(1, 1),
    targetDeviceId = DeviceId(deviceId),
    action = ActionSpec.MediaSetState(MediaPlaybackState.PLAYING),
)

private fun deviceState(deviceId: String, volume: Int? = null, muted: Boolean? = null, media: MediaPlaybackState? = null) =
    DeviceState(DeviceId(deviceId), ConnectionState.ONLINE, volume = volume, muted = muted, mediaState = media)

class GroupControllerTest {

    // ---- 4.1 RELATIVE_SLIDER ----

    @Test
    fun `relative slider - PC plus 10 shifts all members by delta`() {
        val pc = volumeWidget("pc", "pc-dev")
        val phone = volumeWidget("phone", "phone-dev")
        val tablet = volumeWidget("tablet", "tablet-dev")
        val group = Group(GroupId("g1"), "Volume", GroupKind.RELATIVE_SLIDER, listOf(pc.id, phone.id, tablet.id))

        val context = GroupContext.of(
            listOf(pc, phone, tablet),
            listOf(deviceState("pc-dev", volume = 20), deviceState("phone-dev", volume = 50), deviceState("tablet-dev", volume = 80)),
        )

        val dispatches = GroupController.apply(group, context, GroupUserInput.SliderMoved(pc.id, oldValue = 20, newValue = 30))

        val byWidget = dispatches.associateBy { it.widgetId }
        assertEquals(ActionSpec.VolumeSet(30), byWidget.getValue(pc.id).action)
        assertEquals(ActionSpec.VolumeSet(60), byWidget.getValue(phone.id).action)
        assertEquals(ActionSpec.VolumeSet(90), byWidget.getValue(tablet.id).action)
        assertEquals(3, dispatches.size)
    }

    @Test
    fun `relative slider - independent clamp not redistribution`() {
        val a = volumeWidget("a", "a-dev")
        val phone = volumeWidget("phone", "phone-dev")
        val c = volumeWidget("c", "c-dev")
        val group = Group(GroupId("g1"), "Volume", GroupKind.RELATIVE_SLIDER, listOf(a.id, phone.id, c.id))

        val context = GroupContext.of(
            listOf(a, phone, c),
            listOf(deviceState("a-dev", volume = 95), deviceState("phone-dev", volume = 80), deviceState("c-dev", volume = 90)),
        )

        val dispatches = GroupController.apply(group, context, GroupUserInput.SliderMoved(phone.id, oldValue = 80, newValue = 100))

        val byWidget = dispatches.associateBy { it.widgetId }
        assertEquals(ActionSpec.VolumeSet(100), byWidget.getValue(a.id).action)
        assertEquals(ActionSpec.VolumeSet(100), byWidget.getValue(phone.id).action)
        assertEquals(ActionSpec.VolumeSet(100), byWidget.getValue(c.id).action)
    }

    @Test
    fun `relative slider - member already saturated in direction of travel is skipped`() {
        val a = volumeWidget("a", "a-dev")
        val b = volumeWidget("b", "b-dev")
        val group = Group(GroupId("g1"), "Volume", GroupKind.RELATIVE_SLIDER, listOf(a.id, b.id))

        val context = GroupContext.of(
            listOf(a, b),
            listOf(deviceState("a-dev", volume = 100), deviceState("b-dev", volume = 50)),
        )

        val dispatches = GroupController.apply(group, context, GroupUserInput.SliderMoved(b.id, oldValue = 50, newValue = 60))

        assertEquals(1, dispatches.size)
        assertEquals(b.id, dispatches.single().widgetId)
        assertEquals(ActionSpec.VolumeSet(60), dispatches.single().action)
    }

    @Test
    fun `relative slider - zero delta produces no dispatches`() {
        val a = volumeWidget("a", "a-dev")
        val group = Group(GroupId("g1"), "Volume", GroupKind.RELATIVE_SLIDER, listOf(a.id))
        val context = GroupContext.of(listOf(a), listOf(deviceState("a-dev", volume = 50)))

        val dispatches = GroupController.apply(group, context, GroupUserInput.SliderMoved(a.id, oldValue = 50, newValue = 50))

        assertTrue(dispatches.isEmpty())
    }

    @Test
    fun `relative slider - lower bound clamp at zero`() {
        val a = volumeWidget("a", "a-dev")
        val b = volumeWidget("b", "b-dev")
        val group = Group(GroupId("g1"), "Volume", GroupKind.RELATIVE_SLIDER, listOf(a.id, b.id))
        val context = GroupContext.of(
            listOf(a, b),
            listOf(deviceState("a-dev", volume = 5), deviceState("b-dev", volume = 30)),
        )

        val dispatches = GroupController.apply(group, context, GroupUserInput.SliderMoved(a.id, oldValue = 5, newValue = 0))

        val byWidget = dispatches.associateBy { it.widgetId }
        assertEquals(ActionSpec.VolumeSet(0), byWidget.getValue(a.id).action)
        assertEquals(ActionSpec.VolumeSet(25), byWidget.getValue(b.id).action)
    }

    @Test
    fun `relative slider - empty group produces no dispatches`() {
        val group = Group(GroupId("g1"), "Empty", GroupKind.RELATIVE_SLIDER, emptyList())
        val context = GroupContext.of(emptyList(), emptyList())

        val dispatches = GroupController.apply(group, context, GroupUserInput.SliderMoved(WidgetId("ghost"), 10, 20))

        assertTrue(dispatches.isEmpty())
    }

    @Test
    fun `relative slider - single member group behaves like a solo slider`() {
        val a = volumeWidget("a", "a-dev")
        val group = Group(GroupId("g1"), "Solo", GroupKind.RELATIVE_SLIDER, listOf(a.id))
        val context = GroupContext.of(listOf(a), listOf(deviceState("a-dev", volume = 40)))

        val dispatches = GroupController.apply(group, context, GroupUserInput.SliderMoved(a.id, oldValue = 40, newValue = 55))

        assertEquals(1, dispatches.size)
        assertEquals(ActionSpec.VolumeSet(55), dispatches.single().action)
    }

    // ---- 4.2 ABSOLUTE_TOGGLE ----

    @Test
    fun `mute group - activating an unmuted group mutes everyone`() {
        val a = muteWidget("a", "a-dev")
        val b = muteWidget("b", "b-dev")
        val group = Group(GroupId("g1"), "Mute", GroupKind.ABSOLUTE_TOGGLE, listOf(a.id, b.id))
        val context = GroupContext.of(listOf(a, b), listOf(deviceState("a-dev", muted = false), deviceState("b-dev", muted = false)))

        val dispatches = GroupController.apply(group, context, GroupUserInput.ToggleActivated(a.id))

        assertTrue(dispatches.all { it.action == ActionSpec.SetMuted(true) })
        assertEquals(2, dispatches.size)
    }

    @Test
    fun `mute group - activating an already fully muted group unmutes everyone`() {
        val a = muteWidget("a", "a-dev")
        val b = muteWidget("b", "b-dev")
        val group = Group(GroupId("g1"), "Mute", GroupKind.ABSOLUTE_TOGGLE, listOf(a.id, b.id))
        val context = GroupContext.of(listOf(a, b), listOf(deviceState("a-dev", muted = true), deviceState("b-dev", muted = true)))

        val dispatches = GroupController.apply(group, context, GroupUserInput.ToggleActivated(a.id))

        assertTrue(dispatches.all { it.action == ActionSpec.SetMuted(false) })
    }

    @Test
    fun `mute group - partially muted group is treated as not fully muted, so activating mutes everyone`() {
        val a = muteWidget("a", "a-dev")
        val b = muteWidget("b", "b-dev")
        val group = Group(GroupId("g1"), "Mute", GroupKind.ABSOLUTE_TOGGLE, listOf(a.id, b.id))
        val context = GroupContext.of(listOf(a, b), listOf(deviceState("a-dev", muted = true), deviceState("b-dev", muted = false)))

        val dispatches = GroupController.apply(group, context, GroupUserInput.ToggleActivated(a.id))

        assertTrue(dispatches.all { it.action == ActionSpec.SetMuted(true) })
    }

    // ---- 4.3 ABSOLUTE_MEDIA ----

    @Test
    fun `media group - explicit desired state broadcasts to all members`() {
        val a = mediaWidget("a", "a-dev")
        val b = mediaWidget("b", "b-dev")
        val c = mediaWidget("c", "c-dev")
        val group = Group(GroupId("g1"), "Media", GroupKind.ABSOLUTE_MEDIA, listOf(a.id, b.id, c.id))
        val context = GroupContext.of(
            listOf(a, b, c),
            listOf(
                deviceState("a-dev", media = MediaPlaybackState.PLAYING),
                deviceState("b-dev", media = MediaPlaybackState.PAUSED),
                deviceState("c-dev", media = MediaPlaybackState.PLAYING),
            ),
        )

        val dispatches = GroupController.apply(group, context, GroupUserInput.MediaDesiredState(a.id, MediaPlaybackState.PAUSED))

        assertTrue(dispatches.all { it.action == ActionSpec.MediaSetState(MediaPlaybackState.PAUSED) })
        assertEquals(3, dispatches.size)
    }

    @Test
    fun `media group - ambiguous toggle flips majority-playing to paused for all`() {
        val a = mediaWidget("a", "a-dev")
        val b = mediaWidget("b", "b-dev")
        val c = mediaWidget("c", "c-dev")
        val group = Group(GroupId("g1"), "Media", GroupKind.ABSOLUTE_MEDIA, listOf(a.id, b.id, c.id))
        val context = GroupContext.of(
            listOf(a, b, c),
            listOf(
                deviceState("a-dev", media = MediaPlaybackState.PLAYING),
                deviceState("b-dev", media = MediaPlaybackState.PLAYING),
                deviceState("c-dev", media = MediaPlaybackState.PAUSED),
            ),
        )

        val dispatches = GroupController.apply(group, context, GroupUserInput.MediaToggle(a.id))

        assertTrue(dispatches.all { it.action == ActionSpec.MediaSetState(MediaPlaybackState.PAUSED) })
    }

    @Test
    fun `media group - edge triggered next broadcasts unconditionally to all members`() {
        val a = mediaWidget("a", "a-dev")
        val b = mediaWidget("b", "b-dev")
        val group = Group(GroupId("g1"), "Media", GroupKind.ABSOLUTE_MEDIA, listOf(a.id, b.id))
        val context = GroupContext.of(listOf(a, b), listOf(deviceState("a-dev"), deviceState("b-dev")))

        val dispatches = GroupController.apply(group, context, GroupUserInput.MediaEdge(a.id, ActionSpec.MediaNext))

        assertEquals(2, dispatches.size)
        assertTrue(dispatches.all { it.action == ActionSpec.MediaNext })
    }
}
