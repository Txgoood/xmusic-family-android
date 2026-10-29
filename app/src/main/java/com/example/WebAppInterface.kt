package com.example

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.util.Locale
import android.widget.Toast
import androidx.media3.session.MediaController

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
