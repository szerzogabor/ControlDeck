package com.controlldeck.app.persistence.db

import com.controlldeck.domain.ActionSpec
import com.controlldeck.domain.AppId
import com.controlldeck.domain.MediaPlaybackState
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Compact, dependency-free encoding of [ActionSpec] for Room storage —
 * avoids pulling the wire (:protocol) format into the persistence layer.
 * Format: `TYPE` or `TYPE|value`.
 */
object ActionSpecCodec {

    fun encode(action: ActionSpec): String = when (action) {
        is ActionSpec.BrightnessSet -> "BRIGHTNESS_SET|${action.value}"
        is ActionSpec.VolumeSet -> "VOLUME_SET|${action.value}"
        is ActionSpec.SetMuted -> "SET_MUTED|${action.muted}"
        is ActionSpec.MediaSetState -> "MEDIA_SET_STATE|${action.state.name}"
        ActionSpec.MediaNext -> "MEDIA_NEXT"
        ActionSpec.MediaPrevious -> "MEDIA_PREVIOUS"
        is ActionSpec.AppLaunch -> "APP_LAUNCH|${action.appId.value}"
    }

    fun decode(encoded: String): ActionSpec {
        val parts = encoded.split("|", limit = 2)
        val type = parts[0]
        val value = parts.getOrNull(1)
        return when (type) {
            "BRIGHTNESS_SET" -> ActionSpec.BrightnessSet(value?.toIntOrNull() ?: 0)
            "VOLUME_SET" -> ActionSpec.VolumeSet(value?.toIntOrNull() ?: 0)
            "SET_MUTED" -> ActionSpec.SetMuted(value?.toBoolean() ?: false)
            "MEDIA_SET_STATE" -> ActionSpec.MediaSetState(
                runCatching { MediaPlaybackState.valueOf(value ?: "PAUSED") }.getOrDefault(MediaPlaybackState.PAUSED),
            )
            "MEDIA_NEXT" -> ActionSpec.MediaNext
            "MEDIA_PREVIOUS" -> ActionSpec.MediaPrevious
            "APP_LAUNCH" -> ActionSpec.AppLaunch(AppId(value ?: ""))
            else -> error("Unknown encoded ActionSpec type: $type")
        }
    }
}

/**
 * Encodes a `Map<String, String>` / `List<String>` as a single column
 * without pulling a JSON dependency into the persistence layer. Uses the
 * ASCII unit/record separator control characters as delimiters (never
 * legal inside URL-encoded UTF-8 output, so this never collides with
 * encoded key/value content).
 */
object MapListCodec {
    private const val ENTRY_SEPARATOR = "" // unit separator
    private const val KV_SEPARATOR = "" // record separator

    fun encodeMap(map: Map<String, String>): String =
        map.entries.joinToString(ENTRY_SEPARATOR) { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}$KV_SEPARATOR${URLEncoder.encode(v, "UTF-8")}"
        }

    fun decodeMap(encoded: String): Map<String, String> {
        if (encoded.isBlank()) return emptyMap()
        return encoded.split(ENTRY_SEPARATOR).mapNotNull { entry ->
            val kv = entry.split(KV_SEPARATOR, limit = 2)
            if (kv.size != 2) return@mapNotNull null
            URLDecoder.decode(kv[0], "UTF-8") to URLDecoder.decode(kv[1], "UTF-8")
        }.toMap()
    }

    fun encodeList(list: List<String>): String = list.joinToString(ENTRY_SEPARATOR) { URLEncoder.encode(it, "UTF-8") }

    fun decodeList(encoded: String): List<String> {
        if (encoded.isBlank()) return emptyList()
        return encoded.split(ENTRY_SEPARATOR).map { URLDecoder.decode(it, "UTF-8") }
    }
}
