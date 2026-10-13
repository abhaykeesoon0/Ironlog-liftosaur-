package com.ironlog.app.core.ai

import com.ironlog.app.data.repository.UserFacingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** One thing found online: what it says, and where it was read so the user can check. */
data class WebFact(
    val title: String,
    val extract: String,
    val url: String,
    val source: String
)

/**
 * Keyless lookups on the open web, so the coach can answer with a fact it
 * was not written with.
 *
 * Two services, neither needing an account or a key, so nothing has to be
 * shipped inside the APK: DuckDuckGo's instant-answer endpoint, which returns
 * an encyclopaedic abstract for a topic, and Wikipedia's own search and
 * summary endpoints as the fallback when DuckDuckGo has nothing. Both are
 * openly licensed (CC BY-SA for Wikipedia text) and are credited under the
 * answer with a link, because a fact the user cannot check is a rumour.
 *
 * This is on by default, so a fresh install can answer a question the coach
 * was not written with, and it is the one network feature that is: the other
 * lookups are opt-in. Only the topic of the question leaves the phone -- never
 * the user's log -- and the switch is in the coach itself, where it is used,
 * so turning it off is one tap.
 */
@Singleton
class WebLookup @Inject constructor() {

    /** The best fact for a question, or null when the web has nothing usable. */
    suspend fun lookup(question: String): WebFact? = withContext(Dispatchers.IO) {
        val topic = topicOf(question)
        if (topic.length < 3) return@withContext null

        parseDuckDuckGo(fetch("$DDG?q=${encode(topic)}&format=json&no_html=1&skip_disambig=1"))
            ?: wikipedia(topic)
    }

    private fun wikipedia(topic: String): WebFact? {
        val title = parseWikipediaSearch(
            fetch("$WIKI_SEARCH?action=opensearch&search=${encode(topic)}&limit=1&namespace=0&format=json")
        ) ?: return null
        return parseWikipediaSummary(fetch("$WIKI_SUMMARY/${encode(title.replace(' ', '_'))}"))
    }

    private fun fetch(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("accept", "application/json")
            setRequestProperty("user-agent", USER_AGENT)
        }
        try {
            val status = connection.responseCode
            if (status == 404) return "{}"
            if (status !in 200..299) throw UserFacingException("The web lookup returned an error ($status).")
            return connection.inputStream.bufferedReader().use(BufferedReader::readText)
        } catch (e: UserFacingException) {
            throw e
        } catch (e: Exception) {
            throw UserFacingException("Could not reach the web for a lookup; answered from the phone instead.")
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        private const val DDG = "https://api.duckduckgo.com/"
        private const val WIKI_SEARCH = "https://en.wikipedia.org/w/api.php"
        private const val WIKI_SUMMARY = "https://en.wikipedia.org/api/rest_v1/page/summary"
        private const val TIMEOUT_MS = 8_000
        private const val USER_AGENT = "Liftosaur/1.0 (coursework project; offline strength tracker)"

        /** Longest extract shown; an encyclopaedia lead can run to a screen and a half. */
        const val MAX_EXTRACT = 600

        private val FILLER = listOf(
            "what is the", "what are the", "what is a", "what is an", "what is", "what are", "what's",
            "who is", "who was", "tell me about", "explain", "how does", "how do", "how to", "how much",
            "how many", "should i", "can i", "do i need", "is it", "define", "i do", "you do", "i", "you",
            "do", "perform", "the", "a", "an", "please"
        )

        /**
         * The topic a question is about, with the asking stripped off:
         * "What is creatine?" becomes "creatine". Pure, so phrasings are
         * testable without a network.
         */
        fun topicOf(question: String): String {
            var q = question.trim().lowercase().replace(Regex("[?!.]+$"), "").trim()
            var changed = true
            while (changed) {
                changed = false
                for (filler in FILLER) {
                    if (q.startsWith("$filler ")) {
                        q = q.removePrefix("$filler ").trim()
                        changed = true
                    }
                }
            }
            return q.replace(Regex("\\s+"), " ")
        }

        /** The abstract out of a DuckDuckGo instant answer, or null when there is none. */
        fun parseDuckDuckGo(payload: String): WebFact? {
            val root = runCatching { Json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
            val text = root["AbstractText"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (text.isBlank()) return null
            val heading = root["Heading"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank { "Result" }
            val url = root["AbstractURL"]?.jsonPrimitive?.content?.trim().orEmpty()
            val source = root["AbstractSource"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank { "DuckDuckGo" }
            return WebFact(heading, clip(text), url, source)
        }

        /** The first title out of a Wikipedia opensearch reply: `["query",["Title"],[...],[...]]`. */
        fun parseWikipediaSearch(payload: String): String? {
            val root = runCatching { Json.parseToJsonElement(payload).jsonArray }.getOrNull() ?: return null
            val titles = root.getOrNull(1)?.jsonArray ?: return null
            return titles.firstOrNull()?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotBlank() }
        }

        /** The lead paragraph out of a Wikipedia page summary. */
        fun parseWikipediaSummary(payload: String): WebFact? {
            val root = runCatching { Json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
            if (root["type"]?.jsonPrimitive?.content == "disambiguation") return null
            val extract = root["extract"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (extract.isBlank()) return null
            val title = root["title"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank { "Wikipedia" }
            val url = root["content_urls"]?.jsonObject?.get("mobile")?.jsonObject?.get("page")
                ?.jsonPrimitive?.content?.trim().orEmpty()
            return WebFact(title, clip(extract), url, "Wikipedia")
        }

        private fun clip(text: String): String {
            if (text.length <= MAX_EXTRACT) return text
            // Cut at the last sentence end that fits, rather than mid-word.
            val cut = text.substring(0, MAX_EXTRACT)
            val end = cut.lastIndexOfAny(charArrayOf('.', '!', '?'))
            return if (end > MAX_EXTRACT / 2) cut.substring(0, end + 1) else "$cut…"
        }
    }
}
