package com.controlldeck.app.capability

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent
import com.controlldeck.app.logging.Logger
import com.controlldeck.app.persistence.AppRegistryRepository
import com.controlldeck.domain.ActionSpec
import com.controlldeck.domain.Capability
import com.controlldeck.domain.MediaPlaybackState

private const val TAG = "LocalCapability"

/** Outcome of executing an [ActionSpec] against this device's platform APIs. */
sealed class LocalActionOutcome {
    data class Success(val resultingState: ActionSpec) : LocalActionOutcome()
    data class Failure(val errorCode: String) : LocalActionOutcome()
}

object LocalActionErrorCode {
    const val UNSUPPORTED_CAPABILITY = "UNSUPPORTED_CAPABILITY"
    const val APP_NOT_FOUND = "APP_NOT_FOUND"
    const val PLATFORM_ERROR = "PLATFORM_ERROR"
    const val INVALID_VALUE = "INVALID_VALUE"
}

/**
 * Executes ACTIONs against real Android platform APIs and reports this
 * device's own fixed capability set (docs/ARCHITECTURE.md "Capability
 * Registry"). Every platform call is wrapped so failures become
 * [LocalActionOutcome.Failure] rather than propagating exceptions
 * (docs/ARCHITECTURE.md §7).
 */
class LocalCapabilityController(
    private val context: Context,
    private val appRegistryRepository: AppRegistryRepository,
    private val logger: Logger,
) {
    private val audioManager: AudioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    /** This device's fixed capability set — Android always supports all of these APIs. */
    fun localCapabilities(): Set<Capability> = setOf(
        Capability.BRIGHTNESS,
        Capability.VOLUME,
        Capability.MUTE,
        Capability.MEDIA_PLAY_PAUSE,
        Capability.MEDIA_NEXT,
        Capability.MEDIA_PREVIOUS,
        Capability.APP_LAUNCH,
    )

    fun canWriteSystemSettings(): Boolean = Settings.System.canWrite(context)

    /** Launches the system settings screen for the user to grant WRITE_SETTINGS, per Android's required flow. */
    fun buildWriteSettingsPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    suspend fun execute(action: ActionSpec): LocalActionOutcome = try {
        when (action) {
            is ActionSpec.BrightnessSet -> setBrightness(action.value)
            is ActionSpec.VolumeSet -> setVolume(action.value)
            is ActionSpec.SetMuted -> setMuted(action.muted)
            is ActionSpec.MediaSetState -> setMediaState(action.state)
            ActionSpec.MediaNext -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT).let { LocalActionOutcome.Success(ActionSpec.MediaNext) }
            ActionSpec.MediaPrevious -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS).let { LocalActionOutcome.Success(ActionSpec.MediaPrevious) }
            is ActionSpec.AppLaunch -> launchApp(action)
        }
    } catch (e: Exception) {
        logger.e(TAG, "action execution threw for $action", e)
        LocalActionOutcome.Failure(LocalActionErrorCode.PLATFORM_ERROR)
    }

    private fun setBrightness(value: Int): LocalActionOutcome {
        if (value !in 0..100) return LocalActionOutcome.Failure(LocalActionErrorCode.INVALID_VALUE)
        if (!Settings.System.canWrite(context)) return LocalActionOutcome.Failure(LocalActionErrorCode.PLATFORM_ERROR)
        val systemValue = (value * 255) / 100
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, systemValue)
        return LocalActionOutcome.Success(ActionSpec.BrightnessSet(value))
    }

    fun getBrightnessPercent(): Int = try {
        val raw = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        (raw * 100) / 255
    } catch (e: Settings.SettingNotFoundException) {
        50
    }

    private fun setVolume(value: Int): LocalActionOutcome {
        if (value !in 0..100) return LocalActionOutcome.Failure(LocalActionErrorCode.INVALID_VALUE)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (value * max) / 100
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        return LocalActionOutcome.Success(ActionSpec.VolumeSet(value))
    }

    fun getVolumePercent(): Int {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max == 0) return 0
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return (current * 100) / max
    }

    private fun setMuted(muted: Boolean): LocalActionOutcome {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
            0,
        )
        return LocalActionOutcome.Success(ActionSpec.SetMuted(muted))
    }

    fun isMuted(): Boolean = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)

    private fun setMediaState(state: MediaPlaybackState): LocalActionOutcome {
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        // Android exposes no reliable synchronous "force play"/"force pause" for an
        // arbitrary foreground media session without a MediaController bound to a
        // specific session, so PLAY_PAUSE toggle is dispatched and the requested
        // state is optimistically reported; real state reconciles via the next
        // STATE_UPDATE once the OS-level playback state actually changes.
        return LocalActionOutcome.Success(ActionSpec.MediaSetState(state))
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val eventTime = System.currentTimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private suspend fun launchApp(action: ActionSpec.AppLaunch): LocalActionOutcome {
        val entry = appRegistryRepository.getEntry(action.appId) ?: return LocalActionOutcome.Failure(LocalActionErrorCode.APP_NOT_FOUND)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(entry.packageName)
            ?: return LocalActionOutcome.Failure(LocalActionErrorCode.APP_NOT_FOUND)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return LocalActionOutcome.Success(action)
    }
}
