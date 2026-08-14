package com.controlldeck.domain

/**
 * One outbound command produced by group math: which widget triggered it
 * (for UI bookkeeping), which device it must be sent to, and the resulting
 * [ActionSpec]. [GroupController] never performs I/O itself — callers
 * (Action Engine) are responsible for actually sending these over the
 * transport. This is what makes the group math unit-testable in isolation.
 */
data class GroupDispatch(
    val widgetId: WidgetId,
    val targetDeviceId: DeviceId,
    val action: ActionSpec,
)

/** User-originated interactions that can trigger group semantics. */
sealed class GroupUserInput {
    /** A relative slider (brightness/volume) was dragged from [oldValue] to [newValue]. */
    data class SliderMoved(val originWidgetId: WidgetId, val oldValue: Int, val newValue: Int) : GroupUserInput()

    /** The single mute/toggle button for an ABSOLUTE_TOGGLE group was pressed. */
    data class ToggleActivated(val originWidgetId: WidgetId) : GroupUserInput()

    /**
     * A play/pause **button pair** was pressed with an explicit desired
     * state (docs/ARCHITECTURE.md §4.3, preferred form — unambiguous).
     */
    data class MediaDesiredState(val originWidgetId: WidgetId, val desired: MediaPlaybackState) : GroupUserInput()

    /**
     * A single play/pause toggle button was pressed (ambiguous form): the
     * desired state is derived from the majority of current member states.
     */
    data class MediaToggle(val originWidgetId: WidgetId) : GroupUserInput()

    /** MEDIA_NEXT/MEDIA_PREVIOUS are edge-triggered and always broadcast unconditionally. */
    data class MediaEdge(val originWidgetId: WidgetId, val action: ActionSpec) : GroupUserInput()
}

/**
 * Snapshot of everything [GroupController] needs to compute group math,
 * supplied by the caller (Action Engine / ViewModel) — never fetched by
 * this class, keeping it pure and side-effect free.
 */
class GroupContext(
    private val widgetsById: Map<WidgetId, Widget>,
    private val deviceStates: Map<DeviceId, DeviceState>,
) {
    fun widget(id: WidgetId): Widget? = widgetsById[id]

    fun currentValue(widget: Widget): Int? = when (widget.type) {
        WidgetType.SLIDER_BRIGHTNESS -> deviceStates[widget.targetDeviceId]?.brightness
        WidgetType.SLIDER_VOLUME -> deviceStates[widget.targetDeviceId]?.volume
        else -> null
    }

    fun currentMuted(widget: Widget): Boolean? = deviceStates[widget.targetDeviceId]?.muted

    fun currentMediaState(widget: Widget): MediaPlaybackState? = deviceStates[widget.targetDeviceId]?.mediaState

    companion object {
        fun of(widgets: List<Widget>, deviceStates: List<DeviceState>): GroupContext =
            GroupContext(widgets.associateBy { it.id }, deviceStates.associateBy { it.deviceId })
    }
}

/**
 * Pure implementation of the three group algorithms in
 * docs/ARCHITECTURE.md §4. UI layers must always route group interactions
 * through [apply] rather than computing deltas/targets themselves, so
 * Android and Windows controllers cannot drift apart.
 */
object GroupController {

    fun apply(group: Group, context: GroupContext, input: GroupUserInput): List<GroupDispatch> {
        val members = group.memberWidgetIds.mapNotNull { context.widget(it) }
        return when (group.kind) {
            GroupKind.RELATIVE_SLIDER -> applyRelativeSlider(members, context, input)
            GroupKind.ABSOLUTE_TOGGLE -> applyAbsoluteToggle(members, context, input)
            GroupKind.ABSOLUTE_MEDIA -> applyAbsoluteMedia(members, context, input)
        }
    }

    /** ARCHITECTURE.md §4.1 — independent clamp(0,100) per member, no redistribution. */
    private fun applyRelativeSlider(
        members: List<Widget>,
        context: GroupContext,
        input: GroupUserInput,
    ): List<GroupDispatch> {
        val moved = input as? GroupUserInput.SliderMoved ?: return emptyList()
        val delta = moved.newValue - moved.oldValue
        if (delta == 0 || members.isEmpty()) return emptyList()

        val dispatches = mutableListOf<GroupDispatch>()
        for (member in members) {
            val current = context.currentValue(member) ?: continue
            val proposed = clampPercent(current + delta)
            // Skip members already saturated in the direction of travel: no-op traffic avoidance.
            if (proposed == current) continue
            val action = when (member.type) {
                WidgetType.SLIDER_BRIGHTNESS -> ActionSpec.BrightnessSet(proposed)
                WidgetType.SLIDER_VOLUME -> ActionSpec.VolumeSet(proposed)
                else -> continue
            }
            dispatches += GroupDispatch(member.id, member.targetDeviceId, action)
        }
        return dispatches
    }

    /** ARCHITECTURE.md §4.2 — mute everything unless everything is already muted. */
    private fun applyAbsoluteToggle(
        members: List<Widget>,
        context: GroupContext,
        input: GroupUserInput,
    ): List<GroupDispatch> {
        if (input !is GroupUserInput.ToggleActivated) return emptyList()
        if (members.isEmpty()) return emptyList()

        val allMuted = members.all { context.currentMuted(it) == true }
        val desiredMuted = !allMuted
        return members.map { member ->
            GroupDispatch(member.id, member.targetDeviceId, ActionSpec.SetMuted(desiredMuted))
        }
    }

    /** ARCHITECTURE.md §4.3 — explicit desired state, majority-derived toggle, or edge-triggered skip. */
    private fun applyAbsoluteMedia(
        members: List<Widget>,
        context: GroupContext,
        input: GroupUserInput,
    ): List<GroupDispatch> {
        if (members.isEmpty()) return emptyList()

        return when (input) {
            is GroupUserInput.MediaDesiredState -> members.map { member ->
                GroupDispatch(member.id, member.targetDeviceId, ActionSpec.MediaSetState(input.desired))
            }

            is GroupUserInput.MediaToggle -> {
                val states = members.mapNotNull { context.currentMediaState(it) }
                val playingCount = states.count { it == MediaPlaybackState.PLAYING }
                val pausedCount = states.count { it == MediaPlaybackState.PAUSED }
                val majorityIsPlaying = playingCount >= pausedCount && playingCount > 0
                val desired = if (majorityIsPlaying) MediaPlaybackState.PAUSED else MediaPlaybackState.PLAYING
                members.map { member ->
                    GroupDispatch(member.id, member.targetDeviceId, ActionSpec.MediaSetState(desired))
                }
            }

            is GroupUserInput.MediaEdge -> members.map { member ->
                GroupDispatch(member.id, member.targetDeviceId, input.action)
            }

            else -> emptyList()
        }
    }
}
