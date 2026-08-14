package com.controlldeck.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageDispatcherTest {

    private val json = ProtocolJson.instance

    // ---- 3.1 HELLO — literal JSON from protocol/PROTOCOL.md §3.1 ----

    @Test
    fun `HELLO round-trips against the literal spec JSON`() {
        val raw = """
            {
              "protocolVersion": 1,
              "type": "HELLO",
              "messageId": "b3d6e2b0-0000-0000-0000-000000000001",
              "sourceDeviceId": "pc-uuid",
              "timestamp": 1723600000000,
              "payload": {
                "protocolVersion": 1,
                "deviceId": "uuid",
                "deviceName": "Gaming PC",
                "platform": "WINDOWS",
                "appVersion": "0.1.0",
                "secure": false
              }
            }
        """.trimIndent()

        val parsed = MessageDispatcher.parse(raw, json)
        check(parsed is ParsedMessage.Hello)
        assertEquals("uuid", parsed.payload.deviceId)
        assertEquals("Gaming PC", parsed.payload.deviceName)
        assertEquals("WINDOWS", parsed.payload.platform)
        assertEquals("0.1.0", parsed.payload.appVersion)
        assertFalse(parsed.payload.secure)
        assertEquals("pc-uuid", parsed.envelope.sourceDeviceId)
        assertNull(parsed.envelope.targetDeviceId)

        val reserialized = MessageDispatcher.serialize(parsed, json)
        val reparsed = MessageDispatcher.parse(reserialized, json)
        assertEquals(parsed, reparsed)
    }

    // ---- 3.2 Pairing ----

    @Test
    fun `PAIR_REQUEST round-trips against the literal spec JSON`() {
        val raw = """
            {
              "type": "PAIR_REQUEST",
              "messageId": "msg-1",
              "sourceDeviceId": "tablet-uuid",
              "timestamp": 1000,
              "payload": {
                "requesterDeviceId": "uuid",
                "requesterDeviceName": "Living Room Tablet",
                "requesterPlatform": "ANDROID",
                "pairingToken": "481927"
              }
            }
        """.trimIndent()

        val parsed = MessageDispatcher.parse(raw, json)
        check(parsed is ParsedMessage.PairRequest)
        assertEquals("Living Room Tablet", parsed.payload.requesterDeviceName)
        assertEquals("ANDROID", parsed.payload.requesterPlatform)
        assertEquals("481927", parsed.payload.pairingToken)
    }

    @Test
    fun `PAIR_RESPONSE accepted round-trips against the literal spec JSON`() {
        val raw = """
            {
              "type": "PAIR_RESPONSE",
              "messageId": "msg-2",
              "sourceDeviceId": "pc-uuid",
              "timestamp": 1001,
              "payload": {
                "accepted": true,
                "reason": null,
                "deviceId": "uuid-of-responder",
                "deviceName": "Gaming PC",
                "platform": "WINDOWS",
                "sharedSecret": "base64-32-random-bytes"
              }
            }
        """.trimIndent()

        val parsed = MessageDispatcher.parse(raw, json)
        check(parsed is ParsedMessage.PairResponse)
        assertTrue(parsed.payload.accepted)
        assertNull(parsed.payload.reason)
        assertEquals("base64-32-random-bytes", parsed.payload.sharedSecret)
    }

    @Test
    fun `PAIR_RESPONSE rejected carries a reason and null secret`() {
        val raw = """
            {
              "type": "PAIR_RESPONSE",
              "messageId": "msg-3",
              "sourceDeviceId": "pc-uuid",
              "timestamp": 1002,
              "payload": {
                "accepted": false,
                "reason": "TOKEN_EXPIRED",
                "deviceId": "uuid-of-responder",
                "deviceName": "Gaming PC",
                "platform": "WINDOWS",
                "sharedSecret": null
              }
            }
        """.trimIndent()

        val parsed = MessageDispatcher.parse(raw, json)
        check(parsed is ParsedMessage.PairResponse)
        assertFalse(parsed.payload.accepted)
        assertEquals(PairRejectReason.TOKEN_EXPIRED, parsed.payload.reason)
        assertNull(parsed.payload.sharedSecret)
    }

    // ---- 3.3 AUTH ----

    @Test
    fun `AUTH and AUTH_RESULT round-trip against the literal spec JSON`() {
        val authRaw = """
            {
              "type": "AUTH",
              "messageId": "msg-4",
              "sourceDeviceId": "tablet-uuid",
              "timestamp": 1003,
              "payload": { "deviceId": "uuid-of-sender", "proof": "base64hmac" }
            }
        """.trimIndent()
        val authResultRaw = """
            {
              "type": "AUTH_RESULT",
              "messageId": "msg-5",
              "sourceDeviceId": "pc-uuid",
              "timestamp": 1004,
              "payload": { "accepted": true, "reason": null }
            }
        """.trimIndent()

        val auth = MessageDispatcher.parse(authRaw, json)
        check(auth is ParsedMessage.Auth)
        assertEquals("uuid-of-sender", auth.payload.deviceId)
        assertEquals("base64hmac", auth.payload.proof)

        val authResult = MessageDispatcher.parse(authResultRaw, json)
        check(authResult is ParsedMessage.AuthResult)
        assertTrue(authResult.payload.accepted)
    }

    // ---- 3.4 DEVICE_INFO / CAPABILITIES ----

    @Test
    fun `DEVICE_INFO round-trips against the literal spec JSON`() {
        val raw = """
            {
              "type": "DEVICE_INFO",
              "messageId": "msg-6",
              "sourceDeviceId": "pc-uuid",
              "timestamp": 1005,
              "payload": { "deviceId": "uuid", "deviceName": "Gaming PC", "platform": "WINDOWS", "appVersion": "0.1.0" }
            }
        """.trimIndent()

        val parsed = MessageDispatcher.parse(raw, json)
        check(parsed is ParsedMessage.DeviceInfo)
        assertEquals("Gaming PC", parsed.payload.deviceName)
    }

    @Test
    fun `CAPABILITIES round-trips against the literal spec JSON`() {
        val raw = """
            {
              "type": "CAPABILITIES",
              "messageId": "msg-7",
              "sourceDeviceId": "pc-uuid",
              "timestamp": 1006,
              "payload": {
                "deviceId": "uuid",
                "capabilities": ["VOLUME", "MUTE", "MEDIA_PLAY_PAUSE", "MEDIA_NEXT", "MEDIA_PREVIOUS", "APP_LAUNCH"],
                "apps": [
                  { "appId": "spotify", "displayName": "Spotify" },
                  { "appId": "discord", "displayName": "Discord" }
                ]
              }
            }
        """.trimIndent()

        val parsed = MessageDispatcher.parse(raw, json)
        check(parsed is ParsedMessage.Capabilities)
        assertEquals(6, parsed.payload.capabilities.size)
        assertEquals("spotify", parsed.payload.apps[0].appId)
        assertEquals("Spotify", parsed.payload.apps[0].displayName)
    }

    @Test
    fun `CAPABILITIES tolerates an unknown future capability string`() {
        val raw = """
            {
              "type": "CAPABILITIES",
              "messageId": "msg-7b",
              "sourceDeviceId": "pc-uuid",
              "timestamp": 1006,
              "payload": { "deviceId": "uuid", "capabilities": ["VOLUME", "SOME_FUTURE_CAPABILITY"], "apps": [] }
            }
        """.trimIndent()

        val parsed = MessageDispatcher.parse(raw, json)
        check(parsed is ParsedMessage.Capabilities)
        assertTrue("SOME_FUTURE_CAPABILITY" in parsed.payload.capabilities)
        assertFalse("SOME_FUTURE_CAPABILITY" in CapabilityValues.known)
    }

    // ---- 3.5 ACTION / ACTION_RESULT ----

    @Test
    fun `ACTION round-trips against the literal spec JSON`() {
        val raw = """
            {
              "type": "ACTION",
              "messageId": "msg-8",
              "sourceDeviceId": "tablet-id",
              "targetDeviceId": "pc-id",
              "timestamp": 1007,
              "payload": { "action": { "type": "BRIGHTNESS_SET", "value": 70 } }
            }
        """.trimIndent()

        val parsed = MessageDispatcher.parse(raw, json)
        check(parsed is ParsedMessage.Action)
        assertEquals(ActionDto.BrightnessSet(70), parsed.payload.action)
        assertEquals("pc-id", parsed.envelope.targetDeviceId)
    }

    @Test
    fun `every ACTION type round-trips through its ActionDto subtype`() {
        val actions = listOf(
            ActionDto.BrightnessSet(70),
            ActionDto.VolumeSet(35),
            ActionDto.SetMuted(true),
            ActionDto.MediaSetState(MediaStateValues.PLAYING),
            ActionDto.MediaNext,
            ActionDto.MediaPrevious,
            ActionDto.AppLaunch("spotify"),
        )
        for (action in actions) {
            val message = ParsedMessage.Action(
                envelope = Envelope(type = MessageTypes.ACTION, messageId = "m", sourceDeviceId = "a", targetDeviceId = "b", timestamp = 1),
                payload = ActionPayload(action),
            )
            val roundTripped = MessageDispatcher.parse(MessageDispatcher.serialize(message, json), json)
            check(roundTripped is ParsedMessage.Action)
            assertEquals(action, roundTripped.payload.action)
        }
    }

    @Test
    fun `ACTION_RESULT round-trips against the literal spec JSON`() {
        val raw = """
            {
              "type": "ACTION_RESULT",
              "messageId": "msg-9",
              "sourceDeviceId": "pc-id",
              "timestamp": 1008,
              "payload": {
                "correlatesTo": "messageId-of-the-ACTION",
                "success": true,
                "errorCode": null,
                "resultingState": { "type": "BRIGHTNESS_SET", "value": 70 }
              }
            }
        """.trimIndent()

        val parsed = MessageDispatcher.parse(raw, json)
        check(parsed is ParsedMessage.ActionResult)
        assertEquals("messageId-of-the-ACTION", parsed.payload.correlatesTo)
        assertTrue(parsed.payload.success)
        assertNull(parsed.payload.errorCode)
        assertEquals(ActionDto.BrightnessSet(70), parsed.payload.resultingState)
    }

    @Test
    fun `ACTION_RESULT failure carries an error code and no resultingState`() {
        val raw = """
            {
              "type": "ACTION_RESULT",
              "messageId": "msg-10",
              "sourceDeviceId": "pc-id",
              "timestamp": 1009,
              "payload": { "correlatesTo": "m", "success": false, "errorCode": "UNSUPPORTED_CAPABILITY", "resultingState": null }
            }
        """.trimIndent()

        val parsed = MessageDispatcher.parse(raw, json)
        check(parsed is ParsedMessage.ActionResult)
        assertFalse(parsed.payload.success)
        assertEquals(ActionErrorCode.UNSUPPORTED_CAPABILITY, parsed.payload.errorCode)
        assertNull(parsed.payload.resultingState)
    }

    // ---- 3.6 STATE_UPDATE ----

    @Test
    fun `STATE_UPDATE round-trips and mirrors the resultingState shape`() {
        val message = ParsedMessage.StateUpdate(
            envelope = Envelope(type = MessageTypes.STATE_UPDATE, messageId = "m", sourceDeviceId = "pc-id", timestamp = 2000),
            payload = StateUpdatePayload(deviceId = "pc-id", state = ActionDto.VolumeSet(42)),
        )

        val roundTripped = MessageDispatcher.parse(MessageDispatcher.serialize(message, json), json)
        check(roundTripped is ParsedMessage.StateUpdate)
        assertEquals("pc-id", roundTripped.payload.deviceId)
        assertEquals(ActionDto.VolumeSet(42), roundTripped.payload.state)
    }

    // ---- 3.7 DASHBOARD_SYNC / DASHBOARD_ACK ----

    @Test
    fun `DASHBOARD_SYNC round-trips against the literal spec JSON shape`() {
        val raw = """
            {
              "type": "DASHBOARD_SYNC",
              "messageId": "msg-11",
              "sourceDeviceId": "tablet-id",
              "timestamp": 1010,
              "payload": {
                "dashboard": {
                  "id": "uuid",
                  "name": "Gaming",
                  "version": 14,
                  "widgets": [
                    {
                      "id": "w1",
                      "type": "SLIDER_VOLUME",
                      "position": { "x": 0, "y": 0 },
                      "size": { "width": 2, "height": 1 },
                      "targetDeviceId": "pc-id",
                      "action": { "type": "VOLUME_SET", "value": 50 },
                      "configuration": { "label": "PC Volume" }
                    }
                  ],
                  "groups": [
                    { "id": "g1", "name": "Volume", "kind": "RELATIVE_SLIDER", "memberWidgetIds": ["w1"], "reconnectPolicy": "SYNC_GROUP_STATE" }
                  ]
                }
              }
            }
        """.trimIndent()

        val parsed = MessageDispatcher.parse(raw, json)
        check(parsed is ParsedMessage.DashboardSync)
        assertEquals(14L, parsed.payload.dashboard.version)
        assertEquals(1, parsed.payload.dashboard.widgets.size)
        assertEquals("PC Volume", parsed.payload.dashboard.widgets[0].configuration["label"])
        assertEquals(ActionDto.VolumeSet(50), parsed.payload.dashboard.widgets[0].action)
        assertEquals(1, parsed.payload.dashboard.groups.size)
        assertEquals("RELATIVE_SLIDER", parsed.payload.dashboard.groups[0].kind)
    }

    @Test
    fun `DASHBOARD_ACK round-trips against the literal spec JSON`() {
        val raw = """
            {
              "type": "DASHBOARD_ACK",
              "messageId": "msg-12",
              "sourceDeviceId": "pc-id",
              "timestamp": 1011,
              "payload": { "dashboardId": "uuid", "appliedVersion": 14 }
            }
        """.trimIndent()

        val parsed = MessageDispatcher.parse(raw, json)
        check(parsed is ParsedMessage.DashboardAck)
        assertEquals("uuid", parsed.payload.dashboardId)
        assertEquals(14L, parsed.payload.appliedVersion)
    }

    // ---- 3.8 ERROR / PING / PONG ----

    @Test
    fun `ERROR round-trips against the literal spec JSON`() {
        val raw = """
            { "type": "ERROR", "messageId": "msg-13", "sourceDeviceId": "pc-id", "timestamp": 1012,
              "payload": { "code": "UNSUPPORTED_MESSAGE_TYPE", "message": "unrecognized type", "correlatesTo": null } }
        """.trimIndent()

        val parsed = MessageDispatcher.parse(raw, json)
        check(parsed is ParsedMessage.Error)
        assertEquals(ErrorCode.UNSUPPORTED_MESSAGE_TYPE, parsed.payload.code)
    }

    @Test
    fun `PING and PONG carry no meaningful payload`() {
        val pingRaw = """{ "type": "PING", "messageId": "p1", "sourceDeviceId": "a", "timestamp": 1 }"""
        val pongRaw = """{ "type": "PONG", "messageId": "p2", "sourceDeviceId": "b", "timestamp": 2 }"""

        assertTrue(MessageDispatcher.parse(pingRaw, json) is ParsedMessage.Ping)
        assertTrue(MessageDispatcher.parse(pongRaw, json) is ParsedMessage.Pong)
    }

    // ---- Forward compatibility (protocol/PROTOCOL.md §2 / §6) ----

    @Test
    fun `unknown extra fields in payload do not break decoding`() {
        val raw = """
            {
              "type": "DEVICE_INFO",
              "messageId": "msg-14",
              "sourceDeviceId": "pc-id",
              "timestamp": 1013,
              "payload": {
                "deviceId": "uuid", "deviceName": "PC", "platform": "WINDOWS", "appVersion": "0.1.0",
                "somethingFromTheFuture": { "nested": true, "value": 42 },
                "anotherNewField": "ignored"
              }
            }
        """.trimIndent()

        val parsed = MessageDispatcher.parse(raw, json)
        check(parsed is ParsedMessage.DeviceInfo)
        assertEquals("PC", parsed.payload.deviceName)
    }

    @Test
    fun `unrecognized type decodes into Unknown rather than throwing`() {
        val raw = """
            { "type": "SOME_FUTURE_MESSAGE_TYPE", "messageId": "msg-15", "sourceDeviceId": "pc-id", "timestamp": 1014,
              "payload": { "anything": "goes" } }
        """.trimIndent()

        val parsed = MessageDispatcher.parse(raw, json)

        assertTrue(parsed is ParsedMessage.Unknown)
        assertEquals("SOME_FUTURE_MESSAGE_TYPE", parsed.envelope.type)
        assertEquals("msg-15", parsed.envelope.messageId)
    }

    @Test
    fun `malformed payload with a missing required field throws MalformedPayloadException`() {
        val raw = """
            { "type": "HELLO", "messageId": "msg-16", "sourceDeviceId": "pc-id", "timestamp": 1015,
              "payload": { "protocolVersion": 1, "deviceName": "PC" } }
        """.trimIndent()

        val ex = assertThrows(MalformedPayloadException::class.java) {
            MessageDispatcher.parse(raw, json)
        }
        assertEquals("HELLO", ex.messageType)
    }

    @Test
    fun `malformed payload wrong JSON type throws MalformedPayloadException`() {
        val raw = """
            { "type": "ACTION", "messageId": "msg-17", "sourceDeviceId": "a", "targetDeviceId": "b", "timestamp": 1016,
              "payload": { "action": { "type": "BRIGHTNESS_SET", "value": "not-a-number" } } }
        """.trimIndent()

        assertThrows(MalformedPayloadException::class.java) {
            MessageDispatcher.parse(raw, json)
        }
    }

    @Test
    fun `malformed envelope missing required field throws MalformedPayloadException`() {
        val raw = """{ "type": "HELLO", "sourceDeviceId": "pc-id", "timestamp": 1017, "payload": {} }"""

        val ex = assertThrows(MalformedPayloadException::class.java) {
            MessageDispatcher.parse(raw, json)
        }
        assertEquals("<envelope>", ex.messageType)
    }

    // ---- messageId / deviceId round trip ----

    @Test
    fun `messageId and device ids survive a full encode-decode round trip untouched`() {
        val message = ParsedMessage.Action(
            envelope = Envelope(
                type = MessageTypes.ACTION,
                messageId = "b3d6e2b0-1111-2222-3333-444455556666",
                sourceDeviceId = "aaaaaaaa-source",
                targetDeviceId = "bbbbbbbb-target",
                timestamp = 1723600000000,
            ),
            payload = ActionPayload(ActionDto.VolumeSet(10)),
        )

        val raw = MessageDispatcher.serialize(message, json)
        val roundTripped = MessageDispatcher.parse(raw, json)

        assertEquals(message.envelope.messageId, roundTripped.envelope.messageId)
        assertEquals(message.envelope.sourceDeviceId, roundTripped.envelope.sourceDeviceId)
        assertEquals(message.envelope.targetDeviceId, roundTripped.envelope.targetDeviceId)
    }

    @Test
    fun `envelope defaults protocolVersion to the current version when omitted`() {
        val envelope = json.decodeFromString(
            Envelope.serializer(),
            """{ "type": "PING", "messageId": "m", "sourceDeviceId": "a", "timestamp": 1 }""",
        )
        assertEquals(PROTOCOL_VERSION, envelope.protocolVersion)
    }
}
