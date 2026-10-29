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
