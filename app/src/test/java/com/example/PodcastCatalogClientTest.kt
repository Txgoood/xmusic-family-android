package com.example

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PodcastCatalogClientTest {
    @Test fun searchResultsKeepOnlyDistinctUsablePodcastFeeds() {
        val raw = """{"results":[
            {"kind":"podcast","collectionName":"Bilim Programı","artistName":"Yayıncı","feedUrl":"https://example.org/rss","artworkUrl100":"https://example.org/art.jpg","collectionViewUrl":"https://podcasts.apple.com/tr/podcast/example/id1","trackCount":24},
            {"kind":"podcast","collectionName":"Tekrar","feedUrl":"https://example.org/rss"},
            {"kind":"podcast","collectionName":"RSS yok"},
            {"kind":"podcast","collectionName":"Güvensiz","feedUrl":"http://example.org/rss"},
            {"kind":"song","feedUrl":"https://example.org/other"}]}"""
        val entries = PodcastCatalogClient.parse(raw).getJSONArray("items")
        assertEquals(1, entries.length())
        val entry = entries.getJSONObject(0)
        assertEquals("Bilim Programı", entry.getString("title"))
        assertEquals("https://example.org/rss", entry.getString("feed"))
        assertEquals(24, entry.getInt("episodeCount"))
    }

    @Test fun unknownLinksCannotBeOpenedAsApplePages() {
        val raw = """{"results":[{"kind":"podcast","collectionName":"Test","feedUrl":"https://example.org/rss","collectionViewUrl":"https://fake.example.org/id","artworkUrl600":"http://example.org/img"}]}"""
        val row = PodcastCatalogClient.parse(raw).getJSONArray("items").getJSONObject(0)
        assertEquals("", row.getString("url"))
        assertEquals("", row.getString("artwork"))
    }

    @Test fun emptySearchResponseReturnsEmptyList() {
        assertEquals(0, PodcastCatalogClient.parse("""{"results":[]}""").getJSONArray("items").length())
    }
}
