package com.example

import android.content.ComponentName
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.playback.PlaybackService

class MainActivity : ComponentActivity() {
    // Erişim anahtarı yalnızca bellekte tutulur; süre bitince yeniden yetki alınır.
    private var driveAccessToken: String? = null
    private var driveFolderId: String? = null
    private var driveAccountId: String? = null
    private var driveBusy = false
    private val driveFolderWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val driveFilePicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) uploadLocalFileToDrive(uri)
    }

    fun toggleAutomaticArchive() {
        val prefs = getSharedPreferences("xmusic_auto_archive", MODE_PRIVATE)
        val enabled = !prefs.getBoolean("enabled", true)
        prefs.edit().putBoolean("enabled", enabled).apply()
        if (!enabled) androidx.work.WorkManager.getInstance(this).cancelAllWorkByTag("xmusic-auto-archive")
        showDriveError(if (enabled) "Otomatik Drive arşivi açık. Drive hesabını bağladıktan sonra çalan YouTube kayıtları kuyruğa alınır." else "Otomatik Drive arşivi kapalı.")
    }

    fun playArchivedSource(sourceId: String, title: String, artist: String): Boolean {
        val account = driveAccountId ?: return false
        val token = driveAccessToken ?: return false
        val fileId = getSharedPreferences("xmusic_auto_archive", MODE_PRIVATE).getString(account + ":" + sourceId, null) ?: return false
        if (!fileId.matches(Regex("[A-Za-z0-9_-]{1,200}"))) return false
        val controller = mediaController ?: return false
        val service = PlaybackService.activeService ?: return false
        val url = "https://www.googleapis.com/drive/v3/files/" + fileId + "?alt=media"
        service.registerExperimentalStream(url, mapOf("Authorization" to "Bearer $token"))
        val metadata = androidx.media3.common.MediaMetadata.Builder().setTitle(title).setArtist("Drive · " + artist)
            .setArtworkUri(android.net.Uri.parse("https://i.ytimg.com/vi/$sourceId/mqdefault.jpg")).build()
        controller.setMediaItem(androidx.media3.common.MediaItem.Builder().setMediaId("drive-source:" + sourceId)
            .setUri(url).setMediaMetadata(metadata).build())
        controller.prepare()
        controller.play()
        return true
    }

    fun openDriveLibrary() {
        val token = driveAccessToken
        val folder = driveFolderId
        if (driveBusy) { showDriveError("Drive işlemi sürüyor."); return }
        if (token == null || folder == null) { showDriveError("Önce Ayarlar'dan Google Drive hesabını bağla."); return }
        driveBusy = true
        driveFolderWorker.execute {
            try {
                val tracks = com.example.data.drive.DriveFolderClient().listAudio(token, folder)
                runOnUiThread {
                    if (tracks.isEmpty()) showDriveError("XMusic Family klasöründe ses dosyası bulunamadı.")
                    else android.app.AlertDialog.Builder(this).setTitle("Drive müzik arşivi")
                        .setItems(tracks.map { it.name }.toTypedArray()) { _, index ->
                            val track = tracks[index]
                            val controller = mediaController
                            val service = PlaybackService.activeService
                            if (controller == null || service == null) {
                                showDriveError("Oynatıcı henüz hazır değil.")
                            } else if (driveAccessToken != token || driveFolderId != folder) {
                                showDriveError("Drive hesabı değişti; arşivi yeniden aç.")
                            } else {
                                val url = "https://www.googleapis.com/drive/v3/files/" + track.id + "?alt=media"
                                service.registerExperimentalStream(url, mapOf("Authorization" to "Bearer $token"))
                                val metadata = androidx.media3.common.MediaMetadata.Builder()
                                    .setTitle(track.name).setArtist("Google Drive arşivi").build()
                                val item = androidx.media3.common.MediaItem.Builder()
                                    .setMediaId("drive:" + track.id).setUri(url).setMediaMetadata(metadata).build()
                                controller.setMediaItem(item)
                                controller.prepare()
                                controller.play()
                            }
                        }.setNegativeButton("Kapat", null).show()
                }
            } catch (e: Exception) {
                runOnUiThread { showDriveError(e.message ?: "Drive arşivi açılamadı") }
            } finally { runOnUiThread { driveBusy = false } }
        }
    }

    fun chooseDriveUpload() {
        if (driveBusy) {
            showDriveError("Drive işlemi sürüyor; tamamlanmasını bekle.")
            return
        }
        if (driveAccessToken == null || driveFolderId == null) {
            showDriveError("Önce Google Drive hesabını bağla ve klasörün hazır olmasını bekle.")
            return
        }
        driveFilePicker.launch(arrayOf("audio/*"))
    }

    private fun uploadLocalFileToDrive(uri: android.net.Uri) {
        val account = driveAccountId ?: run { showDriveError("Önce Drive hesabını yeniden bağla."); return }
        try {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            com.example.data.drive.DriveUploadWorker.enqueue(this, uri, account)
            showDriveError("Dosya kalıcı yükleme kuyruğuna eklendi. Durumu 'Drive yükleme kuyruğu' düğmesinden izleyebilirsin.")
        } catch (_: Exception) { showDriveError("Dosya için kalıcı okuma izni alınamadı; başka bir dosya sağlayıcısından seç.") }
    }

    fun showDriveUploadQueue() {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            val status = androidx.work.WorkManager.getInstance(this)
                .getWorkInfosByTagLiveData("xmusic-drive-upload")
            val dialog = android.app.AlertDialog.Builder(this)
                .setTitle("Drive yükleme kuyruğu")
                .setMessage("Kuyruk okunuyor…")
                .setPositiveButton("Tamam", null).create()
            val observer = androidx.lifecycle.Observer<List<androidx.work.WorkInfo>> { jobs ->
                val text = jobs.sortedBy { it.state.isFinished }.take(30).joinToString("\n\n") { job ->
                    val state = when (job.state) {
                        androidx.work.WorkInfo.State.ENQUEUED -> "Bekliyor / yeniden denenecek"
                        androidx.work.WorkInfo.State.RUNNING -> {
                            val stage = job.progress.getString("stage") ?: "Yükleniyor"
                            val percent = job.progress.getInt("percent", -1)
                            stage + if (percent in 0..100) " · %" + percent else ""
                        }
                        androidx.work.WorkInfo.State.SUCCEEDED -> if (job.outputData.getBoolean("duplicate", false)) "Drive'da zaten var" else "Tamamlandı"
                        androidx.work.WorkInfo.State.FAILED -> "Başarısız"
                        androidx.work.WorkInfo.State.BLOCKED -> "Sıra bekliyor"
                        else -> "İptal edildi"
                    }
                    val name = job.outputData.getString("name") ?: job.progress.getString("name")
                        ?: job.id.toString().take(8)
                    name + ": " + state + (job.outputData.getString("error")?.let { "\n" + it } ?: "")
                }.ifBlank { "Yükleme kuyruğu boş." }
                dialog.setMessage(text)
            }
            // Ekran açıkken canlı izle; kapatıldığında gözlemciyi kaldır.
            dialog.setOnDismissListener { status.removeObserver(observer) }
            dialog.show()
            status.observe(this, observer)
        }
    }
    private val driveAuthLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        try {
            val data = result.data
            if (data == null) {
                val detail = if (result.resultCode == android.app.Activity.RESULT_CANCELED)
                    "Google hesap ekranı iptal edildi veya sonuç döndürmedi." else
                    "Google hesap ekranı sonuç döndürmedi. Sonuç kodu: " + result.resultCode
                showDriveError(detail)
            } else {
                val auth = com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient(this)
                    .getAuthorizationResultFromIntent(data)
                receiveDriveAuthorization(auth)
            }
        } catch (e: Exception) {
            showDriveException("Yetkilendirme sonucu", e)
        }
    }

    private fun showDriveError(detail: String) {
        android.app.AlertDialog.Builder(this).setTitle("Google Drive bağlantısı")
            .setMessage(detail).setPositiveButton("Tamam", null).show()
    }

    private fun showDriveException(stage: String, error: Exception) {
        val api = error as? com.google.android.gms.common.api.ApiException
        val detail = if (api != null) "Google hata kodu: " + api.statusCode +
            "\nDurum: " + (api.status.statusMessage ?: "Açıklama yok") else
            "Hata türü: " + error.javaClass.simpleName
        showDriveError(stage + "\n" + detail)
    }

    private fun receiveDriveAuthorization(result: com.google.android.gms.auth.api.identity.AuthorizationResult) {
        val token = result.accessToken
        if (token.isNullOrBlank()) { showDriveError("Drive erişimi alınamadı"); return }
        driveAccessToken = token
        driveFolderId = null
        driveAccountId = null
        driveBusy = true
        driveFolderWorker.execute {
            try {
                val folderClient = com.example.data.drive.DriveFolderClient()
                val account = folderClient.accountId(token)
                val folder = folderClient.getOrCreate(token)
                runOnUiThread {
                    if (driveAccessToken == token) {
                        driveFolderId = folder; driveAccountId = account
                        getSharedPreferences("xmusic_auto_archive", MODE_PRIVATE).edit().putString("account", account).apply()
                    }
                    showDriveError("Drive bağlantısı hazır. XMusic Family klasörü bulundu veya oluşturuldu. Şimdi 'Drive’a ses dosyası yükle' düğmesini kullanabilirsin.")
                }
            } catch (e: Exception) {
                runOnUiThread { showDriveError(e.message ?: "Drive klasörü hazırlanamadı") }
            } finally { runOnUiThread { driveBusy = false } }
        }
    }

    fun authorizeDrive() {
        if (driveBusy) { showDriveError("Drive işlemi sürüyor; tamamlanmasını bekle."); return }
        val request = com.google.android.gms.auth.api.identity.AuthorizationRequest.builder()
            .setRequestedScopes(listOf(com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/drive.file")))
            .build()
        val client = com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient(this)
        client.authorize(request).addOnSuccessListener { result ->
            if (result.hasResolution()) {
                val pending = result.pendingIntent
                if (pending != null) {
                    driveAuthLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(pending.intentSender).build())
                } else android.widget.Toast.makeText(this, "Google hesap seçimi açılamadı", android.widget.Toast.LENGTH_LONG).show()
            } else receiveDriveAuthorization(result)
        }.addOnFailureListener { error ->
            showDriveException("Google izin isteği", error)
        }
    }

    private var mediaController: MediaController? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private lateinit var playbackBridge: PlaybackWebBridge
    private lateinit var appWebView: WebView
    private lateinit var appActions: AppActionsInterface
    private val audioPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                var title = "Yerel ses dosyası"
                contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) title = it.getString(0)
                }
                val data = org.json.JSONObject().put("id", "local:$uri").put("title", title).put("artist", "Yerel dosya")
                appActions.playSavedTrack(data.toString(), false)
                appWebView.evaluateJavascript("window.__xmusicLocalImported && window.__xmusicLocalImported($data)", null)
            } catch (_: Exception) { android.widget.Toast.makeText(this, "Dosyaya erişilemedi", 0).show() }
        }
    }
    fun openLocalAudio() { audioPicker.launch(arrayOf("audio/*")) }
    private val speechPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val phrase = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!phrase.isNullOrBlank()) appWebView.evaluateJavascript(
                "(function(){const i=document.getElementById('search-input');if(i){i.value=" + org.json.JSONObject.quote(phrase) + ";i.dispatchEvent(new Event('input',{bubbles:true}));}})()", null)
        }
    }
    fun openVoiceSearch() {
        try {
            speechPicker.launch(android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "tr-TR"))
        } catch (_: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(this, "Bu cihazda ses tanıma hizmeti yok", 0).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val webView = WebView(this)
        appWebView = webView
        playbackBridge = PlaybackWebBridge(webView)
        val actionsScript = assets.open("app-actions.js").bufferedReader().use { it.readText() }
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
                view?.evaluateJavascript(actionsScript, null)
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
                      // Keşfet: Stitch arama çubuğu ve filtrelerini gerçek aramaya bağla.
                      if ((location.pathname || '').includes('/arama_ve_ke_fet/')) {
                        const input = document.getElementById('search-input');
                        const shell = input && input.closest('section');
                        if (shell && window.Android) {
                          const panel = document.createElement('section');
                          panel.id='xmusic-live-search';
                          panel.setAttribute('aria-label','Arama sonuçları');
                          panel.style.cssText='display:none;margin:8px 16px 18px;padding:12px;background:#181c1f;border-radius:16px;color:#e0e3e7;';
                          shell.insertAdjacentElement('afterend',panel);
                          const browse=panel.nextElementSibling;
                          shell.style.top=Math.ceil(document.querySelector('header')?.getBoundingClientRect().height||64)+'px';
                          const nav=document.querySelector('nav');
                          if(nav)nav.querySelectorAll('a[data-path]').forEach(function(a){
                            const active=a.dataset.path==='kesfet-ara';
                            a.classList.toggle('text-primary',active);
                            a.classList.toggle('font-bold',active);
                            a.classList.toggle('text-on-surface-variant',!active);
                            if(active)a.setAttribute('aria-current','page');else a.removeAttribute('aria-current');
                          });
                          const labels={all:'Tümü',songs:'Şarkılar',artists:'Sanatçılar',albums:'Albümler'};
                          let state={filter:'all',view:'search',id:'',name:''};
                          let history=[],generation=0,requestId=0,debounce;
                          const pending=new Map();
                          function owned(button,run){
                            button.type='button';button.dataset.xmOwned='true';button.__xmOwnedRun=run;
                            delete button.dataset.xmBlocked;
                          }
                          function button(text,run){
                            const b=document.createElement('button');b.textContent=text;
                            b.style.cssText='padding:9px 12px;border:1px solid #3c4a3c;border-radius:18px;color:#4efd7d;font-size:13px;';
                            owned(b,run);return b;
                          }
                          const recentItems=document.getElementById('history-items-wrapper');
                          const clearRecent=document.getElementById('clear-all-history-btn');
                          let recentSearches=[],recentTimer,lastCountedQuery='';
                          function editRecent(action,query){
                            try {
                              const result=JSON.parse(window.AndroidActions.searchHistory(action,query||''));
                              if(!Array.isArray(result))throw new Error('Geçmiş kaydedilemedi');
                              recentSearches=result;drawRecent();
                            }catch(_){
                              if(action!=='read')window.Android.showToast('Arama geçmişi kaydedilemedi.');
                            }
                          }
                          function rememberQuery(){
                            if(state.view!=='search')return;
                            const query=input.value.replace(/\s+/g,' ').trim();
                            if(query.length>=2&&query.length<=100){
                              editRecent('add',query);
                              const key=query.toLocaleLowerCase('tr');
                              if(key!==lastCountedQuery){
                                window.AndroidActions.recordSearch(query);lastCountedQuery=key;
                                if(window.__xmusicRefreshTopSearches)window.__xmusicRefreshTopSearches();
                              }
                            }
                          }
                          function drawRecent(){
                            if(!recentItems)return;
                            recentItems.replaceChildren();
                            if(clearRecent)clearRecent.style.display=recentSearches.length?'':'none';
                            if(!recentSearches.length){
                              const empty=document.createElement('span');
                              empty.textContent='Henüz arama yapmadın.';
                              empty.className='font-body-sm text-body-sm text-on-surface-variant py-2';
                              recentItems.appendChild(empty);return;
                            }
                            recentSearches.forEach(function(query){
                              const chip=document.createElement('div');
                              chip.className='history-chip flex items-center gap-1.5 pl-3.5 pr-1.5 py-1.5 rounded-full bg-surface-container-low text-on-surface shadow-sm';
                              chip.style.maxWidth='100%';
                              const search=button(query,function(){
                                state={filter:'all',view:'search',id:'',name:''};history=[];
                                updateChips();input.value=query;
                                input.dispatchEvent(new Event('input',{bubbles:true}));input.focus();
                              });
                              search.setAttribute('aria-label',query+' yeniden ara');search.title=query;
                              search.style.cssText='padding:2px 0;color:inherit;text-align:left;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;';
                              const remove=button('×',function(){editRecent('remove',query);});
                              remove.setAttribute('aria-label','Kaldır '+query);
                              remove.style.cssText='flex-shrink:0;width:28px;height:28px;color:#bbcbb8;border-radius:50%;font-size:22px;';
                              chip.append(search,remove);recentItems.appendChild(chip);
                            });
                          }
                          if(clearRecent)owned(clearRecent,function(){editRecent('clear','');});
                          editRecent('read','');
                          input.addEventListener('keydown',function(event){
                            if(event.key==='Enter'){event.preventDefault();rememberQuery();input.blur();}
                          });
                          input.addEventListener('blur',rememberQuery);
                          function updateChips(){
                            document.querySelectorAll('.search-filter-chip').forEach(function(chip){
                              const selected=chip.dataset.filter===state.filter;
                              chip.setAttribute('aria-pressed',String(selected));
                              chip.classList.toggle('bg-primary',selected);chip.classList.toggle('text-on-primary',selected);
                              chip.classList.toggle('shadow-sm',selected);
                              chip.classList.toggle('bg-surface-container-high',!selected);
                              chip.classList.toggle('text-on-surface-variant',!selected);
                            });
                          }
                          function selectFilter(filter){
                            state={filter:filter,view:'search',id:'',name:''};history=[];
                            updateChips();runSearch();
                          }
                          document.querySelectorAll('.search-filter-chip').forEach(function(chip){
                            const filter=chip.dataset.filter;
                            if(labels[filter])owned(chip,function(){selectFilter(filter);});
                            else if(filter==='playlists'||filter==='podcasts')owned(chip,function(){
                              window.Android.navigateTo(filter==='playlists'?'kitapl_m':'podcast_merkezi');
                            });
                          });
                          function enter(view,id,name){
                            rememberQuery();
                            history.push(Object.assign({},state));
                            state=Object.assign({},state,{view:view,id:id||'',name:name||''});
                            runSearch();
                          }
                          function renderFrame(){
                            panel.replaceChildren();
                            if(history.length)panel.appendChild(button('← Geri',function(){
                              state=history.pop();updateChips();runSearch();
                            }));
                            const heading=document.createElement('h2');
                            heading.textContent=state.view==='search'?'Arama sonuçları':state.view==='versions'?'Dinlenecek kaydı seç':state.name;
                            heading.style.cssText='font-weight:700;font-size:17px;margin:8px 0 12px;';
                            panel.appendChild(heading);
                          }
                          function requestCatalog(kind,id,container,epoch){
                            const request=++requestId;
                            pending.set(request,{kind:kind,container:container,epoch:epoch});
                            window.Android.searchAppleCatalog(input.value.trim(),id,kind,request);
                            setTimeout(function(){
                              const entry=pending.get(request);
                              if(entry && entry.epoch===generation){container.textContent='Arama yanıt vermedi. Yeniden dene.';pending.delete(request);}
                            },16000);
                          }
                          function catalogRow(item,kind){
                            const row=document.createElement('button');
                            row.style.cssText='display:flex;gap:12px;align-items:center;text-align:left;width:100%;padding:12px 0;color:#e0e3e7;border-bottom:1px solid #313539;';
                            if(item.artwork && item.artwork.startsWith('https://')){
                              const image=document.createElement('img');image.src=item.artwork;image.alt='';image.loading='lazy';
                              image.style.cssText='width:48px;height:48px;flex-shrink:0;object-fit:cover;border-radius:9px;';
                              row.appendChild(image);
                            }
                            const text=document.createElement('div');text.style.minWidth='0';
                            const title=document.createElement('strong');title.textContent=item.title||'İsimsiz';
                            const sub=document.createElement('div');
                            sub.textContent=(kind==='albums'?(item.category||'Albüm')+' · ':'')+(item.subtitle||'');
                            sub.style.cssText='font-size:13px;color:#b4c1c9;margin-top:4px;';
                            text.append(title,sub);row.appendChild(text);
                            owned(row,function(){
                              if(kind==='artists')enter('albums',item.id,item.title);
                              else if(kind==='albums')enter('songs',item.id,item.title);
                              else enter('versions','',((item.subtitle||'')+' '+(item.title||'')).trim());
                            });
                            return row;
                          }
                          window.__xmusicAppleResult=function(id,payload){
                            const entry=pending.get(id);pending.delete(id);
                            if(!entry || entry.epoch!==generation)return;
                            const target=entry.container;target.replaceChildren();
                            if(payload.error){target.textContent='Aramaya ulaşılamadı. İnternet bağlantısını kontrol edip yeniden dene.';return;}
                            const items=payload.items||[];
                            if(!items.length){target.textContent='Sonuç bulunamadı.';return;}
                            let release='Tümü';
                            const rows=document.createElement('div');
                            function draw(){
                              rows.replaceChildren();
                              const visible=items.filter(function(item){return release==='Tümü'||item.category===release;});
                              const preview=state.view==='search'&&state.filter==='all';
                              visible.slice(0,preview?6:visible.length).forEach(function(item){rows.appendChild(catalogRow(item,entry.kind));});
                              if(!visible.length)rows.textContent='Bu türde kayıt bulunamadı.';
                              if(preview && visible.length>6)rows.appendChild(button('Tümünü gör',function(){selectFilter(entry.kind);}));
                            }
                            if(entry.kind==='albums'&&state.view==='albums'){
                              const filters=document.createElement('div');
                              filters.style.cssText='display:flex;gap:8px;flex-wrap:wrap;margin:8px 0;';
                              ['Tümü','Albüm','EP','Single'].forEach(function(category){
                                const count=items.filter(function(item){return category==='Tümü'||item.category===category;}).length;
                                const chip=button(category+' ('+count+')',function(){
                                  release=category;
                                  Array.from(filters.children).forEach(function(b){b.setAttribute('aria-pressed',String(b===chip));b.style.background=b===chip?'#36584d':'transparent';});
                                  draw();
                                });
                                chip.setAttribute('aria-pressed',String(category===release));
                                chip.style.background=category===release?'#36584d':'transparent';filters.appendChild(chip);
                              });target.appendChild(filters);
                            }
                            target.appendChild(rows);draw();
                          };
                          window.__xmusicExperimentalResult=function(id,payload){
                            const entry=pending.get(id);pending.delete(id);
                            if(!entry || entry.epoch!==generation || state.view!=='versions')return;
                            const target=entry.container;target.replaceChildren();
                            if(payload.error){target.textContent=payload.error;return;}
                            const items=payload.items||[];
                            if(!items.length){target.textContent='Dinlenebilir kayıt bulunamadı.';return;}
                            const hint=document.createElement('p');hint.textContent='Dinlemek istediğin sürümü ve yükleyeni kontrol ederek seç.';
                            hint.style.cssText='font-size:12px;color:#b4c1c9;margin-bottom:8px;';target.appendChild(hint);
                            items.forEach(function(item){
                              const row=catalogRow({title:item.title,subtitle:item.artist,artwork:'https://i.ytimg.com/vi/'+item.id+'/mqdefault.jpg'},'songs');
                              owned(row,function(){window.Android.playExperimentalSource(item.id,item.title||'',item.artist||'');});
                              target.appendChild(row);
                            });
                            if(payload.limit===10&&items.length===10)target.appendChild(button('Daha fazla sonuç',function(){
                              target.textContent='Aranıyor…';
                              const request=++requestId;pending.set(request,{container:target,epoch:generation});
                              window.Android.searchExperimentalSourceMore(state.name,request,30);
                            }));
                          };
                          function runSearch(){
                            clearTimeout(debounce);clearTimeout(recentTimer);generation++;pending.clear();
                            const query=input.value.trim();
                            panel.style.display=query?'block':'none';
                            if(browse)browse.style.display=query?'none':'';
                            if(!query){panel.replaceChildren();return;}
                            renderFrame();
                            const epoch=generation;
                            const body=document.createElement('div');body.textContent='Aranıyor…';panel.appendChild(body);
                            debounce=setTimeout(function(){
                              if(epoch!==generation)return;
                              body.replaceChildren();
                              // Yazım durakladığında son sorguyu kaydet; hızlı ara harfleri kaydetme.
                              if(state.view==='search')recentTimer=setTimeout(function(){
                                if(epoch===generation)rememberQuery();
                              },1200);
                              if(state.view==='versions'){
                                body.textContent='Kayıtlar aranıyor…';
                                const request=++requestId;pending.set(request,{container:body,epoch:epoch});
                                window.Android.searchExperimentalSource(state.name,request);return;
                              }
                              const kinds=state.view==='search'?(state.filter==='all'?['artists','albums','songs']:[state.filter]):[state.view];
                              kinds.forEach(function(kind){
                                const group=document.createElement('div');group.style.marginBottom='16px';
                                if(kinds.length>1){
                                  const h=document.createElement('h3');h.textContent=labels[kind];h.style.cssText='font-weight:600;color:#4efd7d;margin-bottom:6px;';group.appendChild(h);
                                }
                                const results=document.createElement('div');results.textContent='Aranıyor…';group.appendChild(results);body.appendChild(group);
                                requestCatalog(kind,state.view==='search'?'':state.id,results,epoch);
                              });
                            },350);
                          }
                          input.addEventListener('input',function(){
                            if(!input.value.trim())lastCountedQuery='';
                            state=Object.assign({},state,{view:'search',id:'',name:''});history=[];runSearch();
                          });
                          const clear=document.getElementById('search-clear-btn');
                          if(clear)owned(clear,function(){
                            input.value='';input.dispatchEvent(new Event('input',{bubbles:true}));input.focus();
                          });
                          updateChips();
                        }
                      }
                      if ((location.pathname || '').includes('/ayarlar_ve_profil_y_netimi/')) {
                        const main=document.querySelector('main');
                        if(main && !document.getElementById('xmusic-drive-connect')){
                          const btn=document.createElement('button');
                          btn.id='xmusic-drive-connect';btn.type='button';btn.textContent='Google Drive hesabını bağla';
                          btn.style.cssText='display:block;padding:12px;margin:12px;border:1px solid #62796f;border-radius:12px;color:#e0e3e7;';
                          btn.dataset.xmOwned='true';btn.__xmOwnedRun=function(){if(window.Android)window.Android.connectDrive();};
                          const upload=document.createElement('button');
                          upload.type='button';upload.textContent='Drive’a ses dosyası yükle';upload.style.cssText=btn.style.cssText;
                          upload.dataset.xmOwned='true';upload.__xmOwnedRun=function(){if(window.Android)window.Android.chooseDriveUpload();};
                          const library=document.createElement('button');
                          library.type='button';library.textContent='Drive müzik arşivini aç';library.style.cssText=btn.style.cssText;
                          library.dataset.xmOwned='true';library.__xmOwnedRun=function(){if(window.Android)window.Android.openDriveLibrary();};
                          const queue=document.createElement('button');queue.type='button';queue.textContent='Drive yükleme kuyruğu';queue.style.cssText=btn.style.cssText;
                          queue.dataset.xmOwned='true';queue.__xmOwnedRun=function(){if(window.Android)window.Android.showDriveUploadQueue();};
                          const automatic=document.createElement('button');automatic.type='button';automatic.textContent='Otomatik Drive arşivini aç / kapat';automatic.style.cssText=btn.style.cssText;
                          automatic.dataset.xmOwned='true';automatic.__xmOwnedRun=function(){if(window.Android)window.Android.toggleAutomaticArchive();};
                          main.prepend(automatic);main.prepend(queue);main.prepend(library);main.prepend(upload);main.prepend(btn);
                        }
                      }
                      // Yalnız gerçek arka plan işlerine bağlı ağ tercihi etkin.
                      if ((location.pathname || '').includes('/ayarlar_ve_profil_y_netimi/')) {
                        document.querySelectorAll('input[type="checkbox"]').forEach(function(box, index) {
                          if(index===2 && window.Android){
                            box.checked=window.Android.getDownloadWifiOnly();
                            box.addEventListener('change',function(){window.Android.setDownloadWifiOnly(box.checked);});
                          } else {
                            box.checked=false;box.disabled=true;box.title='Bu özellik henüz hazır değil';
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
        appActions = AppActionsInterface(this, webView)
        webView.addJavascriptInterface(appActions, "AndroidActions")
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            if (!isDestroyed) {
                try {
                    val controller = future.get()
                    mediaController = controller
                    webAppInterface.setMediaController(controller)
                    appActions.controller = controller
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
        appActions.controller = null
        appWebView.removeJavascriptInterface("AndroidActions")
        appWebView.removeJavascriptInterface("Android")
        appWebView.destroy()
        super.onDestroy()
    }
}
