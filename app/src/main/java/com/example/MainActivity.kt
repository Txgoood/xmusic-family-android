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
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Stitch HTML/CSS'yi değiştirmeden ekranlara etkileşim ekle.
                view?.evaluateJavascript("""
                    (function() {
                      if (window.__xmusicNavigationInstalled) return;
                      window.__xmusicNavigationInstalled = true;
                      const pages = {
                        'ana-sayfa': 'ana_sayfa',
                        'kesfet-ara': 'arama_ve_ke_fet',
                        'muzik': 'm_zik_merkezi',
                        'podcast': 'podcast_merkezi',
                        'kitapligim': 'kitapl_m'
                      };
                      document.addEventListener('click', function(event) {
                        const target = event.target.closest('a[data-path], button');
                        if (!target) return;
                        if (target.matches('a[data-path]')) {
                          const folder = pages[target.getAttribute('data-path')];
                          if (folder && window.Android) {
                            event.preventDefault();
                            window.Android.navigateTo(folder);
                          }
                          return;
                        }
                        const label = (target.getAttribute('aria-label') || target.textContent || '').trim().toLocaleLowerCase('tr');
                        if (!window.Android) return;
                        if (label.includes('profil') || label.includes('aile alan')) {
                          window.Android.navigateTo('aile_profili_se_imi');
                        } else if (label.includes('dinleme geçmiş')) {
                          window.Android.navigateTo('kitapl_m');
                        } else if (label.includes('oynat veya duraklat')) {
                          window.Android.play();
                        }
                      });
                    })();
                """.trimIndent(), null)
            }
        }

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
