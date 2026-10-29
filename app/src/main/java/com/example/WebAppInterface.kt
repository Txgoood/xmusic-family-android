package com.example

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.util.Locale
import android.widget.Toast
import androidx.media3.session.MediaController
import androidx.media3.common.MediaItem

/**
 * JavaScript ve Native Android kodu arasındaki köprü.
 */
class WebAppInterface(private val context: Context, private val webView: WebView, private var mediaController: MediaController?) {
    private val musicBrainzRequestLock = Any()
    private var lastMusicBrainzRequest = 0L

    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun navigateTo(pageFolder: String) {
        // Yalnızca önceden tanımlanmış Stitch ekranlarına geçişe izin ver.
        val allowedPages = setOf(
            "ana_sayfa", "podcast_merkezi", "imdi_al_yor", "i_ndirme_y_neticisi",
            "m_zik_merkezi", "kitapl_m", "arama_ve_ke_fet", "aile_profili_se_imi",
            "sanat_detay", "alb_m_detay", "alma_listesi_y_netimi",
            "ocuk_modu_kids_safe", "ayarlar_ve_profil_y_netimi",
            "podcast_program_ve_b_l_m_detay", "oynatma_s_ras_queue",
            "uyku_zamanlay_c_s", "hata_ve_durum_ekranlar",
            "i_lk_kurulum_ve_profil_olu_turma"
        )
        if (pageFolder !in allowedPages) return
        val url = "file:///android_asset/stitch/$pageFolder/code.html"
        webView.post { webView.loadUrl(url) }
    }

    // Ayarlar yalnızca cihazda saklanır; bulut senkronizasyonuna karışmaz.
    @JavascriptInterface
    fun saveSetting(key: String, enabled: Boolean) {
        if (!key.matches(Regex("setting_[0-9]{1,2}"))) return
        context.getSharedPreferences("xmusic_settings", Context.MODE_PRIVATE)
            .edit().putBoolean(key, enabled).apply()
    }

    @JavascriptInterface
    fun loadSetting(key: String, defaultValue: Boolean): Boolean {
        if (!key.matches(Regex("setting_[0-9]{1,2}"))) return defaultValue
        return context.getSharedPreferences("xmusic_settings", Context.MODE_PRIVATE)
            .getBoolean(key, defaultValue)
    }

