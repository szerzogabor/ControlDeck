package com.controlldeck.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CapabilityValidatorTest {

    private val widget = Widget(
        id = WidgetId("w1"),
        type = WidgetType.SLIDER_BRIGHTNESS,
        position = GridPosition(0, 0),
        size = GridSize(1, 1),
        targetDeviceId = DeviceId("pc-1"),
        action = ActionSpec.BrightnessSet(50),
    )

    @Test
    fun `widget targeting a device lacking the required capability is flagged unsupported`() {
        val caps = mapOf(
            DeviceId("pc-1") to DeviceCapabilities(DeviceId("pc-1"), setOf(Capability.VOLUME, Capability.MUTE)),
        )

        val result = CapabilityValidator.validate(widget, caps)

        assertEquals(CapabilityValidation.Unsupported(Capability.BRIGHTNESS, DeviceId("pc-1")), result)
        assertTrue(!CapabilityValidator.isSupported(widget, caps))
    }

    @Test
    fun `widget targeting a device with the required capability is supported`() {
        val caps = mapOf(
            DeviceId("pc-1") to DeviceCapabilities(DeviceId("pc-1"), setOf(Capability.BRIGHTNESS, Capability.VOLUME)),
        )

        val result = CapabilityValidator.validate(widget, caps)

        assertEquals(CapabilityValidation.Supported, result)
        assertTrue(CapabilityValidator.isSupported(widget, caps))
    }

    @Test
    fun `widget targeting an unknown device is flagged as unknown, not silently unsupported`() {
        val result = CapabilityValidator.validate(widget, emptyMap())

        assertEquals(CapabilityValidation.UnknownDevice(DeviceId("pc-1")), result)
    }

    @Test
    fun `every widget type maps to its documented required capability`() {
        assertEquals(Capability.BRIGHTNESS, WidgetType.SLIDER_BRIGHTNESS.requiredCapability())
        assertEquals(Capability.VOLUME, WidgetType.SLIDER_VOLUME.requiredCapability())
        assertEquals(Capability.MUTE, WidgetType.BUTTON_MUTE.requiredCapability())
        assertEquals(Capability.MEDIA_PLAY_PAUSE, WidgetType.BUTTON_MEDIA_PLAY_PAUSE.requiredCapability())
        assertEquals(Capability.MEDIA_NEXT, WidgetType.BUTTON_MEDIA_NEXT.requiredCapability())
        assertEquals(Capability.MEDIA_PREVIOUS, WidgetType.BUTTON_MEDIA_PREVIOUS.requiredCapability())
        assertEquals(Capability.APP_LAUNCH, WidgetType.APP_LAUNCH.requiredCapability())
    }
}
