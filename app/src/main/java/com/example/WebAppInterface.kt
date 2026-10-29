package com.example

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
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
