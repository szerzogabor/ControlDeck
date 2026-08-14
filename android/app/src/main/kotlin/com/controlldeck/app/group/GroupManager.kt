package com.controlldeck.app.group

import com.controlldeck.domain.DeviceState
import com.controlldeck.domain.Group
import com.controlldeck.domain.GroupContext
import com.controlldeck.domain.GroupController
import com.controlldeck.domain.GroupDispatch
import com.controlldeck.domain.GroupUserInput
import com.controlldeck.domain.ReconnectPolicyResolver
import com.controlldeck.domain.Widget
import com.controlldeck.domain.DeviceId

/**
 * Thin app-layer wrapper around the pure :domain [GroupController] /
 * [ReconnectPolicyResolver] — assembles the [GroupContext] snapshot from
 * live app state and forwards to the pure functions. Per
 * docs/ARCHITECTURE.md §4: "UI layers only ever call
 * GroupController.apply(...) — they never compute deltas or target states
 * themselves."
 */
class GroupManager {

    fun apply(group: Group, widgets: List<Widget>, deviceStates: List<DeviceState>, input: GroupUserInput): List<GroupDispatch> {
        val context = GroupContext.of(widgets, deviceStates)
        return GroupController.apply(group, context, input)
    }

    /** Called by the transport layer when [reconnectedDeviceId] transitions OFFLINE -> ONLINE. */
    fun onDeviceReconnected(
        groups: List<Group>,
        widgets: List<Widget>,
        deviceStates: List<DeviceState>,
        reconnectedDeviceId: DeviceId,
    ): List<GroupDispatch> {
        val context = GroupContext.of(widgets, deviceStates)
        return groups
            .filter { group -> group.memberWidgetIds.any { id -> widgets.find { it.id == id }?.targetDeviceId == reconnectedDeviceId } }
            .flatMap { group -> ReconnectPolicyResolver.resolve(group, reconnectedDeviceId, context) }
    }
}
