package com.example

import android.content.ComponentName
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.playback.PlaybackService
import com.google.common.util.concurrent.MoreExecutors

class MainActivity : ComponentActivity() {
    private var mediaController: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        // Orijinal Stitch HTML dosyalarını değiştirmeden JavaScript köprüsünü kullan.
        // window.Android nesnesini JavaScript ile yeniden tanımlama: özyinelemeli çağrı oluşturur.
        webView.webViewClient = android.webkit.WebViewClient()

        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                this.mediaController = controller
                (webView.tag as? WebAppInterface)?.setMediaController(controller)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())

        // Yerel Stitch dosyalarına erişim
        val webAppInterface = WebAppInterface(this, webView, null)
        webView.tag = webAppInterface
        webView.addJavascriptInterface(webAppInterface, "Android")
        webView.loadUrl("file:///android_asset/stitch/ana_sayfa/code.html")
        
        setContentView(webView)
    }
}
