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
    private val audiusTracks = java.util.concurrent.ConcurrentHashMap<String, MediaItem>()
    private val musicBrainzRequestLock = Any()
    private var lastMusicBrainzRequest = 0L

    @JavascriptInterface
    fun chooseDriveUpload() { webView.post { (context as? MainActivity)?.chooseDriveUpload() } }

    @JavascriptInterface
    fun connectDrive() { webView.post { (context as? MainActivity)?.authorizeDrive() } }

    @JavascriptInterface
    fun showToast(message: String) {
        webView.post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
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

    // Deneysel arama sonuçları her zaman kullanıcıya kaynak belirtilerek gösterilir.
    @JavascriptInterface
    fun searchExperimentalSource(query: String, requestId: Int) {
        searchExperimentalSourceMore(query, requestId, 10)
    }

    @JavascriptInterface
    fun searchExperimentalSourceMore(query: String, requestId: Int, limit: Int) {
        if (query.isBlank() || query.length > 120 || limit !in setOf(10, 30)) return
        Thread {
            val result = try {
                val engine = com.yausername.youtubedl_android.YoutubeDL.getInstance()
                engine.init(context.applicationContext)
                val request = com.yausername.youtubedl_android.YoutubeDLRequest("ytsearch$limit:" + query.trim())
                request.addOption("--flat-playlist")
                request.addOption("--dump-single-json")
                request.addOption("--no-warnings")
                val response = engine.execute(request, "xmusic-search-$requestId") { _, _, _ -> }
                val entries = org.json.JSONObject(response.out).optJSONArray("entries") ?: org.json.JSONArray()
                val items = org.json.JSONArray()
                for (i in 0 until minOf(entries.length(), limit)) {
                    val entry = entries.optJSONObject(i) ?: continue
                    val id = entry.optString("id")
                    if (!id.matches(Regex("[A-Za-z0-9_-]{11}"))) continue
                    items.put(org.json.JSONObject().put("id", id).put("title", entry.optString("title")).put("artist", entry.optString("channel", entry.optString("uploader"))))
                }
                org.json.JSONObject().put("items", items).put("limit", limit)
            } catch (e: Exception) {
                org.json.JSONObject().put("error", "Kaynak araması başarısız oldu. İnternet bağlantısını kontrol edip tekrar dene.")
            }
            webView.post { webView.evaluateJavascript("window.__xmusicExperimentalResult && window.__xmusicExperimentalResult($requestId,${result})", null) }
        }.start()
    }

    @JavascriptInterface
    fun playExperimentalSource(id: String, title: String, artist: String) {
        if (!id.matches(Regex("[A-Za-z0-9_-]{11}"))) return
        Thread {
            try {
                val stream = com.example.data.source.ExperimentalAudioResolver(context).resolve("https://www.youtube.com/watch?v=$id")
                webView.post {
                    val controller = mediaController
                    if (controller == null) {
                        Toast.makeText(context, "Oynatıcı hazır değil", Toast.LENGTH_SHORT).show()
                    } else {
                        val metadata = androidx.media3.common.MediaMetadata.Builder().setTitle(title.take(180)).setArtist(artist.take(180))
                            .setArtworkUri(android.net.Uri.parse("https://i.ytimg.com/vi/$id/mqdefault.jpg")).build()
                        val item = MediaItem.Builder().setMediaId("experimental:$id").setUri(stream.url).setMediaMetadata(metadata).build()
                        com.example.playback.PlaybackService.activeService?.registerExperimentalStream(stream.url, stream.headers)
                        controller.setMediaItem(item)
                        controller.prepare()
                        controller.play()
                    }
                }
            } catch (e: Exception) {
                webView.post {
                    android.app.AlertDialog.Builder(context)
                        .setTitle("Ses kaynağı açılamadı")
                        .setMessage(e.message ?: "Bilinmeyen kaynak hatası")
                        .setPositiveButton("Tamam", null)
                        .show()
                }
            }
        }.start()
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
                        val id = track.optString("id")
                        val playable = track.optBoolean("is_streamable", false) &&
                            !track.optBoolean("is_stream_gated", false) &&
                            !track.optBoolean("is_delete", false) &&
                            track.optBoolean("is_available", true)
                        if (playable && id.matches(Regex("[a-zA-Z0-9]{1,64}"))) {
                            val metadata = androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(track.optString("title").take(180))
                                .setArtist(user?.optString("name")?.take(180))
                            val image = artwork?.optString("150x150").orEmpty()
                            if (image.startsWith("https://")) metadata.setArtworkUri(android.net.Uri.parse(image))
                            audiusTracks[id] = MediaItem.Builder().setMediaId("audius:$id")
                                .setUri("https://discoveryprovider.audius.co/v1/tracks/$id/stream?app_name=XMusicFamily")
                                .setMediaMetadata(metadata.build()).build()
                        } else { audiusTracks.remove(id) }
                        items.put(org.json.JSONObject().apply {
                            put("playable", playable)
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
            } else if (controller.mediaItemCount == 0) {
                Toast.makeText(context, "Önce dinlenecek bir parça seç", Toast.LENGTH_SHORT).show()
            } else if (controller.playWhenReady && controller.playbackState != androidx.media3.common.Player.STATE_ENDED && controller.playerError == null) {
                controller.pause()
            } else {
                if (controller.playbackState == androidx.media3.common.Player.STATE_ENDED) controller.seekTo(0)
                if (controller.playerError != null || controller.playbackState == androidx.media3.common.Player.STATE_IDLE) controller.prepare()
                controller.play()
            }
        }
    }

    @JavascriptInterface
    fun playAudiusTrack(trackId: String, title: String, artist: String) {
        if (!trackId.matches(Regex("[a-zA-Z0-9]{1,64}"))) {
            webView.post { Toast.makeText(context, "Geçersiz Audius parça kimliği", Toast.LENGTH_SHORT).show() }
            return
        }
        webView.post {
            val controller = mediaController
            if (controller == null) {
                Toast.makeText(context, "Müzik oynatıcı hazır değil", Toast.LENGTH_SHORT).show()
                return@post
            }
            // Yalnızca API'nin erişilebilir bildirdiği arama kaydını kullan; başlığa göre eşleştirme yapma.
            val item = audiusTracks[trackId]
            if (item == null) {
                Toast.makeText(context, "Bu kayıt için oynatma erişimi doğrulanamadı. Yeniden ara.", Toast.LENGTH_SHORT).show()
                return@post
            }
            controller.setMediaItem(item)
            controller.prepare()
            controller.play()
            Toast.makeText(context, "Audius: " + title.take(50), Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun resolveAudiusTrack(trackId: String, enqueue: Boolean) {
        if (!trackId.matches(Regex("[a-zA-Z0-9]{1,64}"))) return
        Thread {
            try {
                val connection = java.net.URL("https://discoveryprovider.audius.co/v1/tracks/$trackId?app_name=XMusicFamily")
                    .openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 12000
                val track = try {
                    if (connection.responseCode !in 200..299) throw java.io.IOException("Kaynak yanıt vermedi")
                    val data = org.json.JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optJSONArray("data")
                    data?.optJSONObject(0) ?: throw java.io.IOException("Kayıt bulunamadı")
                } finally { connection.disconnect() }
                if (track.optString("id") != trackId || !track.optBoolean("is_streamable", false) ||
                    track.optBoolean("is_stream_gated", false) || track.optBoolean("is_delete", false) ||
                    !track.optBoolean("is_available", true)) throw java.io.IOException("Oynatma erişimi yok")
                val metadata = androidx.media3.common.MediaMetadata.Builder().setTitle(track.optString("title"))
                    .setArtist(track.optJSONObject("user")?.optString("name"))
                val artwork = track.optJSONObject("artwork")?.optString("150x150").orEmpty()
                if (artwork.startsWith("https://")) metadata.setArtworkUri(android.net.Uri.parse(artwork))
                val item = MediaItem.Builder().setMediaId("audius:$trackId")
                    .setUri("https://discoveryprovider.audius.co/v1/tracks/$trackId/stream?app_name=XMusicFamily")
                    .setMediaMetadata(metadata.build()).build()
                webView.post {
                    val p = mediaController ?: return@post
                    if (enqueue) p.addMediaItem(item) else { p.setMediaItem(item); p.prepare(); p.play() }
                }
            } catch (_: Exception) { showToast("Kayıt açılamadı. İnternet veya kaynak erişimi uygun değil.") }
        }.start()
    }

    @JavascriptInterface
    fun enqueueAudiusTrack(trackId: String) {
        webView.post {
            val item = audiusTracks[trackId]
            val p = mediaController
            if (item != null && p != null) {
                p.addMediaItem(item)
                Toast.makeText(context, "Sıraya eklendi", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(context, "Erişimi doğrulamak için yeniden ara", Toast.LENGTH_SHORT).show()
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
        webView.post { mediaController?.let { if (it.mediaItemCount > 0) it.play() } }
    }

    @JavascriptInterface
    fun pause() {
        webView.post { mediaController?.pause() }
    }

    @JavascriptInterface
    fun next() {
        webView.post { mediaController?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() } }
    }

    @JavascriptInterface
    fun previous() {
        webView.post { mediaController?.let { if (it.mediaItemCount > 0) it.seekToPrevious() } }
    }

    @JavascriptInterface
    fun seekTo(positionMs: Long) {
        webView.post {
            mediaController?.let {
                if (it.isCurrentMediaItemSeekable && it.duration > 0)
                    it.seekTo(positionMs.coerceIn(0L, it.duration))
            }
        }
    }

    fun setMediaController(controller: MediaController) {
        this.mediaController = controller
    }
}

