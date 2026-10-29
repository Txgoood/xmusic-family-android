package com.example

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PodcastRssParserTest {
    @Test fun readsPublisherEnclosuresAndSkipsMissingOrInsecureAudio() {
        val xml = """<rss><channel><title>Bilim &amp; Aile</title>
            <item><title>Birinci</title><enclosure url="https://example.org/one.mp3" type="audio/mpeg"/></item>
            <item><title>Bağlantı yok</title></item>
            <item><title>Güvensiz</title><enclosure url="http://example.org/two.mp3" type="audio/mpeg"/></item>
            <item><title>Video</title><enclosure url="https://example.org/video.mp4" type="video/mp4"/></item>
            </channel></rss>"""
        val parsed = PodcastRssParser.parse(xml, "https://example.org/rss")
        assertEquals("Bilim & Aile", parsed.getString("title"))
        assertEquals(1, parsed.getJSONArray("items").length())
        val item = parsed.getJSONArray("items").getJSONObject(0)
        assertTrue(item.getString("id").startsWith("podcast:"))
        assertEquals("https://example.org/one.mp3", item.getString("url"))
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsExternalEntities() {
        PodcastRssParser.parse("<!DOCTYPE rss [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]><rss/>", "https://example.org/rss")
    }
    @Test fun capsEpisodeCount() {
        val entries = (1..120).joinToString("") { "<item><title>$it</title><enclosure url=\"https://example.org/$it.mp3\" type=\"audio/mpeg\"/></item>" }
        assertEquals(100, PodcastRssParser.parse("<rss><channel><title>Test</title>$entries</channel></rss>", "https://example.org/rss").getJSONArray("items").length())
    }
}
