package com.ironlog.app.core

import com.ironlog.app.core.ai.WebLookup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebLookupTest {

    @Test
    fun `the topic is the question with the asking stripped off`() {
        assertEquals("creatine", WebLookup.topicOf("What is creatine?"))
        assertEquals("progressive overload", WebLookup.topicOf("Tell me about the progressive overload"))
        assertEquals("romanian deadlift", WebLookup.topicOf("How do I do a Romanian deadlift"))
        assertEquals("protein", WebLookup.topicOf("How much protein"))
        assertEquals("creatine monohydrate", WebLookup.topicOf("  creatine   monohydrate!! "))
    }

    @Test
    fun `a DuckDuckGo abstract becomes a fact with its source and link`() {
        val payload = """
            {"Heading":"Creatine","AbstractText":"Creatine is an organic compound found in muscle.",
             "AbstractURL":"https://en.wikipedia.org/wiki/Creatine","AbstractSource":"Wikipedia"}
        """.trimIndent()
        val fact = WebLookup.parseDuckDuckGo(payload)
        assertNotNull(fact)
        assertEquals("Creatine", fact!!.title)
        assertEquals("Creatine is an organic compound found in muscle.", fact.extract)
        assertEquals("https://en.wikipedia.org/wiki/Creatine", fact.url)
        assertEquals("Wikipedia", fact.source)
    }

    @Test
    fun `an empty DuckDuckGo answer is nothing, not a blank fact`() {
        assertNull(WebLookup.parseDuckDuckGo("""{"Heading":"","AbstractText":"","AbstractURL":""}"""))
        assertNull(WebLookup.parseDuckDuckGo("not json"))
    }

    @Test
    fun `wikipedia search yields the first title and summary yields the lead`() {
        assertEquals("Deadlift", WebLookup.parseWikipediaSearch("""["deadlift",["Deadlift","Deadlift (album)"],["",""],["u1","u2"]]"""))
        assertNull(WebLookup.parseWikipediaSearch("""["nothing",[],[],[]]"""))

        val summary = """
            {"type":"standard","title":"Deadlift","extract":"The deadlift is a weight training exercise.",
             "content_urls":{"desktop":{"page":"https://en.wikipedia.org/wiki/Deadlift"},
                             "mobile":{"page":"https://en.m.wikipedia.org/wiki/Deadlift"}}}
        """.trimIndent()
        val fact = WebLookup.parseWikipediaSummary(summary)
        assertNotNull(fact)
        assertEquals("Deadlift", fact!!.title)
        assertEquals("Wikipedia", fact.source)
        assertEquals("https://en.m.wikipedia.org/wiki/Deadlift", fact.url)
    }

    @Test
    fun `a disambiguation page is not an answer`() {
        assertNull(WebLookup.parseWikipediaSummary("""{"type":"disambiguation","title":"Press","extract":"Press may refer to:"}"""))
    }

    @Test
    fun `long extracts are cut at a sentence end`() {
        val sentence = "Creatine is a compound that helps supply energy to muscle cells. "
        val long = sentence.repeat(20)
        val fact = WebLookup.parseDuckDuckGo("""{"Heading":"Creatine","AbstractText":"$long","AbstractURL":"","AbstractSource":"Wikipedia"}""")
        assertTrue(fact!!.extract.length <= WebLookup.MAX_EXTRACT)
        assertTrue(fact.extract.endsWith("."))
    }
}
