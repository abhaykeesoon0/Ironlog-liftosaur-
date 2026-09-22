package com.ironlog.app.core.ai

import com.ironlog.app.core.security.SecureTokenStore
import com.ironlog.app.data.repository.UserFacingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The optional cloud assistant: questions answered by Claude rather than by the
 * built-in coach.
 *
 * This is the one part of IronLog that leaves the phone, and it is off until
 * the user turns it on and supplies their own key. That matters twice over.
 * The app is otherwise offline by design, so the report has to be able to say
 * exactly which feature is the exception and that it is opt-in. And an API key
 * shipped inside an APK is a key anyone can extract with a zip tool, so there
 * is none: the user's key is entered in Settings and held in the same
 * encrypted store as the session tokens.
 *
 * Written against the HTTP API with [HttpURLConnection] rather than the
 * Anthropic SDK, because pulling in an SDK -- and the HTTP stack underneath it
 * -- would add exactly the kind of network dependency the project rules out.
 */
@Singleton
class CloudAssistant @Inject constructor(
    private val tokenStore: SecureTokenStore
) {

    fun hasKey(): Boolean = !tokenStore.assistantKey().isNullOrBlank()

    fun saveKey(key: String) = tokenStore.saveAssistantKey(key.trim().takeIf { it.isNotBlank() })

    /**
     * Asks Claude, with the user's own training figures supplied as context so
     * the answer is about them rather than about lifting in general.
     *
     * The question and that context are what leave the device; nothing else
     * does, and the system prompt says so.
     */
    suspend fun ask(question: String, context: String): String = withContext(Dispatchers.IO) {
        val key = tokenStore.assistantKey()?.takeIf { it.isNotBlank() }
            ?: throw UserFacingException("Add your Claude API key in Settings first")

        val body = buildJsonObject {
            put("model", MODEL)
            put("max_tokens", MAX_TOKENS)
            // Short answers on a phone screen, and a cheaper call: this is a
            // coaching reply, not an essay.
            putJsonObject("output_config") { put("effort", "low") }
            put("system", SYSTEM_PROMPT)
            // Routes around a refusal automatically rather than showing the
            // user a dead end they cannot act on.
            put("fallbacks", "default")
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", "$context\n\nQuestion: $question")
                })
            })
        }

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-api-key", key)
            setRequestProperty("anthropic-version", API_VERSION)
            setRequestProperty("anthropic-beta", FALLBACK_BETA)
        }

        try {
            connection.outputStream.use { it.write(body.toString().toByteArray()) }

            val status = connection.responseCode
            val payload = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            if (status !in 200..299) throw UserFacingException(messageFor(status, payload))
            textFrom(payload)
        } catch (e: UserFacingException) {
            throw e
        } catch (e: Exception) {
            // Anything else is the network: no signal, DNS, a captive portal.
            // The coach still works, so say that rather than just failing.
            throw UserFacingException("Could not reach Claude. The built-in coach still works offline.")
        } finally {
            connection.disconnect()
        }
    }

    /** Pulls the reply out of the response, and reports a refusal as one. */
    private fun textFrom(payload: String): String {
        val json = Json.parseToJsonElement(payload).jsonObject

        if (json["stop_reason"]?.jsonPrimitive?.contentOrNull() == "refusal") {
            throw UserFacingException("Claude declined to answer that one.")
        }

        val text = (json["content"] as? JsonArray)
            ?.mapNotNull { block ->
                val obj = block as? JsonObject ?: return@mapNotNull null
                if (obj["type"]?.jsonPrimitive?.contentOrNull() != "text") null
                else obj["text"]?.jsonPrimitive?.contentOrNull()
            }
            ?.joinToString("\n")
            .orEmpty()

        return text.ifBlank { throw UserFacingException("Claude sent an empty reply.") }
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
        runCatching { content }.getOrNull()

    /**
     * Turns a status code into something the user can act on. The raw body is
     * never shown: it can carry the key back in an error message.
     */
    private fun messageFor(status: Int, payload: String): String = when (status) {
        401, 403 -> "That API key was rejected. Check it in Settings."
        429 -> "Claude is rate limiting the key. Try again in a moment."
        in 500..599 -> "Claude is having trouble at the moment. Try again shortly."
        else -> "The assistant could not answer that (error $status)."
    }

    private companion object {
        const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val API_VERSION = "2023-06-01"

        /** Opts into server-side routing when a request is refused. */
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

        const val MODEL = "claude-opus-5"

        /** A coaching answer, not a document; this also caps what a call costs. */
        const val MAX_TOKENS = 1024

        const val TIMEOUT_MS = 30_000

        val SYSTEM_PROMPT = """
            You are the assistant inside IronLog, an offline Android strength-training app.
            Answer questions about training, recovery, programming and general fitness.

            The user's own figures are given to you below; use them and do not invent others.
            Keep answers short enough to read on a phone: a few sentences or a short list.

            You are not a doctor. If the question is about pain, injury, illness, medication or
            anything else medical, say plainly that it needs a professional and do not guess.
        """.trimIndent()
    }
}
