package com.example

import android.content.ComponentName
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.playback.PlaybackService

class MainActivity : ComponentActivity() {
    private var mediaController: MediaController? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private lateinit var playbackBridge: PlaybackWebBridge
    private lateinit var appWebView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val webView = WebView(this)
        appWebView = webView
        playbackBridge = PlaybackWebBridge(webView)
        val playbackScript = assets.open("playback.js").bufferedReader().use { it.readText() }
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        // Orijinal Stitch HTML dosyalarını değiştirmeden JavaScript köprüsünü kullan.
        // window.Android nesnesini JavaScript ile yeniden tanımlama: özyinelemeli çağrı oluşturur.
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Sayfa geçişinde ham HTML/ikon yazıları kısa süre görüntülenmesin.
                view?.alpha = 0f
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(playbackScript, null)
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
                        // Sağ grup içindeki çan + profilin sonuna ekle; üçüncü flex sütunu oluşturma.
                        const rightGroup = header.querySelector('button[aria-label="Profil ve Aile Alanı"]')?.parentElement;
                        if (rightGroup) {
                          rightGroup.appendChild(button);
                          rightGroup.style.setProperty('margin-left','auto','important');
                          rightGroup.style.setProperty('flex-shrink','0','important');
                          rightGroup.style.setProperty('justify-content','flex-end','important');
                          const bell = rightGroup.querySelector('button[aria-label="Bildirimler"]');
                          if (bell) bell.innerHTML = '<svg width="23" height="23" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"/><path d="M10 21h4"/></svg>';
                          button.innerHTML = '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8"/><path d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-1.9 1.9-.06-.06a1.7 1.7 0 0 0-1.88-.34 1.7 1.7 0 0 0-.96 1.57V21h-2.7v-.09a1.7 1.7 0 0 0-1.1-1.58 1.7 1.7 0 0 0-1.88.34l-.06.06-1.9-1.9.06-.06a1.7 1.7 0 0 0 .34-1.88A1.7 1.7 0 0 0 6.2 15H6v-3h.2a1.7 1.7 0 0 0 1.58-1.1 1.7 1.7 0 0 0-.34-1.88l-.06-.06 1.9-1.9.06.06a1.7 1.7 0 0 0 1.88.34A1.7 1.7 0 0 0 12.3 6V5h2.7v1a1.7 1.7 0 0 0 1.1 1.58 1.7 1.7 0 0 0 1.88-.34l.06-.06 1.9 1.9-.06.06a1.7 1.7 0 0 0-.34 1.88A1.7 1.7 0 0 0 21 12h.1v3z" transform="translate(-1.5 -1.5)"/></svg>';
                        } else {
                          header.appendChild(button);
                        }
                      }
                      // Material Symbols fontu yüklenene kadar ikon adlarını metin olarak gösterme.
                      // SVG yalnızca üst çandaki değil, tüm sayfalardaki bildirim ikonlarının
                      // geçiş sırasında kısa süre görünmesini önler.
                      document.querySelectorAll('.material-symbols-outlined').forEach(function(icon) {
                        if ((icon.textContent || '').trim() === 'notifications') {
                          icon.innerHTML = '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"/><path d="M10 21h4"/></svg>';
                          icon.style.fontSize = '0';
                        }
                      });
                      // Stitch arama ekranına gerçek Audius sonuçları ekle; HTML şablonunu koru.
                      if ((location.pathname || '').includes('/arama_ve_ke_fet/')) {
                        const input = document.getElementById('search-input');
                        if (input && window.Android) {
                          const panel = document.createElement('section');
                          panel.id = 'xmusic-live-search';
                          panel.style.cssText = 'display:none;margin:8px 16px 18px;padding:12px;background:#181c1f;border-radius:16px;color:#e0e3e7;';
                          const heading = document.createElement('h2');
                          heading.textContent = 'Audius topluluk araması';
                          heading.style.cssText = 'font-weight:700;font-size:16px;margin-bottom:6px;';
                          const list = document.createElement('div');
                          const disclaimer=document.createElement('p');
                          disclaimer.textContent='Bu sonuçlar Audius kullanıcı yüklemeleridir; resmî sanatçı diskografisi veya doğrulanmış albümler değildir.';
                          disclaimer.style.cssText='color:#b4c1c9;font-size:11px;line-height:1.4;margin-bottom:8px;';;
                          panel.append(heading,disclaimer,list);
                          const main = document.querySelector('main');
                          if (main) main.insertBefore(panel,main.firstChild);
                          let currentId = 0;
                          let debounce;
                          window.__xmusicSearchResult = function(id,payload) {
                            if(id!==currentId)return;
                            list.replaceChildren();
                            if(payload.error){list.textContent=payload.error;return;}
                            const entries=payload.items||[];
                            if(!entries.length){list.textContent='Sonuç bulunamadı';return;}
                            entries.forEach(function(item) {
                              const line=document.createElement('div');
                              line.style.cssText='display:flex;gap:12px;align-items:center;padding:10px 0;border-bottom:1px solid #313539;';
                              if(item.artwork && item.artwork.startsWith('https://')){
                                const img=document.createElement('img');img.src=item.artwork;
                                img.style.cssText='width:48px;height:48px;object-fit:cover;border-radius:8px;';
                                line.appendChild(img);
                              }
                              const caption=document.createElement('div');
                              const name=document.createElement('strong');name.textContent=item.title||'İsimsiz parça';
                              const artist=document.createElement('div');artist.textContent=item.artist||'Bilinmeyen sanatçı';artist.style.color='#bbcbb8';
                              const access=document.createElement('small');access.textContent=item.playable?'Audius · Akış denenebilir':'Bu kayıt için oynatma erişimi yok';caption.append(name,artist,access);line.appendChild(caption);
                              if(item.playable && item.id && /^[a-zA-Z0-9]{1,64}$/.test(item.id)){
                                const play=document.createElement('button');play.type='button';
                                play.textContent='▶ Dinle';play.setAttribute('aria-label',item.title+' oynat');
                                play.style.cssText='margin-left:auto;flex-shrink:0;color:#9af0ba;border:1px solid #36584d;padding:8px;border-radius:12px;font-size:12px;';
                                play.addEventListener('click',function(e){
                                  e.stopPropagation();
                                  window.Android.playAudiusTrack(item.id,item.title||'',item.artist||'');
                                });
                                line.appendChild(play);
                              }
                              list.appendChild(line);
                            });
                          };
                          // Sanatçı ve albüm/koleksiyon aramalarını mevcut arama paneline ekle.
                          const categories = document.createElement('div');
                          categories.style.cssText='display:flex;gap:8px;margin-bottom:12px;flex-wrap:wrap;';
                          const modes=[['tracks','Şarkılar'],['users','Sanatçılar'],['playlists','Kullanıcı listeleri']];
                          let mode='tracks';
                          const buttons=[];
                          modes.forEach(function(pair){
                            const btn=document.createElement('button');btn.textContent=pair[1];
                            btn.type='button';btn.style.cssText='border:1px solid #5b6369;border-radius:18px;padding:7px 12px;color:white;';
                            btn.addEventListener('click',function(){
                              mode=pair[0];buttons.forEach(function(b){b.style.background=b===btn?'#36584d':'transparent';});
                              runSearch();
                            });
                            categories.appendChild(btn);buttons.push(btn);
                          });
                          buttons[0].style.background='#36584d';
                          panel.insertBefore(categories,list);
                          const official=document.createElement('button');
                          official.type='button';official.textContent='Doğrulanabilir sanatçı / albüm kataloğu';
                          official.style.cssText='flex:1;padding:8px;border:1px solid #62796f;border-radius:10px;color:#e0e3e7;text-align:center;font-size:12px;';
                          panel.insertBefore(official,list);
                          const appleButton=document.createElement('button');
                          appleButton.type='button';appleButton.textContent='Apple Türkiye müzik kataloğu';
                          appleButton.style.cssText='flex:1;padding:8px;border:1px solid #62796f;border-radius:10px;color:#e0e3e7;text-align:center;font-size:12px;';
                          panel.insertBefore(appleButton,official);
                          // Kaynak anahtarları iki büyük satır yerine kompakt yatay sekmeler.
                          const sourceRow=document.createElement('div');
                          sourceRow.style.cssText='display:flex;gap:8px;margin:4px 0 8px;';
                          const sourceAnchor=appleButton.parentNode;
                          sourceAnchor.insertBefore(sourceRow,appleButton);
                          sourceRow.append(appleButton,official);
                          let appleMode=false, appleId='', appleStage='artists', appleName='';
                          let releaseFilter='Albüm';
                          function showApple(id,payload){
                            if(id!==currentId || !appleMode)return;
                            list.replaceChildren();
                            if(payload.error){list.textContent=payload.error;return;}
                            const items=payload.items||[];
                            if(!items.length){list.textContent='Apple Türkiye kataloğunda sonuç bulunamadı';return;}
                            if(appleStage!=='artists'){
                              const back=document.createElement('button');
                              back.type='button';back.textContent='← Geri';
                              back.style.cssText='display:block;padding:9px;color:#9af0ba;';
                              back.addEventListener('click',function(){
                                if(appleStage==='songs'){appleStage='albums';appleId=window.__appleArtistId||'';}
                                else {appleStage='artists';appleId='';}
                                runSearch();
                              });list.appendChild(back);
                            }
                            if(appleStage==='albums'){
                              const counts={};
                              items.forEach(function(item){const c=item.category||'Albüm';counts[c]=(counts[c]||0)+1;});
                              const summary=document.createElement('div');
                              summary.textContent=Object.keys(counts).map(function(k){return k+': '+counts[k];}).join('  ·  ');
                              summary.style.cssText='color:#9af0ba;font-size:12px;padding:6px 0 10px;';
                              list.appendChild(summary);
                              const filters=document.createElement('div');
                              filters.style.cssText='display:flex;gap:8px;flex-wrap:wrap;margin-bottom:12px;';
                              ['Albüm','EP','Single','Tümü'].forEach(function(category){
                                const chip=document.createElement('button');
                                chip.type='button';chip.textContent=category+' ('+(category==='Tümü'?items.length:(counts[category]||0))+')';
                                chip.style.cssText='border-radius:16px;padding:7px 11px;font-size:12px;border:1px solid #62796f;color:#e0e3e7;background:'+(releaseFilter===category?'#36584d':'transparent');
                                chip.addEventListener('click',function(){releaseFilter=category;showApple(id,payload);});
                                filters.appendChild(chip);
                              });
                              list.appendChild(filters);
                              items.sort(function(a,b){
                                const order={'Albüm':0,'EP':1,'Single':2};
                                return (order[a.category]??3)-(order[b.category]??3);
                              });
                            }
                            items.forEach(function(item){
                              if(appleStage==='albums' && releaseFilter!=='Tümü' && item.category!==releaseFilter)return;
                              const row=document.createElement('button');
                              row.type='button';row.style.cssText='display:flex;gap:12px;align-items:center;text-align:left;width:100%;padding:10px 0;color:#e0e3e7;border-bottom:1px solid #313539;';
                              if(item.artwork && item.artwork.startsWith('https://')){
                                const img=document.createElement('img');img.src=item.artwork;
                                img.style.cssText='width:48px;height:48px;object-fit:cover;border-radius:8px;';
                                row.appendChild(img);
                              }
                              const cell=document.createElement('div');
                              const title=document.createElement('strong');title.textContent=item.title||'İsimsiz';
                              const sub=document.createElement('div');sub.textContent=(appleStage==='albums'?((item.category||'Albüm')+' · '):appleStage==='songs'?'Yalnızca katalog · ':'')+(item.subtitle||'');sub.style.cssText='color:#b4c1c9;font-size:13px;';
                              cell.append(title,sub);row.appendChild(cell);
                              row.addEventListener('click',function(){
                                if(appleStage==='artists'){
                                  appleId=item.id;window.__appleArtistId=item.id;appleName=item.title;
                                  appleStage='albums';runSearch();
                                }else if(appleStage==='albums'){
                                  appleId=item.id;appleStage='songs';runSearch();
                                }else{
                                  // Apple katalog kayıtları ses akışı değildir: yanlışlıkla Apple Music'e yönlendirme.
                                  if(window.Android)window.Android.showToast('Bu şarkı katalog bilgisidir. Bu kayıt için yetkili ses kaynağı henüz bağlı değil.');
                                }
                              });
                              list.appendChild(row);
                            });
                          }
                          window.__xmusicAppleResult=showApple;
                          appleButton.addEventListener('click',function(){
                            appleMode=!appleMode;appleStage='artists';appleId='';
                            if(appleMode){verifiedMode=false;official.style.background='transparent';}
                            appleButton.style.background=appleMode?'#36584d':'transparent';
                            categories.style.display=appleMode?'none':'flex';
                            heading.textContent=appleMode?'Apple Türkiye sanatçı ve albüm kataloğu':'Audius topluluk araması';
                            disclaimer.textContent=appleMode?
                              'Sanatçı → albüm → şarkı listesi. Apple kayıt bilgileri gösterilir; tam şarkı oynatma bu katalogdan yapılmaz.':
                              'Bu sonuçlar Audius kullanıcı yüklemeleridir; resmî sanatçı diskografisi veya doğrulanmış albümler değildir.';
                            runSearch();
                          });
                          let verifiedMode=false;
                          let selectedArtist='';
                          function showVerified(payload,id) {
                            if(id!==currentId || !verifiedMode)return;
                            list.replaceChildren();
                            if(payload.error){list.textContent=payload.error;return;}
                            const items=payload.items||[];
                            if(!items.length){list.textContent='Katalogda sonuç bulunamadı';return;}
                            if(selectedArtist){
                              const back=document.createElement('button');
                              back.textContent='← Sanatçılara dön';
                              back.style.cssText='padding:8px;color:#9af0ba;';
                              back.addEventListener('click',function(){selectedArtist='';runSearch();});
                              list.appendChild(back);
                            }
                            items.forEach(function(item){
                              const row=document.createElement('button');
                              row.type='button';row.style.cssText='display:block;width:100%;text-align:left;padding:12px 4px;border-bottom:1px solid #313539;color:#e0e3e7;';
                              const title=document.createElement('strong');title.textContent=item.title;
                              const desc=document.createElement('div');desc.textContent=item.subtitle||'';
                              desc.style.cssText='color:#b4c1c9;font-size:13px;margin-top:4px;';
                              row.append(title,desc);
                              if(!selectedArtist){
                                row.addEventListener('click',function(){selectedArtist=item.id;runSearch();});
                              }else{row.disabled=true;}
                              list.appendChild(row);
                            });
                          }
                          window.__xmusicVerifiedCatalog=showVerified;
                          official.addEventListener('click',function(){
                            verifiedMode=!verifiedMode;selectedArtist='';appleMode=false;appleButton.style.background='transparent';
                            official.style.background=verifiedMode?'#36584d':'transparent';
                            categories.style.display=verifiedMode?'none':'flex';
                            heading.textContent=verifiedMode?'MusicBrainz sanatçı ve albümleri':'Audius topluluk araması';
                            disclaimer.textContent=verifiedMode?
                              'Sanatçı seçip kayıtlı albümlerini görebilirsin. Bu yalnızca katalog bilgisidir; ses dosyası veya oynatma hakkı içermez.':
                              'Bu sonuçlar Audius kullanıcı yüklemeleridir; resmî sanatçı diskografisi veya doğrulanmış albümler değildir.';
                            runSearch();
                          });

                          const renderCatalog=function(id,type,payload){
                            if(id!==currentId || type!==mode)return;
                            list.replaceChildren();
                            if(payload.error){list.textContent=payload.error;return;}
                            const entries=payload.items||[];
                            if(!entries.length){list.textContent='Sonuç bulunamadı';return;}
                            entries.forEach(function(item){
                              const line=document.createElement('div');
                              line.style.cssText='display:flex;align-items:center;gap:12px;padding:12px 0;border-bottom:1px solid #313539;';
                              if(item.artwork && item.artwork.startsWith('https://')){
                                const img=document.createElement('img');img.src=item.artwork;
                                img.style.cssText='width:48px;height:48px;object-fit:cover;border-radius:8px;';
                                line.appendChild(img);
                              }
                              const info=document.createElement('div');
                              const title=document.createElement('strong');title.textContent=item.title||'İsimsiz';
                              const sub=document.createElement('div');sub.textContent=item.artist||'';sub.style.color='#b4c1c9';
                              info.append(title,sub);line.appendChild(info);list.appendChild(line);
                            });
                          };
                          window.__xmusicCatalogResult=renderCatalog;
                          function runSearch(){
                            clearTimeout(debounce);currentId+=1;
                            const id=currentId;const q=input.value.trim();
                            if(!q){panel.style.display='none';return;}
                            panel.style.display='block';list.textContent='Aranıyor...';
                            debounce=setTimeout(function(){
                              if(verifiedMode){
                                setTimeout(function(){if(verifiedMode && id===currentId && list.textContent==='Aranıyor...'){list.textContent='Katalog yanıt vermedi. Aramayı yeniden dene.';}},16000);
                              }
                              if(appleMode)window.Android.searchAppleCatalog(q,appleId,appleStage,id);
                              else if(verifiedMode)window.Android.searchVerifiedCatalog(q,selectedArtist,id);
                              else if(mode==='tracks')window.Android.searchMusic(q,id);
                              else window.Android.searchAudiusCatalog(q,mode,id);
                            },350);
                          }
                          input.addEventListener('input',function() {
                            runSearch();
                          });
                        }
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
                      // Main zaten pt-16 kullanıyor; üst boşluğu ikinci kez EKLEME.
                      const fixedHeader = document.querySelector('header.fixed, header.sticky');
                      const pageMain = document.querySelector('main');
                      if (fixedHeader && pageMain) {
                        const height = Math.ceil(fixedHeader.getBoundingClientRect().height);
                        pageMain.style.setProperty('padding-top', Math.max(height + 8, 72) + 'px', 'important');
                      }
                      // Ayarlar tasarımındaki yanlış statik başlığı çalışma anında düzelt.
                      if ((location.pathname || '').includes('/ayarlar_ve_profil_y_netimi/')) {
                        const topTitle = document.querySelector('header h1, header h2');
                        if (topTitle && /şimdi çalıyor/i.test(topTitle.textContent || '')) topTitle.textContent = 'Ayarlar';
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
                        // Inline onclick=history.back() çalışmadan önce yakala; çift geri dönüşü engelle.
                        const topLabel=(target.getAttribute('aria-label')||'').trim().toLocaleLowerCase('tr');
                        if(topLabel==='geri'){
                          event.preventDefault(); event.stopImmediatePropagation();
                          if(window.Android) window.Android.goBack();
                          return;
                        }
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
                      }, true);
                    })();
                """.trimIndent()) { playbackBridge.publish(); view?.post { view.alpha = 1f } }
            }
        }

        // Köprü, asenkron bağlantı tamamlanmadan önce hazır olmalı.
        val webAppInterface = WebAppInterface(this, webView, null)
        webView.tag = webAppInterface
        webView.addJavascriptInterface(webAppInterface, "Android")
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            if (!isDestroyed) {
                try {
                    val controller = future.get()
                    mediaController = controller
                    webAppInterface.setMediaController(controller)
                    playbackBridge.attach(controller)
                } catch (e: Exception) {
                    android.util.Log.w("XMusicPlayback", "Oynatıcı bağlantısı kurulamadı", e)
                    playbackBridge.publish()
                }
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(this))
        webView.loadUrl("file:///android_asset/stitch/ana_sayfa/code.html")
        
        setContentView(webView)
    }
    override fun onStart() {
        super.onStart()
        playbackBridge.start()
    }

    override fun onStop() {
        playbackBridge.stop()
        super.onStop()
    }

    override fun onDestroy() {
        playbackBridge.close()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        appWebView.removeJavascriptInterface("Android")
        appWebView.destroy()
        super.onDestroy()
    }
}
