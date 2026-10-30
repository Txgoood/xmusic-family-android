package com.example

import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import org.json.JSONObject

/** Kalıcı yerel kitaplık ve gerçek oynatıcı işlemleri. */
class AppActionsInterface(private val activity: MainActivity, private val webView: WebView) {
    var controller: MediaController? = null
    private val profiles = ProfileStore(activity)
    private var libraryProfileId = profiles.activeId()
    private val prefs get() = activity.getSharedPreferences("xmusic_library_" + profiles.activeId(), Context.MODE_PRIVATE)
    private val avatarPrefs get() = activity.getSharedPreferences("xmusic_profile_avatars", Context.MODE_PRIVATE)
    @JavascriptInterface fun activeAvatar(): String = FamilySyncStore(activity).syncedAvatar(profiles.activeId(), avatarPrefs.getString(profiles.activeId(), "") ?: "")
    @JavascriptInterface fun setActiveAvatar(value: String): Boolean {
        if (value !in setOf("🎵", "🎧", "🎸", "🌸", "🌟", "🐱", "🦊", "🐼")) return false
        return avatarPrefs.edit().putString(profiles.activeId(), value).putLong("time_" + profiles.activeId(), System.currentTimeMillis()).commit()
    }
    @JavascriptInterface fun pickActiveAvatar() { webView.post { activity.pickProfilePhoto() } }
    @JavascriptInterface fun openEqualizer() { webView.post {
        val session = com.example.playback.PlaybackService.activeService?.audioSessionId() ?: 0
        if (session == 0) { android.widget.Toast.makeText(activity, "Önce bir parça çal", 0).show(); return@post }
        try {
            val intent = Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                .putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, session)
                .putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, activity.packageName)
                .putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
            activity.startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(activity, "Bu cihazda ekolayzır ekranı bulunamadı", 0).show()
        } catch (_: SecurityException) {
            android.widget.Toast.makeText(activity, "Ekolayzır açılamadı", 0).show()
        }
    } }

    @JavascriptInterface fun setParentPin(oldPin: String, newPin: String): Boolean = profiles.setParentPin(oldPin, newPin)
    @JavascriptInterface fun saveFamilyProfile(id: String, name: String, role: String, oldPin: String, newPin: String, parentPin: String, minutes: Int, mix: Boolean): Boolean =
        profiles.saveProfile(id, name, role, oldPin, newPin, parentPin, minutes, mix)
    @JavascriptInterface fun childTracks(id: String): String =
        if (profiles.isAdmin() || id == profiles.activeId()) profiles.approved(id).toString() else "[]"
    @JavascriptInterface fun approveChildTrack(id: String, track: String, remove: Boolean, parentPin: String): Boolean =
        profiles.approve(id, track, remove, parentPin)
    @JavascriptInterface fun childReview(id: String): String = profiles.childReview(id)
    @JavascriptInterface fun requestChildReview(id: String, track: String): Boolean = profiles.requestReview(id, track)
    @JavascriptInterface fun setChildAge(id: String, age: String, pin: String): Boolean = profiles.setChildAge(id, age, pin)
    @JavascriptInterface fun resolveChildReview(id: String, key: String, deny: Boolean, pin: String): Boolean = profiles.resolveReview(id, key, deny, pin)
    @JavascriptInterface fun familySyncState(): String = FamilySyncStore(activity).state()
    @JavascriptInterface fun familySetup(action: String, code: String) { webView.post { activity.setupFamily(action, code) } }
    @JavascriptInterface fun copyFamilyInvitation() { webView.post { activity.copyFamilyInvitation() } }
    @JavascriptInterface fun setFamilySharing(enabled: Boolean): Boolean {
        if (profiles.isChild()) return false
        FamilySyncStore(activity).setSharing(profiles.activeId(), enabled)
        webView.post { activity.syncFamilyIfReady(true) }; return true
    }
    @JavascriptInterface fun familySharing(): Boolean {
        val data = JSONObject(profiles.publicState()).getJSONArray("profiles")
        val row = (0 until data.length()).map { data.getJSONObject(it) }.firstOrNull { it.getString("id") == profiles.activeId() }
        return !profiles.isChild() && FamilySyncStore(activity).isSharing(profiles.activeId(), row?.optBoolean("mixEnabled", false) ?: false)
    }
    @JavascriptInterface fun familyMix(kind: String): String {
        if (profiles.isChild()) return profiles.approved().toString()
        if (kind !in setOf("favorites", "history")) return "[]"
        val result = org.json.JSONArray(); val ids = mutableSetOf<String>()
        fun canonical(id: String): String = if (id.startsWith("drive-source:")) "experimental:" + id.substringAfter(':') else id
        for (id in profiles.mixProfileIds()) {
            val data = try { JSONObject(activity.getSharedPreferences("xmusic_library_$id", Context.MODE_PRIVATE).getString("data", "{}") ?: "{}") } catch (_: Exception) { continue }
            val rows = data.optJSONArray(kind) ?: continue
            for (i in 0 until minOf(rows.length(), 100)) {
                val row = rows.optJSONObject(i) ?: continue
                val trackId = row.optString("id")
                if (trackId.isNotBlank() && ids.add(canonical(trackId))) result.put(row)
            }
        }
        val remote = FamilySyncStore(activity).remoteMix(kind)
        for (i in 0 until remote.length()) {
            val row = remote.getJSONObject(i); if (ids.add(canonical(row.optString("id")))) result.put(row)
        }
        val sorted = org.json.JSONArray()
        (0 until result.length()).map { result.getJSONObject(it) }.sortedBy { canonical(it.optString("id")) }.take(200).forEach { sorted.put(it) }
        return sorted.toString()
    }

    @JavascriptInterface fun profileState(): String = profiles.publicState()
    @JavascriptInterface fun changeProfile(action: String, id: String, name: String, oldPin: String, newPin: String): Boolean {
        val changed = profiles.change(action, id, name, oldPin, newPin)
        if (changed && action == "select") webView.post { controller?.clearMediaItems(); com.example.playback.PlaybackService.activeService?.setSleepTimer(0) }
        if (changed && action == "delete") {
            activity.getSharedPreferences("xmusic_library_$id", Context.MODE_PRIVATE).edit().clear().commit()
            MusicListeningStats(activity).removeProfile(id)
            PlaybackResumeStore(activity).clear(id)
            val removedDownloads = activity.getSharedPreferences("xmusic_downloads_$id", Context.MODE_PRIVATE)
            val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            removedDownloads.all.values.forEach { raw ->
                try { manager.remove(JSONObject(raw as String).getLong("downloadId")) } catch (_: Exception) { }
            }
            removedDownloads.edit().clear().commit()
        }
        return changed
    }

    private val podcastCatalog = PodcastCatalogClient()

    @JavascriptInterface fun searchPodcasts(query: String, requestId: Int) {
        if (query.trim().length !in 2..100) return
        Thread {
            val result = try { podcastCatalog.search(query) }
            catch (_: Exception) { JSONObject().put("error", "Podcast araması yapılamadı. Bağlantını kontrol edip yeniden dene.") }
            webView.post {
                webView.evaluateJavascript("window.__xmusicPodcastSearchResult && window.__xmusicPodcastSearchResult(" +
                    requestId + "," + result.toString() + ")", null)
            }
        }.start()
    }

    @JavascriptInterface fun openPodcastPage(address: String) { webView.post {
        if (profiles.isChild()) return@post
        try {
            val uri = android.net.Uri.parse(address)
            require(uri.scheme == "https" && uri.host == "podcasts.apple.com")
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) { android.widget.Toast.makeText(activity, "Program bağlantısı açılamadı.", 0).show() }
    } }

    private val episodes = java.util.concurrent.ConcurrentHashMap<String, JSONObject>()
    private val downloadsPrefs get() = activity.getSharedPreferences("xmusic_downloads_" + profiles.activeId(), Context.MODE_PRIVATE)

    private fun reply(requestId: Int, data: JSONObject) { webView.post {
        webView.evaluateJavascript("window.__xmusicRssResult && window.__xmusicRssResult($requestId,$data)", null)
    } }

    @JavascriptInterface fun loadRss(address: String, requestId: Int) {
        Thread {
            val result = try {
                val uri = android.net.Uri.parse(address)
                require(uri.scheme == "https" && !uri.host.isNullOrBlank())
                val conn = java.net.URL(address).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000; conn.readTimeout = 12000
                val xml = try {
                    if (conn.responseCode !in 200..299) throw java.io.IOException("RSS HTTP ${conn.responseCode}")
                    conn.inputStream.use { input ->
                        val bytes = input.readBytesLimited(2_000_000)
                        String(bytes, Charsets.UTF_8)
                    }
                } finally { conn.disconnect() }
                val parsed = PodcastRssParser.parse(xml, address)
                val rows = parsed.getJSONArray("items")
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    episodes[row.getString("id")] = row
                }
                parsed.put("feed", address)
            } catch (_: Exception) { JSONObject().put("feed", address).put("error", "RSS açılamadı. HTTPS adresini ve internet bağlantısını kontrol et.") }
            reply(requestId, result)
        }.start()
    }

    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = read(buffer); if (n < 0) break
            require(output.size() + n <= limit)
            output.write(buffer, 0, n)
        }
        return output.toByteArray()
    }

    @JavascriptInterface fun playPodcast(id: String, enqueue: Boolean) { webView.post {
        val data = episodes[id]
        if (data == null) { android.widget.Toast.makeText(activity, "Önce podcast RSS kaynağını aç", 0).show(); return@post }
        val item = MediaItem.Builder().setMediaId(id).setUri(data.getString("url"))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(data.optString("title")).setArtist(data.optString("artist"))
                .setExtras(android.os.Bundle().apply { putString("feed", data.optString("feed")) }).build()).build()
        val p = controller ?: return@post
        if (enqueue) p.addMediaItem(item) else { p.setMediaItem(item); p.prepare(); p.play() }
    } }

    @JavascriptInterface fun resumePodcast(address: String, id: String, position: Long) {
        if (!id.matches(Regex("podcast:[a-f0-9]{64}"))) return
        Thread {
            try {
                var data = episodes[id]
                var audioUri: android.net.Uri? = null
                val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                val downloaded = downloadsPrefs.getString(id.removePrefix("podcast:"), null)
                if (downloaded != null) {
                    val saved = JSONObject(downloaded)
                    audioUri = manager.getUriForDownloadedFile(saved.getLong("downloadId"))
                    if (audioUri != null) data = saved
                }
                if (data == null) {
                    val source = android.net.Uri.parse(address)
                    require(source.scheme == "https" && !source.host.isNullOrBlank())
                    val conn = java.net.URL(address).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 10000; conn.readTimeout = 12000
                    val xml = try {
                        if (conn.responseCode !in 200..299) throw java.io.IOException("RSS bağlantısı açılamadı")
                        conn.inputStream.use { String(it.readBytesLimited(2_000_000), Charsets.UTF_8) }
                    } finally { conn.disconnect() }
                    val items = PodcastRssParser.parse(xml, address).getJSONArray("items")
                    for (i in 0 until items.length()) {
                        val row = items.getJSONObject(i)
                        if (row.optString("id") == id) { data = row; episodes[id] = row; break }
                    }
                }
                val track = data ?: throw java.io.IOException("Bölüm bulunamadı")
                val uri = audioUri ?: android.net.Uri.parse(track.getString("url"))
                val item = MediaItem.Builder().setMediaId(id).setUri(uri).setMediaMetadata(MediaMetadata.Builder()
                    .setTitle(track.optString("title")).setArtist(track.optString("artist"))
                    .setExtras(android.os.Bundle().apply { putString("feed", track.optString("feed")) }).build()).build()
                webView.post {
                    controller?.let { p ->
                        p.setMediaItem(item);p.seekTo(position.coerceIn(0L, 86_400_000L));p.prepare();p.play()
                    }
                }
            } catch (_: Exception) { webView.post { android.widget.Toast.makeText(activity, "Podcast bölümüne erişilemedi.", 0).show() } }
        }.start()
    }

    @JavascriptInterface fun downloadPodcast(id: String) { webView.post {
        if (profiles.isChild()) return@post
        val data = episodes[id] ?: return@post
        try {
            val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            // Yalnızca yayıncının RSS enclosure olarak sunduğu ses kaynağı indirilir.
            val key = id.removePrefix("podcast:")
            val existing = downloadsPrefs.getString(key, null)
            if (existing != null) { android.widget.Toast.makeText(activity, "Bu bölüm zaten indirme listesinde", 0).show(); return@post }
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(data.getString("url")))
                .setTitle(data.optString("title"))
                .setDescription("XMusic Family podcast")
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(activity, android.os.Environment.DIRECTORY_MUSIC, "$key.audio")
            if (activity.getSharedPreferences("xmusic_settings", Context.MODE_PRIVATE).getBoolean("download_unmetered_only", false))
                request.setAllowedNetworkTypes(android.app.DownloadManager.Request.NETWORK_WIFI)
            val downloadId = manager.enqueue(request)
            downloadsPrefs.edit().putString(key, JSONObject(data.toString()).put("downloadId", downloadId).toString()).apply()
            android.widget.Toast.makeText(activity, "Podcast indirme kuyruğuna eklendi", 0).show()
        } catch (_: Exception) { android.widget.Toast.makeText(activity, "İndirme başlatılamadı", 0).show() }
    } }

    @JavascriptInterface fun listDownloads(): String {
        val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        val rows = org.json.JSONArray()
        downloadsPrefs.all.values.forEach { raw ->
            try {
                val item = JSONObject(raw as String)
                manager.query(android.app.DownloadManager.Query().setFilterById(item.getLong("downloadId")))?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        item.put("status", cursor.getInt(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS)))
                        item.put("bytes", cursor.getLong(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)))
                        item.put("total", cursor.getLong(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES)))
                        rows.put(item)
                    } else { rows.put(item.put("status", 16).put("bytes", 0).put("total", 0)) }
                }
            } catch (_: Exception) { }
        }
        return rows.toString()
    }

    @JavascriptInterface fun removeDownload(id: String) { webView.post {
        val key = id.removePrefix("podcast:")
        try {
            val item = JSONObject(downloadsPrefs.getString(key, null) ?: return@post)
            val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            manager.remove(item.getLong("downloadId"))
            downloadsPrefs.edit().remove(key).apply()
        } catch (_: Exception) { }
    } }

    @JavascriptInterface fun playDownload(id: String) { webView.post {
        try {
            val item = JSONObject(downloadsPrefs.getString(id.removePrefix("podcast:"), null) ?: return@post)
            val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            val uri = manager.getUriForDownloadedFile(item.getLong("downloadId")) ?: return@post
            val media = MediaItem.Builder().setMediaId(id).setUri(uri).setMediaMetadata(MediaMetadata.Builder()
                .setTitle(item.optString("title")).setArtist(item.optString("artist")).build()).build()
            controller?.let { it.setMediaItem(media); it.prepare(); it.play() }
        } catch (_: Exception) { android.widget.Toast.makeText(activity, "İndirilen dosya açılamadı", 0).show() }
    } }

    @JavascriptInterface fun loadLibrary(): String {
        libraryProfileId = profiles.activeId()
        if (profiles.activeId() == "default" && !prefs.contains("data")) {
            val legacy = activity.getSharedPreferences("xmusic_library", Context.MODE_PRIVATE).getString("data", "{}")
            prefs.edit().putString("data", legacy).commit()
        }
        return prefs.getString("data", "{}") ?: "{}"
    }
    @JavascriptInterface fun saveLibrary(value: String): Boolean {
        if (profiles.activeId() != libraryProfileId || value.length > 1_000_000) return false
        return try { JSONObject(value); prefs.edit().putString("data", value).commit() } catch (_: Exception) { false }
    }
    @JavascriptInterface fun loadPlaybackResume(): String =
        if (profiles.activeId() == libraryProfileId) PlaybackResumeStore(activity).load(libraryProfileId) else "{}"
    @JavascriptInterface fun clearPlaybackResume() {
        if (profiles.isChild()) return
        if (profiles.activeId() == libraryProfileId) PlaybackResumeStore(activity).clear(libraryProfileId)
    }

    @JavascriptInterface fun assignMusicGenre(videoId: String, genre: String) {
        if (profiles.activeId() == libraryProfileId) MusicListeningStats(activity).assign(videoId, genre)
    }
    @JavascriptInterface fun familyGenreStats(): String = MusicListeningStats(activity).summary()

    @JavascriptInterface fun recordSearch(query: String) {
        if (profiles.activeId() != libraryProfileId) return
        try { SearchStatsStore(activity, libraryProfileId).record(query) } catch (_: Exception) { }
    }
    @JavascriptInterface fun topSearches(): String {
        if (profiles.activeId() != libraryProfileId) return "[]"
        return try { SearchStatsStore(activity, libraryProfileId).read().toString() } catch (_: Exception) { "[]" }
    }

    @JavascriptInterface fun searchHistory(action: String, query: String): String {
        // Önceki profilin açık sayfasından yeni profile geçmiş yazılmasını engelle.
        if (profiles.activeId() != libraryProfileId) return "[]"
        return try { SearchHistoryStore(activity, libraryProfileId).update(action, query) }
        catch (_: Exception) { "null" }
    }

    @JavascriptInterface fun voiceSearch() { webView.post { activity.openVoiceSearch() } }
    @JavascriptInterface fun openLocalAudio() { webView.post { activity.openLocalAudio() } }
    @JavascriptInterface fun clearWebCache() { webView.post {
        webView.clearCache(true)
        android.widget.Toast.makeText(activity, "Web önbelleği temizlendi; kitaplık korundu", android.widget.Toast.LENGTH_SHORT).show()
    } }
    @JavascriptInterface fun openSoundSettings() { webView.post {
        if (profiles.isChild()) return@post
        try { activity.startActivity(Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)) }
        catch (_: Exception) { android.widget.Toast.makeText(activity, "Ses ayarları açılamadı", 0).show() }
    } }
    @JavascriptInterface fun shareText(value: String) { webView.post {
        if (profiles.isChild()) return@post
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, value.take(4000))
        activity.startActivity(Intent.createChooser(intent, "Paylaş"))
    } }
    @JavascriptInterface fun queueAction(action: String, index: Int, target: Int) { webView.post {
        val p = controller ?: return@post
        when (action) {
            "clear" -> p.clearMediaItems()
            "remove" -> if (index in 0 until p.mediaItemCount) p.removeMediaItem(index)
            "move" -> if (index in 0 until p.mediaItemCount && target in 0 until p.mediaItemCount) p.moveMediaItem(index, target)
            "play" -> if (index in 0 until p.mediaItemCount) { p.seekToDefaultPosition(index); p.prepare(); p.play() }
            "shuffle" -> p.shuffleModeEnabled = !p.shuffleModeEnabled
            "repeat" -> p.repeatMode = (p.repeatMode + 1) % 3
            "back15" -> if (p.isCurrentMediaItemSeekable) p.seekTo((p.currentPosition - 15000).coerceAtLeast(0))
            "forward30" -> if (p.isCurrentMediaItemSeekable && p.duration > 0) p.seekTo((p.currentPosition + 30000).coerceAtMost(p.duration))
            "speed" -> p.setPlaybackSpeed(target.coerceIn(50, 200) / 100f)
        }
    } }
    @JavascriptInterface fun setSleepFade(enabled: Boolean) { webView.post {
        com.example.playback.PlaybackService.activeService?.setSleepFade(enabled)
    } }
    @JavascriptInterface fun setSleepTimer(minutes: Int) { webView.post {
        val service = com.example.playback.PlaybackService.activeService
        if (service == null || (minutes != 0 && controller?.currentMediaItem == null)) {
            android.widget.Toast.makeText(activity, "Önce bir parça başlat", 0).show()
        } else { service.setSleepTimer(minutes); android.widget.Toast.makeText(activity, if (minutes == 0) "Zamanlayıcı iptal edildi" else "Zamanlayıcı ayarlandı", 0).show() }
    } }
    @JavascriptInterface fun playSavedTrack(value: String, enqueue: Boolean) = startSavedTrack(value, enqueue, 0L)
    @JavascriptInterface fun resumeLocalTrack(value: String, position: Long) = startSavedTrack(value, false, position.coerceIn(0L, 86_400_000L))
    private fun startSavedTrack(value: String, enqueue: Boolean, position: Long) { webView.post {
        try {
            val data = JSONObject(value)
            val id = data.optString("id")
            val uri = if (id.startsWith("local:")) android.net.Uri.parse(id.removePrefix("local:")) else null
            // Kaydedilmiş kullanıcı dosyaları yalnızca kalıcı SAF izniyle açılır.
            if (uri == null || uri.scheme != "content" || !activity.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }) {
                android.widget.Toast.makeText(activity, "Çevrimiçi kaydı yeniden arayıp erişimini doğrula", 0).show(); return@post
            }
            val item = MediaItem.Builder().setMediaId(id).setUri(uri).setMediaMetadata(MediaMetadata.Builder()
                .setTitle(data.optString("title").take(180)).setArtist(data.optString("artist").take(180)).build()).build()
            val p = controller ?: return@post
            if (enqueue) p.addMediaItem(item) else { p.setMediaItem(item); if (position > 0) p.seekTo(position); p.prepare(); p.play() }
        } catch (_: Exception) { android.widget.Toast.makeText(activity, "Dosya açılamadı. Dosyayı yeniden seç.", 0).show() }
    } }
}