    @JavascriptInterface
    fun searchMusic(query: String, requestId: Int) {
        if (query.isBlank() || query.length > 120) return
        Thread {
            val result = try {
                val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
                val endpoint = java.net.URL("https://discoveryprovider.audius.co/v1/tracks/search?query=$encoded&app_name=XMusicFamily")
                val connection = (endpoint.openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 12000
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val code = connection.responseCode
                    if (code !in 200..299) throw java.io.IOException("HTTP $code")
                    val payload = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = org.json.JSONObject(payload)
                    val tracks = json.optJSONArray("data") ?: org.json.JSONArray()
                    val items = org.json.JSONArray()
                    for (i in 0 until minOf(tracks.length(), 30)) {
                        val track = tracks.optJSONObject(i) ?: continue
                        val user = track.optJSONObject("user")
                        val artwork = track.optJSONObject("artwork")
                        items.put(org.json.JSONObject().apply {
                            put("title", track.optString("title"))
                            put("artist", user?.optString("name") ?: "")
                            put("id", track.optString("id"))
                            put("artwork", artwork?.optString("150x150") ?: "")
                        })
                    }
                    org.json.JSONObject().put("items", items)
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                org.json.JSONObject().put("error", "Arama bağlantısı kurulamadı. İnterneti kontrol et.")
            }
            webView.post {
                val script = "window.__xmusicSearchResult && window.__xmusicSearchResult(" + requestId + "," + result.toString() + ")"
                webView.evaluateJavascript(script, null)
            }
        }.start()
    }

    @JavascriptInterface
    fun searchAudiusCatalog(query: String, category: String, requestId: Int) {
        if (query.isBlank() || query.length > 120 || category !in setOf("users", "playlists")) return
        Thread {
            val result = try {
                val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
                val endpoint = java.net.URL("https://discoveryprovider.audius.co/v1/$category/search?query=$encoded&app_name=XMusicFamily")
                val conn = (endpoint.openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 12000
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    if (conn.responseCode !in 200..299) throw java.io.IOException("API request failed")
                    val data = org.json.JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                        .optJSONArray("data") ?: org.json.JSONArray()
                    val rows = org.json.JSONArray()
                    for (i in 0 until minOf(data.length(), 25)) {
                        val entry = data.optJSONObject(i) ?: continue
                        val creator = entry.optJSONObject("user")
                        val art = if (category == "users") entry.optJSONObject("profile_picture") else entry.optJSONObject("artwork")
                        rows.put(org.json.JSONObject().apply {
                            put("id", entry.optString("id"))
                            put("title", if (category == "users") entry.optString("name") else entry.optString("playlist_name"))
                            put("artist", if (category == "users") "Sanatçı" else (creator?.optString("name") ?: ""))
                            put("artwork", art?.optString("150x150") ?: "")
                        })
                    }
                    org.json.JSONObject().put("items", rows)
                } finally { conn.disconnect() }
            } catch (e: Exception) {
                org.json.JSONObject().put("error", "Katalog yüklenemedi. İnternet bağlantısını kontrol et.")
            }
            webView.post {
                webView.evaluateJavascript(
                    "window.__xmusicCatalogResult && window.__xmusicCatalogResult(" +
                        requestId + "," + org.json.JSONObject.quote(category) + "," + result.toString() + ")", null
                )
            }
        }.start()
    }

    // MusicBrainz metadata is separate from Audius community audio uploads.
    @JavascriptInterface
    fun searchVerifiedCatalog(query: String, artistId: String, requestId: Int) {
        if (query.length > 100 || (artistId.isNotEmpty() && !artistId.matches(Regex("[0-9a-fA-F-]{36}")))) return
        Thread {
            val result = try {
                // Limit MusicBrainz requests across both search and discography calls.
                synchronized(musicBrainzRequestLock) {
                    val waitMs = 1100 - (System.currentTimeMillis() - lastMusicBrainzRequest)
                    if (waitMs > 0) Thread.sleep(waitMs)
                    lastMusicBrainzRequest = System.currentTimeMillis()
                }
                val url = if (artistId.isEmpty()) {
                    "https://musicbrainz.org/ws/2/artist/?query=" +
                        java.net.URLEncoder.encode("artist:\"" + query.trim() + "\"", "UTF-8") + "&fmt=json&limit=15"
                } else {
                    "https://musicbrainz.org/ws/2/release-group?artist=" + artistId +
                        "&type=album&fmt=json&limit=50"
                }
                val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 12000
                    setRequestProperty("User-Agent", "XMusicFamily/0.1 (https://github.com/Txgoood/xmusic-family-android)")
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    if (conn.responseCode !in 200..299) throw java.io.IOException("MusicBrainz HTTP " + conn.responseCode)
                    val raw = org.json.JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val array = raw.optJSONArray(if (artistId.isEmpty()) "artists" else "release-groups") ?: org.json.JSONArray()
                    val items = org.json.JSONArray()
                    for (i in 0 until array.length()) {
                        val record = array.optJSONObject(i) ?: continue
                        items.put(org.json.JSONObject().apply {
                            put("id", record.optString("id"))
                            put("title", if (artistId.isEmpty()) record.optString("name") else record.optString("title"))
                            put("subtitle", if (artistId.isEmpty()) {
                                listOf(record.optString("country"), record.optString("disambiguation"))
                                    .filter { it.isNotBlank() }.joinToString(" · ")
                            } else record.optString("first-release-date"))
                        })
                    }
                    org.json.JSONObject().put("items", items)
                } finally { conn.disconnect() }
            } catch (e: Exception) {
                android.util.Log.w("XMusicCatalog", "MusicBrainz lookup failed", e)
                org.json.JSONObject().put("error", "MusicBrainz yanıt vermedi. Bağlantıyı kontrol edip tekrar dene.")
            }
            webView.post {
                webView.evaluateJavascript(
                    "window.__xmusicVerifiedCatalog && window.__xmusicVerifiedCatalog(" +
                        requestId + "," + result.toString() + ")", null
                )
            }
        }.start()
    }

