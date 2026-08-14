package com.controlldeck.domain

/** Result of validating a widget against its target device's advertised capabilities. */
sealed class CapabilityValidation {
    data object Supported : CapabilityValidation()
    data class Unsupported(val required: Capability, val deviceId: DeviceId) : CapabilityValidation()
    /** The target device has never advertised any capabilities (e.g. never connected/paired). */
    data class UnknownDevice(val deviceId: DeviceId) : CapabilityValidation()
}

/**
 * Validates that a widget's target device actually supports the capability
 * its action requires, per protocol/PROTOCOL.md §3.4 (CAPABILITIES) and
 * §3.5 (ACTION errorCode UNSUPPORTED_CAPABILITY).
 */
object CapabilityValidator {

    fun validate(widget: Widget, capabilitiesByDevice: Map<DeviceId, DeviceCapabilities>): CapabilityValidation {
        val deviceCaps = capabilitiesByDevice[widget.targetDeviceId]
            ?: return CapabilityValidation.UnknownDevice(widget.targetDeviceId)

        val required = widget.type.requiredCapability()
        return if (required in deviceCaps.capabilities) {
            CapabilityValidation.Supported
        } else {
            CapabilityValidation.Unsupported(required, widget.targetDeviceId)
        }
    }

    fun isSupported(widget: Widget, capabilitiesByDevice: Map<DeviceId, DeviceCapabilities>): Boolean =
        validate(widget, capabilitiesByDevice) is CapabilityValidation.Supported
}
