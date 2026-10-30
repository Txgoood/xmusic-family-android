(function () {
  'use strict';
  if (window.__xmusicAppActions) return;
  window.__xmusicAppActions = true;
  const A = window.AndroidActions, N = window.Android;
  if (!A || !N) return;
  const page = location.pathname.split('/').slice(-2)[0];
  let state = {}, lastQueue = '', timerMinutes = 45;
  let library;
  try { library = JSON.parse(A.loadLibrary() || '{}'); } catch (_) { library = {}; }
  library.favorites ||= []; library.playlists ||= []; library.local ||= []; library.history ||= []; library.feeds ||= [];
  const go = name => N.navigateTo(name);
  const create = (tag, value, cls) => { const n = document.createElement(tag); if (value !== undefined) n.textContent = value; if (cls) n.className = cls; return n; };
  const save = () => { const ok = A.saveLibrary(JSON.stringify(library)); if (!ok) N.showToast('Kayıt yapılamadı; depolama alanını kontrol et.'); return ok; };
  const style = create('style');
  style.textContent = '.xm-panel{background:#181c1f;border:1px solid #3c4a3c;border-radius:16px;padding:16px;margin:12px 0;color:#e0e3e7}.xm-row{display:flex;align-items:center;gap:8px;flex-wrap:wrap;padding:10px 0;border-bottom:1px solid #313539}.xm-btn{border:1px solid #62796f;border-radius:12px;padding:9px 12px;color:#9af0ba;background:#1c2023;cursor:pointer}.xm-input{width:100%;padding:12px;margin:10px 0;background:#101417;color:#e0e3e7;border:1px solid #62796f;border-radius:10px}.xm-overlay{position:fixed;inset:0;z-index:999;background:#000b;display:flex;align-items:center;justify-content:center;padding:18px}.xm-dialog{background:#181c1f;color:#e0e3e7;border:1px solid #62796f;border-radius:20px;padding:20px;width:100%;max-width:450px;max-height:85vh;overflow:auto}button[data-xm-blocked]{opacity:.5!important}';
  document.head.appendChild(style);
  function button(label, fn) { const b = create('button',label,'xm-btn'); b.type='button'; b.dataset.xmOwned='true'; b.__xmOwnedRun=fn; return b; }
  function modal(title, build) {
    document.getElementById('xm-dialog')?.remove();
    const overlay=create('div',undefined,'xm-overlay');overlay.id='xm-dialog';
    const panel=create('div',undefined,'xm-dialog'); panel.setAttribute('role','dialog');panel.setAttribute('aria-modal','true');panel.setAttribute('aria-label',title);
    const close=()=>overlay.remove();panel.append(create('h2',title));build(panel,close);panel.append(button('Kapat',close));overlay.append(panel);document.body.append(overlay);
    overlay.addEventListener('click',e=>{if(e.target===overlay)close();});panel.querySelector('input,button')?.focus();
  }
  function confirmAction(title, action) { modal(title,(p,close)=>p.append(button('Onayla',()=>{action();close();}))); }
  function editName(title, initial, callback) { modal(title,(p,close)=>{
    const input=create('input',undefined,'xm-input');input.maxLength=title.includes('RSS')?2048:80;input.value=initial;input.setAttribute('aria-label',title);p.append(input,button('Kaydet',()=>{const name=input.value.trim();if(!name){input.focus();return;}callback(name);close();}));
  }); }
  function trackData(s=state) { return {id:s.id,title:s.title,artist:s.artist,artwork:s.artwork||'',feed:s.feed||sessionStorage.getItem('xm-feed')||''}; }
  function favorite() {
    if(!state.id){N.showToast('Önce gerçek bir parça seç.');return;}
    const index=library.favorites.findIndex(t=>t.id===state.id);
    if(index<0)library.favorites.push(trackData());else library.favorites.splice(index,1);
    if(save()){N.showToast(index<0?'Favorilere eklendi':'Favorilerden çıkarıldı');updateFavorites();}
  }
  function updateFavorites() { document.querySelectorAll('[data-xm-action="favorite"]').forEach(b=>{
    const liked=!!state.id&&library.favorites.some(t=>t.id===state.id);b.setAttribute('aria-pressed',String(liked));const icon=b.querySelector('span');if(icon)icon.style.fontVariationSettings=liked?"'FILL' 1":"'FILL' 0";
  }); }
  function search(query='') { sessionStorage.setItem('xm-search',query);go('arama_ve_ke_fet'); }
  function playTrack(track, enqueue=false) {
    if(track.id.startsWith('local:')) A.playSavedTrack(JSON.stringify(track),enqueue);
    else if(track.id.startsWith('podcast:')) { if(track.feed){sessionStorage.setItem('xm-feed',track.feed);go('podcast_program_ve_b_l_m_detay');}else N.showToast('Podcast kaynağını aç.'); }
    else if(track.id.startsWith('audius:')) N.resolveAudiusTrack(track.id.slice(7),enqueue);
    else search(track.title+' '+track.artist);
  }
  function newPlaylist() {editName('Yeni çalma listesi','',name=>{
    const id=Date.now().toString(36)+'-'+Math.random().toString(36).slice(2,8);
    library.playlists.push({id,name,tracks:[]});if(save()){sessionStorage.setItem('xm-playlist',id);go('alma_listesi_y_netimi');}
  });}
  function addToPlaylist(track=trackData()) {
    if(!track.id){N.showToast('Önce gerçek bir parça seç.');return;}
    modal('Çalma listesine ekle',(p,close)=>{
      library.playlists.forEach(list=>p.append(button(list.name,()=>{if(!list.tracks.some(t=>t.id===track.id))list.tracks.push(track);if(save()){N.showToast('Listeye eklendi');close();}})));
      p.append(button('Yeni liste oluştur',()=>{close();editName('Liste adı','',name=>{library.playlists.push({id:Date.now().toString(36),name,tracks:[track]});save();});}));
    });
  }
  function more() {modal('Parça işlemleri',(p,close)=>{
    p.append(button('Favori ekle / kaldır',()=>{favorite();close();}),button('Çalma listesine ekle',()=>{close();addToPlaylist();}),button('Oynatma sırasını aç',()=>go('oynatma_s_ras_queue')),button('Uyku zamanlayıcısı',()=>go('uyku_zamanlay_c_s')));
  });}
  function trackRow(track, extra) {
    const row=create('div',undefined,'xm-row'),label=create('div');label.style.flex='1';label.append(create('strong',track.title||'İsimsiz'),create('p',track.artist||''));row.append(label,button('Dinle',()=>playTrack(track)));
    if(extra)extra(row);return row;
  }
  function content(title) {
    const main=document.querySelector('main');if(!main)return null;
    main.replaceChildren();const panel=create('section',undefined,'xm-panel');panel.style.margin='16px';panel.append(create('h2',title));main.append(panel);return panel;
  }
  function renderLibrary() {
    const panel=content('Kitaplığım');if(!panel)return;
    panel.append(create('p','Bu cihazda kayıtlı içerikler'),button('Yeni Çalma Listesi Oluştur',newPlaylist),button('Yerel Dosyaları Aç',()=>A.openLocalAudio()));
    const input=create('input',undefined,'xm-input');input.placeholder='Kitaplıkta ara';input.setAttribute('aria-label','Kitaplıkta ara');panel.append(input);
    const listArea=create('div');panel.append(listArea);
    function draw(){listArea.replaceChildren();const q=input.value.toLocaleLowerCase('tr');
      [['Favoriler',library.favorites],['Yerel Dosyalar',library.local],['Dinleme Geçmişi',library.history]].forEach(([title,tracks])=>{
        listArea.append(create('h3',title));const filtered=tracks.filter(t=>(t.title+' '+t.artist).toLocaleLowerCase('tr').includes(q));
        if(!filtered.length)listArea.append(create('p','Kayıt yok.'));
        filtered.forEach(t=>listArea.append(trackRow(t,row=>row.append(button('Listeye Ekle',()=>addToPlaylist(t)),button('Kaldır',()=>{const i=tracks.findIndex(x=>x.id===t.id);if(i>=0)tracks.splice(i,1);if(save())draw();})))));
      });
      listArea.append(create('h3','Çalma Listeleri'));library.playlists.filter(l=>l.name.toLocaleLowerCase('tr').includes(q)).forEach(l=>listArea.append(button(l.name+' ('+l.tracks.length+')',()=>{sessionStorage.setItem('xm-playlist',l.id);go('alma_listesi_y_netimi');})));
    }input.addEventListener('input',draw);draw();
  }
  function renderPlaylist() {
    const id=sessionStorage.getItem('xm-playlist');const list=library.playlists.find(l=>l.id===id);const panel=content('Çalma Listesi');if(!panel)return;
    if(!list){panel.append(create('p','Örnek liste yerine kendi listeni oluştur.'),button('Yeni Liste',newPlaylist));return;}
    const name=create('input',undefined,'xm-input');name.value=list.name;name.maxLength=80;name.setAttribute('aria-label','Liste adı');panel.append(name,button('Kaydet',()=>{if(!name.value.trim())return;list.name=name.value.trim();if(save())N.showToast('Kaydedildi');}),button('Şarkı Ekle veya Ara',()=>search()),button('Çalan Parçayı Ekle',()=>{if(!state.id)return N.showToast('Önce bir parça seç.');if(!list.tracks.some(t=>t.id===state.id))list.tracks.push(trackData());if(save())renderPlaylist();}));
    if(!list.tracks.length)panel.append(create('p','Liste boş. Oynatıcıdaki seçeneklerden gerçek parçalar ekleyebilirsin.'));
    list.tracks.forEach((track,index)=>panel.append(trackRow(track,row=>{
      row.append(button('↑',()=>{if(index>0){[list.tracks[index-1],list.tracks[index]]=[list.tracks[index],list.tracks[index-1]];if(save())renderPlaylist();}}),button('↓',()=>{if(index<list.tracks.length-1){[list.tracks[index+1],list.tracks[index]]=[list.tracks[index],list.tracks[index+1]];if(save())renderPlaylist();}}),button('Kaldır',()=>{list.tracks.splice(index,1);if(save())renderPlaylist();}));
    })));
    panel.append(button('Çalma Listesini Sil',()=>confirmAction('Bu liste silinsin mi?',()=>{library.playlists=library.playlists.filter(l=>l.id!==id);if(save())go('kitapl_m');})));
  }
  function renderQueue() {
    const panel=content('Gerçek Oynatma Sırası');if(!panel)return;
    panel.append(button('Şarkı Ara / Ekle',()=>search()),button('Yerel Dosya Aç',()=>A.openLocalAudio()),button('Temizle',()=>confirmAction('Oynatma duracak ve sıra temizlenecek.',()=>A.queueAction('clear',0,0))),button('Karışık: '+(state.shuffle?'Açık':'Kapalı'),()=>A.queueAction('shuffle',0,0)),button('Tekrar: '+(['Kapalı','Tek parça','Tümü'][state.repeat||0]),()=>A.queueAction('repeat',0,0)));
    if(!state.queue?.length)panel.append(create('p','Sıra boş. Arama sonuçlarında + Sıraya düğmesini kullan.'));
    (state.queue||[]).forEach((t,i)=>{const row=create('div',undefined,'xm-row');row.append(create('span',(i===state.index?'▶ ':'')+t.title+' — '+t.artist),button('Oynat',()=>A.queueAction('play',i,0)),button('↑',()=>A.queueAction('move',i,i-1)),button('↓',()=>A.queueAction('move',i,i+1)),button('Kaldır',()=>A.queueAction('remove',i,0)));panel.append(row);});
  }
  function profileState(){try{return JSON.parse(A.profileState()||'{}');}catch(_){return {};}}
  function profileForm(profile){
    modal(profile?'Profil Düzenle':'Yeni Yetişkin Profili',(p,close)=>{
      const name=create('input',undefined,'xm-input');name.maxLength=60;name.placeholder='Profil adı';name.setAttribute('aria-label','Profil adı');name.value=profile?.name||'';
      const current=create('input',undefined,'xm-input');current.type='password';current.inputMode='numeric';current.maxLength=8;current.placeholder='Mevcut PIN';current.setAttribute('aria-label','Mevcut PIN');
      const pin=create('input',undefined,'xm-input');pin.type='password';pin.inputMode='numeric';pin.maxLength=8;pin.placeholder='Yeni PIN (isteğe bağlı, 4–8 rakam)';pin.setAttribute('aria-label','Yeni PIN');
      p.append(name);if(profile?.hasPin)p.append(current);p.append(pin,create('p','Yeni PIN boş bırakılırsa bu yetişkin profili şifresiz açılır. Çocuk güvenliği henüz kullanıma açık değil.'));
      p.append(button('Kaydet',()=>{if(A.changeProfile(profile?'update':'create',profile?.id||'',name.value,current.value,pin.value)){close();go('aile_profili_se_imi');}else N.showToast('Kayıt başarısız. Adı, PIN biçimini ve mevcut PIN’i kontrol et.');}));
    });
  }
  function renderProfiles(){
    const data=profileState(), panel=content('Yerel Aile Profilleri');if(!panel)return;
    panel.append(create('p','Her yetişkin profilinin favorileri, listeleri ve geçmişi ayrıdır. Bulut eşitleme ve güvenli çocuk profili henüz hazır değil.'),button('Yeni Profil Ekle',()=>profileForm()));
    (data.profiles||[]).forEach(profile=>{
      const row=create('div',undefined,'xm-row');row.append(create('strong',profile.name+(profile.id===data.active?' · Aktif':'')));
      row.append(button('Seç',()=>{
        if(profile.id===data.active){go('ana_sayfa');return;}
        if(!profile.hasPin){if(A.changeProfile('select',profile.id,'','',''))go('ana_sayfa');return;}
        modal('Profil PIN’i',(p,close)=>{const input=create('input',undefined,'xm-input');input.type='password';input.inputMode='numeric';input.maxLength=8;input.setAttribute('aria-label','PIN');p.append(input,button('Aç',()=>{if(A.changeProfile('select',profile.id,'',input.value,'')){close();go('ana_sayfa');}else N.showToast('PIN yanlış veya deneme sınırına ulaşıldı. Beş hatada 1 dakika bekle.');}));});
      }),button('Düzenle',()=>profileForm(profile)));
      if(profile.id!==data.active)row.append(button('Sil',()=>modal('Profili Sil',(p,close)=>{const pin=create('input',undefined,'xm-input');pin.type='password';pin.inputMode='numeric';pin.setAttribute('aria-label','PIN');if(profile.hasPin)p.append(pin);p.append(create('p','Profil, yerel kitaplığı ve indirdiği podcast dosyaları silinir. Bu işlem için onay ver.'),button('Onayla',()=>{if(A.changeProfile('delete',profile.id,'',pin.value,'')){close();renderProfiles();}else N.showToast('Profil silinemedi; PIN’i kontrol et.');}));})));
      panel.append(row);
    });
  }
  let rssRequest=0, downloadSnapshot='';
  let podcastRequest=0, podcastQuery='', podcastTimer;
  function openFeed(feed){
    sessionStorage.setItem('xm-feed',feed.url);
    sessionStorage.setItem('xm-feed-info',JSON.stringify(feed));
    go('podcast_program_ve_b_l_m_detay');
  }
  function toggleFeed(feed,control){
    const previous=library.feeds.slice();
    const index=library.feeds.findIndex(f=>f.url===feed.url);
    if(index<0)library.feeds.push(feed);else library.feeds.splice(index,1);
    if(!save()){library.feeds=previous;return;}
    control.textContent=index<0?'Takibi Bırak':'Takip Et';
  }
  function renderPodcasts() {
    const main=document.querySelector('main');if(!main)return;
    // Orijinal başlığı koru; demo programların yerine gerçek arama ve takipler gelsin.
    const host=main.firstElementChild||main;
    document.getElementById('xm-podcast-panel')?.remove();
    Array.from(host.children).slice(1).forEach(n=>{n.style.display='none';});
    const chips=document.getElementById('podcast-chips');if(chips)chips.style.display='none';
    const panel=create('section',undefined,'xm-panel');panel.id='xm-podcast-panel';panel.style.margin='0 0 16px';
    host.appendChild(panel);
    const nav=document.querySelector('nav');if(nav)nav.querySelectorAll('a[data-path]').forEach(a=>{
      const active=a.dataset.path==='podcast';
      a.classList.toggle('text-primary',active);a.classList.toggle('font-bold',active);
      a.classList.toggle('text-on-surface-variant',!active);
      if(active)a.setAttribute('aria-current','page');else a.removeAttribute('aria-current');
    });
    panel.append(create('h2','Podcast ara'));
    const form=create('form');
    const input=create('input',undefined,'xm-input');
    input.id='xm-podcast-search';input.type='search';input.maxLength=100;
    input.placeholder='Program adı veya yayıncı ara…';input.setAttribute('aria-label','Podcast ara');input.value=podcastQuery;
    form.append(input,button('Ara',()=>searchNow()),button('Aramayı Temizle',()=>{
      input.value='';input.dispatchEvent(new Event('input',{bubbles:true}));input.focus();
    }));panel.appendChild(form);
    const results=create('div','Bir program adı veya yayıncı yazarak ara.');results.id='xm-podcast-results';results.setAttribute('aria-live','polite');
    panel.append(results);
    let currentQuery='';
    function searchNow(){
      clearTimeout(podcastTimer);
      const query=input.value.trim();podcastQuery=query;
      const id=++podcastRequest;currentQuery=query;
      results.replaceChildren();
      if(query.length<2){results.textContent=query?'En az iki karakter yaz.':'Bir program adı veya yayıncı yazarak ara.';return;}
      results.textContent='Podcast’ler aranıyor…';
      A.searchPodcasts(query,id);
    }
    form.addEventListener('submit',e=>{e.preventDefault();searchNow();});
    input.addEventListener('input',()=>{
      clearTimeout(podcastTimer);++podcastRequest;podcastQuery=input.value.trim();
      if(podcastQuery.length<2){results.textContent=podcastQuery?'En az iki karakter yaz.':'Bir program adı veya yayıncı yazarak ara.';return;}
      results.textContent='Podcast’ler aranıyor…';
      podcastTimer=setTimeout(searchNow,650);
    });
    function drawFollowed(){
      following.replaceChildren();
      if(!library.feeds.length){following.append(create('p','Henüz takip ettiğin program yok.'));return;}
      library.feeds.forEach(feed=>{
        const row=create('div',undefined,'xm-row');
        const unfollow=button('Takibi Bırak',()=>confirmAction('Podcast takibi kaldırılsın mı?',()=>{
          toggleFeed(feed,unfollow);drawFollowed();
        }));
        row.append(button(feed.title,()=>openFeed(feed)),unfollow);following.appendChild(row);
      });
    }
    window.__xmusicPodcastSearchResult=(id,payload)=>{
      if(id!==podcastRequest||currentQuery!==input.value.trim())return;
      results.replaceChildren();
      if(payload.error){results.append(create('p',payload.error),button('Tekrar Dene',searchNow));return;}
      if(!payload.items?.length){results.textContent='Açık RSS kaynağı olan program bulunamadı.';return;}
      payload.items.forEach(item=>{
        const feed={url:item.feed,title:item.title,artist:item.artist,artwork:item.artwork};
        const row=create('div',undefined,'xm-row');row.style.alignItems='flex-start';
        if(item.artwork?.startsWith('https://')){
          const image=create('img');image.src=item.artwork;image.alt='';image.loading='lazy';
          image.style.cssText='width:64px;height:64px;border-radius:12px;object-fit:cover;flex-shrink:0;';row.appendChild(image);
        }
        const info=create('div');info.style.cssText='flex:1;min-width:160px;';
        info.append(create('strong',item.title||'Podcast'),create('p',item.artist||''),
          create('small',[item.genre,item.episodeCount>0?item.episodeCount+' bölüm':''].filter(Boolean).join(' · ')));
        const controls=create('div',undefined,'xm-row');controls.style.border='0';
        const follow=button(library.feeds.some(f=>f.url===feed.url)?'Takibi Bırak':'Takip Et',()=>{
          toggleFeed(feed,follow);drawFollowed();
        });
        controls.append(button('Bölümleri Aç',()=>openFeed(feed)),follow);
        if(item.url)controls.append(button('Apple Podcasts’te Gör',()=>A.openPodcastPage(item.url)));
        info.appendChild(controls);row.appendChild(info);results.appendChild(row);
      });
    };
    panel.append(create('h3','Takip Ettiklerim'));
    const following=create('div');following.id='xm-podcast-following';panel.appendChild(following);drawFollowed();
    panel.append(button('RSS Kaynağı Ekle',()=>editName('HTTPS RSS adresi','',address=>{
      try {const url=new URL(address);if(url.protocol!=='https:')throw Error();}
      catch(_){N.showToast('Geçerli bir HTTPS RSS adresi gir.');return;}
      const feed={url:address,title:address};
      if(!library.feeds.some(f=>f.url===address))library.feeds.push(feed);
      if(save())openFeed(feed);
    })),button('İndirilen Bölümler',()=>go('i_ndirme_y_neticisi')));
    if(podcastQuery.length>=2)searchNow();
  }
  function renderFeed(){
    const panel=content('Podcast Bölümleri');if(!panel)return;
    const address=sessionStorage.getItem('xm-feed');
    panel.append(button('Podcast Kitaplığı',()=>go('podcast_merkezi')));
    if(!address){panel.append(create('p','Önce gerçek bir RSS programı seç.'));return;}
    let info={url:address,title:address};
    try{const saved=JSON.parse(sessionStorage.getItem('xm-feed-info')||'null');if(saved?.url===address)info=saved;}catch(_){}
    if(info.artwork?.startsWith('https://')){
      const image=create('img');image.src=info.artwork;image.alt='';
      image.style.cssText='width:96px;height:96px;object-fit:cover;border-radius:16px;margin:12px 0;';panel.appendChild(image);
    }
    if(info.title!==address)panel.append(create('h3',info.title));
    if(info.artist)panel.append(create('p',info.artist));
    const follow=button(library.feeds.some(f=>f.url===address)?'Takibi Bırak':'Takip Et',()=>toggleFeed(info,follow));
    panel.appendChild(follow);
    const results=create('div','RSS yükleniyor…');panel.append(results);
    const id=++rssRequest;
    window.__xmusicRssResult=(request,payload)=>{
      if(request!==rssRequest||(payload.feed&&payload.feed!==address))return;
      results.replaceChildren();
      if(payload.error){results.append(create('p',payload.error),button('Tekrar Dene',renderFeed));return;}
      info.title=payload.title||info.title;
      const feed=library.feeds.find(f=>f.url===address);if(feed&&feed.title!==payload.title){feed.title=payload.title;save();}
      results.append(create('h3',payload.title||'Podcast'));
      if(!payload.items?.length)results.append(create('p','Desteklenen HTTPS ses bölümü bulunamadı.'));
      (payload.items||[]).forEach(t=>{const row=create('div',undefined,'xm-row');row.append(create('strong',t.title),button('Oynat',()=>A.playPodcast(t.id,false)),button('+ Sıraya',()=>A.playPodcast(t.id,true)),button('İndir',()=>A.downloadPodcast(t.id)),button('Listeye Ekle',()=>addToPlaylist(t)));results.append(row);});
    };
    A.loadRss(address,id);
  }
  function renderDownloads(){
    let rows=[];try{rows=JSON.parse(A.listDownloads()||'[]');}catch(_){}
    const snapshot=JSON.stringify(rows);if(snapshot===downloadSnapshot&&document.getElementById('xm-downloads'))return;downloadSnapshot=snapshot;
    const panel=content('İndirilen Podcast Bölümleri');if(!panel)return;panel.id='xm-downloads';
    panel.append(create('p','Yayıncının RSS ses bağlantılarından yapılan gerçek indirmeler. Müzik katalogları indirme kaynağı değildir.'),button('Podcast Ekle',()=>go('podcast_merkezi')),button('Yenile',()=>{downloadSnapshot='';renderDownloads();}));
    if(!rows.length)panel.append(create('p','Henüz indirme yok.'));
    rows.forEach(t=>{const row=create('div',undefined,'xm-row');const labels={1:'Sırada',2:'İndiriliyor',4:'Ağ bekleniyor',8:'Tamamlandı',16:'İndirme başarısız'};
      const progress=t.total>0?' · %'+Math.floor(100*t.bytes/t.total):'';
      row.append(create('strong',t.title),create('p',(labels[t.status]||'Bekleniyor')+progress));
      if(t.status===8)row.append(button('Çevrimdışı Oynat',()=>A.playDownload(t.id)));
      row.append(button(t.status===8?'Dosyayı Sil':'İptal / Kaldır',()=>confirmAction('İndirme ve dosyası kaldırılsın mı?',()=>{A.removeDownload(t.id);setTimeout(renderDownloads,500);})));panel.append(row);
    });
  }
  function block(b, reason) {b.dataset.xmBlocked=reason;b.disabled=true;b.title=reason;b.setAttribute('aria-disabled','true');const help=create('small',' — '+reason);help.style.fontSize='10px';b.append(help);}
  const routes={
    'Profil ve Aile Alanı':'aile_profili_se_imi','Depolama Ayarları':'ayarlar_ve_profil_y_netimi',
    'Dinleme Geçmişi':'kitapl_m','İndirilenleri Yönet':'i_ndirme_y_neticisi','Sırayı Yönet':'oynatma_s_ras_queue',
    'Çalma Sırası':'oynatma_s_ras_queue','Podcast Tercihleri':'ayarlar_ve_profil_y_netimi',
    'Kitaplıkta Ara':'kitapl_m','Yeni Çalma Listesi Oluştur':'newPlaylist'
  };
  function bind(b, action, fn) {b.dataset.xmAction=action;b.__xmRun=fn;}
  // Var olan arama ve filtre demo etkileşimleri korunur; gerçek katalog paneli ayrı işlenir.
  const visualIds=new Set(['search-clear-btn','clear-all-history-btn','viewListBtn','viewGridBtn','toggleDescBtn','role-adult','role-kids','pin-cancel-btn','close-pin-modal','toggle-sim','tab-offline','tab-playback','tab-download','tab-storage','tab-empty']);
  document.querySelectorAll('button,a').forEach(b=>{
    const label=(b.getAttribute('aria-label')||b.textContent).trim().replace(/\s+/g,' ');
    if(b.matches('a[data-path]')){b.dataset.xmAction='navigation';return;}
    if(b.tagName==='A'){const href=b.getAttribute('href');if(href&&href!=='#')b.dataset.xmAction='link';else block(b,'Hedef bağlantı yok');return;}
    if(label==='Geri'||label==='Kapat'||label==='Vazgeç'||label==='İptal'){bind(b,'back',()=>N.goBack());return;}
    if(routes[label]){bind(b,'navigation',()=>routes[label]==='newPlaylist'?newPlaylist():go(routes[label]));return;}
    if(label==='Aramayı Temizle'){bind(b,'clearSearch',()=>{const i=document.getElementById('settingsSearch');if(i){i.value='';i.dispatchEvent(new Event('input',{bubbles:true}));}});return;}
    if(label==='tune'){bind(b,'settings',()=>go('ayarlar_ve_profil_y_netimi'));return;}
    if(/İndirilenlere Git|İndirilen Şarkıları Yönet/.test(label)){bind(b,'downloads',()=>go('i_ndirme_y_neticisi'));return;}
    if(label==='Paylaş'){bind(b,'share',()=>A.shareText(state.id?(state.title+' — '+state.artist):'XMusic Family'));return;}
    if(label==='Bildirimler'||/Bildirim Ayarları|Sanatçı Bildirimleri/.test(label)){bind(b,'notifications',()=>modal('Bildirimler',(p)=>p.append(create('p','Yeni bildirim yok. Medya kontrolleri bir parça çalarken Android tarafından gösterilir.'))));return;}
    if(/Oynat veya Duraklat|Oynat \/ Duraklat|Durdur \/ Oynat|Duraklat veya Çal|Oynat \/ Durdur/.test(label)){bind(b,'play',()=>N.togglePlayPause());return;}
    if(/Önceki Parça|Önceki Masal/.test(label)){bind(b,'previous',()=>N.previous());return;}
    if(/Sonraki Parça|Sonraki Masal/.test(label)){bind(b,'next',()=>N.next());return;}
    if(label==='Favorilere Ekle' && (b.closest('aside')||page==='imdi_al_yor'||page==='oynatma_s_ras_queue')){bind(b,'favorite',favorite);return;}
    if(/Karışık/.test(label)||b.id==='toggleShuffle'){bind(b,'shuffle',()=>A.queueAction('shuffle',0,0));return;}
    if(/Tekrar Modu|Tümünü Tekrar/.test(label)||b.id==='toggleRepeat'){bind(b,'repeat',()=>A.queueAction('repeat',0,0));return;}
    if(/15 [Ss]aniye/.test(label)){bind(b,'back15',()=>A.queueAction('back15',0,0));return;}
    if(/30 [Ss]aniye/.test(label)){bind(b,'forward30',()=>A.queueAction('forward30',0,0));return;}
    if(label==='Oynatma Hızı'){bind(b,'speed',()=>modal('Oynatma hızı',p=>[75,100,125,150,200].forEach(v=>p.append(button(v/100+'×',()=>A.queueAction('speed',0,v))))));return;}
    if(/Yerel Dosya/.test(label)){bind(b,'local',()=>A.openLocalAudio());return;}
    if(/Önbelleği Temizle|Temizle \(1.2 GB|1.2 GB Temizle/.test(label)){b.textContent='Web Önbelleğini Temizle';bind(b,'cache',()=>confirmAction('Web önbelleği temizlensin mi?',()=>A.clearWebCache()));return;}
    if(label==='Bağlı Cihaz'||/Cihaza Bağlan|Google Cast|Yansıt/.test(label)){b.textContent='Cihaz Ses Ayarları';bind(b,'audioSettings',()=>A.openSoundSettings());return;}
    if(/uyku sayacı|Uyku zamanlayıcısı/i.test(label)){bind(b,'sleepPage',()=>go('uyku_zamanlay_c_s'));return;}
    if(b.id==='submit-timer-btn'){bind(b,'timer',()=>{A.setSleepTimer(timerMinutes);});return;}
    if(b.id==='cancel-timer-btn'){bind(b,'cancelTimer',()=>A.setSleepTimer(0));return;}
    if(page==='uyku_zamanlay_c_s'&&/Dakika|Şarkı Bitiminde/.test(label)){bind(b,'timerOption',()=>{timerMinutes=label.includes('Şarkı Bitiminde')?-1:Number(label.match(/(\d+) Dakika/)[1]);const slider=document.getElementById('duration-slider');if(timerMinutes>0&&slider)slider.value=timerMinutes;const val=document.getElementById('custom-slider-val');if(val)val.textContent=timerMinutes<0?'Şarkı Sonu':timerMinutes+' dk';document.querySelectorAll('[data-minutes]').forEach(n=>n.setAttribute('aria-pressed',String(n===b)));N.showToast(timerMinutes<0?'Şarkı bitimi seçildi':'Seçildi: '+timerMinutes+' dakika');});return;}
    if(page==='imdi_al_yor'&&label==='Seçenekler'){bind(b,'more',more);return;}
    if(label==='Çalma Listesine Ekle'||label==='Listeye Ekle'){bind(b,'playlist',()=>addToPlaylist());return;}
    if(b.id==='search-mic-btn'){bind(b,'voice',()=>A.voiceSearch());return;}
    if(/Gizlilik/.test(label)){bind(b,'privacy',()=>modal('Yerel veriler',(p)=>p.append(create('p','Favoriler, listeler ve geçmiş bu cihazda saklanır. Arama sorguları seçili katalog sağlayıcısına gönderilir. Bulut senkronizasyonu henüz bağlı değil.'),button('Dinleme geçmişini sil',()=>confirmAction('Geçmiş silinsin mi?',()=>{library.history=[];save();})))));return;}
    if(/Geçmişi Sıfırla/.test(label)){bind(b,'resetHistory',()=>confirmAction('Dinleme geçmişi silinsin mi?',()=>{library.history=[];save();}));return;}
    if(/Profilleri Yönet|Yeni Profil|Yönetici PIN|manage_accounts Düzenle/.test(label)){bind(b,'profiles',()=>go('aile_profili_se_imi'));return;}
    if(/Düzenle|Çocuk Kilidini|Aile Planı|Kurulumu Tamamla|Daha Sonra Ayarla|PIN ile Çıkış|Profili Değiştir/.test(label)){block(b,'Profil ve PIN altyapısı tamamlanmadı');return;}
    if(page==='imdi_al_yor'&&label==='İndirildi'){b.textContent='Bölümü İndir';bind(b,'downloadPodcast',()=>{if(state.id?.startsWith('podcast:'))A.downloadPodcast(state.id);else N.showToast('Bu parça için indirme izni doğrulanmadı.');});return;}
    if(visualIds.has(b.id)){b.dataset.xmAction='existingUI';return;}
    if(/İndir|İndirildi|Hücresel|Wi-Fi|Çevrimdışı|Ses Kalitesi|Stüdyo Kalite/.test(label)||b.id==='offline-switch'){block(b,'Yetkili indirme / kalite kaynağı bağlı değil');return;}
    if(/Takip Et|Takip Ediliyor/.test(label)){block(b,'Gerçek sanatçı / podcast kaydı bağlı değil');return;}
    if(/Katıl|Davet|Aile Ortak|Aile Karışımı|Aile DJ/.test(label)){block(b,'Aile senkronizasyonu bağlı değil');return;}
    if(visualIds.has(b.id)||/^(Tümü|Şarkılar|Sanatçılar|Albümler|Çalma Listeleri|Podcast.ler|Pop|Rock|Rap & Hip-Hop|Elektronik|Caz|Klasik|Teknoloji|Haber & Gündem|Kişisel Gelişim)$/.test(label)||label.startsWith('Kaldır ')){b.dataset.xmAction='existingUI';return;}
    if(/Keşfet|Tümünü Gör|Tümü|Göster|Hemen Dinle|Oynat|Çal|Dinle|Başlat|Devam Et|Devamını|Sıraya Ekle|Radyo|Seçenek|seçenek|Menü|Diğer|Daha Fazla|Grafik|Sıralama|En Yeni|Yönet|Hi-Res albümleri|Anadolu Rock 70/.test(label)){
      bind(b,'discover',()=>search(extractQuery(b)));return;
    }
    if(label==='Müzik'||label==='Podcast'){bind(b,'navigation',()=>go(label==='Müzik'?'m_zik_merkezi':'podcast_merkezi'));return;}
    if(label.includes('Tekrar Dene')){bind(b,'retry',()=>N.togglePlayPause());return;}
    if(label.includes('Sorunu Bildir')){bind(b,'report',()=>A.shareText('XMusic Family — '+(state.error||'Sorun açıklamasını buraya ekleyin.')));return;}
    if(b.id==='clearQueueBtn'||label.includes('Kuyruğu Temizle')){bind(b,'clearQueue',()=>confirmAction('Sıra temizlensin mi?',()=>A.queueAction('clear',0,0)));return;}
    if(page==='i_lk_kurulum_ve_profil_olu_turma'||page==='aile_profili_se_imi'){block(b,'Profil altyapısı tamamlanmadı');return;}
    block(b,'Gerçek veri bağlantısı bekliyor');
  });
  function extractQuery(b) {
    const box=b.closest('section')||b.parentElement;
    const heading=box?.querySelector('h3,h2');const text=heading?.textContent?.trim()||'';
    return /Popüler|Öner|Keşfet|Son |Tüm|Kaldığın|Albüm|Müzik/.test(text)?'':text.slice(0,100);
  }
  document.addEventListener('click',e=>{
    const own=e.target.closest('[data-xm-owned]');if(own){e.preventDefault();e.stopImmediatePropagation();own.__xmOwnedRun();return;}
    if(e.target.closest('#xm-dialog'))return;
    const b=e.target.closest('button,a');if(!b)return;
    if(b.__xmRun){e.preventDefault();e.stopImmediatePropagation();b.__xmRun();}
  },true);
  // Yerel dosya seçildikten sonra kitaplık kalıcı olarak güncellenir.
  window.__xmusicLocalImported=t=>{if(!library.local.some(x=>x.id===t.id))library.local.push(t);if(save()&&page==='kitapl_m')renderLibrary();};
  window.addEventListener('xmusic:playback',e=>{
    state=e.detail;updateFavorites();
    if(state.playing&&state.id&&library.history[0]?.id!==state.id){library.history=[trackData(),...library.history.filter(t=>t.id!==state.id)].slice(0,100);save();}
    const key=JSON.stringify([state.queue,state.index,state.shuffle,state.repeat]);if(page==='oynatma_s_ras_queue'&&key!==lastQueue){lastQueue=key;renderQueue();}
    const sleep=document.getElementById('xm-sleep-status');if(sleep)sleep.textContent=state.sleepAtEnd?'Şarkı bitiminde duracak':state.sleepRemaining>0?'Kalan: '+Math.ceil(state.sleepRemaining/60000)+' dakika':'Zamanlayıcı kapalı';
    const count=document.getElementById('xmusic-queue-count');if(count)count.textContent='Sırada ('+(state.queue?.length||0)+')';
    const sleepLabel=document.getElementById('xmusic-sleep-label');if(sleepLabel)sleepLabel.textContent=state.sleepRemaining>0?Math.ceil(state.sleepRemaining/60000)+' dk uyku sayacı':'Uyku zamanlayıcısı';
  });
  if(page==='ocuk_modu_kids_safe'){const panel=content('Çocuk Modu');panel?.append(create('p','Güvenli içerik listesi ve ebeveyn çıkış denetimi tamamlanmadı. Bu ekran henüz çocuk kullanımına hazır değil.'),button('Profil Seçimine Dön',()=>go('aile_profili_se_imi')));}
  if(page==='aile_profili_se_imi')renderProfiles();
  if(page==='i_lk_kurulum_ve_profil_olu_turma'){content('Yeni Profil');profileForm();}
  const activeProfile=profileState();const activeName=activeProfile.profiles?.find(p=>p.id===activeProfile.active)?.name;
  if(activeName)document.querySelectorAll('header span').forEach(n=>{if(n.textContent.trim()==='Burak')n.textContent=activeName;});
  if(page==='podcast_merkezi')renderPodcasts();
  if(page==='podcast_program_ve_b_l_m_detay')renderFeed();
  if(page==='i_ndirme_y_neticisi'){renderDownloads();setInterval(renderDownloads,1500);}
  if(page==='kitapl_m')renderLibrary();
  if(page==='alma_listesi_y_netimi')renderPlaylist();
  if(page==='oynatma_s_ras_queue')renderQueue();
  if(page==='uyku_zamanlay_c_s'){
    const banner=document.getElementById('cancel-timer-btn')?.closest('.rounded-2xl');if(banner){banner.querySelectorAll('p,span').forEach(n=>{if(/25 dakika|Şu Anda Aktif|Bitiş saati/.test(n.textContent))n.textContent='';});}
    const panel=create('p','Zamanlayıcı kapalı','xm-panel');panel.id='xm-sleep-status';document.querySelector('main')?.prepend(panel);
    document.getElementById('duration-slider')?.addEventListener('input',e=>{timerMinutes=Number(e.target.value);});
    ['toggle-fade','toggle-screen'].forEach(id=>{const input=document.getElementById(id);if(input){input.checked=false;input.disabled=true;input.title='Bu ek seçenek henüz bağlı değil';}});
  }
  if(page==='arama_ve_ke_fet')setTimeout(function seedSearch(){if(!window.__xmusicActionsInstalled){setTimeout(seedSearch,50);return;}const query=sessionStorage.getItem('xm-search');if(query!==null){sessionStorage.removeItem('xm-search');const input=document.getElementById('search-input');if(input){input.value=query;input.dispatchEvent(new Event('input',{bubbles:true}));input.focus();}}},0);
  window.__xmusicActionsAudit=()=>Array.from(document.querySelectorAll('button,a')).filter(b=>!b.closest('#xmusic-live-search')).map(b=>({label:b.getAttribute('aria-label')||b.textContent.trim(),action:b.dataset.xmAction|| (b.dataset.xmOwned?'owned':null),blocked:b.dataset.xmBlocked||null}));
})();
