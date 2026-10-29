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
    @JavascriptInterface fun profileState(): String = profiles.publicState()
    @JavascriptInterface fun changeProfile(action: String, id: String, name: String, oldPin: String, newPin: String): Boolean {
        val changed = profiles.change(action, id, name, oldPin, newPin)
        if (changed && action == "select") webView.post { controller?.clearMediaItems(); com.example.playback.PlaybackService.activeService?.setSleepTimer(0) }
        if (changed && action == "delete") {
            activity.getSharedPreferences("xmusic_library_$id", Context.MODE_PRIVATE).edit().clear().commit()
            val removedDownloads = activity.getSharedPreferences("xmusic_downloads_$id", Context.MODE_PRIVATE)
            val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            removedDownloads.all.values.forEach { raw ->
                try { manager.remove(JSONObject(raw as String).getLong("downloadId")) } catch (_: Exception) { }
            }
            removedDownloads.edit().clear().commit()
        }
        return changed
    }

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
            .setMediaMetadata(MediaMetadata.Builder().setTitle(data.optString("title")).setArtist(data.optString("artist")).build()).build()
        val p = controller ?: return@post
        if (enqueue) p.addMediaItem(item) else { p.setMediaItem(item); p.prepare(); p.play() }
    } }

    @JavascriptInterface fun downloadPodcast(id: String) { webView.post {
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
    @JavascriptInterface fun voiceSearch() { webView.post { activity.openVoiceSearch() } }
    @JavascriptInterface fun openLocalAudio() { webView.post { activity.openLocalAudio() } }
    @JavascriptInterface fun clearWebCache() { webView.post {
        webView.clearCache(true)
        android.widget.Toast.makeText(activity, "Web önbelleği temizlendi; kitaplık korundu", android.widget.Toast.LENGTH_SHORT).show()
    } }
    @JavascriptInterface fun openSoundSettings() { webView.post {
        try { activity.startActivity(Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)) }
        catch (_: Exception) { android.widget.Toast.makeText(activity, "Ses ayarları açılamadı", 0).show() }
    } }
    @JavascriptInterface fun shareText(value: String) { webView.post {
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
    @JavascriptInterface fun setSleepTimer(minutes: Int) { webView.post {
        val service = com.example.playback.PlaybackService.activeService
        if (service == null || (minutes != 0 && controller?.currentMediaItem == null)) {
            android.widget.Toast.makeText(activity, "Önce bir parça başlat", 0).show()
        } else { service.setSleepTimer(minutes); android.widget.Toast.makeText(activity, if (minutes == 0) "Zamanlayıcı iptal edildi" else "Zamanlayıcı ayarlandı", 0).show() }
    } }
    @JavascriptInterface fun playSavedTrack(value: String, enqueue: Boolean) { webView.post {
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
            if (enqueue) p.addMediaItem(item) else { p.setMediaItem(item); p.prepare(); p.play() }
        } catch (_: Exception) { android.widget.Toast.makeText(activity, "Dosya açılamadı. Dosyayı yeniden seç.", 0).show() }
    } }
}
