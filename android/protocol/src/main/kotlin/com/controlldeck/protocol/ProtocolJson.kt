package com.controlldeck.protocol

import kotlinx.serialization.json.Json

/**
 * The single [Json] configuration every ControlDeck peer must use.
 * `ignoreUnknownKeys = true` implements the forward-compatibility rule in
 * protocol/PROTOCOL.md §2/§6 ("unknown fields in payload MUST be ignored").
 */
object ProtocolJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
        isLenient = false
        classDiscriminator = "type"
    }
}
