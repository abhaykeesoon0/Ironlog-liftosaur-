package com.ironlog.app.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.ironlog.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The launch sting.
 *
 * Playing audio unasked is intrusive, so this declines in every case where a
 * sound would be unwelcome rather than assuming it is wanted:
 *
 *  - the phone is on silent or vibrate;
 *  - something is already playing, so the user is listening to their own music
 *    and a chime over the top of it is an interruption, not a welcome;
 *  - the user turned the setting off.
 *
 * [SoundPool] rather than MediaPlayer because this is a short, fully decoded
 * clip played at most once per launch, which is exactly what SoundPool is for.
 */
@Singleton
class WelcomeSound @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var pool: SoundPool? = null
    private var soundId: Int = 0

    /**
     * True when a sound would actually be heard. Checked before loading so a
     * silenced phone does no decoding work at all.
     */
    private fun canBeHeard(): Boolean {
        val manager = audioManager ?: return false
        if (manager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return false
        if (manager.isMusicActive) return false
        return true
    }

    /**
     * Plays the sting once, if it would be heard.
     *
     * Loading is asynchronous, so playback is started from the load callback.
     * A failed load leaves [playedSuccessfully] false and plays nothing --
     * there is no error to surface here, because a missing chime is not
     * something the user needs to act on or would thank us for interrupting
     * their launch to be told about.
     */
    fun play(onFinished: (() -> Unit)? = null) {
        if (!canBeHeard()) {
            onFinished?.invoke()
            return
        }
        release()

        val attributes = AudioAttributes.Builder()
            // Sonification, not media: this is UI feedback, and it should not
            // be treated as content the way a podcast or a track would be.
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val created = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(attributes)
            .build()
        pool = created

        created.setOnLoadCompleteListener { loadedPool, loadedId, status ->
            if (status == LOAD_SUCCESS && loadedId == soundId) {
                loadedPool.play(loadedId, VOLUME, VOLUME, 1, 0, 1f)
            }
            onFinished?.invoke()
        }
        soundId = created.load(context, R.raw.welcome_sting, 1)
    }

    /** Frees the decoded clip. The sting is not needed again this launch. */
    fun release() {
        pool?.release()
        pool = null
        soundId = 0
    }

    private companion object {
        const val LOAD_SUCCESS = 0

        /**
         * Below full scale deliberately. The sting is a greeting, not an alarm,
         * and it plays before the user has had any chance to reach for a volume
         * key.
         */
        const val VOLUME = 0.6f
    }
}
