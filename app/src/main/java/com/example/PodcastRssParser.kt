package com.example

import org.json.JSONObject

object PodcastRssParser {
    fun parse(xml: String, address: String): JSONObject {
                require(!xml.contains("<!DOCTYPE", true) && !xml.contains("<!ENTITY", true))
                val parser = android.util.Xml.newPullParser()
                parser.setInput(java.io.StringReader(xml))
                val rows = org.json.JSONArray()
                var item: JSONObject? = null
                var feedTitle = "Podcast"
                var event = parser.eventType
                while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT && rows.length() < 100) {
                    if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                        when (parser.name.lowercase()) {
                            "item" -> item = JSONObject()
                            "title" -> { val title = parser.nextText().take(300); if (item != null) item.put("title", title) else feedTitle = title }
                            "pubdate" -> if (item != null) item.put("published", parser.nextText().take(120))
                            "description" -> if (item != null) {
                                val raw = parser.nextText().take(10000)
                                item.put("description", android.text.Html.fromHtml(raw, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim().take(500))
                            }
                            "enclosure" -> if (item != null) {
                                val url = parser.getAttributeValue(null, "url").orEmpty()
                                val type = parser.getAttributeValue(null, "type").orEmpty()
                                if (url.startsWith("https://") && (type.startsWith("audio/") || url.substringBefore('?').matches(Regex(".*\\.(mp3|m4a|ogg|aac|opus)$", RegexOption.IGNORE_CASE)))) item.put("url", url)
                            }
                        }
                    } else if (event == org.xmlpull.v1.XmlPullParser.END_TAG && parser.name.equals("item", true)) {
                        val row = item
                        if (row != null && row.has("url")) {
                            val url = row.getString("url")
                            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
                            val id = "podcast:$digest"
                            row.put("id", id).put("artist", feedTitle).put("feed", address)
                            rows.put(row)
                        }
                        item = null
                    }
                    event = parser.next()
                }
                return JSONObject().put("title", feedTitle).put("items", rows)
    }
}
