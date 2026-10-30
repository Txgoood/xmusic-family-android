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
    @Test fun readsEpisodeDateAndPlainTextDescription() {
        val xml = """<rss><channel><title>Podcast</title><item><title>Bölüm</title>
            <pubDate>Fri, 30 Oct 2026 10:00:00 GMT</pubDate>
            <description><![CDATA[<p>Bilim ve <b>sanat</b></p>]]></description>
            <enclosure url="https://example.org/episode.mp3" type="audio/mpeg"/>
            </item></channel></rss>"""
        val row = PodcastRssParser.parse(xml, "https://example.org/rss").getJSONArray("items").getJSONObject(0)
        assertTrue(row.getString("published").contains("2026"))
        assertEquals("Bilim ve sanat", row.getString("description"))
    }
    @Test fun readsProgramDescriptionAndEpisodeDurationWithoutOverwritingTitleFromImage() {
        val xml = """<rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"><channel>
            <title>Gerçek Program</title><description><![CDATA[<p>Program <b>açıklaması</b></p>]]></description>
            <itunes:author>Sunucu</itunes:author><image><title>Kapak başlığı</title></image>
            <item><title>Bir bölüm</title><itunes:duration>30:00</itunes:duration>
            <enclosure url="https://example.org/one.mp3" type="audio/mpeg"/></item></channel></rss>"""
        val result = PodcastRssParser.parse(xml, "https://example.org/rss")
        assertEquals("Gerçek Program", result.getString("title"))
        assertEquals("Program açıklaması", result.getString("description"))
        assertEquals("Sunucu", result.getString("author"))
        assertEquals("30:00", result.getJSONArray("items").getJSONObject(0).getString("duration"))
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