    // Apple iTunes Store Türkiye: sanatçı / albüm / şarkı meta verileri.
    @JavascriptInterface
    fun searchAppleCatalog(query: String, itemId: String, kind: String, requestId: Int) {
        if (query.length > 100 || kind !in setOf("artists", "albums", "songs") ||
            (itemId.isNotEmpty() && !itemId.matches(Regex("[0-9]{1,16}")))) return
        Thread {
            val result = try {
                val url = if (itemId.isNotEmpty()) {
                    "https://itunes.apple.com/lookup?id=" + itemId +
                        "&country=TR&entity=" + (if (kind == "albums") "album" else "song") + "&limit=100"
                } else {
                    "https://itunes.apple.com/search?term=" +
                        java.net.URLEncoder.encode(query.trim(), "UTF-8") +
                        "&country=TR&media=music&entity=musicArtist&limit=30"
                }
                val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 9000
                    readTimeout = 11000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "XMusicFamily/0.2")
                }
                try {
                    if (conn.responseCode !in 200..299) throw java.io.IOException("Apple HTTP " + conn.responseCode)
                    val data = org.json.JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                        .optJSONArray("results") ?: org.json.JSONArray()
                    val rows = org.json.JSONArray()
                    val seen = HashSet<String>()
                    for (i in 0 until data.length()) {
                        val entry = data.optJSONObject(i) ?: continue
                        val type = entry.optString("wrapperType")
                        if (itemId.isEmpty() && type != "artist") continue
                        if (itemId.isNotEmpty() && (type == "artist" || (kind == "albums" && type != "collection") ||
                                (kind == "songs" && type != "track"))) continue
                        if (kind == "albums" && entry.optString("collectionType") != "Album") continue
                        val id = if (type == "artist") entry.optString("artistId") else if (type == "collection")
                            entry.optString("collectionId") else entry.optString("trackId")
                        if (id.isBlank() || !seen.add(id)) continue
                        rows.put(org.json.JSONObject().apply {
                            put("id", id)
                            put("title", if (type == "artist") entry.optString("artistName")
                                else if (type == "collection") entry.optString("collectionName") else entry.optString("trackName"))
                            put("subtitle", if (type == "artist") entry.optString("primaryGenreName")
                                else entry.optString("artistName"))
                            put("artwork", entry.optString("artworkUrl100"))
                            put("releaseDate", entry.optString("releaseDate"))
                            put("trackCount", entry.optInt("trackCount", 0))
                            put("category", if (type == "collection") {
                                val name = entry.optString("collectionName")
                                val count = entry.optInt("trackCount", 0)
                                when {
                                    name.contains(" - Single", ignoreCase = true) || count in 1..3 -> "Single"
                                    name.contains(" - EP", ignoreCase = true) || count in 4..6 -> "EP"
                                    else -> "Albüm"
                                }
                            } else "")
                            put("type", type)
                            put("url", entry.optString("artistViewUrl", entry.optString("collectionViewUrl", entry.optString("trackViewUrl"))))
                        })
                    }
                    org.json.JSONObject().put("items", rows)
                } finally { conn.disconnect() }
            } catch (e: Exception) {
                android.util.Log.w("XMusicCatalog", "Apple catalog failed", e)
                org.json.JSONObject().put("error", "Apple kataloğuna ulaşılamadı. İnternet bağlantısını kontrol et.")
            }
            webView.post {
                webView.evaluateJavascript(
                    "window.__xmusicAppleResult && window.__xmusicAppleResult(" +
                        requestId + "," + result.toString() + ")", null)
            }
        }.start()
    }

    @JavascriptInterface
    fun goBack() {
        webView.post {
            if (webView.canGoBack()) webView.goBack()
            else webView.loadUrl("file:///android_asset/stitch/ana_sayfa/code.html")
        }
    }

    @JavascriptInterface
    fun togglePlayPause() {
        webView.post {
            val controller = mediaController
            if (controller == null) {
                Toast.makeText(context, "Oynatıcı henüz hazır değil", Toast.LENGTH_SHORT).show()
            } else if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
        }
    }

    @JavascriptInterface
    fun playStream(url: String) {
        val uri = android.net.Uri.parse(url)
        if (uri.scheme != "https" && uri.scheme != "http") {
            webView.post { Toast.makeText(context, "Geçerli medya adresi bulunamadı", Toast.LENGTH_SHORT).show() }
            return
        }
        webView.post {
            val controller = mediaController
            if (controller == null) {
                Toast.makeText(context, "Oynatıcı hazır değil", Toast.LENGTH_SHORT).show()
            } else {
                controller.setMediaItem(MediaItem.fromUri(uri))
                controller.prepare()
                controller.play()
            }
        }
    }

    @JavascriptInterface
    fun resumeStream(url: String) {
        val uri = android.net.Uri.parse(url)
        if (uri.scheme != "https" && uri.scheme != "http") {
            webView.post { Toast.makeText(context, "Devam edilecek gerçek kayıt bulunamadı", Toast.LENGTH_SHORT).show() }
            return
        }
        webView.post {
            val controller = mediaController ?: return@post
            val preferences = context.getSharedPreferences("xmusic_playback", Context.MODE_PRIVATE)
            val position = preferences.getLong("position_" + url.hashCode(), 0L)
            controller.setMediaItem(MediaItem.fromUri(uri))
            controller.prepare()
            controller.seekTo(position.coerceAtLeast(0))
            controller.play()
        }
    }

    @JavascriptInterface
    fun hasResumeData(url: String): Boolean {
        return context.getSharedPreferences("xmusic_playback", Context.MODE_PRIVATE)
            .contains("position_" + url.hashCode())
    }

    @JavascriptInterface
    fun showUnavailable(feature: String) {
        webView.post {
            Toast.makeText(context, "$feature için gerçek içerik bağlantısı henüz yok", Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun play() {
        mediaController?.play()
    }

    @JavascriptInterface
    fun pause() {
        mediaController?.pause()
    }

    @JavascriptInterface
    fun next() {
        mediaController?.seekToNext()
    }

    @JavascriptInterface
    fun previous() {
        mediaController?.seekToPrevious()
    }

    @JavascriptInterface
    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
    }

    fun setMediaController(controller: MediaController) {
        this.mediaController = controller
    }
}
