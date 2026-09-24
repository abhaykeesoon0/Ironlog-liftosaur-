package com.ironlog.app.core.ai

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini Nano, running on the phone.
 *
 * This is a real language model with no network call: recent Pixel and Galaxy
 * handsets ship one inside the system through AICore, and ML Kit hands an app a
 * prompt interface to it. Nothing is added to the APK, nothing is uploaded, and
 * the answer is generated on the device.
 *
 * The catch is reach. Only a handful of phones have it, and the model has to be
 * downloaded by the system before first use, so every path here reports
 * unavailability plainly and the coach falls back to its own rules. That
 * fallback is not a degraded mode -- it is what most devices will run.
 */
@Singleton
class OnDeviceAssistant @Inject constructor() {

    enum class Availability {
        /** Ready to answer now. */
        READY,

        /** Supported, but the system has to fetch the model first. */
        NEEDS_DOWNLOAD,

        /** No Gemini Nano on this device, which is the common case. */
        UNSUPPORTED
    }

    private val client by lazy { runCatching { Generation.getClient() }.getOrNull() }

    suspend fun availability(): Availability = withContext(Dispatchers.IO) {
        val model = client ?: return@withContext Availability.UNSUPPORTED
        runCatching {
            when (model.checkStatus()) {
                FeatureStatus.AVAILABLE -> Availability.READY
                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> Availability.NEEDS_DOWNLOAD
                else -> Availability.UNSUPPORTED
            }
        }.getOrDefault(Availability.UNSUPPORTED)
    }

    /**
     * Asks the system to fetch the model.
     *
     * Returns true once it is usable. The download is the system's, not ours --
     * it is shared with every other app on the phone using Gemini Nano, which
     * is why it does not count against this app's size.
     */
    suspend fun prepare(): Boolean = withContext(Dispatchers.IO) {
        val model = client ?: return@withContext false
        runCatching {
            if (model.checkStatus() == FeatureStatus.AVAILABLE) return@runCatching true
            model.download().firstOrNull()
            model.checkStatus() == FeatureStatus.AVAILABLE
        }.getOrDefault(false)
    }

    /**
     * Answers a question on the device, or returns null if it cannot.
     *
     * Null rather than an exception: an unsupported phone is the expected case,
     * not a failure, and the caller's response to it is simply to use the rules
     * coach instead.
     */
    suspend fun ask(question: String, context: String): String? = withContext(Dispatchers.IO) {
        val model = client ?: return@withContext null
        runCatching {
            if (model.checkStatus() != FeatureStatus.AVAILABLE) return@runCatching null
            val prompt = buildString {
                appendLine(SYSTEM_PREAMBLE)
                appendLine()
                appendLine(context)
                appendLine()
                append("Question: ")
                append(question)
            }
            model.generateContent(prompt)?.toString()?.trim()?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private companion object {
        /**
         * Nano is a small model, so the instruction is short and concrete.
         * The medical line is the same floor the offline coach applies, because
         * the model can produce whatever it likes and the app should not relay
         * a guess about somebody's pain.
         */
        val SYSTEM_PREAMBLE = """
            You are a fitness coach inside a strength-training app. Answer in at most four sentences,
            using the training figures below where they are relevant. Do not invent numbers.
            If reference material is included below, use it and say where it came from.
            If the question is about pain, injury or illness, say it needs a doctor or physiotherapist.
        """.trimIndent()
    }
}
