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
        webView.settings.domStorageEnabled = true
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        // Orijinal Stitch HTML dosyalarını değiştirmeden JavaScript köprüsünü kullan.
        // window.Android nesnesini JavaScript ile yeniden tanımlama: özyinelemeli çağrı oluşturur.
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Stitch HTML/CSS'yi değiştirmeden ekranlara etkileşim ekle.
                view?.evaluateJavascript("""
                    (function() {
                      if (window.__xmusicActionsInstalled) return;
                      window.__xmusicActionsInstalled = true;
                      const pages = {
                        'ana-sayfa':'ana_sayfa',
                        'kesfet-ara':'arama_ve_ke_fet',
                        'muzik':'m_zik_merkezi',
                        'podcast':'podcast_merkezi',
                        'kitapligim':'kitapl_m'
                      };
                      const go = function(folder) {
                        if (window.Android && folder) window.Android.navigateTo(folder);
                      };
                      // Üst kısımda eksik olan Ayarlar bağlantısını orijinal dosyaya dokunmadan göster.
                      const header = document.querySelector('header .h-16');
                      if (header && !document.getElementById('xmusic-settings-link')) {
                        const button = document.createElement('button');
                        button.id = 'xmusic-settings-link';
                        button.type = 'button';
                        button.setAttribute('aria-label','Ayarlar');
                        button.style.cssText = 'flex-shrink:0;width:40px;height:40px;color:#bbcbb8;background:transparent;border:0;display:flex;align-items:center;justify-content:center;';
                        button.innerHTML = '<span class="material-symbols-outlined" style="font-size:26px">settings</span>';
                        button.addEventListener('click',function(e){e.stopPropagation();go('ayarlar_ve_profil_y_netimi');});
                        header.appendChild(button);
                      }
                      // Ekran genişliğini aşan sabit öğelerin yatay taşmasını önle.
                      const sheet = document.createElement('style');
                      sheet.textContent = 'html,body{overflow-x:hidden;max-width:100%;}header{max-width:100vw;}header .h-16{min-width:0;}';
                      document.head.appendChild(sheet);
                      // Sabit üst çubuğun sayfa başlığını örtmesini önle.
                      // Başlık ve içerik düzeni yalnızca çalışma anında ayarlanır.
                      const fixedHeader = document.querySelector('header.fixed, header.sticky');
                      const pageMain = document.querySelector('main');
                      if (fixedHeader && pageMain) {
                        const fixTopSpacing = function() {
                          const headerBottom = Math.ceil(fixedHeader.getBoundingClientRect().bottom);
                          const mainTop = pageMain.getBoundingClientRect().top;
                          const missing = headerBottom + 8 - mainTop;
                          if (missing > 0) {
                            const current = parseFloat(getComputedStyle(pageMain).paddingTop) || 0;
                            pageMain.style.setProperty('padding-top', (current + missing) + 'px', 'important');
                          }
                        };
                        requestAnimationFrame(fixTopSpacing);
                      }
                      // Ayarlar düğmesi için sağ üstte yer aç; profil alanı taşmasın.
                      if (header) {
                        header.style.minWidth = '0';
                        header.style.gap = '8px';
                        Array.from(header.children).forEach(function(child) { child.style.minWidth = '0'; });
                      }
                      document.addEventListener('click',function(event) {
                        const target = event.target.closest('a[data-path],button');
                        if (!target) return;
                        if (target.matches('a[data-path]')) {
                          const folder=pages[target.getAttribute('data-path')];
                          if (folder){event.preventDefault();go(folder);}
                          return;
                        }
                        if(target.id==='xmusic-settings-link') return;
                        const label=(target.getAttribute('aria-label')||target.textContent||'').trim().toLocaleLowerCase('tr');
                        if(label==='geri'||label.startsWith('geri ')){
                          event.preventDefault();
                          if(window.Android)window.Android.goBack();
                        }else if(label.includes('ayarlar')||label.includes('gizlilik')){
                          go('ayarlar_ve_profil_y_netimi');
                        }else if(label.includes('profil')||label.includes('aile alan')){
                          go('aile_profili_se_imi');
                        }else if(label.includes('dinleme geçmiş')){
                          go('kitapl_m');
                        }else if(label.includes('indirme')){
                          go('i_ndirme_y_neticisi');
                        }else if(label.includes('oynatma sırası')||label.includes('sırayı görüntüle')){
                          go('oynatma_s_ras_queue');
                        }else if(label.includes('uyku zamanlay')){
                          go('uyku_zamanlay_c_s');
                        }else if(label.includes('oynat veya duraklat')){
                          if(window.Android)window.Android.togglePlayPause();
                        }else if(label==='sonraki'){
                          if(window.Android)window.Android.next();
                        }else if(label==='önceki'){
                          if(window.Android)window.Android.previous();
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
