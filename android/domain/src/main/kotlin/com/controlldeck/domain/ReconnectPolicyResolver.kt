package com.controlldeck.domain

/**
 * Implements docs/ARCHITECTURE.md §5: what happens when a group member
 * transitions OFFLINE -> ONLINE. Called by the Group Manager, never by the
 * UI directly.
 */
object ReconnectPolicyResolver {

    /**
     * Computes the corrective dispatches (if any) for [reconnectedDeviceId]
     * rejoining [group]. Returns an empty list for [ReconnectPolicy.KEEP_DEVICE_STATE]
     * and [ReconnectPolicy.NO_ACTION] — those policies never send an ACTION.
     */
    fun resolve(group: Group, reconnectedDeviceId: DeviceId, context: GroupContext): List<GroupDispatch> {
        if (group.reconnectPolicy != ReconnectPolicy.SYNC_GROUP_STATE) return emptyList()

        val members = group.memberWidgetIds.mapNotNull { context.widget(it) }
        val reconnectedMembers = members.filter { it.targetDeviceId == reconnectedDeviceId }
        if (reconnectedMembers.isEmpty()) return emptyList()

        val otherMembers = members.filterNot { it.targetDeviceId == reconnectedDeviceId }
        if (otherMembers.isEmpty()) return emptyList()

        return when (group.kind) {
            GroupKind.RELATIVE_SLIDER -> syncRelativeSlider(reconnectedMembers, otherMembers, context)
            GroupKind.ABSOLUTE_TOGGLE -> syncAbsoluteToggle(reconnectedMembers, otherMembers, context)
            GroupKind.ABSOLUTE_MEDIA -> syncAbsoluteMedia(reconnectedMembers, otherMembers, context)
        }
    }

    private fun syncRelativeSlider(
        reconnected: List<Widget>,
        others: List<Widget>,
        context: GroupContext,
    ): List<GroupDispatch> {
        val baseline = mode(others.mapNotNull { context.currentValue(it) }) ?: return emptyList()
        return reconnected.mapNotNull { widget ->
            val current = context.currentValue(widget)
            if (current == baseline) return@mapNotNull null
            val action = when (widget.type) {
                WidgetType.SLIDER_BRIGHTNESS -> ActionSpec.BrightnessSet(baseline)
                WidgetType.SLIDER_VOLUME -> ActionSpec.VolumeSet(baseline)
                else -> return@mapNotNull null
            }
            GroupDispatch(widget.id, widget.targetDeviceId, action)
        }
    }

    private fun syncAbsoluteToggle(
        reconnected: List<Widget>,
        others: List<Widget>,
        context: GroupContext,
    ): List<GroupDispatch> {
        val baseline = mode(others.mapNotNull { context.currentMuted(it) }) ?: return emptyList()
        return reconnected.mapNotNull { widget ->
            val current = context.currentMuted(widget)
            if (current == baseline) return@mapNotNull null
            GroupDispatch(widget.id, widget.targetDeviceId, ActionSpec.SetMuted(baseline))
        }
    }

    private fun syncAbsoluteMedia(
        reconnected: List<Widget>,
        others: List<Widget>,
        context: GroupContext,
    ): List<GroupDispatch> {
        val baseline = mode(others.mapNotNull { context.currentMediaState(it) }) ?: return emptyList()
        return reconnected.mapNotNull { widget ->
            val current = context.currentMediaState(widget)
            if (current == baseline) return@mapNotNull null
            GroupDispatch(widget.id, widget.targetDeviceId, ActionSpec.MediaSetState(baseline))
        }
    }

    /** Most frequent value; ties broken by first-encountered (stable) order. Null for an empty input. */
    private fun <T> mode(values: List<T>): T? {
        if (values.isEmpty()) return null
        return values.groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }
            .first().key
    }
}
