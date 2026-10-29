package com.example.data.source.podcast

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.URL

data class PodcastEpisode(val title: String, val enclosureUrl: String)

class RssParser {
    fun parse(url: String): List<PodcastEpisode> {
        val episodes = mutableListOf<PodcastEpisode>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(URL(url).openStream(), null)

        var eventType = parser.eventType
        var currentTitle = ""
        var currentUrl = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "title") currentTitle = parser.nextText()
                    if (name == "enclosure") currentUrl = parser.getAttributeValue(null, "url")
                }
                XmlPullParser.END_TAG -> {
                    if (name == "item") {
                        episodes.add(PodcastEpisode(currentTitle, currentUrl))
                    }
                }
            }
            eventType = parser.next()
        }
        return episodes
    }
}
