package com.ironlog.app.core.sensors

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.ironlog.app.data.repository.UserFacingException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Listens for one spoken phrase and hands back the words.
 *
 * The phone's own recogniser, asked to prefer its offline model: on a modern
 * phone the phrase never leaves the device, and on an older one it goes to
 * the platform's speech service, which is the same place the keyboard's
 * microphone sends it. Nothing is recorded by this app either way; the audio
 * is the recogniser's and the text is all that comes back.
 *
 * One phrase per call, because a set is one phrase.
 */
@Singleton
class VoiceSetInput @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    suspend fun listen(): Result<String> = withContext(Dispatchers.Main) {
        if (!isAvailable()) {
            return@withContext Result.failure(UserFacingException("Speech recognition is not available on this phone"))
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        try {
            suspendCancellableCoroutine { cont ->
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (cont.isActive) cont.resume(
                            heard?.takeIf { it.isNotBlank() }?.let { Result.success(it) }
                                ?: Result.failure(UserFacingException("Didn't catch that"))
                        )
                    }

                    override fun onError(error: Int) {
                        val message = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that. Try \"sixty kilos for eight\"."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone access is needed to listen"
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "The recogniser needs a connection on this phone"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The microphone is busy"
                            else -> "Could not listen right now"
                        }
                        if (cont.isActive) cont.resume(Result.failure(UserFacingException(message)))
                    }

                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                recognizer.startListening(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the weight and the reps")
                    }
                )
                cont.invokeOnCancellation { recognizer.cancel() }
            }
        } finally {
            recognizer.destroy()
        }
    }
}
