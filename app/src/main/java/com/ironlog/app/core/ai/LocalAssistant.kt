package com.ironlog.app.core.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The downloaded model, answering on the phone.
 *
 * This is the coach's third brain, between Gemini Nano (only on some phones)
 * and Claude (only with the user's own key): a model file the user fetched
 * once, run by an on-device inference library. No key, no account, and after
 * the download nothing leaves the handset. It reads the same brief the other
 * two get: the user's own figures, and a web extract when lookups are on.
 *
 * Loading the model costs seconds and a good slice of memory, so the engine
 * is built once and kept, and questions queue behind a lock rather than
 * loading a second copy.
 */
@Singleton
class LocalAssistant @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: LocalModelStore
) {
    private var loaded: Pair<LocalModel, LlmInference>? = null
    private val lock = Mutex()

    /** Answers on the phone, or returns null when no model is ready or it fails to answer. */
    suspend fun ask(question: String, brief: String): String? = withContext(Dispatchers.Default) {
        val model = store.ready() ?: return@withContext null
        lock.withLock {
            val engine = runCatching { engineFor(model) }
                .onFailure { Log.e("IronLog", "Could not load the coach model", it) }
                .getOrNull() ?: return@withLock null
            runCatching {
                LlmInferenceSession.createFromOptions(
                    engine,
                    LlmInferenceSession.LlmInferenceSessionOptions.builder()
                        .setTopK(TOP_K)
                        .setTemperature(TEMPERATURE)
                        .build()
                ).use { session ->
                    session.addQueryChunk(prompt(question, brief))
                    session.generateResponse()
                }
            }
                .onFailure { Log.e("IronLog", "The coach model failed to answer", it) }
                .getOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
    }

    /** Drops the loaded engine so its memory goes back, for when the model is removed or changed. */
    suspend fun release() = lock.withLock {
        loaded?.second?.close()
        loaded = null
    }

    private fun engineFor(model: LocalModel): LlmInference {
        loaded?.let { (current, engine) -> if (current == model) return engine else engine.close() }
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(store.file(model).absolutePath)
            .setMaxTokens(MAX_TOKENS)
            .build()
        return LlmInference.createFromOptions(context, options).also { loaded = model to it }
    }

    /**
     * The model's own chat layout, written out rather than relied on: the
     * bundle carries a template, but spelling it out means the instruction,
     * the user's figures and the question land in the roles the model was
     * trained to expect.
     */
    private fun prompt(question: String, brief: String): String = buildString {
        append("<|im_start|>system\n").append(SYSTEM_PREAMBLE).append("<|im_end|>\n")
        append("<|im_start|>user\n").append(brief.trim()).append("\n\nQuestion: ").append(question).append("<|im_end|>\n")
        append("<|im_start|>assistant\n")
    }

    private companion object {
        /** Prompt and answer together; the brief is a few lines, so most of this is the reply. */
        const val MAX_TOKENS = 1024
        const val TOP_K = 40
        const val TEMPERATURE = 0.6f

        val SYSTEM_PREAMBLE = """
            You are a knowledgeable, friendly strength-training coach inside a workout-tracking app.
            Answer in plain English in at most five sentences. Speak to the user as "you", never as "the user".
            Write plain prose: no markdown, no asterisks, no headings, no numbered or bulleted lists.
            The user's own training figures are provided; use them where they are relevant and never invent numbers.
            If reference material from the web is included, draw on it and say where it came from.
            If the question is about pain, injury or illness, say it needs a doctor or physiotherapist.
        """.trimIndent()
    }
}
