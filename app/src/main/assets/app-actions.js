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
    else if(track.id.startsWith('experimental:')||track.id.startsWith('drive-source:')) {
      const id=track.id.split(':')[1];N.playExperimentalSource(id,track.title||'',track.artist||'');
    }
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
  let libraryPlaylistTemplate=null;
  function renderLibrary() {
    const main=document.querySelector('main'),host=main?.firstElementChild;
    host?.querySelector('#xm-library-tracks')?.remove();host?.querySelector('#xm-library-search')?.remove();
    if(!host||host.children.length<8)return;
    const [top,filters,toolbar,pins,artists,playlists,local,shared]=Array.from(host.children);
    // Aile senkronizasyonu ve sanatçı takibi henüz gerçek veri sağlamıyor.
    const sync=top.querySelector('h1')?.nextElementSibling;if(sync)sync.style.display='none';
    artists.style.display='none';shared.style.display='none';
    if(pins.children[2])pins.children[2].style.display='none';
    const sort=toolbar.querySelector('#sortDropdownBtn');if(sort)sort.style.display='none';
    const listBtn=toolbar.querySelector('#viewListBtn'),gridBtn=toolbar.querySelector('#viewGridBtn');
    if(listBtn)listBtn.style.display='none';if(gridBtn)gridBtn.style.display='none';
    toolbar.style.display='none';
    function own(el,fn){
      if(!el)return;el.dataset.xmOwned='true';delete el.dataset.xmBlocked;el.removeAttribute('aria-disabled');el.disabled=false;el.querySelector('small')?.remove();el.__xmOwnedRun=fn;
    }
    function clickable(el,fn){
      if(!el)return;el.setAttribute('role','button');el.tabIndex=0;
      el.onclick=e=>{if(!e.target.closest('button'))fn();};
      el.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();fn();}};
    }
    const favoriteCard=pins.children[0],downloadCard=pins.children[1];
    const favoriteSub=favoriteCard?.querySelector('h2')?.parentElement?.nextElementSibling;
    if(favoriteSub)favoriteSub.textContent=library.favorites.length+' şarkı';
    const downloadSub=downloadCard?.querySelector('h2')?.nextElementSibling;
    let downloads=[];try{downloads=JSON.parse(A.listDownloads()||'[]');}catch(_){}
    if(downloadSub)downloadSub.textContent=downloads.filter(d=>d.status===8).length+' indirilmiş podcast bölümü';
    const localSub=local.querySelector('h4')?.nextElementSibling;
    if(localSub)localSub.textContent=library.local.length+' yerel kayıt';
    const playlistTitle=playlists.querySelector('h3'),playlistHeader=playlistTitle?.parentElement?.parentElement;
    // Template’in liste satırını kendi verilerimizle tekrar kullan.
    if(!libraryPlaylistTemplate)libraryPlaylistTemplate=playlists.children[1]?.cloneNode(true);
    const template=libraryPlaylistTemplate;
    Array.from(playlists.children).slice(1).forEach(n=>n.remove());
    const rows=create('div');rows.id='xm-library-tracks';rows.className='px-4 flex flex-col gap-2';
    pins.insertAdjacentElement('afterend',rows);
    const searchInput=create('input',undefined,'xm-input');searchInput.id='xm-library-search';searchInput.placeholder='Kitaplıkta ara';
    searchInput.setAttribute('aria-label','Kitaplıkta ara');searchInput.style.cssText='display:none;margin:4px 16px 12px;width:calc(100% - 32px);padding:12px;background:#1c2023;border-radius:14px;color:#e0e3e7;';
    top.insertAdjacentElement('afterend',searchInput);
    let filter='all',showFavorites=false;
    function draw(){
      if(favoriteSub)favoriteSub.textContent=library.favorites.length+' şarkı';
      if(localSub)localSub.textContent=library.local.length+' yerel kayıt';
      const q=searchInput.value.trim().toLocaleLowerCase('tr');
      pins.style.display=filter==='all'?'':'none';
      local.style.display=(filter==='all'||filter==='local')?'':'none';
      playlists.style.display=(filter==='all'||filter==='playlists')?'':'none';
      rows.replaceChildren();
      const lookup=t=>((t.title||'')+' '+(t.artist||'')).toLocaleLowerCase('tr').includes(q);
      const sections=[];
      if((filter==='all'&&(showFavorites||q))||filter==='favorites')sections.push(['Beğenilen Şarkılar',library.favorites]);
      if(filter==='all'||filter==='local')sections.push(['Yerel Kayıtlar',library.local]);
      if(filter==='all'&&(library.history.length||q))sections.push(['Dinleme Geçmişi',library.history]);
      if(filter==='podcasts')sections.push(['Takip Edilen Podcastler',library.feeds]);
      sections.forEach(([label,tracks])=>{
        const matching=tracks.filter(t=>lookup(t));
        if(!matching.length&&!q&&label!=='Beğenilen Şarkılar'&&filter!=='podcasts')return;
        const section=create('section',undefined,'flex flex-col gap-2 pt-3');
        section.append(create('h3',label,'font-headline-sm text-headline-sm text-on-surface'));
        if(!matching.length)section.append(create('p','Henüz kayıt yok.','text-on-surface-variant'));
        matching.forEach(t=>{
          if(label==='Takip Edilen Podcastler'){
            const row=button(t.title,()=>openFeed(t));row.className='w-full text-left p-3 rounded-xl bg-surface-container-low text-on-surface';section.append(row);return;
          }
          const row=trackRow(t,r=>r.append(button('Listeye Ekle',()=>addToPlaylist(t)),button('Kaldır',()=>{
            const index=tracks.findIndex(x=>x.id===t.id);if(index>=0)tracks.splice(index,1);if(save())draw();
          })));
          row.className='flex items-center gap-2 flex-wrap p-3 rounded-xl bg-surface-container-low';section.append(row);
        });
        rows.append(section);
      });
      Array.from(playlists.children).slice(1).forEach(n=>n.remove());
      const matches=library.playlists.filter(l=>(l.name||'').toLocaleLowerCase('tr').includes(q));
      const heading=playlists.firstElementChild;
      const badge=heading?.querySelector('span');if(badge)badge.textContent=matches.length+' Liste';
      if(!matches.length){const empty=create('p','Henüz çalma listesi yok.','text-on-surface-variant');playlists.append(empty);}
      matches.forEach(l=>{
        if(!template)return;
        const row=template.cloneNode(true),name=row.querySelector('p.font-headline-sm')||row.querySelector('p');
        if(name)name.textContent=l.name;
        const subtitle=name?.nextElementSibling;if(subtitle)subtitle.textContent=(l.tracks||[]).length+' parça';
        const image=row.querySelector('img');const artwork=(l.tracks||[]).find(t=>t.artwork?.startsWith('https://'))?.artwork;
        if(image){if(artwork)image.src=artwork;else image.removeAttribute('src');image.alt='';}
        row.querySelectorAll('.absolute').forEach(n=>n.style.display='none');
        const menu=row.querySelector('button');if(menu)own(menu,()=>{sessionStorage.setItem('xm-playlist',l.id);go('alma_listesi_y_netimi');});
        clickable(row,()=>{sessionStorage.setItem('xm-playlist',l.id);go('alma_listesi_y_netimi');});
        playlists.appendChild(row);
      });
    }
    clickable(favoriteCard,()=>{filter='favorites';showFavorites=true;draw();});
    own(favoriteCard?.querySelector('button'),()=>{filter='favorites';showFavorites=true;draw();});
    clickable(downloadCard,()=>go('i_ndirme_y_neticisi'));
    const localCard=local.firstElementChild;clickable(localCard,()=>{filter='local';draw();});
    own(local.querySelector('[aria-label="Yerel Dosyaları Aç"]'),()=>A.openLocalAudio());
    const chips=Array.from(filters.querySelectorAll('button'));
    const modes=['all','playlists','downloads','artists','albums','podcasts','local'];
    chips.forEach((chip,index)=>{
      const mode=modes[index];if(mode==='artists'||mode==='albums'){chip.style.display='none';return;}
      own(chip,()=>{
        if(mode==='downloads'){go('i_ndirme_y_neticisi');return;}
        filter=mode;showFavorites=false;
        chips.forEach((b,i)=>{const active=i===index;b.classList.toggle('bg-primary',active);b.classList.toggle('text-on-primary',active);b.classList.toggle('bg-surface-container-high',!active);b.classList.toggle('text-on-surface-variant',!active);b.setAttribute('aria-pressed',String(active));});
        draw();
      });
    });
    own(document.getElementById('searchToggleBtn'),()=>{
      searchInput.style.display=searchInput.style.display==='none'?'block':'none';if(searchInput.style.display==='block')searchInput.focus();
      else {searchInput.value='';draw();}
    });
    searchInput.addEventListener('input',draw);
    const nav=document.querySelector('nav');if(nav)nav.querySelectorAll('a[data-path]').forEach(a=>{
      const active=a.dataset.path==='kitapligim';a.classList.toggle('text-primary',active);a.classList.toggle('font-bold',active);a.classList.toggle('text-on-surface-variant',!active);
      if(active)a.setAttribute('aria-current','page');else a.removeAttribute('aria-current');
    });
    draw();
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
    const main=document.querySelector('main'),host=main?.firstElementChild;
    if(!host||host.children.length<5)return;
    const [header,recent,children,followSection,episodeSection]=Array.from(host.children);
    // Çocuklara uygunluk doğrulanana kadar örnek güvenlik kartlarını gösterme.
    children.style.display='none';
    const chips=document.getElementById('podcast-chips');
    const nav=document.querySelector('nav');if(nav)nav.querySelectorAll('a[data-path]').forEach(a=>{
      const active=a.dataset.path==='podcast';a.classList.toggle('text-primary',active);a.classList.toggle('font-bold',active);a.classList.toggle('text-on-surface-variant',!active);
      if(active)a.setAttribute('aria-current','page');else a.removeAttribute('aria-current');
    });
    function own(el,fn){
      if(!el)return;el.dataset.xmOwned='true';delete el.dataset.xmBlocked;el.removeAttribute('aria-disabled');el.disabled=false;el.querySelector('small')?.remove();el.__xmOwnedRun=fn;
    }
    const followHeading=followSection.querySelector('h2');if(followHeading)followHeading.textContent='Takip Ettiklerim';
    const followContainer=followSection.children[1],cardTemplate=followContainer?.firstElementChild?.cloneNode(true);
    followContainer.id='xm-podcast-following';
    const allLink=followSection.querySelector('button');if(allLink)allLink.style.display='none';
    const sectionHeading=episodeSection.querySelector('h2');if(sectionHeading)sectionHeading.textContent='Son Dinlenen Bölümler';
    const episodeHeader=sectionHeading?.parentElement?.querySelector('p');if(episodeHeader)episodeHeader.textContent='Dinleme geçmişinden';
    const sorting=episodeSection.querySelector('[aria-label="Sıralama"]');if(sorting)sorting.style.display='none';
    const episodeList=episodeSection.children[1];
    function savedFeed(track){
      return library.feeds.find(f=>f.url===track.feed)||{url:track.feed,title:track.artist||'Podcast',artwork:track.artwork||''};
    }
    function recentSection(){
      const history=library.history.filter(t=>t.id?.startsWith('podcast:')&&t.feed);
      const old=recent.children[1];
      if(old)old.remove();
      const card=create('div',undefined,'relative overflow-hidden rounded-2xl bg-surface-container-high p-4 flex flex-col gap-3.5 shadow-lg');
      if(history.length){
        const t=history[0],layout=create('div',undefined,'flex items-center gap-3');
        const image=create('img');image.className='w-16 h-16 rounded-xl object-cover bg-surface-container-lowest';
        if(t.artwork?.startsWith('https://'))image.src=t.artwork;image.alt='';
        const labels=create('div',undefined,'flex flex-col min-w-0');
        labels.append(create('strong',t.title,'font-headline-sm text-headline-sm text-on-surface'),create('p',t.artist||'Podcast','text-body-sm text-on-surface-variant'));
        layout.append(image,labels);card.append(layout);
        const open=button('Programı Aç',()=>openFeed(savedFeed(t)));open.className='self-end px-5 py-2 rounded-full bg-primary text-on-primary font-label-md';card.append(open);
      }else card.append(create('p','Henüz podcast dinlemedin.','text-body-sm text-on-surface-variant'));
      recent.append(card);
      episodeList.replaceChildren();
      if(!history.length){episodeList.append(create('p','Dinlediğin bölümler burada görünecek.','text-body-sm text-on-surface-variant'));return;}
      history.slice(0,10).forEach(t=>{
        const card=create('div',undefined,'flex items-center gap-3 p-3 rounded-2xl bg-surface-container-low');
        const art=create('img');art.className='w-14 h-14 rounded-xl object-cover bg-surface-container';if(t.artwork?.startsWith('https://'))art.src=t.artwork;art.alt='';
        const text=create('div',undefined,'flex flex-col min-w-0 flex-1');
        text.append(create('strong',t.title||'Bölüm','font-label-lg text-label-lg text-on-surface'),create('span',t.artist||'Podcast','text-body-sm text-on-surface-variant'));
        const open=button('Programı Aç',()=>openFeed(savedFeed(t)));open.className='px-3 py-2 rounded-full bg-primary text-on-primary font-label-md';
        card.append(art,text,open);episodeList.append(card);
      });
    }
    recentSection();
    const searchSection=create('section',undefined,'flex flex-col gap-2');
    const form=create('form',undefined,'flex items-center gap-2');
    const input=create('input');input.id='xm-podcast-search';input.type='search';input.maxLength=100;input.value=podcastQuery;
    input.placeholder='Program adı veya yayıncı ara…';input.setAttribute('aria-label','Podcast ara');
    input.className='w-full rounded-full bg-surface-container px-4 py-3 text-body-md text-on-surface focus:outline-none';
    const submit=button('Ara',()=>searchNow());submit.className='px-4 py-2 rounded-full bg-primary text-on-primary font-label-md';
    const clear=button('×',()=>{input.value='';input.dispatchEvent(new Event('input',{bubbles:true}));input.focus();});
    clear.setAttribute('aria-label','Podcast aramasını temizle');clear.className='w-10 h-10 rounded-full bg-surface-container-high text-on-surface';
    form.append(input,submit,clear);searchSection.append(form);
    const results=create('div');results.id='xm-podcast-results';results.setAttribute('aria-live','polite');results.className='flex flex-col gap-3';
    searchSection.append(results);
    const extra=create('div',undefined,'flex gap-3 flex-wrap');
    const rss=button('RSS Kaynağı Ekle',()=>editName('HTTPS RSS adresi','',address=>{
      try {const url=new URL(address);if(url.protocol!=='https:')throw Error();}
      catch(_){N.showToast('Geçerli bir HTTPS RSS adresi gir.');return;}
      const feed={url:address,title:address};
      if(!library.feeds.some(f=>f.url===address))library.feeds.push(feed);
      if(save())openFeed(feed);
    }));
    const downloads=button('İndirilen Bölümler',()=>go('i_ndirme_y_neticisi'));
    [rss,downloads].forEach(b=>{b.className='px-3 py-2 rounded-full bg-surface-container-high text-on-surface-variant font-label-md';extra.append(b);});
    searchSection.append(extra);header.insertAdjacentElement('afterend',searchSection);
    let currentQuery='';
    function searchNow(){
      clearTimeout(podcastTimer);
      const query=input.value.trim();podcastQuery=query;const id=++podcastRequest;currentQuery=query;
      results.replaceChildren();
      if(query.length<2){results.textContent=query?'En az iki karakter yaz.':'';return;}
      results.textContent='Podcast’ler aranıyor…';A.searchPodcasts(query,id);
    }
    form.addEventListener('submit',e=>{e.preventDefault();searchNow();});
    input.addEventListener('input',()=>{
      clearTimeout(podcastTimer);++podcastRequest;podcastQuery=input.value.trim();
      if(podcastQuery.length<2){results.textContent=podcastQuery?'En az iki karakter yaz.':'';return;}
      results.textContent='Podcast’ler aranıyor…';podcastTimer=setTimeout(searchNow,650);
    });
    if(chips)Array.from(chips.querySelectorAll('button')).forEach((chip,index)=>{
      if(index===2){chip.style.display='none';return;}
      own(chip,()=>{
        chips.querySelectorAll('button').forEach(b=>{const active=b===chip;b.classList.toggle('bg-primary',active);b.classList.toggle('text-on-primary',active);b.classList.toggle('bg-surface-container-high',!active);b.classList.toggle('text-on-surface-variant',!active);});
        input.value=index===0?'':chip.textContent.trim();input.dispatchEvent(new Event('input',{bubbles:true}));
      });
    });
    function makeCard(feed,appleUrl){
      if(!cardTemplate)return create('div');
      const card=cardTemplate.cloneNode(true);
      const image=card.querySelector('img');if(image){if(feed.artwork?.startsWith('https://'))image.src=feed.artwork;else image.removeAttribute('src');image.alt='';}
      card.querySelectorAll('span.absolute').forEach(s=>s.style.display='none');
      const name=card.querySelector('h3');if(name)name.textContent=feed.title||'Podcast';
      const artist=card.querySelector('p');if(artist)artist.textContent=[feed.artist,feed.genre].filter(Boolean).join(' · ');
      const open=card.querySelector('[aria-label="Hızlı Dinle"]');if(open){open.setAttribute('aria-label','Bölümleri Aç');own(open,()=>openFeed(feed));}
      const follow=card.querySelector('.podcast-follow-btn');
      if(follow){follow.textContent=library.feeds.some(f=>f.url===feed.url)?'Takibi Bırak':'Takip Et';own(follow,()=>{toggleFeed(feed,follow);drawFollowed();});}
      if(appleUrl){const link=button('Apple Podcasts’te Gör',()=>A.openPodcastPage(appleUrl));link.className='text-label-sm text-on-surface-variant';card.append(link);}
      return card;
    }
    function drawFollowed(){
      followContainer.replaceChildren();
      if(!library.feeds.length){followContainer.append(create('p','Henüz takip ettiğin program yok.','text-body-sm text-on-surface-variant'));return;}
      library.feeds.forEach(feed=>followContainer.append(makeCard(feed,'')));
    }
    window.__xmusicPodcastSearchResult=(id,payload)=>{
      if(id!==podcastRequest||currentQuery!==input.value.trim())return;
      results.replaceChildren();
      if(payload.error){results.append(create('p',payload.error),button('Tekrar Dene',searchNow));return;}
      if(!payload.items?.length){results.textContent='Açık RSS kaynağı olan program bulunamadı.';return;}
      const label=create('h2','Arama Sonuçları','font-headline-sm text-headline-sm text-on-surface');
      const grid=create('div',undefined,'flex gap-4 overflow-x-auto no-scrollbar pb-2');
      payload.items.forEach(item=>grid.append(makeCard({url:item.feed,title:item.title,artist:item.artist,artwork:item.artwork,genre:item.genre},item.url)));
      results.append(label,grid);
    };
    drawFollowed();
    if(podcastQuery.length>=2)searchNow();
  }
  function renderFeed(){
    const main=document.querySelector('main');if(!main)return;
    const address=sessionStorage.getItem('xm-feed');
    let info={url:address,title:'Podcast'};
    try{const saved=JSON.parse(sessionStorage.getItem('xm-feed-info')||'null');if(saved?.url===address)info=saved;}catch(_){}
    const identity=main.querySelector('.relative.z-10.flex.flex-col.items-center.text-center');
    const hero=main.querySelector('section.mt-6');
    const all=main.querySelector('section.mt-8');
    const filters=document.getElementById('filterContainer');
    if(!identity||!hero||!all||!filters)return;
    const list=filters.nextElementSibling;
    const follow=document.getElementById('followBtn'),followText=document.getElementById('followText');
    const cover=identity.querySelector('img'),title=identity.querySelector('h2'),artist=title?.nextElementSibling;
    const coverBadge=cover?.parentElement?.querySelector('span');if(coverBadge)coverBadge.style.display='none';
    const tags=title?.previousElementSibling;
    const category=tags?.children[0],rating=tags?.children[1],count=tags?.children[2];
    if(rating)rating.style.display='none';
    if(category){category.textContent=info.genre||'';category.hidden=!info.genre;}
    if(count)count.textContent='';
    if(title)title.textContent=info.title&&info.title!==address?info.title:'Podcast';
    if(artist)artist.textContent=info.artist||'';
    function coverImage(img){
      if(!img)return;
      if(info.artwork?.startsWith('https://'))img.src=info.artwork;else img.removeAttribute('src');
      img.alt='';img.style.background='#262a2e';
    }
    coverImage(cover);
    const desc=document.getElementById('showDescription'),descToggle=document.getElementById('toggleDescBtn');
    if(desc)desc.textContent='Program bilgileri yükleniyor…';
    const bell=document.getElementById('bellBtn');if(bell)bell.style.display='none';
    const more=follow?.parentElement?.querySelector('[aria-label="Seçenekler"]');if(more)more.style.display='none';
    function own(node,fn){
      if(!node)return;
      node.dataset.xmOwned='true';delete node.dataset.xmBlocked;node.removeAttribute('aria-disabled');
      node.disabled=false;node.title='';node.querySelector('small')?.remove();node.__xmOwnedRun=fn;
    }
    function updateFollow(){
      if(followText)followText.textContent=library.feeds.some(f=>f.url===address)?'Takip Ediliyor':'Takip Et';
      follow?.setAttribute('aria-pressed',String(library.feeds.some(f=>f.url===address)));
    }
    own(follow,()=>{toggleFeed(info,followText);updateFollow();});
    updateFollow();
    if(follow&&!address)follow.style.display='none';
    if(descToggle){descToggle.style.display='none';own(descToggle,()=>{
      const expanded=desc.classList.toggle('line-clamp-2')===false;
      descToggle.querySelector('span:first-child').textContent=expanded?'Daha Az Gör':'Devamını Gör';
      descToggle.querySelector('.material-symbols-outlined').textContent=expanded?'expand_less':'expand_more';
    });}

    // Orijinal mini oynatıcının alanları gerçek Media3 durum köprüsüyle eşleşir.
    const mini=main.querySelector('aside');
    if(mini){
      const image=mini.querySelector('img'),box=image?.parentElement;
      if(box){box.classList.add('w-12','h-12');box.style.cssText='width:48px;height:48px;overflow:hidden;border-radius:12px;';}
      const miniTitle=mini.querySelector('.text-label-lg');
      const miniArtist=mini.querySelector('.text-body-sm');
      [miniTitle,miniArtist].forEach(node=>{if(node&&node.tagName!=='P'){const p=create('p',node.textContent,node.className);node.replaceWith(p);}});
      const miniPlay=document.getElementById('miniPlayBtn');
      if(miniPlay)miniPlay.setAttribute('aria-label','Oynat veya Duraklat');
    }

    hero.style.display='none';list.replaceChildren();
    const message=create('p','Bölümler yükleniyor…');message.style.cssText='color:#bbcbb8;padding:12px 0;font-size:13px;';
    all.appendChild(message);
    const filterButtons=Array.from(filters.querySelectorAll('button'));
    const filterNames=['Tümü','İndirilenler','Henüz Dinlenmeyenler'];
    let chosen=0,episodes=[];
    function downloadStates(){
      try{return JSON.parse(A.listDownloads()||'[]').filter(x=>x.status===8);}catch(_){return [];}
    }
    function isDownloaded(item){return downloadStates().some(d=>d.id===item.id);}
    function play(item){
      if(isDownloaded(item))A.playDownload(item.id);
      else A.playPodcast(item.id,false);
    }
    function episodeButton(label,icon,fn){
      const b=button('',fn);b.setAttribute('aria-label',label);b.title=label;
      b.style.cssText='border:0;background:transparent;color:#bbcbb8;padding:5px;border-radius:50%;display:inline-flex;align-items:center;justify-content:center;';
      const symbol=create('span',icon,'material-symbols-outlined');symbol.style.fontSize='21px';b.appendChild(symbol);return b;
    }
    function dateLabel(value){
      if(!value)return '';
      const date=new Date(value);return isNaN(date.getTime())?value:date.toLocaleDateString('tr-TR',{day:'numeric',month:'short',year:'numeric'});
    }
    function updateFilters(){
      filterButtons.forEach((b,i)=>{
        b.textContent=i===0?'Tümü ('+episodes.length+')':filterNames[i];
        b.classList.toggle('bg-primary',i===chosen);b.classList.toggle('text-on-primary',i===chosen);
        b.classList.toggle('bg-surface-container',i!==chosen);b.classList.toggle('text-on-surface-variant',i!==chosen);
        b.setAttribute('aria-pressed',String(i===chosen));
      });
    }
    function drawEpisodes(){
      updateFilters();list.replaceChildren();
      const downloaded=downloadStates();const played=new Set(library.history.map(t=>t.id));
      const shown=episodes.filter(t=>chosen===0||(chosen===1?downloaded.some(d=>d.id===t.id):!played.has(t.id)));
      message.textContent=episodes.length?(shown.length?'':'Bu filtrede bölüm yok.'):'Desteklenen ses bölümü bulunamadı.';
      shown.forEach(t=>{
        const card=create('article',undefined,'flex flex-col p-3 rounded-xl bg-surface-container-low transition-colors');
        const top=create('div',undefined,'flex items-start gap-3');
        const art=create('img');art.className='w-12 h-12 rounded-lg object-cover flex-shrink-0';coverImage(art);
        const text=create('div',undefined,'flex-1 min-w-0');
        const meta=create('div',undefined,'flex items-center justify-between');
        const detail=[dateLabel(t.published),t.duration||''].filter(Boolean).join(' · ');
        meta.append(create('span',detail,'text-label-sm font-label-sm text-on-surface-variant'));
        if(downloaded.some(d=>d.id===t.id))meta.append(create('span','✓ İndirildi','text-primary text-label-sm'));
        const name=create('h4',t.title||'Bölüm','text-body-lg font-body-lg text-on-surface font-semibold mt-0.5');
        text.append(meta,name);
        if(t.description)text.append(create('p',t.description,'text-body-sm font-body-sm text-on-surface-variant line-clamp-1 mt-0.5'));
        top.append(art,text);card.append(top);
        const actions=create('div',undefined,'mt-2 pt-2 flex items-center justify-end gap-3');
        actions.append(episodeButton('Oynat','play_arrow',()=>play(t)),episodeButton('İndir','download',()=>A.downloadPodcast(t.id)),
          episodeButton('Sıraya Ekle','playlist_add',()=>A.playPodcast(t.id,true)),episodeButton('Listeye Ekle','add',()=>addToPlaylist(t)));
        card.append(actions);list.appendChild(card);
      });
    }
    filterButtons.forEach((b,i)=>own(b,()=>{chosen=i;drawEpisodes();}));
    updateFilters();
    if(!address){message.textContent='Önce bir podcast programı seç.';return;}
    const id=++rssRequest;
    window.__xmusicRssResult=(request,payload)=>{
      if(request!==rssRequest||(payload.feed&&payload.feed!==address))return;
      if(payload.error){
        message.textContent=payload.error;
        const retry=button('Tekrar Dene',renderFeed);message.append(' ',retry);return;
      }
      info.title=payload.title||info.title;
      if(title)title.textContent=info.title;
      if(artist)artist.textContent=info.artist||payload.author||'';
      if(desc)desc.textContent=payload.description||'Bu program için açıklama bulunamadı.';
      if(descToggle)descToggle.style.display=(payload.description||'').length>150?'':'none';
      const feed=library.feeds.find(f=>f.url===address);
      if(feed&&feed.title!==info.title){feed.title=info.title;save();}
      episodes=payload.items||[];
      if(count)count.textContent=episodes.length?'• '+episodes.length+' bölüm yüklendi':'';
      drawEpisodes();
      if(!episodes.length)return;
      const latest=episodes[0];hero.style.display='';
      const heroImg=hero.querySelector('img');coverImage(heroImg);
      const heroTitle=hero.querySelector('h3');if(heroTitle)heroTitle.textContent=latest.title||'Bölüm';
      const heroDate=hero.querySelector('div.flex.items-center.justify-between > span');if(heroDate)heroDate.textContent=dateLabel(latest.published);
      const heroLabel=hero.querySelector('.bg-surface-container-lowest.text-primary');if(heroLabel)heroLabel.textContent='Son bölüm';
      const heroInfo=heroTitle?.previousElementSibling?.querySelectorAll('span');if(heroInfo?.[1])heroInfo[1].textContent=latest.duration||'';
      const heroSubtitle=heroTitle?.nextElementSibling;if(heroSubtitle)heroSubtitle.textContent=info.artist||payload.author||'';
      const heroDescription=hero.querySelector('p.mt-3');if(heroDescription)heroDescription.textContent=latest.description||'';
      const heroPlay=document.getElementById('heroPlayBtn'),heroText=document.getElementById('heroPlayText');
      if(heroText)heroText.textContent='Bölümü Oynat';
      own(heroPlay,()=>play(latest));
      own(document.getElementById('downloadHeroBtn'),()=>A.downloadPodcast(latest.id));
      own(hero.querySelector('[aria-label="Sıraya Ekle"]'),()=>A.playPodcast(latest.id,true));
      own(hero.querySelector('[aria-label="15 saniye geri sar"]'),()=>A.queueAction('back15',0,0));
      own(hero.querySelector('[aria-label="30 saniye ileri sar"]'),()=>A.queueAction('forward30',0,0));
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

  // Tür ve öneri rafları, yalnızca kaynakta gerçekten bulunan kayıtlardan oluşur.
  let shelfId=0;
  const shelfCallbacks=new Map();
  window.__xmusicMusicShelfResult=(id,payload)=>{
    const callback=shelfCallbacks.get(id);shelfCallbacks.delete(id);if(callback)callback(payload);
  };
  function requestMusicShelf(query,callback){
    const id=++shelfId;shelfCallbacks.set(id,callback);N.searchMusicShelf(query,id);
  }
  function musicRow(item){
    const row=create('div',undefined,'flex items-center gap-3 p-3 rounded-xl bg-surface-container-low');
    const image=create('img');image.src='https://i.ytimg.com/vi/'+item.id+'/mqdefault.jpg';image.alt='';
    image.style.cssText='width:64px;height:48px;object-fit:cover;border-radius:8px;flex-shrink:0;';
    const caption=create('div',undefined,'flex-1 min-w-0');caption.append(create('strong',item.title||'Şarkı'),create('p',item.artist||''));
    const play=button('▶',()=>N.playExperimentalSource(item.id,item.title||'',item.artist||''));
    play.setAttribute('aria-label',(item.title||'Şarkı')+' oynat');play.className='w-10 h-10 rounded-full bg-primary text-on-primary flex items-center justify-center';
    row.append(image,caption,play);return row;
  }
  function openMusicShelf(title,query){
    modal(title,(panel,close)=>{
      const detail=create('p','Kayıtlar getiriliyor…','text-on-surface-variant');panel.append(detail);
      requestMusicShelf(query,payload=>{
        if(!panel.isConnected)return;detail.replaceChildren();
        if(payload.error){detail.textContent=payload.error;return;}
        if(payload.stale)detail.append(create('p','Bağlantı yok; önceki liste gösteriliyor.'));
        if(!(payload.items||[]).length){detail.append(create('p','Sonuç bulunamadı.'));return;}
        (payload.items||[]).forEach(item=>detail.append(musicRow(item)));
      });
    });
  }
  function makeOwned(el,fn){
    if(!el)return;el.dataset.xmOwned='true';delete el.dataset.xmBlocked;el.removeAttribute('aria-disabled');
    if(el.tagName==='BUTTON'){el.disabled=false;el.querySelector('small')?.remove();}
    else {el.setAttribute('role','button');el.tabIndex=0;el.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();fn();}};}
    el.__xmOwnedRun=fn;
  }
  function initDiscoverShelves(){
    const sections=Array.from(document.querySelectorAll('main section'));
    const top=sections.find(s=>s.querySelector('h2')?.textContent.includes('En Çok Arananlar'));
    const genres=sections.find(s=>s.querySelector('h2')?.textContent.includes('Hızlı Göz At'));
    if(top){
      const list=top.children[1];
      window.__xmusicRefreshTopSearches=()=>{
        let rows=[];try{const data=JSON.parse(A.topSearches()||'[]');if(Array.isArray(data))rows=data.slice(0,3);}catch(_){}
        list.replaceChildren();
        if(!rows.length){const empty=create('p','Aradıkların burada sıralanacak.','text-on-surface-variant');empty.style.padding='14px';list.append(empty);return;}
        rows.forEach((item,index)=>{
          const row=button('',()=>{
            const input=document.getElementById('search-input');if(!input)return;
            input.value=item.query;input.dispatchEvent(new Event('input',{bubbles:true}));input.focus();
          });
          row.className='w-full flex items-center gap-4 text-left px-4 py-3 border-b border-surface-container-high text-on-surface';
          row.append(create('strong',String(index+1),'text-primary'),create('span',item.query,'flex-1 font-label-lg'));
          row.append(create('span',item.count+' arama','text-body-sm text-on-surface-variant'));list.append(row);
        });
      };
      window.__xmusicRefreshTopSearches();
    }
    if(genres){
      const grid=genres.querySelector('.grid');if(!grid)return;
      const queries=['Türkçe pop hit şarkılar','Türkçe rock şarkılar','Türkçe rap hip hop','çocuk şarkıları','elektronik müzik','Türkçe akustik şarkılar','tasavvuf klasik müzik'];
      const names=['Pop','Rock','Rap & Hip-Hop','Çocuk','Elektronik','Akustik','Tasavvuf & Klasik'];
      Array.from(grid.children).forEach((card,index)=>{
        // Çocuk güvenliği doğrulanmadan şablondaki onay iddiasını gösterme.
        if(index===3)card.querySelectorAll('span').forEach(n=>{if(/Kids Safe|verified_user/i.test(n.textContent))n.style.display='none';});
        makeOwned(card,()=>{
          if(index===7){go('podcast_merkezi');return;}
          if(queries[index])openMusicShelf(names[index],queries[index]);
        });
      });
    }
  }
  function initMusicHub(){
    const nav=document.querySelector('nav[data-active-classes]');
    if(nav)nav.querySelectorAll('a[data-path]').forEach(a=>{
      const active=a.dataset.path==='muzik';a.classList.toggle('text-primary',active);a.classList.toggle('font-bold',active);a.classList.toggle('text-on-surface-variant',!active);
      if(active)a.setAttribute('aria-current','page');else a.removeAttribute('aria-current');
    });
    const sections=Array.from(document.querySelectorAll('main section'));
    const findTitle=label=>sections.find(s=>Array.from(s.querySelectorAll('h2')).some(h=>h.textContent.trim()===label));
    const first=document.querySelector('main h1')?.closest('section');
    const quality=first?.querySelector('[aria-label="Stüdyo Kalite Seçici"]');if(quality)quality.style.display='none';
    const status=first?.querySelector('span.uppercase');if(status)status.textContent='Müzik';
    const showcase=findTitle('Aile DJ Miksi #42');if(showcase)showcase.style.display='none';
    const family=sections.find(s=>s.textContent.includes('Bu Hafta En Çok Dinlenen Tür'));if(family)family.style.display='none';
    const familySession=sections.find(s=>s.textContent.includes('Ortak Aile Oturumu'));if(familySession)familySession.style.display='none';
    const genres=document.getElementById('genre-filter-tray');
    if(genres)Array.from(genres.querySelectorAll('button')).forEach(b=>makeOwned(b,()=>{
      const label=b.textContent.trim();if(label==='Tümü'){search('');return;}
      openMusicShelf(label,'Türkçe '+label+' şarkılar');
    }));
    const personal=findTitle('Senin İçin Hazırlandı');
    if(personal){
      const grid=personal.children[1],summary=personal.querySelector('p');
      const week=Math.floor(Date.now()/604800000);
      let mix=library.weeklyMusicMix;
      if(!mix||mix.week!==week){
        const seen=new Set(),tracks=[];
        [...library.favorites,...library.history].forEach(t=>{
          if(t.id && !t.id.startsWith('podcast:')&&!seen.has(t.id)){seen.add(t.id);tracks.push(t);}
        });
        mix={week:week,tracks:tracks.slice(0,10)};
        if(tracks.length){library.weeklyMusicMix=mix;save();}
      }
      if(summary)summary.textContent='Bu profilin favorileri ve dinleme geçmişi';
      grid.replaceChildren();
      if(!mix.tracks.length)grid.append(create('p','Dinlemeye başladığında liste burada oluşacak.','text-on-surface-variant'));
      else{
        const card=button('','');card.className='bg-surface-container rounded-2xl p-4 text-left';
        card.append(create('strong','Bu haftaki seçkilerin'),create('p',mix.tracks.length+' kayıt','text-body-sm text-on-surface-variant'));
        makeOwned(card,()=>modal('Bu haftaki seçkilerin',(p)=>mix.tracks.forEach(t=>p.append(trackRow(t)))));
        grid.append(card);
      }
      const all=personal.querySelector('button');if(all&&all!==grid.querySelector('button'))makeOwned(all,()=>modal('Bu haftaki seçkilerin',p=>mix.tracks.forEach(t=>p.append(trackRow(t)))));
    }
    function liveSection(label,title,query,max){
      const section=findTitle(label);if(!section)return;
      const heading=section.querySelector('h2');if(heading)heading.textContent=title;
      const subtitle=section.querySelector('p');if(subtitle)subtitle.textContent='Kaynak arama sonuçları · haftalık yenilenir';
      const holder=section.children[1];if(!holder)return;holder.replaceChildren();
      const message=create('p','Yükleniyor…','text-on-surface-variant');holder.append(message);
      const all=section.querySelector('button');if(all)makeOwned(all,()=>openMusicShelf(title,query));
      requestMusicShelf(query,payload=>{
        holder.replaceChildren();
        if(payload.error){holder.append(create('p',payload.error,'text-on-surface-variant'));return;}
        if(payload.stale)holder.append(create('p','Önceki liste gösteriliyor.','text-on-surface-variant'));
        (payload.items||[]).slice(0,max).forEach(t=>holder.append(musicRow(t)));
        if(!(payload.items||[]).length)holder.append(create('p','Sonuç bulunamadı.','text-on-surface-variant'));
      });
    }
    liveSection('Hi-Res & Stüdyo Kayıtları','Stüdyo ve akustik kayıtlar','Türkçe akustik stüdyo performans',5);
    liveSection('Yeni Çıkanlar','Yeni müzik araması','yeni çıkan Türkçe şarkılar',5);
    const popular=findTitle('Popüler Çalma Listeleri');
    if(popular){
      const holder=popular.children[1];if(holder){holder.replaceChildren();holder.append(create('p','Türkiye geneli listeler için doğrulanmış bir katalog henüz bağlı değil.','text-on-surface-variant'));}
      const btn=popular.querySelector('button');if(btn)btn.style.display='none';
    }
  }

  if(page==='arama_ve_ke_fet')initDiscoverShelves();
  if(page==='m_zik_merkezi')initMusicHub();
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
