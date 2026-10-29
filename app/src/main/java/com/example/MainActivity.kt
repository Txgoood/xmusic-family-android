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
                      // Ayarlar ekranındaki anahtarları Android SharedPreferences ile sakla.
                      // Gerçek işlevi olmayan anahtarın görüntüsü değişse de özellik tamamlandı sayılmaz.
                      if ((location.pathname || '').includes('/ayarlar_ve_profil_y_netimi/')) {
                        document.querySelectorAll('input[type="checkbox"]').forEach(function(box, index) {
                          const key = 'setting_' + index;
                          if (window.Android) {
                            box.checked = window.Android.loadSetting(key, box.checked);
                            box.addEventListener('change', function() {
                              window.Android.saveSetting(key, box.checked);
                            });
                          }
                        });
                      }
                      // Ekran genişliğini aşan sabit öğelerin yatay taşmasını önle.
                      const sheet = document.createElement('style');
                      sheet.textContent = 'html,body{overflow-x:hidden;max-width:100%;}header{max-width:100vw;}header .h-16{min-width:0;}';
                      document.head.appendChild(sheet);
                      // WebView: tüm ekranlarda dikey dokunmatik kaydırmayı etkinleştir.
                      const scrollStyle = document.createElement('style');
                      scrollStyle.id = 'xmusic-scroll-fix';
                      scrollStyle.textContent = 'html,body{height:auto!important;min-height:100%!important;overflow-y:auto!important;overscroll-behavior-y:auto!important;touch-action:pan-y!important;-webkit-overflow-scrolling:touch!important;}main{height:auto!important;min-height:100vh!important;overflow-y:visible!important;touch-action:pan-y!important;}';
                      document.head.appendChild(scrollStyle);
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
                        const target = event.target.closest('a[data-path],button,[data-stream-url],section .cursor-pointer');
                        if (!target) return;
                        if (target.matches('a[data-path]')) {
                          const folder=pages[target.getAttribute('data-path')];
                          if (folder){event.preventDefault();go(folder);}
                          return;
                        }
                        if(target.id==='xmusic-settings-link') return;
                        // Demo kartlarda URL yoksa gerçek oynatma varmış gibi davranma.
                        const resumeSection = target.closest('section');
                        if (resumeSection && (resumeSection.textContent||'').includes('Kaldığın Yerden Devam Et')) {
                          const mediaUrl = target.closest('[data-stream-url]')?.getAttribute('data-stream-url')
                              || resumeSection.querySelector('[data-stream-url]')?.getAttribute('data-stream-url');
                          if (mediaUrl) window.Android.resumeStream(mediaUrl);
                          else window.Android.showUnavailable('Kaldığın yerden devam et');
                          event.preventDefault();
                          return;
                        }
                        const streamElement = target.closest('[data-stream-url]');
                        if (streamElement) {
                          window.Android.playStream(streamElement.getAttribute('data-stream-url'));
                          event.preventDefault();
                          return;
                        }
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
