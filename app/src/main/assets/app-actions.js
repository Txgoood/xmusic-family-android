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
  const setArtwork = window.__xmusicSetArtwork ||= function(img, address, label='') {
    if(!img)return;
    const fallback=window.__xmusicArtworkFallback ||= 'data:image/svg+xml;charset=utf-8,'+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="384" height="384" viewBox="0 0 384 384"><defs><linearGradient id="g" x2="1" y2="1"><stop stop-color="#17412d"/><stop offset="1" stop-color="#101719"/></linearGradient></defs><rect width="384" height="384" fill="url(#g)"/><path fill="#4efd7d" opacity=".75" d="M231 90v147a42 42 0 1 1-20-36V119l-71 16v118a42 42 0 1 1-20-36V119z"/></svg>');
    const wanted=typeof address==='string'&&address.startsWith('https://')?address:'';
    img.alt=label||'';img.style.visibility='visible';img.style.objectFit='cover';
    if(img.dataset.xmArtwork===wanted)return;
    img.dataset.xmArtwork=wanted;
    img.onerror=()=>{img.onerror=null;if(img.getAttribute('src')!==fallback)img.src=fallback;};
    const next=wanted||fallback;if(img.getAttribute('src')!==next)img.src=next;
  };
  function button(label, fn) { const b = create('button',label,'xm-btn'); b.type='button'; b.dataset.xmOwned='true'; b.__xmOwnedRun=fn; return b; }
  function modal(title, build) {
    document.getElementById('xm-dialog')?.remove();
    const overlay=create('div',undefined,'xm-overlay');overlay.id='xm-dialog';
    const panel=create('div',undefined,'xm-dialog'); panel.setAttribute('role','dialog');panel.setAttribute('aria-modal','true');panel.setAttribute('aria-label',title);
    const close=()=>overlay.remove();panel.append(create('h2',title));build(panel,close);if(!panel.querySelector('.xm-selection-header'))panel.append(button('Kapat',close));overlay.append(panel);document.body.append(overlay);
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
    if(profileState().role!=='child'&&/^(drive:|experimental:|drive-source:)/.test(track.id)){N.playTurkeyChartQueue(JSON.stringify([{title:track.title,artist:track.artist,sourceId:track.id}]),0);return;}
    if(track.id.startsWith('drive:')) N.resumeDriveTrack(track.id.slice(6),track.title||'',track.artist||'',0);
    else if(track.id.startsWith('spotify:')) N.playTurkeyChartQueue(JSON.stringify([{title:track.title,artist:track.artist,sourceId:track.id}]),0);
    else if(track.id.startsWith('local:')) A.playSavedTrack(JSON.stringify(track),enqueue);
    else if(track.id.startsWith('podcast:')) {if(track.feed)podResume(track);else N.showToast('Podcast kaynağını aç.');}
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
    const row=create('div',undefined,'xm-row'),label=create('div');row.dataset.xmSourceKey=selectionKey(track);label.style.flex='1';label.append(create('strong',track.title||'İsimsiz'),create('p',track.artist||''));row.append(label,button('Dinle',()=>playTrack(track)));
    if(extra)extra(row);return row;
  }
  function picturedTrackRow(track) { return picturedTrackRowWithActions(track); }
  function picturedTrackRowWithActions(track,extra) {
    const row=trackRow(track,extra),img=create('img');
    img.style.cssText='width:44px;height:44px;object-fit:cover;border-radius:8px;flex:none';
    artworkForTrack(img,track);row.prepend(img);return row;
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
    const sort=toolbar.querySelector('#sortDropdownBtn');
    const listBtn=toolbar.querySelector('#viewListBtn'),gridBtn=toolbar.querySelector('#viewGridBtn');
    let libraryView='list',librarySort='recent';
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
    const libraryStyle=create('style');
    libraryStyle.textContent=`.xm-library-row{display:flex;align-items:center;gap:12px;min-width:0;padding:10px 4px;border-bottom:1px solid #ffffff0d}.xm-library-row img{width:48px;height:48px;object-fit:cover;border-radius:6px;flex:none}.xm-library-row .xm-library-title{flex:1;min-width:0;text-align:left;background:none;border:0;color:#fff}.xm-library-title strong,.xm-library-title small{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.xm-library-title small{color:#aaa;margin-top:3px}.xm-library-menu{border:0;background:none;color:#aaa;font-size:24px;width:40px;height:48px;flex:none}.xm-library-subfilters{display:flex;gap:8px;overflow-x:auto;padding:0 16px 10px}.xm-library-subfilters button{border:0;background:#272727;color:#fff;border-radius:24px;padding:8px 14px;white-space:nowrap}.xm-library-subfilters button[aria-pressed=true]{background:#20df64;color:#111}`;document.head.append(libraryStyle);
    const subfilters=create('div',undefined,'xm-library-subfilters');filters.insertAdjacentElement('afterend',subfilters);
    function draw(){
      if(favoriteSub)favoriteSub.textContent=library.favorites.length+' şarkı';
      if(localSub)localSub.textContent=library.local.length+' yerel kayıt';
      const q=searchInput.value.trim().toLocaleLowerCase('tr');
      pins.style.display=filter==='all'?'':'none';
      local.style.display='none';
      playlists.style.display=(filter==='all'||filter==='playlists')?'':'none';
      rows.replaceChildren();subfilters.replaceChildren();
      if(filter==='playlists'){
        [['Tümü',()=>{filter='all';draw();},false],['Çalma Listeleri',()=>{},true],['Beğenilenler',()=>{filter='favorites';draw();},false]].forEach(([label,action,active])=>{const chip=button(label,action);chip.setAttribute('aria-pressed',String(active));subfilters.append(chip);});
      }else if(filter==='favorites'){
        [['Tümü',()=>{filter='all';draw();},false],['Çalma Listeleri',()=>{filter='playlists';draw();},false],['Beğenilenler',()=>{},true]].forEach(([label,action,active])=>{const chip=button(label,action);chip.setAttribute('aria-pressed',String(active));subfilters.append(chip);});
      }
      const lookup=t=>((t.title||'')+' '+(t.artist||'')).toLocaleLowerCase('tr').includes(q);
      const sections=[];
      if((filter==='all'&&(showFavorites||q))||filter==='favorites')sections.push(['Beğenilen Şarkılar',library.favorites]);
      if(filter==='all'&&(library.history.length||q))sections.push(['Dinleme Geçmişi',library.history]);
      if(filter==='podcasts')sections.push(['Takip Edilen Podcastler',library.feeds]);
      if(filter==='artists'){
        const seenArtists=new Set();
        [...library.history,...library.favorites].forEach(track=>{
          const artist=(track.artist||'').trim(),key=artist.toLocaleLowerCase('tr');
          if(!artist||seenArtists.has(key)||!key.includes(q)||track.id?.startsWith('podcast:'))return;
          seenArtists.add(key);
          const row=button('',()=>search(artist));row.className='xm-library-row';
          const image=create('img');setArtwork(image,track.artwork,artist);image.style.borderRadius='50%';
          const name=create('span',artist);name.style.cssText='flex:1;text-align:left;color:#fff';row.append(image,name);rows.append(row);
        });
        if(!seenArtists.size)rows.append(create('p','Henüz dinlenen sanatçı yok.','text-on-surface-variant'));
      }
      sections.forEach(([label,tracks])=>{
        const matching=tracks.filter(t=>lookup(t));
        if(!matching.length&&!q&&label!=='Beğenilen Şarkılar'&&filter!=='podcasts')return;
        const section=create('section',undefined,'flex flex-col gap-2 pt-3');
        section.append(create('h3',label,'font-headline-sm text-headline-sm text-on-surface'));
        if(!matching.length)section.append(create('p','Henüz kayıt yok.','text-on-surface-variant'));
        matching.forEach(t=>{
          if(label==='Takip Edilen Podcastler'){
            const row=button('',()=>openFeed(t));row.className='xm-library-row';const image=create('img');artworkForTrack(image,t);const text=create('span',t.title||'Podcast');text.style.cssText='flex:1;text-align:left;color:#fff';row.append(image,text);section.append(row);return;
          }
          const row=create('div',undefined,'xm-library-row');row.dataset.xmSourceKey=selectionKey(t);
          const image=create('img');artworkForTrack(image,t);
          const play=button('',()=>playTrack(t));play.className='xm-library-title';
          play.append(create('strong',t.title||'İsimsiz'),create('small',t.artist||''));
          const menu=button('⋮',()=>modal('Parça işlemleri',(panel,close)=>{
            panel.append(button('Listeye Ekle',()=>{close();addToPlaylist(t);}),button('Kaldır',()=>{
              const index=tracks.findIndex(x=>x.id===t.id);if(index>=0)tracks.splice(index,1);if(save()){close();draw();}
            }));
          }));
          menu.className='xm-library-menu';menu.setAttribute('aria-label',(t.title||'Parça')+' seçenekleri');
          row.append(image,play,menu);section.append(row);
        });
        rows.append(section);
      });
      Array.from(playlists.children).slice(1).forEach(n=>n.remove());
      const matches=library.playlists.filter(l=>(l.name||'').toLocaleLowerCase('tr').includes(q));
      if(librarySort==='name')matches.sort((a,b)=>(a.name||'').localeCompare(b.name||'','tr'));else matches.reverse();
      const heading=playlists.firstElementChild;
      const badge=heading?.querySelector('span');if(badge)badge.textContent=matches.length+' Liste';
      if(!matches.length){const empty=create('p','Henüz çalma listesi yok.','text-on-surface-variant');playlists.append(empty);}
      const playlistRows=create('div');playlistRows.style.cssText=libraryView==='grid'?'display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px':'';playlists.append(playlistRows);
      matches.forEach(l=>{
        if(!template)return;
        const row=template.cloneNode(true),name=row.querySelector('p.font-headline-sm')||row.querySelector('p');
        if(name)name.textContent=l.name;
        const subtitle=name?.nextElementSibling;if(subtitle)subtitle.textContent=(l.tracks||[]).length+' parça';
        const image=row.querySelector('img');const artwork=(l.tracks||[]).find(t=>t.artwork?.startsWith('https://'))?.artwork;
        setArtwork(image,artwork);
        if(!artwork && l.tracks?.length)artworkForTrack(image,l.tracks[0]);
        row.querySelectorAll('.absolute').forEach(n=>n.style.display='none');
        const menu=row.querySelector('button');if(menu)own(menu,()=>{sessionStorage.setItem('xm-playlist',l.id);go('alma_listesi_y_netimi');});
        clickable(row,()=>{sessionStorage.setItem('xm-playlist',l.id);go('alma_listesi_y_netimi');});
        if(libraryView==='grid')row.style.cssText='min-width:0;overflow:hidden';
        playlistRows.appendChild(row);
      });
    }
    own(sort,()=>modal('Listeleri sırala',(p,close)=>{[['Son eklenenler','recent'],['Ada göre','name']].forEach(([label,value])=>p.append(button(label,()=>{librarySort=value;const labelNode=sort.querySelector('span:not(.material-symbols-outlined)');if(labelNode)labelNode.textContent=label;close();draw();})));}));
    own(listBtn,()=>{libraryView='list';draw();});own(gridBtn,()=>{libraryView='grid';draw();});
    clickable(favoriteCard,()=>{filter='favorites';showFavorites=true;draw();});
    own(favoriteCard?.querySelector('button'),()=>{filter='favorites';showFavorites=true;draw();});
    clickable(downloadCard,()=>go('i_ndirme_y_neticisi'));
    const localCard=local.firstElementChild;clickable(localCard,()=>{filter='local';draw();});
    own(local.querySelector('[aria-label="Yerel Dosyaları Aç"]'),()=>A.openLocalAudio());
    const chips=Array.from(filters.querySelectorAll('button'));
    const modes=['all','playlists','downloads','artists','albums','podcasts','local'];
    chips.forEach((chip,index)=>{
      const mode=modes[index];if(mode==='albums'||mode==='local'){chip.style.display='none';return;}
      if(mode==='artists')chip.textContent='Dinlenen Sanatçılar';
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
    panel.classList.add('xm-own-playlist');
    const heading=panel.querySelector('h2');
    heading.textContent=list.name;
    heading.classList.add('xm-own-playlist-title');
    const hero=create('div',undefined,'xm-own-playlist-hero');
    const art=create('div',undefined,'xm-selection-cover');
    coverMosaic(art,list.tracks||[]);
    hero.append(art,create('strong',list.name),create('p',(list.tracks||[]).length+' şarkı'));
    panel.append(hero);
    const find=create('input',undefined,'xm-input');
    find.type='search';find.placeholder='Listede şarkı ara';find.setAttribute('aria-label','Listede şarkı ara');
    panel.append(find);
    find.addEventListener('input',()=>{
      const q=find.value.trim().toLocaleLowerCase('tr');
      panel.querySelectorAll('[data-xm-playlist-search]').forEach(row=>{
        row.hidden=q!==''&&!row.dataset.xmPlaylistSearch.includes(q);
      });
    });
    const name=create('input',undefined,'xm-input');name.value=list.name;name.maxLength=80;name.setAttribute('aria-label','Liste adı');panel.append(name,button('Kaydet',()=>{if(!name.value.trim())return;list.name=name.value.trim();if(save())N.showToast('Kaydedildi');}),button('Şarkı Ekle veya Ara',()=>search()),button('Çalan Parçayı Ekle',()=>{if(!state.id)return N.showToast('Önce bir parça seç.');if(!list.tracks.some(t=>t.id===state.id))list.tracks.push(trackData());if(save())renderPlaylist();}));
    if(!list.tracks.length)panel.append(create('p','Liste boş. Oynatıcıdaki seçeneklerden gerçek parçalar ekleyebilirsin.'));
    list.tracks.forEach((track,index)=>panel.append(picturedTrackRowWithActions(track,row=>{
      row.dataset.xmPlaylistSearch=((track.title||'')+' '+(track.artist||'')).toLocaleLowerCase('tr');
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
  function familySyncState(){try{return JSON.parse(A.familySyncState?.()||'{}')||{};}catch(_){return {};}}
  function canManageFamily(){const f=familySyncState();return profileState().role==='admin'&&(!f.enabled||f.owner);}
  function familyConnection(){modal('Aile bağlantısı · Kurulum',(panel)=>{
    const data=profileState(),family=familySyncState();
    if(family.enabled){
      panel.append(create('strong',family.owner?'Bu telefon aile kurucusu':'Bu telefon aileye bağlı'));
      panel.append(create('p',family.lastSync?'Son eşitleme: '+new Date(family.lastSync).toLocaleString('tr-TR'):'İlk eşitleme bekleniyor.'));
      if(family.error)panel.append(create('p',family.error));
      panel.append(create('p','İki telefonda aynı ortak Drive klasörü ve Otomatik ortak arşiv bağlantısı açık olmalı. Uygulama açıkken yaklaşık bir dakikada yenilenir; kapalıyken anlık bildirim gelmez. İnternet yokken son alınan kurallar kullanılır; farklı cihazlardaki süre kullanımı yeniden bağlanınca birleşir.'));
      if(data.role!=='child'){
        const share=create('input');share.type='checkbox';share.checked=!!A.familySharing();share.setAttribute('aria-label','Bu profilin miks paylaşımı');
        const label=create('label',' Bu profildeki beğeni ve son dinlemelerimi aile miksine kat');label.prepend(share);panel.append(label);
        share.addEventListener('change',()=>N.showToast(A.setFamilySharing(share.checked)?'Miks paylaşımı kaydedildi':'Kaydedilemedi'));
        panel.append(button('Şimdi eşitle',()=>A.familySetup('sync','')));
        if(family.owner&&canManageFamily())panel.append(button('Aile bağlantı kodunu kopyala',()=>A.copyFamilyInvitation()));
      }
      panel.append(create('p',family.owner?'Fatma ve çocuk profillerini buradan ekleyip düzenleyebilirsin. Çocuk istekleri Onaylı içerikleri yönet bölümüne gelir.':'Profil ekleme, roller, çocuk onayları ve süre sınırları aile kurucusunun telefonundan yönetilir. Kendi profilini Seç ile aç.'));
    }else{
      panel.append(create('h3','1 · Önce iki telefonda Drive'));
      panel.append(create('p','Ayarlar → Google hesap ve Drive ayarları: aynı ortak müzik klasörünü seçin; Otomatik ortak arşiv bağlantısını açın. Herkes kendi Google hesabını kullanır.'));
      panel.append(create('h3','2 · Tolga’nın telefonunda aileyi oluştur'));
      panel.append(create('p','Yönetici profilinde ebeveyn PIN’i belirle. Fatma’yı yetişkin, çocukları çocuk rolüyle bu telefonda ekle. Sonra aşağıdaki düğmeye bas. Yeni profil sonradan da eklenebilir.'));
      if(data.role==='admin')panel.append(button('Bu telefonda aileyi oluştur',()=>A.familySetup('create','')));
      panel.append(create('h3','3 · Diğer telefonda aileye katıl'));
      panel.append(create('p','Kurucu telefondan Aile bağlantı kodunu kopyala. Buraya yapıştır, Aileye katıl de ve bu telefonu kullanacak kişiyi seç (örneğin Fatma). Telefonundaki aktif profilin kitaplığı o aile profiline taşınır; eski kopya korunur. Çocuk telefonu kurulurken ebeveyn çocuk profilini seçer.'));
      const code=create('textarea',undefined,'xm-input');code.placeholder='XMF1. ile başlayan aile bağlantı kodu';code.maxLength=4000;code.setAttribute('aria-label','Aile bağlantı kodu');panel.append(code,button('Aileye katıl',()=>A.familySetup('join',code.value.trim())));
      panel.append(create('p','Kod aile verilerine erişim anahtarıdır; yalnızca ailenle paylaş. Aile ayarları Drive’a şifreli yazılır.'));
    }
  });}
  function avatarNode(value){
    const wrap=create('span',undefined,'flex items-center justify-center rounded-full bg-surface-container-high overflow-hidden');
    wrap.style.cssText='width:48px;height:48px;font-size:27px;flex-shrink:0;';
    if(typeof value==='string'&&value.startsWith('data:image/jpeg;base64,')){const img=create('img');img.src=value;img.alt='Profil fotoğrafı';img.style.cssText='width:100%;height:100%;object-fit:cover';wrap.append(img);}
    else wrap.textContent=value||'🎵';return wrap;
  }
  function refreshAvatar(){
    const value=A.activeAvatar()||'';
    document.querySelectorAll('[data-xm-active-avatar]').forEach(n=>n.replaceChildren(avatarNode(value)));
    const profileButton=document.querySelector('header button[aria-label="Profil ve Aile Alanı"]');
    const img=profileButton?.querySelector('img');
    if(img){const holder=img.parentElement;if(holder){holder.replaceChildren(avatarNode(value));}}
  }
  window.__xmusicRefreshAvatar=refreshAvatar;
  function avatarChooser(){modal('Profil görseli',(panel,close)=>{
    panel.append(create('p','Hazır görsel seç veya telefonundan bir fotoğraf kullan.'));
    const choices=create('div',undefined,'flex flex-wrap gap-2');
    ['🎵','🎧','🎸','🌸','🌟','🐱','🦊','🐼'].forEach(icon=>choices.append(button(icon,()=>{if(A.setActiveAvatar(icon)){refreshAvatar();close();}})));
    panel.append(choices,button('Telefondan fotoğraf seç',()=>{close();A.pickActiveAvatar();}));
  });}
  const roleNames={admin:'Yönetici',adult:'Yetişkin',child:'Çocuk'};
  function parentPinForm(){modal('Ebeveyn PIN’i',(p,close)=>{
    const data=profileState(),old=create('input',undefined,'xm-input'),pin=create('input',undefined,'xm-input');
    [old,pin].forEach(n=>{n.type='password';n.inputMode='numeric';n.maxLength=8;});
    old.placeholder='Mevcut ebeveyn PIN’i';old.setAttribute('aria-label',old.placeholder);
    pin.placeholder='Yeni ebeveyn PIN’i (4–8 rakam)';pin.setAttribute('aria-label',pin.placeholder);
    if(data.parentConfigured)p.append(old);p.append(pin,create('p','Çocuktan yetişkin profiline geçişte bu PIN istenir. Unutmayacağın bir PIN belirle.'),button('Kaydet',()=>{
      if(A.setParentPin(old.value,pin.value)){close();N.showToast('Ebeveyn PIN’i kaydedildi.');go('aile_profili_se_imi');}else N.showToast('Yönetici profilinde olmalısın. Mevcut PIN’i ve yeni PIN biçimini kontrol et.');
    }));
  });}
  function profileForm(profile){
    const data=profileState(),manager=canManageFamily();
    modal(profile?'Profil Düzenle':'Yeni Profil',(p,close)=>{
      const name=create('input',undefined,'xm-input');name.maxLength=60;name.placeholder='Profil adı';name.setAttribute('aria-label','Profil adı');name.value=profile?.name||'';
      const current=create('input',undefined,'xm-input');current.type='password';current.inputMode='numeric';current.maxLength=8;current.placeholder='Mevcut profil PIN’i';current.setAttribute('aria-label','Mevcut PIN');
      const pin=create('input',undefined,'xm-input');pin.type='password';pin.inputMode='numeric';pin.maxLength=8;pin.placeholder='Yeni profil PIN’i (isteğe bağlı)';pin.setAttribute('aria-label','Yeni PIN');
      const parent=create('input',undefined,'xm-input');parent.type='password';parent.inputMode='numeric';parent.maxLength=8;parent.placeholder='Ebeveyn PIN’i';parent.setAttribute('aria-label','Ebeveyn PIN’i');
      const role=create('select',undefined,'xm-input');role.setAttribute('aria-label','Profil rolü');
      Object.entries(roleNames).forEach(([value,label])=>{const o=create('option',label);o.value=value;role.append(o);});role.value=profile?.role||'adult';role.disabled=!manager;
      const limit=create('input',undefined,'xm-input');limit.type='number';limit.min='1';limit.max='720';limit.value=profile?.dailyMinutes||60;limit.setAttribute('aria-label','Günlük dinleme sınırı (dakika)');
      const mix=create('input');mix.type='checkbox';mix.checked=!!profile?.mixEnabled;mix.setAttribute('aria-label','Aile miksine katıl');
      const mixLabel=create('label',' Bu cihazdaki aile miksine beğeni ve geçmişimle katıl');mixLabel.prepend(mix);
      p.append(name,create('label','Profil rolü'),role);if(profile?.hasPin)p.append(current);p.append(pin);
      const childOptions=create('div');childOptions.append(create('label','Günlük dinleme sınırı (1–720 dakika)'),limit,create('p','Çocuk yalnızca yöneticinin onayladığı kayıtları dinler. Arama, indirme ve Drive ayarları kapalıdır.'));
      p.append(childOptions,mixLabel);const update=()=>{childOptions.hidden=role.value!=='child';mixLabel.hidden=role.value==='child';};role.addEventListener('change',update);update();
      if(manager&&data.parentConfigured)p.append(parent);
      p.append(create('p','Profil PIN’i boş bırakılırsa yetişkin girişinde kişisel PIN aranmaz. Çocuk profilden çıkışta ebeveyn PIN’i her zaman istenir.'));
      if(manager&&!data.parentConfigured)p.append(button('Önce ebeveyn PIN’i belirle',()=>{close();parentPinForm();}));
      p.append(button('Kaydet',()=>{
        if(role.value==='child'&&!data.parentConfigured){N.showToast('Önce ebeveyn PIN’i belirle.');return;}
        if(A.saveFamilyProfile(profile?.id||'',name.value,role.value,current.value,pin.value,parent.value,Number(limit.value),mix.checked)){close();go('aile_profili_se_imi');}
        else N.showToast('Kayıt başarısız. Yetkini, PIN’leri ve süreyi kontrol et. Son yönetici kaldırılamaz.');
      }));
    });
  }
  function manageChild(profile){modal(profile.name+' · Onaylı İçerikler',(panel)=>{
    const pin=create('input',undefined,'xm-input');pin.type='password';pin.inputMode='numeric';pin.maxLength=8;pin.placeholder='Ebeveyn PIN’i';pin.setAttribute('aria-label','Ebeveyn PIN’i');panel.append(pin,create('p','Önce yetişkin profilinde kaydı dinle veya beğen; sonra burada çocuğa izin ver. Onay yalnızca seçilen kaydı kapsar.'));
    let approved=[];try{approved=JSON.parse(A.childTracks(profile.id)||'[]');}catch(_){}
    const act=(track,remove)=>{if(A.approveChildTrack(profile.id,JSON.stringify(track),remove,pin.value)){N.showToast(remove?'Onay kaldırıldı':'Çocuk için onaylandı');}else N.showToast('Ebeveyn PIN’ini kontrol et.');};
    panel.append(create('h3','Onaylı kayıtlar'));
    approved.forEach(track=>panel.append(button('Kaldır · '+track.title,()=>act(track,true))));
    const seen=new Set();const candidates=[...(state.id?[trackData()]:[]),...library.favorites,...library.history].filter(t=>t.id&&!seen.has(t.id)&&seen.add(t.id)).slice(0,100);
    let review={};
    if(A.requestChildReview&&A.childReview){
      candidates.forEach(t=>A.requestChildReview(profile.id,JSON.stringify(t)));
      try{review=JSON.parse(A.childReview(profile.id)||'{}');}catch(_){}
      panel.append(create('h3','Yaş grubu ve inceleme'));
      const age=create('select',undefined,'xm-input');age.setAttribute('aria-label','Çocuğun yaş grubu');
      [['under7','7 yaş altı'],['under13','7–12 yaş'],['teen','13–17 yaş']].forEach(([v,t])=>{const o=create('option',t);o.value=v;age.append(o);});age.value=review.ageGroup||'under13';
      panel.append(age,button('Yaş grubunu kaydet',()=>N.showToast(A.setChildAge(profile.id,age.value,pin.value)?'Kaydedildi':'PIN’i kontrol et.')),
        create('p','Yaş grubu ebeveyn incelemesi içindir. Tüm yaşlarda yalnızca onaylı kayıtlar çalınır. Başlık kontrolü söz analizi veya güvenlik garantisi değildir.'));
      panel.append(create('h3','Onay bekleyenler · '+(review.pending||[]).length));
      (review.pending||[]).forEach(t=>{
        const row=create('div',undefined,'xm-row');row.append(create('strong',t.title+' — '+(t.artist||'')),create('p',t.reason));
        const candidate=candidates.find(x=>x.id===t.id)||approved.find(x=>x.id===t.id)||(/^(experimental:|drive-source:|drive:)/.test(t.id||'')?t:null);
        if(candidate)row.append(button('Önce dinle',()=>playTrack(candidate)));
        if(candidate)row.append(button('Kaydı onayla',()=>{
          if(A.approveChildTrack(profile.id,JSON.stringify(candidate),false,pin.value)){row.remove();N.showToast('Onaylandı');}else N.showToast('PIN’i kontrol et.');
        }));
        else row.append(create('p','Bu bir şarkı isteği. Yetişkin profilinde doğru kaydı bulup dinle; ardından aşağıdan onayla.'));
        row.append(button('Reddet',()=>{if(A.resolveChildReview(profile.id,t.requestKey,true,pin.value)){row.remove();N.showToast('Reddedildi');}else N.showToast('PIN’i kontrol et.');}));panel.append(row);
      });
      panel.append(create('p','Reddedilen istek: '+(review.denied||[]).length+' · İstekler ve kararlar bu cihazda saklanır.'));
    }
    panel.append(create('h3','Bu profilden eklenecek kayıtlar'));
    if(!candidates.length)panel.append(create('p','Henüz kayıt yok. Önce bir kayıt dinle.'));
    candidates.forEach(track=>panel.append(button('Onayla · '+track.title+' — '+(track.artist||''),()=>act(track,false))));
  });}
  function renderProfiles(){
    const heading=document.querySelector('header h1');if(heading)heading.textContent='Aile Profilleri';
    const data=profileState(),manager=canManageFamily(),child=data.role==='child',panel=content('Aile Profilleri');if(!panel)return;
    panel.append(create('p',familySyncState().enabled?'Aile profilleri ortak Drive üzerinden otomatik eşitlenir. Çocuk kurallarını aile kurucusu yönetir.':'Telefonlar arası aile miksini ve çocuk denetimlerini kullanmak için Aile bağlantısı kurulumunu bir kez tamamla.'),button('Aile bağlantısı · Kurulum ve durum',familyConnection));
    if(manager)panel.append(button('Yeni Profil Ekle',()=>profileForm()),button(data.parentConfigured?'Ebeveyn PIN’ini değiştir':'Ebeveyn PIN’i belirle',parentPinForm));
    panel.append(button('Aktif profil görselini değiştir',avatarChooser));
    (data.profiles||[]).forEach(profile=>{
      const row=create('div',undefined,'xm-row');row.append(create('strong',profile.name+' · '+(roleNames[profile.role]||'Yetişkin')+(profile.id===data.active?' · Aktif':'')));
      if(profile.role==='child')row.append(create('p',profile.usedMinutes+'/'+profile.dailyMinutes+' dk bugün · '+profile.approvedCount+' onaylı kayıt · '+(profile.pendingCount||0)+' onay bekliyor'));
      row.append(button('Seç',()=>{
        if(profile.id===data.active){go('ana_sayfa');return;}
        if(!profile.hasPin&&!profile.needsParentPin){if(A.changeProfile('select',profile.id,'','',''))go('ana_sayfa');return;}
        modal(profile.needsParentPin?'Ebeveyn PIN’i':'Profil PIN’i',(p,close)=>{const input=create('input',undefined,'xm-input');input.type='password';input.inputMode='numeric';input.maxLength=8;input.setAttribute('aria-label','PIN');p.append(input,button('Aç',()=>{if(A.changeProfile('select',profile.id,'',input.value,'')){close();go('ana_sayfa');}else N.showToast('PIN yanlış veya deneme sınırına ulaşıldı. Beş hatada 1 dakika bekle.');}));});
      }));
      if(!child&&(manager||profile.id===data.active))row.append(button('Düzenle',()=>profileForm(profile)));
      if(manager&&profile.role==='child')row.append(button('Onaylı içerikleri yönet',()=>manageChild(profile)));
      if(manager&&profile.id!==data.active)row.append(button('Sil',()=>modal('Profili Sil',(p,close)=>{const pin=create('input',undefined,'xm-input');pin.type='password';pin.inputMode='numeric';pin.setAttribute('aria-label','PIN');if(profile.hasPin)p.append(pin);p.append(create('p','Profil ve bu cihazdaki yerel kitaplığı silinir. Son yönetici silinemez.'),button('Onayla',()=>{if(A.changeProfile('delete',profile.id,'',pin.value,'')){close();renderProfiles();}else N.showToast('Profil silinemedi; yetkini ve PIN’i kontrol et.');}));})));
      panel.append(row);
    });
  }
  function showFamilyMix(kind){
    modal(kind==='favorites'?'Aile Beğenileri':'Aile Son Dinlenenler',panel=>{
      let tracks=[];try{tracks=JSON.parse(A.familyMix(kind)||'[]');}catch(_){}
      if(!Array.isArray(tracks))tracks=[];
      panel.append(create('p',familySyncState().enabled?'Aile bağlantısında paylaşımı açık yetişkinlerin ortak kayıtları.':'Bu cihazda aile miksine katılmayı seçen yetişkin profilleri.'));
      if(!tracks.length){panel.append(create('p','Henüz ortak kayıt yok. Aile bağlantısı ekranında bu profilin miks paylaşımını aç; birkaç kayıt dinle veya beğen.'),button('Aile bağlantısını aç',familyConnection));return;}
      const offset=Math.floor(Date.now()/604800000)%tracks.length;tracks=tracks.slice(offset).concat(tracks.slice(0,offset));
      const snapshot=tracks.filter(t=>typeof t.title==='string'&&t.title.trim()&&typeof t.artist==='string'&&t.artist.trim()&&!String(t.id||'').startsWith('podcast:')).slice(0,1000).map(t=>({title:t.title.trim(),artist:t.artist.trim(),sourceId:t.id||''}));
      selectionChrome(panel,kind==='favorites'?'Aile Beğenileri':'Aile Son Dinlenenler',tracks,'#315342',()=>{if(!snapshot.length){N.showToast('Bu listede oynatılabilir şarkı bulunamadı.');return;}N.playTurkeyChartQueue(JSON.stringify(snapshot),0);},(holder,term)=>{
        tracks.filter(t=>!term||((t.title||'')+' '+(t.artist||'')).toLocaleLowerCase('tr').includes(term)).forEach(t=>holder.append(picturedTrackRow(t)));
      },tracks.length,'Ailenin dinlemeleri');
      if(profileState().role==='child')panel.querySelector('.xm-selection-play')?.remove();
    });
  }
  function mixTracks(kind) {
    try { const rows=JSON.parse(A.familyMix(kind)||'[]');return Array.isArray(rows)?rows:[]; } catch (_) { return []; }
  }
  function coverMosaic(holder, tracks) {
    holder.replaceChildren();
    const covers=[...new Set(tracks.map(t=>t.artwork).filter(a=>typeof a==='string'&&a.startsWith('https://')))].slice(0,4);
    if(!covers.length)holder.append(create('span','music_note','material-symbols-outlined text-6xl text-on-surface/60'));
    tracks.slice(0,8).filter(t=>!t.artwork).forEach(track=>{
      if(!holder.isConnected)return;
      loadTrackArtwork(track,()=>{if(holder.isConnected){coverMosaic(holder,tracks);tintFromArtwork(holder.parentElement,track.artwork);}});
    });
    if(!covers.length)return;
    const grid=create('div');grid.style.cssText='display:grid;position:absolute;inset:0;grid-template-columns:repeat(2,1fr);grid-template-rows:repeat(2,1fr);overflow:hidden;';
    for(let i=0;i<4;i++){const img=create('img');img.style.cssText='width:100%;height:100%;object-fit:cover;min-width:0;min-height:0';setArtwork(img,covers[i%covers.length]);grid.append(img);}
    holder.append(grid);
  }
  function isPodcastTrack(track){return String(track.id||'').startsWith('podcast:')||(!track.id&&typeof track.url==='string');}
  function podcastIdentity(track){
    const norm=s=>String(s||'').toLocaleLowerCase('tr').normalize('NFKC').replace(/[^\p{L}\p{N}]+/gu,' ').trim();
    const address=track.feed||(!track.id?track.url:'')||'';
    const title=track.id?track.artist:track.title;
    const exact=library.feeds.find(f=>address&&f.url===address);
    const names=library.feeds.filter(f=>norm(f.title)&&norm(f.title)===norm(title));
    const feed=exact||(names.length===1?names[0]:null);
    return {feed:address||feed?.url||'',title:feed?.title||title||'',artwork:feed?.artwork||''};
  }
  function selectionKey(track) {
    if(isPodcastTrack(track))return 'podcast|'+(track.id||track.url||track.title||'');
    const norm=s=>String(s||'').toLocaleLowerCase('tr').normalize('NFKC').replace(/[^\p{L}\p{N}]+/gu,' ').trim();
    const title=norm(track.title),artist=norm(track.artist);
    return title&&artist?artist+'|'+title:'';
  }
  const artworkQueue=[], artworkPending=new Map(), artworkAttempted=new Set();
  let artworkActive=0, artworkRequest=Number(sessionStorage.getItem('xm-artwork-sequence'))||0;
  function loadTrackArtwork(track, onReady){
    const key=selectionKey(track);
    if(!key || track.artwork || (!isPodcastTrack(track)&&typeof N.findTrackArtwork!=='function'))return;
    const existing=artworkPending.get(key);
    if(existing){existing.callbacks.push(onReady);return;}
    if(artworkAttempted.has(key))return;
    artworkAttempted.add(key);
    const item={key,track,podcast:isPodcastTrack(track),profile:profileState().active,callbacks:[onReady],id:++artworkRequest};
    sessionStorage.setItem('xm-artwork-sequence',String(artworkRequest));artworkPending.set(key,item);artworkQueue.push(item);pumpArtwork();
  }
  function pumpArtwork(){
    while(artworkActive<2 && artworkQueue.length){
      const item=artworkQueue.shift();artworkActive++;
      if(item.podcast){const info=podcastIdentity(item.track);A.findPodcastArtwork(info.feed,info.title,item.track.id||'',item.id);}else N.findTrackArtwork(item.track.title,item.track.artist,item.id);
    }
  }
  window.__xmusicArtworkResult=(id,url)=>{
    const item=[...artworkPending.values()].find(entry=>entry.id===id&&!entry.podcast);
    if(!item)return;
    artworkPending.delete(item.key);artworkActive--;
    if(url && profileState().active===item.profile){
      const update=t=>{if(selectionKey(t)===item.key && !t.artwork)t.artwork=url;};
      library.favorites.forEach(update);library.history.forEach(update);
      library.playlists.forEach(list=>(list.tracks||[]).forEach(update));
      if(library.resume)update(library.resume);
      item.track.artwork ||= url;
      save();item.callbacks.forEach(callback=>callback(url));
    }
    pumpArtwork();
  };
  window.__xmusicPodcastArtworkResult=(id,payload)=>{
    const item=[...artworkPending.values()].find(entry=>entry.id===id&&entry.podcast);if(!item)return;
    artworkPending.delete(item.key);artworkActive--;
    const url=payload?.artwork||'',program=payload?.programArtwork||'';
    if(url.startsWith('https://')&&profileState().active===item.profile){
      const info=podcastIdentity(item.track);
      const apply=t=>{
        if(!isPodcastTrack(t))return;
        if(selectionKey(t)===item.key){if(!t.artwork)t.artwork=url;if(!t.feed&&t.id&&payload.feed)t.feed=payload.feed;}
        else if(!t.artwork&&program.startsWith('https://')&&podcastIdentity(t).feed===info.feed&&info.feed)t.artwork=program;
      };
      library.history.forEach(apply);library.favorites.forEach(apply);library.playlists.forEach(list=>(list.tracks||[]).forEach(apply));if(library.resume)apply(library.resume);
      library.feeds.forEach(feed=>{if((info.feed&&feed.url===info.feed)||feed===item.track){if(!feed.artwork)feed.artwork=program||url;if(!feed.url&&payload.feed)feed.url=payload.feed;}});
      item.track.artwork=url;save();item.callbacks.forEach(callback=>callback(url));
      // Aynı programın başka görünür satırları da ikinci bir arama beklemeden güncellenir.
      document.querySelectorAll('img[data-xm-podcast-feed]').forEach(img=>{if(img.dataset.xmPodcastFeed===info.feed&&!img.dataset.xmArtwork){setArtwork(img,program||url,img.alt);}});
    }
    pumpArtwork();
  };
  function artworkForTrack(img,track){
    if(!img)return;
    img.dataset.xmTrackKey=selectionKey(track);
    if(isPodcastTrack(track)){
      const info=podcastIdentity(track);img.dataset.xmPodcastFeed=info.feed;
      if(!track.artwork&&info.artwork){track.artwork=info.artwork;save();}
    }
    if(!track.artwork){const known=[...library.favorites,...library.history,...library.playlists.flatMap(list=>list.tracks||[])].find(t=>selectionKey(t)===selectionKey(track)&&t.artwork);if(known)track.artwork=known.artwork;}
    setArtwork(img,track.artwork,track.title);
    if(track.artwork)return;
    img.dataset.xmTrackKey=selectionKey(track);
    const expected=img.dataset.xmTrackKey;
    const load=()=>loadTrackArtwork(track,url=>{if(img.isConnected && img.dataset.xmTrackKey===expected)setArtwork(img,url,track.title);});
    if('IntersectionObserver' in window){
      const observer=new IntersectionObserver(entries=>{if(entries.some(entry=>entry.isIntersecting)){observer.disconnect();load();}},{rootMargin:'200px'});
      observer.observe(img);
    }else load();
  }
  window.__xmusicArtworkForTrack=artworkForTrack;
  function tintFromArtwork(holder,address){
    if(!address)return;
    const img=new Image();img.crossOrigin='anonymous';
    img.onload=()=>{try{
      const canvas=document.createElement('canvas');canvas.width=8;canvas.height=8;
      const ctx=canvas.getContext('2d');ctx.drawImage(img,0,0,8,8);
      const data=ctx.getImageData(0,0,8,8).data;let r=0,g=0,b=0;
      for(let i=0;i<data.length;i+=4){r+=data[i];g+=data[i+1];b+=data[i+2];}
      const color='rgb('+Math.round(r/64*.32)+','+Math.round(g/64*.32)+','+Math.round(b/64*.32)+')';
      holder.style.backgroundImage='linear-gradient(150deg,'+color+',#141818)';
      if(holder.classList.contains('xm-selection-dialog'))holder.style.setProperty('--xm-list-color',color);
    }catch(_){}};img.src=address;
  }

  function rankArchive(items) {
    const fav=mixTracks('favorites'),hist=mixTracks('history');
    const favIds=new Set(fav.map(t=>t.id)),favKeys=new Set(fav.map(selectionKey).filter(Boolean));
    const histIds=new Set(hist.map(t=>t.id)),histKeys=new Set(hist.map(selectionKey).filter(Boolean));
    const byId=new Map(),byKey=new Map();[...fav,...hist].forEach(t=>{if(t.id&&!byId.has(t.id))byId.set(t.id,t);const key=selectionKey(t);if(key&&!byKey.has(key))byKey.set(key,t);});
    const week=Math.floor(Date.now()/604800000);
    const hash=id=>{let h=2166136261;for(const c of String(id)+':'+week)h=Math.imul(h^c.charCodeAt(0),16777619);return h>>>0;};
    return items.filter(t=>t&&String(t.id||'').startsWith('drive:')).map(t=>{
      const key=selectionKey(t),liked=favIds.has(t.id)||(key&&favKeys.has(key)),heard=histIds.has(t.id)||(key&&histKeys.has(key));
      const shared=byId.get(t.id)||(key&&byKey.get(key));
      return {track:Object.assign({},t,{artwork:t.artwork||shared?.artwork||''}),score:liked?2:heard?1:0,order:hash(t.id)};
    }).sort((a,b)=>b.score-a.score||a.order-b.order).slice(0,30).map(x=>x.track);
  }
  function showArchiveSelections(tracks) {
    modal('Arşivden Seçkiler',panel=>{
      panel.append(create('p','Aile beğenileri ve dinleme geçmişine öncelik verilir; diğer parçalar haftalık değişir.'));
      if(!tracks.length){panel.append(create('p','Henüz arşivde parça bulunamadı. Drive bağlantısını ve arşiv taramasını kontrol et.'));return;}
      const snapshot=tracks.filter(t=>t.title&&t.artist).slice(0,1000).map(t=>({title:t.title,artist:t.artist}));
      selectionChrome(panel,'Arşivden Seçkiler',tracks,'#315342',()=>N.playTurkeyChartQueue(JSON.stringify(snapshot),0),(holder,term)=>{
        tracks.filter(t=>!term||((t.title||'')+' '+(t.artist||'')).toLocaleLowerCase('tr').includes(term)).forEach(t=>holder.append(picturedTrackRow(t)));
      },tracks.length,'Aile zevkinden ilhamla');
    });
  }
  function renderChildLibrary(){
    const data=profileState(),active=data.profiles?.find(p=>p.id===data.active),panel=content((active?.name||'Çocuk')+' · Onaylı Kitaplık');if(!panel)return;
    panel.append(create('p','Yalnızca ebeveynin onayladığı kayıtlar çalınabilir. Günlük sınır: '+(active?.dailyMinutes||60)+' dakika.'),create('p','Bugün dinlenen: '+(active?.usedMinutes||0)+' dakika.'));
    const status=create('p','');status.id='xm-child-status';panel.append(status,button('Profil değiştir / Ebeveyn girişi',()=>go('aile_profili_se_imi')));
    if(A.requestChildReview){
      const request=create('input',undefined,'xm-input');request.placeholder='İstediğin şarkı ve sanatçı';request.maxLength=180;request.setAttribute('aria-label','Ebeveynden şarkı iste');
      panel.append(request,button('Ebeveynden iste',()=>{const title=request.value.trim();if(!title)return;N.showToast(A.requestChildReview(data.active,JSON.stringify({title}))?'İstek bu cihazdaki ebeveyn onay listesine eklendi.':'İstek eklenemedi.');request.value='';}));
      let review={};try{review=JSON.parse(A.childReview(data.active)||'{}');}catch(_){}
      panel.append(create('p',(review.pending||[]).length+' istek onay bekliyor. Ebeveyn bu cihazdan inceleyebilir.'));
    }
    let tracks=[];try{tracks=JSON.parse(A.childTracks(data.active)||'[]');}catch(_){}
    if(!Array.isArray(tracks)||!tracks.length)panel.append(create('p','Henüz onaylanmış kayıt yok. Ebeveyn profilinden içerik ekleyin.'));
    else tracks.forEach(t=>panel.append(trackRow(t)));
    document.querySelectorAll('nav a').forEach(a=>{if(!['ana-sayfa','kitapligim'].includes(a.dataset.path))a.style.display='none';});
  }
  let rssRequest=Number(sessionStorage.getItem('xm-pod-rss-sequence'))||0, downloadSnapshot='';
  // Podcast ekranları aynı RSS, profil ve Media3 köprüsünü kullanır.
  let podcastRequest=Number(sessionStorage.getItem('xm-pod-search-sequence'))||0, podcastQuery='', podcastTimer;
  const podSearchCallbacks=new Map(),podRssCallbacks=new Map();
  function openFeed(feed){
    if(!feed.url){sessionStorage.setItem('xm-podcast-search',feed.title||'');go('podcast_merkezi');N.showToast('Podcast kaynağını arayıp seç.');return;}
    sessionStorage.setItem('xm-feed',feed.url);sessionStorage.setItem('xm-feed-info',JSON.stringify(feed));go('podcast_program_ve_b_l_m_detay');
  }
  function toggleFeed(feed,control){
    const previous=library.feeds.slice(),index=library.feeds.findIndex(f=>f.url===feed.url);
    if(index<0)library.feeds.push({...feed});else library.feeds.splice(index,1);
    if(!save()){library.feeds=previous;return false;}
    if(control){control.textContent=index<0?'Takibi Bırak':'Takip Et';control.setAttribute('aria-pressed',String(index<0));}return true;
  }
  function podSearch(query,callback){const id=++podcastRequest;sessionStorage.setItem('xm-pod-search-sequence',String(id));podSearchCallbacks.set(id,callback);A.searchPodcasts(query,id);return id;}
  function podRss(feed,callback){const id=++rssRequest;sessionStorage.setItem('xm-pod-rss-sequence',String(id));podRssCallbacks.set(id,callback);A.loadRss(feed,id);return id;}
  function installPodCallbacks(){
    window.__xmusicPodcastSearchResult=(id,payload)=>{const cb=podSearchCallbacks.get(id);podSearchCallbacks.delete(id);if(cb)cb(payload);};
    window.__xmusicRssResult=(id,payload)=>{const cb=podRssCallbacks.get(id);podRssCallbacks.delete(id);if(cb)cb(payload);};
  }
  const podIconPaths={play_arrow:'M8 5v14l11-7z',pause:'M6 5h4v14H6zM14 5h4v14h-4z',download:'M11 3h2v10l4-4 1.4 1.4L12 17l-6.4-6.6L7 9l4 4zM4 19h16v2H4z',search:'M10 2a8 8 0 1 0 4.9 14.3L21 22l1-1-5.7-6.1A8 8 0 0 0 10 2zm0 2a6 6 0 1 1 0 12 6 6 0 0 1 0-12z',close:'M6 4.6 12 10.6 18 4.6 19.4 6 13.4 12 19.4 18 18 19.4 12 13.4 6 19.4 4.6 18 10.6 12 4.6 6z',check:'M9 16.2 4.8 12 3.4 13.4 9 19 21 7 19.6 5.6z',more_horiz:'M5 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm7 0a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm7 0a2 2 0 1 0 0 4 2 2 0 0 0 0-4z'};
  function podSetIcon(control,icon){control?.querySelector('svg path')?.setAttribute('d',podIconPaths[icon]||podIconPaths.play_arrow);}
  function podIcon(label,icon,fn,primary=false){
    const b=button('',fn);b.className='xm-pod-icon'+(primary?' xm-pod-play':'');b.setAttribute('aria-label',label);b.title=label;
    const svg=document.createElementNS('http://www.w3.org/2000/svg','svg'),path=document.createElementNS('http://www.w3.org/2000/svg','path');svg.setAttribute('viewBox','0 0 24 24');svg.setAttribute('width','23');svg.setAttribute('height','23');svg.setAttribute('fill','currentColor');svg.setAttribute('aria-hidden','true');path.setAttribute('d',podIconPaths[icon]||podIconPaths.play_arrow);svg.append(path);b.append(svg);return b;
  }
  function podImage(address,cls='',track=null){const img=create('img',undefined,cls);img.loading='lazy';img.decoding='async';if(track)artworkForTrack(img,track);else setArtwork(img,address);return img;}
  function podSection(root,title,subtitle=''){const section=create('section',undefined,'xm-pod-section');section.append(create('h2',title));if(subtitle)section.append(create('p',subtitle,'xm-pod-muted'));root.append(section);return section;}
  function podDate(value){const d=new Date(value);return !value||isNaN(d.getTime())?'':d.toLocaleDateString('tr-TR',{day:'numeric',month:'short',year:'numeric'});}
  function podDuration(value){if(!value)return '';if(/^\d+$/.test(String(value)))return Math.ceil(Number(value)/60)+' dk';return value;}
  function podProgress(id){const saved=library.podcastProgress?.[id],resume=library.resume?.id===id?library.resume:null;return resume&&(!saved||(resume.updatedAt||0)>(saved.updatedAt||0))?resume:saved||{};}
  function podCompleted(id){const p=podProgress(id);return !!p.completed||(p.duration>0&&p.position/p.duration>=.98);}
  function podPartial(id){const p=podProgress(id);return p.position>0&&!podCompleted(id);}
  function podDownloads(){try{return JSON.parse(A.listDownloads()||'[]').filter(d=>d.status===8);}catch(_){return [];}}
  function podResume(item){
    if(state.id===item.id){N.togglePlayPause();return;}
    const p=podProgress(item.id);A.resumePodcast(item.feed,item.id,podCompleted(item.id)?0:p.position||0);
  }
  function podMount(){
    installPodCallbacks();
    const main=document.querySelector('main');if(!main)return null;
    const mini=main.querySelector('aside');
    if(mini){
      ['.text-label-lg','.text-body-sm'].forEach(sel=>{const n=mini.querySelector(sel);if(n&&n.tagName!=='P'){const p=create('p',n.textContent,n.className);n.replaceWith(p);}});
      mini.querySelectorAll('span.absolute,[aria-label="Cihaza Bağlan"]').forEach(n=>n.remove());
      const play=mini.querySelector('#miniPlayBtn');if(play)play.setAttribute('aria-label','Oynat veya Duraklat');
      mini.style.maxWidth='720px';mini.style.borderRadius='14px';
    }
    main.replaceChildren();const root=create('div',undefined,'xm-pod');main.append(root);if(mini)main.append(mini);
    const title=document.querySelector('header h1');if(title)title.textContent='Podcast';
    document.body.classList.add('xm-pod-page');
    document.querySelectorAll('nav a[data-path]').forEach(a=>{const active=a.dataset.path==='podcast';a.classList.toggle('text-primary',active);if(active)a.setAttribute('aria-current','page');else a.removeAttribute('aria-current');});
    if(!document.getElementById('xm-pod-style')){
      const css=create('style');css.id='xm-pod-style';css.textContent=`
      body.xm-pod-page,body.xm-pod-page main{background:#101111!important;color:#f7f7f7}body.xm-pod-page main{padding-bottom:0!important}.xm-pod,.xm-pod *{box-sizing:border-box}.xm-pod{font-family:Inter,system-ui,sans-serif;padding:24px 18px 160px;width:100%;max-width:1080px;margin:auto;font-size:14px;line-height:1.5}.xm-pod h1{font-size:32px;font-weight:800;letter-spacing:-.8px}.xm-pod h2{font-size:22px;font-weight:750;letter-spacing:-.4px}.xm-pod h3{font-size:16px;font-weight:700}.xm-pod p{margin:0}.xm-pod-muted{color:#a7afad;font-size:13px}.xm-pod-section{margin:28px 0}.xm-pod-section>h2{margin-bottom:12px}.xm-pod-section>p{margin-top:-5px;margin-bottom:14px}.xm-pod button,.xm-pod input,.xm-pod select{font:inherit}.xm-pod button{cursor:pointer;-webkit-tap-highlight-color:transparent}.xm-pod button:focus-visible,.xm-pod input:focus-visible,.xm-pod select:focus-visible{outline:2px solid #43ed80;outline-offset:3px}.xm-pod button:disabled{opacity:.4}.xm-pod-icon{border:0;background:transparent;color:#c4cbc8;min-width:44px;min-height:44px;border-radius:50%;display:inline-flex;align-items:center;justify-content:center;flex-shrink:0}.xm-pod-play{background:#23df68;color:#06180d}.xm-pod-play .material-symbols-outlined{font-size:27px;font-variation-settings:'FILL' 1}.xm-pod-chips{display:flex;gap:8px;overflow-x:auto;scrollbar-width:none;padding:4px 1px 10px}.xm-pod-chip{border:0;background:#292c2b;color:#eee;border-radius:24px;padding:10px 16px;white-space:nowrap;min-height:44px}.xm-pod-chip[aria-pressed=true]{background:#23df68;color:#092111;font-weight:700}.xm-pod-search{display:flex;gap:8px;align-items:center;margin:16px 0}.xm-pod input{min-width:0;width:100%;padding:13px 15px;background:#252928;border:1px solid transparent;border-radius:12px;color:white}.xm-pod-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.xm-pod-cover{width:100%;aspect-ratio:1;object-fit:cover;border-radius:12px;background:#222}.xm-pod-follow{position:relative;min-width:0}.xm-pod-follow>button:first-child{width:100%;text-align:left;border:0;background:transparent;color:#fff}.xm-pod-follow strong{display:block;font-size:12px;margin-top:6px;line-height:1.4}.xm-pod-follow .xm-pod-icon{position:absolute;right:4px;top:4px;background:#101111bb;min-width:32px;min-height:32px}.xm-pod-add{border:1px dashed #65716a!important;border-radius:12px;aspect-ratio:1;background:#1c211f!important;display:flex;align-items:center;justify-content:center;flex-direction:column;color:#c8d3cd!important}.xm-pod-rail{display:flex;gap:14px;overflow-x:auto;padding:2px 0 14px;scroll-snap-type:x proximity;scrollbar-width:thin}.xm-pod-rail>.xm-pod-program{flex:0 0 156px;scroll-snap-align:start}.xm-pod-program{border:0;background:#191d1b;border-radius:14px;overflow:hidden;padding:12px;min-width:0;color:#fff}.xm-pod-program>button:first-child{background:none;border:0;color:inherit;text-align:left;width:100%}.xm-pod-program .xm-pod-chip{margin-top:12px;padding:7px 12px;min-height:38px;font-size:12px}.xm-pod-program h3{margin:10px 0 2px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.xm-pod-categories{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin:14px 0}.xm-pod-category-rail{display:flex!important;overflow-x:auto;padding-bottom:8px;scrollbar-width:thin}.xm-pod-category-rail>button{flex:0 0 150px}.xm-pod-category{border:0;border-radius:12px;color:#fff;text-align:left;padding:18px 14px;min-height:76px;font-weight:750!important}.xm-pod-episodes{display:flex;flex-direction:column;gap:12px}.xm-pod-episode{background:#191d1b;border-radius:16px;padding:15px}.xm-pod-episode-top{display:flex;gap:12px;align-items:start}.xm-pod-episode-top img{width:64px;height:64px;border-radius:9px;flex:none}.xm-pod-copy{min-width:0;flex:1}.xm-pod-copy h3,.xm-pod-copy h4{font-size:15px;font-weight:700;line-height:1.45;overflow-wrap:anywhere}.xm-pod-description{color:#a7afad;font-size:13px;margin-top:10px!important;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.xm-pod-actions{display:flex;align-items:center;justify-content:space-between;margin-top:10px;gap:8px}.xm-pod-actions>div{display:flex;gap:3px;flex-shrink:0}.xm-pod-actions>span{min-width:0;flex:1;line-height:1.5}.xm-pod h1,.xm-pod h2,.xm-pod h3,.xm-pod h4{margin-top:0}.xm-pod-section h2{overflow-wrap:anywhere}.xm-pod-progress{height:3px;background:#3b4540;border-radius:4px;overflow:hidden;margin-top:12px}.xm-pod-progress>i{height:100%;display:block;background:#23df68}.xm-pod-episode[data-playing=true] h3,.xm-pod-episode[data-playing=true] h4{color:#23df68}.xm-pod-hero{padding:22px 18px 25px;margin:-24px -18px 12px;text-align:center;background:linear-gradient(#17443f,#101111);border-radius:0 0 22px 22px}.xm-pod-hero>img{width:190px;height:190px;border-radius:14px;box-shadow:0 15px 35px #0005;margin:0 auto 22px;will-change:transform}.xm-pod-hero h2{font-size:28px;line-height:1.2;margin-bottom:12px}.xm-pod-hero .xm-pod-chip{margin-top:15px}.xm-pod-tabs{display:flex;position:sticky;top:64px;z-index:12;background:#101111f5;border-bottom:1px solid #323733;gap:18px;overflow-x:auto}.xm-pod-tabs button{background:none;border:0;border-bottom:3px solid transparent;color:#a7afad;white-space:nowrap;padding:14px 0}.xm-pod-tabs button[aria-selected=true]{border-color:#23df68;color:#fff;font-weight:700}.xm-pod-toolbar{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin:14px 0}.xm-pod-toolbar select{background:#292c2b;color:#fff;border:0;border-radius:22px;padding:12px;min-height:44px}.xm-pod-empty{padding:22px 0;color:#a7afad}.xm-pod-backtop{position:fixed;bottom:160px;left:50%;transform:translateX(-50%);z-index:30;background:#f3f7f4;color:#131b16;border:0;border-radius:30px;padding:10px 18px;box-shadow:0 4px 20px #0006;font-size:13px}.xm-pod-backtop[hidden],.xm-pod [hidden]{display:none!important}.xm-pod-page header{background:#101111ed;transition:background-color .2s}.xm-pod-page.xm-pod-scrolled header{background:#15352df5}.xm-pod-page.xm-pod-scrolled header h1{font-size:15px}.xm-pod-picker{max-width:720px!important;background:#101111!important;border:1px solid #414a44!important}.xm-pod-picker .xm-pod{padding:0 0 16px}.xm-pod-picker .xm-pod-section{margin:18px 0}.xm-pod-picker .xm-pod-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.xm-pod-picker footer{position:sticky;bottom:-20px;background:#101111;padding:12px 0}.xm-pod-picker footer button{width:100%;background:#23df68;color:#092111;border:0;border-radius:26px;padding:12px;font-weight:750}.xm-pod-page header img{flex-shrink:0}@media(min-width:650px){.xm-pod{padding:28px 28px 160px}.xm-pod-grid{grid-template-columns:repeat(6,minmax(0,1fr))}.xm-pod-categories{grid-template-columns:repeat(4,minmax(0,1fr))}.xm-pod-episodes{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));align-items:start}.xm-pod-hero{margin:-28px -28px 12px}.xm-pod-hero>img{width:230px;height:230px}.xm-pod-rail>.xm-pod-program{flex-basis:190px}}@media(prefers-reduced-motion:reduce){.xm-pod-hero>img{transform:none!important;opacity:1!important}.xm-pod-page header{transition:none}}`;
      document.head.append(css);
    }
    try{const native=JSON.parse(A.loadPlaybackResume()||'{}');if(native.id&&(!library.resume||(native.updatedAt||0)>(library.resume.updatedAt||0)))library.resume=native;}catch(_){}
    return root;
  }
  function podProgram(feed,onChange){
    const card=create('article',undefined,'xm-pod-program'),open=button('',()=>openFeed(feed));open.setAttribute('aria-label','Bölümleri Aç');
    open.append(podImage(feed.artwork,'xm-pod-cover',feed),create('h3',feed.title||'Podcast'),create('p',feed.artist||feed.genre||'','xm-pod-muted'));
    const follow=button('',()=>{if(toggleFeed(feed,follow))onChange?.();});follow.className='xm-pod-chip';follow.textContent=library.feeds.some(f=>f.url===feed.url)?'Takibi Bırak':'Takip Et';follow.setAttribute('aria-pressed',String(library.feeds.some(f=>f.url===feed.url)));
    card.append(open,follow);return card;
  }
  function podEpisode(item,play,onDownload,featured=false){
    const card=create('article',undefined,'xm-pod-episode');card.dataset.episode=item.id;card.dataset.playing=String(state.id===item.id);
    const top=create('div',undefined,'xm-pod-episode-top'),text=create('div',undefined,'xm-pod-copy');
    text.append(create('h4',item.title||'Bölüm'),create('p',item.artist||'','xm-pod-muted'));
    top.append(podImage(item.artwork,'',item),text);card.append(top);
    if(item.description)card.append(create('p',item.description,'xm-pod-description'));
    const p=podProgress(item.id),details=[podDate(item.published),podDuration(item.duration)];
    if(podCompleted(item.id))details.push('Dinlendi');else if(p.duration>p.position&&p.position>0)details.push(Math.ceil((p.duration-p.position)/60000)+' dk kaldı');
    const actions=create('div',undefined,'xm-pod-actions'),controls=create('div');
    controls.append(podIcon('Bölüm işlemleri','more_horiz',()=>modal('Bölüm işlemleri',(panel,close)=>{
      panel.append(create('h3',item.title||'Bölüm'),button('Sıraya Ekle',()=>{A.playPodcast(item.id,true);close();}),button('Listeye Ekle',()=>{close();addToPlaylist(item);}),button(podCompleted(item.id)?'Dinlenmedi olarak işaretle':'Dinlendi olarak işaretle',()=>{
        library.podcastProgress||={};library.podcastProgress[item.id]={...p,completed:!podCompleted(item.id),position:0,updatedAt:Date.now()};save();close();window.__xmusicPodcastRefresh?.();
      }));
    })),podIcon('İndir','download',()=>onDownload?onDownload():A.downloadPodcast(item.id)),podIcon('Oynat','play_arrow',play,true));
    actions.append(create('span',details.filter(Boolean).join(' · '),'xm-pod-muted'),controls);card.append(actions);
    if(state.id===item.id&&state.playing)podSetIcon(controls.querySelector('[aria-label="Oynat"]'),'pause');
    const progress=create('div',undefined,'xm-pod-progress'),bar=create('i');bar.style.width=(p.duration>0?Math.min(100,p.position/p.duration*100):podCompleted(item.id)?100:0)+'%';progress.append(bar);card.append(progress);
    if(featured)tintFromArtwork(card,item.artwork);return card;
  }
  const podCategories=[['Bilim ve Teknoloji','Bilim teknoloji','#215b79'],['Kültür ve Sanat','Kültür sanat','#9a611f'],['Komedi','Komedi','#685081'],['Haber ve Gündem','Haber gündem','#863537'],['Tarih','Tarih','#663e8c'],['Kişisel Gelişim','Kişisel gelişim','#266c60'],['İş ve Ekonomi','Ekonomi','#98512a'],['Öykü ve Edebiyat','Edebiyat','#7b3659']];
  function podGenreQuery(genre){const value=(genre||'').toLocaleLowerCase('tr');return /comedy|komedi/.test(value)?'Komedi':/techn|bilim|science/.test(value)?'Bilim teknoloji':/histor|tarih/.test(value)?'Tarih':/news|haber/.test(value)?'Haber gündem':/business|ekonomi/.test(value)?'Ekonomi':/art|kültür|culture/.test(value)?'Kültür sanat':genre||'Türkçe podcast';}
  function podPreferredGenre(){
    const scores=new Map();library.feeds.forEach(feed=>{
      if(!feed.genre)return;let score=2+library.history.filter(t=>t.feed===feed.url).length;
      score+=(library.spotifyPreferences?.podcastEvents||[]).filter(t=>t.artist===feed.title).length;
      scores.set(feed.genre,(scores.get(feed.genre)||0)+score);
    });return [...scores].sort((a,b)=>b[1]-a[1])[0]?.[0]||'';
  }
  function renderPodcasts(){
    const root=podMount();if(!root)return;
    let filter='all',searchGeneration=0;
    root.append(create('h1','Podcast'));
    const chips=create('div',undefined,'xm-pod-chips');chips.id='podcast-chips';root.append(chips);
    const following=podSection(root,'Takip ettiklerin'),followingGrid=create('div',undefined,'xm-pod-grid');followingGrid.id='xm-podcast-following';following.append(followingGrid);
    const searchArea=podSection(root,'Yeni bir şey keşfet'),form=create('form',undefined,'xm-pod-search'),input=create('input');input.type='search';input.id='xm-podcast-search';input.maxLength=100;input.placeholder='Program adı veya yayıncı ara';input.setAttribute('aria-label','Podcast ara');
    const submit=podIcon('Ara','search',searchNow),clear=podIcon('Podcast aramasını temizle','close',()=>{input.value='';searchNow();});form.append(input,submit,clear);searchArea.append(form);
    const results=create('div');results.id='xm-podcast-results';results.setAttribute('aria-live','polite');searchArea.append(results);
    const categoryGrid=create('div',undefined,'xm-pod-categories xm-pod-category-rail');searchArea.append(categoryGrid);
    podCategories.forEach(([label,term,color])=>{const b=button(label,()=>{input.value=term;searchNow();});b.className='xm-pod-category';b.style.background=color;categoryGrid.append(b);});
    const rss=button('RSS adresi ekle',()=>editName('HTTPS RSS adresi','',address=>{try{const u=new URL(address);if(u.protocol!=='https:')throw Error();}catch(_){N.showToast('Geçerli bir HTTPS RSS adresi gir.');return;}const f={url:address,title:address};if(!library.feeds.some(x=>x.url===address)&&toggleFeed(f))drawFollowing();openFeed(f);}));rss.className='xm-pod-chip';searchArea.append(rss);
    const continueSection=podSection(root,'Kaldığın yerden devam et'),continueList=create('div',undefined,'xm-pod-episodes');continueSection.append(continueList);
    const latestSection=podSection(root,'Takip ettiklerinden yeni bölümler','Yayıncıların RSS kaynaklarından'),latestList=create('div',undefined,'xm-pod-episodes');latestList.id='xm-podcast-latest';latestSection.append(latestList);
    const recentSection=podSection(root,'Son dinlenenler'),recentList=create('div',undefined,'xm-pod-episodes');recentSection.append(recentList);
    const recommendations=podSection(root,'Hoşuna gidebilecek programlar','Takip ettiğin kategorilerden keşif'),recommendedGrid=create('div',undefined,'xm-pod-rail');recommendations.append(recommendedGrid);
    const recommendedEpisodes=podSection(root,'Beğenebileceğin bölümler','Dinlediğin kategorilerdeki programlardan'),recommendedEpisodeList=create('div',undefined,'xm-pod-episodes');recommendedEpisodes.append(recommendedEpisodeList);
    root.insertBefore(continueSection,searchArea);root.insertBefore(latestSection,searchArea);root.insertBefore(recentSection,searchArea);
    let downloads=podDownloads(),feedQueue=[],loadedFeeds=new Set(),inflight=0,latest=[],recommendationTerm='';
    function feedFor(t){return library.feeds.find(f=>f.url===t.feed)||{url:t.feed,title:t.artist||'Podcast',artwork:t.artwork||''};}
    function historyRow(t){const item={...t,artwork:t.artwork||feedFor(t).artwork};return podEpisode(item,()=>podResume(item));}
    function drawSections(){
      const history=library.history.filter(t=>t.id?.startsWith('podcast:')&&t.feed);
      continueList.replaceChildren();history.filter(t=>podPartial(t.id)).slice(0,6).forEach(t=>continueList.append(historyRow(t)));
      continueSection.hidden=!continueList.children.length||!['all','partial'].includes(filter);
      recentList.replaceChildren();history.slice(0,8).forEach(t=>recentList.append(historyRow(t)));recentSection.hidden=!history.length||filter!=='all';
      latestList.replaceChildren();let rows=latest.filter(t=>library.feeds.some(f=>f.url===t.feed));
      if(filter==='partial')rows=rows.filter(t=>podPartial(t.id));
      if(filter==='downloads')rows=downloads;
      if(filter==='new')rows=rows.filter(t=>!podCompleted(t.id)&&!library.history.some(h=>h.id===t.id));
      latestSection.querySelector('h2').textContent=filter==='downloads'?'İndirilen bölümler':filter==='partial'?'Yarım kalan bölümler':'Takip ettiklerinden yeni bölümler';
      const shown=rows.sort((a,b)=>(Date.parse(b.published)||0)-(Date.parse(a.published)||0)).slice(0,20);
      shown.forEach(t=>latestList.append(podEpisode(t,()=>{if(state.id===t.id){N.togglePlayPause();return;}const p=podProgress(t.id);A.playPodcastSequence(JSON.stringify(shown.map(x=>x.id)),shown.indexOf(t),podCompleted(t.id)?0:p.position||0);},undefined,true)));
      if(!rows.length)latestList.append(create('p',inflight||feedQueue.length?'Bölümler yükleniyor…':filter==='downloads'?'Henüz indirilmiş bölüm yok.':filter==='partial'?'Yarım kalan bölüm yok.':'Program takip ettiğinde yeni bölümler burada görünecek.','xm-pod-empty'));
      more.hidden=library.feeds.every(f=>loadedFeeds.has(f.url)||feedQueue.some(q=>q.url===f.url));
      following.hidden=!['all','following'].includes(filter);searchArea.hidden=filter!=='all';recommendations.hidden=filter!=='all';recommendedEpisodes.hidden=filter!=='all';latestSection.hidden=filter==='following';
    }
    function pumpFeeds(){
      while(inflight<2&&feedQueue.length){const feed=feedQueue.shift();if(loadedFeeds.has(feed.url))continue;loadedFeeds.add(feed.url);inflight++;
        podRss(feed.url,payload=>{inflight--;if(!root.isConnected)return;
          if(!payload.error){const artwork=payload.artwork||feed.artwork||'';A.setPodcastArtwork(feed.url,artwork);let changed=false;if(payload.artwork&&feed.artwork!==payload.artwork){feed.artwork=payload.artwork;changed=true;}if(payload.title&&feed.title!==payload.title){feed.title=payload.title;changed=true;}if(changed){save();drawFollowing(false);}latest.push(...(payload.items||[]).slice(0,5).filter(t=>!latest.some(x=>x.id===t.id)).map(t=>({...t,feed:feed.url,artwork:t.artwork||artwork})));}
          else {const note=create('p',(feed.title||'Program')+': '+payload.error,'xm-pod-muted');note.append(button('Tekrar Dene',()=>{note.remove();loadedFeeds.delete(feed.url);feedQueue.push(feed);pumpFeeds();}));latestSection.append(note);}
          drawSections();pumpFeeds();
        });
      }
    }
    function loadMoreFeeds(){const unrequested=library.feeds.filter(f=>f.url&&!loadedFeeds.has(f.url)&&!feedQueue.some(q=>q.url===f.url)).slice(0,8);feedQueue.push(...unrequested);pumpFeeds();}
    function discover(){const term=podGenreQuery(podPreferredGenre());if(recommendationTerm===term)return;recommendationTerm=term;recommendedGrid.textContent='Programlar yükleniyor…';podSearch(term,payload=>{
      if(!root.isConnected||recommendationTerm!==term)return;recommendedGrid.replaceChildren();
      const items=(payload.items||[]).filter(t=>!library.feeds.some(f=>f.url===t.feed));items.slice(0,10).forEach(t=>recommendedGrid.append(podProgram({url:t.feed,title:t.title,artist:t.artist,genre:t.genre,artwork:t.artwork},()=>{drawFollowing();drawSections();})));
      recommendedEpisodeList.replaceChildren();if(!items.length)recommendedEpisodes.hidden=true;
      items.slice(0,2).forEach(t=>podRss(t.feed,payload=>{if(!root.isConnected||recommendationTerm!==term||payload.error)return;A.setPodcastArtwork(t.feed,t.artwork||'');(payload.items||[]).slice(0,2).forEach(episode=>{const item={...episode,feed:t.feed,artwork:episode.artwork||payload.artwork||t.artwork};recommendedEpisodeList.append(podEpisode(item,()=>podResume(item),undefined,true));});}));
      if(!items.length){recommendedGrid.append(create('p',payload.error||'Yeni program bulmak için kategorilerden birini seç.','xm-pod-muted'));if(payload.error)recommendedGrid.append(button('Tekrar Dene',()=>{recommendationTerm='';discover();}));}
    });}
    function drawFollowing(load=true){
      followingGrid.replaceChildren();library.feeds.forEach(feed=>{const box=create('div',undefined,'xm-pod-follow'),open=button('',()=>openFeed(feed));open.append(podImage(feed.artwork,'xm-pod-cover',feed),create('strong',feed.title||'Podcast'));box.append(open,podIcon('Takibi Bırak','check',()=>{if(toggleFeed(feed)){drawFollowing();drawSections();}}));followingGrid.append(box);});
      const add=button('',showPicker);add.className='xm-pod-add';add.setAttribute('aria-label','Podcast seç');const plus=create('span','+');plus.style.fontSize='36px';add.append(plus,create('strong','Podcast seç'));followingGrid.append(add);
      if(load){loadMoreFeeds();discover();}
    }
    function searchNow(){
      clearTimeout(podcastTimer);const query=input.value.trim(),generation=++searchGeneration;podcastQuery=query;results.replaceChildren();categoryGrid.hidden=!!query;
      if(query.length<2){if(query)results.textContent='En az iki karakter yaz.';return;}
      results.textContent='Podcast’ler aranıyor…';podSearch(query,payload=>{if(generation!==searchGeneration||!root.isConnected)return;results.replaceChildren();if(payload.error){results.append(create('p',payload.error),button('Tekrar Dene',searchNow));return;}
        const rail=create('div',undefined,'xm-pod-rail');(payload.items||[]).forEach(t=>rail.append(podProgram({url:t.feed,title:t.title,artist:t.artist,artwork:t.artwork,genre:t.genre},()=>{drawFollowing();drawSections();})));results.append(rail);if(!rail.children.length)results.append(create('p','Açık RSS kaynağı olan program bulunamadı.','xm-pod-muted'));
      });
    }
    function showPicker(){modal('Podcast seç',(panel,close)=>{
      panel.classList.add('xm-pod-picker');const picker=create('div',undefined,'xm-pod'),searchForm=create('form',undefined,'xm-pod-search'),field=create('input');field.type='search';field.placeholder='Program veya konu ara';field.maxLength=100;field.setAttribute('aria-label','Podcast seçimi için ara');
      const items=create('div',undefined,'xm-pod-grid'),categories=create('div',undefined,'xm-pod-categories');let generation=0;
      function run(term){field.value=term;const version=++generation;items.textContent='Programlar yükleniyor…';podSearch(term,payload=>{if(version!==generation||!picker.isConnected)return;items.replaceChildren();if(payload.error){items.append(create('p',payload.error),button('Tekrar Dene',()=>run(term)));return;}(payload.items||[]).forEach(t=>{const feed={url:t.feed,title:t.title,artwork:t.artwork,artist:t.artist,genre:t.genre};items.append(podProgram(feed,()=>{drawFollowing();drawSections();}));});if(!items.children.length)items.textContent='Program bulunamadı.';});}
      searchForm.append(field,podIcon('Ara','search',()=>{if(field.value.trim().length>=2)run(field.value.trim());}));searchForm.addEventListener('submit',e=>{e.preventDefault();if(field.value.trim().length>=2)run(field.value.trim());});
      podCategories.forEach(([label,term,color])=>{const b=button(label,()=>run(term));b.className='xm-pod-category';b.style.background=color;categories.append(b);});
      picker.append(searchForm,categories,items);panel.append(picker);const footer=create('footer');footer.append(button('Bitti',close));panel.append(footer);run(podGenreQuery(podPreferredGenre()));
    });}
    [['all','Tümü'],['following','Takip Ettiklerim'],['new','Yeni Bölümler'],['partial','Yarım Kalanlar'],['downloads','İndirilenler']].forEach(([key,label])=>{const b=button(label,()=>{filter=key;downloads=podDownloads();chips.querySelectorAll('button').forEach(x=>x.setAttribute('aria-pressed',String(x===b)));drawSections();});b.className='xm-pod-chip';b.setAttribute('aria-pressed',String(key===filter));chips.append(b);});
    const more=button('Diğer takiplerimin bölümlerini yükle',()=>{loadMoreFeeds();drawSections();});more.className='xm-pod-chip';latestSection.append(more);
    form.addEventListener('submit',e=>{e.preventDefault();searchNow();});input.addEventListener('input',()=>{searchGeneration++;clearTimeout(podcastTimer);podcastTimer=setTimeout(searchNow,650);});
    window.__xmusicPodcastRefresh=drawSections;drawFollowing();drawSections();
    input.value=sessionStorage.getItem('xm-podcast-search')||podcastQuery;sessionStorage.removeItem('xm-podcast-search');if(input.value.trim().length>=2)searchNow();
  }
  function renderFeed(){
    const root=podMount();if(!root)return;const address=sessionStorage.getItem('xm-feed');let info={url:address,title:'Podcast'};
    try{const v=JSON.parse(sessionStorage.getItem('xm-feed-info')||'null');if(v?.url===address)info=v;}catch(_){}
    const identity=create('div',undefined,'xm-pod-hero'),cover=podImage(info.artwork),title=create('h2',info.title||'Podcast'),author=create('p',info.artist||'','xm-pod-muted'),description=create('p','Program bilgileri yükleniyor…','xm-pod-description');description.id='showDescription';
    const follow=button('',()=>{toggleFeed(info,followText);updateFollow();});follow.className='xm-pod-chip';follow.id='followBtn';follow.hidden=!address;const followText=create('span');followText.id='followText';follow.append(followText);
    function updateFollow(){const active=library.feeds.some(f=>f.url===address);followText.textContent=active?'Takip Ediliyor':'Takip Et';follow.setAttribute('aria-pressed',String(active));}updateFollow();identity.append(cover,title,author,description,follow);root.append(identity);tintFromArtwork(identity,info.artwork);
    const tabs=create('div',undefined,'xm-pod-tabs');tabs.setAttribute('role','tablist');root.append(tabs);const panels=['Bölümler','Hakkında','Benzer Programlar'].map((label,i)=>{const panel=create('section');panel.id='xm-pod-tab-'+i;panel.setAttribute('role','tabpanel');panel.hidden=i!==0;const b=button(label,()=>{panels.forEach((p,j)=>p.hidden=j!==i);tabs.querySelectorAll('button').forEach((t,j)=>{t.setAttribute('aria-selected',String(j===i));t.tabIndex=j===i?0:-1;});if(i===2)loadSimilar();});b.id='xm-pod-tab-button-'+i;b.setAttribute('role','tab');b.setAttribute('aria-selected',String(i===0));b.setAttribute('aria-controls',panel.id);panel.setAttribute('aria-labelledby',b.id);b.tabIndex=i===0?0:-1;tabs.append(b);root.append(panel);return panel;});
    tabs.addEventListener('keydown',e=>{if(!['ArrowLeft','ArrowRight','Home','End'].includes(e.key))return;const buttons=[...tabs.querySelectorAll('button')],current=buttons.indexOf(document.activeElement);if(current<0)return;e.preventDefault();const next=e.key==='Home'?0:e.key==='End'?buttons.length-1:(current+(e.key==='ArrowRight'?1:-1)+buttons.length)%buttons.length;buttons[next].click();buttons[next].focus();});
    const hero=create('section',undefined,'mt-6 xm-pod-episode');hero.style.display='none';hero.append(create('p','Son bölüm','xm-pod-muted'),create('h3',''));const heroPlay=podIcon('Son bölümü oynat','play_arrow',()=>play(latestEpisode()),true);heroPlay.id='heroPlayBtn';hero.append(heroPlay);panels[0].append(hero);
    const form=create('form',undefined,'xm-pod-search'),search=create('input');search.type='search';search.maxLength=150;search.placeholder='Bölümlerde ara';search.setAttribute('aria-label','Bölümlerde ara');form.append(search);form.addEventListener('submit',e=>e.preventDefault());panels[0].append(form);
    const toolbar=create('div',undefined,'xm-pod-toolbar'),filters=create('div',undefined,'xm-pod-chips');filters.id='filterContainer';const order=create('select');order.setAttribute('aria-label','Bölüm sıralaması');[['new','En yeni'],['old','En eski']].forEach(([value,text])=>{const opt=create('option',text);opt.value=value;order.append(opt);});toolbar.append(order);panels[0].append(toolbar,filters);
    const list=create('div',undefined,'xm-pod-episodes');list.id='xm-podcast-episodes';const message=create('p','Bölümler yükleniyor…','xm-pod-empty');message.setAttribute('aria-live','polite');panels[0].append(message,list);
    const loadMore=button('Daha fazla bölüm göster',()=>{limit+=20;drawEpisodes();});loadMore.className='xm-pod-chip';loadMore.hidden=true;panels[0].append(loadMore);
    let episodes=[],chosen=0,limit=20,similarRequested=false,showDescription='';
    function latestEpisode(){return episodes.slice().sort((a,b)=>(Date.parse(b.published)||0)-(Date.parse(a.published)||0))[0];}
    const descriptions=podSection(panels[1],'Program hakkında');const about=create('p','','xm-pod-muted');descriptions.append(about);const expand=button('Açıklamanın tamamını göster',()=>{description.classList.toggle('xm-pod-description');expand.textContent=description.classList.contains('xm-pod-description')?'Açıklamanın tamamını göster':'Açıklamayı daralt';});expand.className='xm-pod-chip';descriptions.append(expand);
    const similar=create('div',undefined,'xm-pod-grid');panels[2].append(similar);
    function loadSimilar(){if(similarRequested)return;similarRequested=true;similar.textContent='Benzer kategorideki programlar yükleniyor…';podSearch(podGenreQuery(info.genre),payload=>{if(!root.isConnected)return;similar.replaceChildren();const items=(payload.items||[]).filter(t=>t.feed!==address);items.slice(0,12).forEach(t=>similar.append(podProgram({url:t.feed,title:t.title,artist:t.artist,artwork:t.artwork,genre:t.genre})));if(!items.length){similar.append(create('p',payload.error||'Bu kategoride başka program bulunamadı.','xm-pod-muted'));if(payload.error)similar.append(button('Tekrar Dene',()=>{similarRequested=false;loadSimilar();}));}});}
    function visibleEpisodes(){const downloads=podDownloads();return episodes.filter(t=>{if(!t.title?.toLocaleLowerCase('tr').includes(search.value.trim().toLocaleLowerCase('tr')))return false;return chosen===0||chosen===1&&downloads.some(d=>d.id===t.id)||chosen===2&&!podCompleted(t.id)&&!podPartial(t.id)||chosen===3&&podPartial(t.id);}).slice().sort((a,b)=>{const diff=(Date.parse(b.published)||0)-(Date.parse(a.published)||0);return order.value==='old'?-diff:diff;});}
    function play(item){if(!item)return;if(state.id===item.id){N.togglePlayPause();return;}const shown=visibleEpisodes(),index=shown.findIndex(t=>t.id===item.id),p=podProgress(item.id);A.playPodcastSequence(JSON.stringify((index<0?[item]:shown).map(t=>t.id)),Math.max(0,index),podCompleted(item.id)?0:p.position||0);}
    function drawEpisodes(){
      if(!root.isConnected)return;list.replaceChildren();const shown=visibleEpisodes();message.textContent=episodes.length?(shown.length?'':'Bu filtrede bölüm yok.'):'Desteklenen ses bölümü bulunamadı.';
      shown.slice(0,limit).forEach(t=>list.append(podEpisode(t,()=>play(t))));loadMore.hidden=shown.length<=limit;
      filters.querySelectorAll('button').forEach((b,i)=>b.setAttribute('aria-pressed',String(i===chosen)));
    }
    ['Tümü','İndirilenler','Henüz Dinlenmeyenler','Yarım Kalanlar'].forEach((label,i)=>{const b=button(label,()=>{chosen=i;limit=20;drawEpisodes();});b.className='xm-pod-chip';b.setAttribute('aria-pressed',String(i===0));filters.append(b);});
    search.addEventListener('input',()=>{limit=20;drawEpisodes();});order.addEventListener('change',()=>{limit=20;drawEpisodes();});
    const backTop=button('↑ En üste dön',()=>window.scrollTo({top:0,behavior:window.matchMedia?.('(prefers-reduced-motion: reduce)').matches?'auto':'smooth'}));backTop.className='xm-pod-backtop';backTop.hidden=true;root.append(backTop);let frame=false;
    window.addEventListener('scroll',()=>{if(frame)return;frame=true;requestAnimationFrame(()=>{frame=false;const offset=window.scrollY||0;backTop.hidden=offset<650;document.body.classList.toggle('xm-pod-scrolled',offset>180);const heading=document.querySelector('header h1');if(heading)heading.textContent=offset>180?info.title||'Podcast':'Podcast';if(!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches){cover.style.transform='scale('+Math.max(.76,1-offset/1300)+')';cover.style.opacity=String(Math.max(.2,1-offset/600));}});},{passive:true});
    window.__xmusicPodcastRefresh=drawEpisodes;
    if(!address){message.textContent='Önce bir podcast programı seç.';return;}
    function load(){message.textContent='Bölümler yükleniyor…';podRss(address,payload=>{
      if(!root.isConnected||(payload.feed&&payload.feed!==address))return;if(payload.error){message.textContent=payload.error;message.append(button('Tekrar Dene',load));return;}
      info={...info,title:payload.title||info.title,artist:info.artist||payload.author||'',artwork:payload.artwork||info.artwork||''};title.textContent=info.title;author.textContent=info.artist;A.setPodcastArtwork(address,info.artwork);showDescription=payload.description||'Bu program için açıklama bulunamadı.';description.textContent=showDescription;about.textContent=showDescription;setArtwork(cover,info.artwork);tintFromArtwork(identity,info.artwork);
      const followed=library.feeds.find(f=>f.url===address);if(followed){Object.assign(followed,info);save();}sessionStorage.setItem('xm-feed-info',JSON.stringify(info));
      episodes=(payload.items||[]).map(t=>({...t,feed:address,artwork:t.artwork||info.artwork}));drawEpisodes();
      if(payload.stale)message.textContent='Çevrimdışı: son alınan bölümler gösteriliyor.';
      if(episodes.length){const latest=latestEpisode();hero.style.display='';hero.dataset.episode=latest.id;heroPlay.setAttribute('aria-label','Oynat');hero.querySelector('h3').textContent=latest.title;tintFromArtwork(hero,latest.artwork);}
    });}load();
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
    if(b.id==='submit-timer-btn'){bind(b,'timer',()=>{A.setSleepFade(!!document.getElementById('toggle-fade')?.checked);A.setSleepTimer(timerMinutes);});return;}
    if(b.id==='cancel-timer-btn'){bind(b,'cancelTimer',()=>A.setSleepTimer(0));return;}
    if(page==='uyku_zamanlay_c_s'&&/Dakika|Şarkı Bitiminde/.test(label)){bind(b,'timerOption',()=>{timerMinutes=label.includes('Şarkı Bitiminde')?-1:Number(label.match(/(\d+) Dakika/)[1]);const slider=document.getElementById('duration-slider');if(timerMinutes>0&&slider)slider.value=timerMinutes;const val=document.getElementById('custom-slider-val');if(val)val.textContent=timerMinutes<0?'Şarkı Sonu':timerMinutes+' dk';document.querySelectorAll('[data-minutes]').forEach(n=>{const selected=n===b;n.setAttribute('aria-pressed',String(selected));n.classList.toggle('active-preset',selected);n.classList.toggle('bg-primary/15',selected);n.classList.toggle('ring-1',selected);n.classList.toggle('ring-primary/40',selected);n.classList.toggle('bg-surface-container',!selected);n.querySelector('.check-icon')?.classList.toggle('hidden',!selected);});N.showToast(timerMinutes<0?'Şarkı bitimi seçildi':'Seçildi: '+timerMinutes+' dakika');});return;}
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
  let resumeSavedAt=0;
  window.addEventListener('xmusic:playback',e=>{
    const wasPlaying=state.playing,previousId=state.id,previousState=state;
    if(previousId?.startsWith('podcast:')&&previousId!==e.detail.id&&previousState.duration>0&&previousState.position>=previousState.duration-1500){
      library.podcastProgress||={};library.podcastProgress[previousId]={position:previousState.duration,duration:previousState.duration,completed:true,updatedAt:Date.now()};
    }
    state=e.detail;updateFavorites();
    const childStatus=document.getElementById('xm-child-status');if(childStatus)childStatus.textContent=state.familyBlock||'';
    if(state.familyBlock&&state.familyBlock!==window.__xmLastFamilyBlock){window.__xmLastFamilyBlock=state.familyBlock;N.showToast(state.familyBlock);}if(!state.familyBlock)window.__xmLastFamilyBlock='';
    if(state.playing&&state.id&&library.history[0]?.id!==state.id){library.history=[trackData(),...library.history.filter(t=>t.id!==state.id)].slice(0,Math.max(100,library.history.length));save();}
    if(state.id&&state.artwork){
      let changed=false;
      const update=t=>{if(t.id===state.id&&t.artwork!==state.artwork){t.artwork=state.artwork;changed=true;}};
      library.history.forEach(update);library.favorites.forEach(update);library.playlists.forEach(l=>(l.tracks||[]).forEach(update));if(library.resume)update(library.resume);
      if(changed)save();
    }

    if(state.id && !state.pending && (state.playing||wasPlaying) &&
      (previousId!==state.id || (wasPlaying&&!state.playing) || Date.now()-resumeSavedAt>=5000)){
      library.resume=Object.assign(trackData(),{position:Math.max(0,Number(state.position)||0),duration:Math.max(0,Number(state.duration)||0),updatedAt:Date.now()});
      resumeSavedAt=Date.now();save();
    }
    if(state.id?.startsWith('podcast:') && state.duration>0 && (state.playing||wasPlaying||state.ended)){
      library.podcastProgress||={};const previous=library.podcastProgress[state.id];
      if(!previous||previousId!==state.id||wasPlaying!==state.playing||state.ended||Date.now()-(previous.updatedAt||0)>=5000){
        library.podcastProgress[state.id]={position:Math.max(0,state.position||0),duration:state.duration,completed:!!state.ended||(state.position/state.duration>=.98),updatedAt:Date.now()};
        const entries=Object.entries(library.podcastProgress).sort((a,b)=>(b[1].updatedAt||0)-(a[1].updatedAt||0));library.podcastProgress=Object.fromEntries(entries.slice(0,500));save();
      }
    }
    if(document.body.classList.contains('xm-pod-page'))document.querySelectorAll('[data-episode]').forEach(card=>{
      const current=card.dataset.episode===state.id;card.dataset.playing=String(current);
      podSetIcon(card.querySelector('[aria-label="Oynat"]'),current&&state.playing?'pause':'play_arrow');
      if(current){const bar=card.querySelector('.xm-pod-progress>i');if(bar)bar.style.width=(state.duration>0?Math.min(100,(state.position||0)/state.duration*100):0)+'%';}
    });
    if(page==='ana_sayfa'&&window.__xmusicHomeRefresh)window.__xmusicHomeRefresh();
    const key=JSON.stringify([state.queue,state.index,state.shuffle,state.repeat]);if(key!==lastQueue){lastQueue=key;if(page==='oynatma_s_ras_queue')renderQueue();if(page==='imdi_al_yor')renderInlineQueue();}
    const requestedKey=selectionKey({title:state.selectionTitle||'',artist:state.selectionArtist||''});
    document.querySelectorAll('[data-xm-source-key]').forEach(row=>{
      let note=row.querySelector('.xm-source-status');
      if(requestedKey&&row.dataset.xmSourceKey===requestedKey&&state.selectionMessage){
        if(!note){note=create('small',undefined,'xm-source-status');note.style.cssText='display:block;color:#a9b5ad;font-size:11px;line-height:1.5;flex-basis:100%';note.setAttribute('role','status');(row.querySelector('.xm-library-title')||row.querySelector('div')||row.querySelector('span.flex-1')||row).append(note);}
        note.textContent=state.selectionMessage;
      }else note?.remove();
    });
    const sleep=document.getElementById('xm-sleep-status');if(sleep)sleep.textContent=state.sleepAtEnd?'Şarkı bitiminde duracak':state.sleepRemaining>0?'Kalan: '+Math.ceil(state.sleepRemaining/60000)+' dakika':'Zamanlayıcı kapalı';
    const count=document.getElementById('xmusic-queue-count');if(count)count.textContent='Sırada ('+(state.queue?.length||0)+')';
    const sleepLabel=document.getElementById('xmusic-sleep-label');if(sleepLabel)sleepLabel.textContent=state.sleepRemaining>0?Math.ceil(state.sleepRemaining/60000)+' dk uyku sayacı':'Uyku zamanlayıcısı';
  });
  if(page==='ocuk_modu_kids_safe')renderChildLibrary();
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
  function musicRow(item,genre=''){
    const row=create('div',undefined,'flex items-center gap-3 p-3 rounded-xl bg-surface-container-low');
    const image=create('img');setArtwork(image,'https://i.ytimg.com/vi/'+item.id+'/mqdefault.jpg');
    image.style.cssText='width:64px;height:48px;object-fit:cover;border-radius:8px;flex-shrink:0;';
    const caption=create('div',undefined,'flex-1 min-w-0');caption.append(create('strong',item.title||'Şarkı'),create('p',item.artist||''));
    const play=button('▶',()=>{if(genre)A.assignMusicGenre(item.id,genre);N.playExperimentalSource(item.id,item.title||'',item.artist||'');});
    play.setAttribute('aria-label',(item.title||'Şarkı')+' oynat');play.className='w-10 h-10 rounded-full bg-primary text-on-primary flex items-center justify-center';
    row.append(image,caption,play);return row;
  }
  const editorialKeys={
    'Türkçe pop hit şarkılar':'pop','Türkçe Pop şarkılar':'pop',
    'Türkçe rock şarkılar':'rock','Türkçe Rock şarkılar':'rock',
    'Türkçe rap hip hop':'rap','Türkçe Rap & Hip-Hop şarkılar':'rap',
    'elektronik müzik':'electronic','Türkçe Elektronik şarkılar':'electronic',
    'Türkçe Caz şarkılar':'jazz','Türkçe Klasik şarkılar':'classical',
    'Türkçe akustik şarkılar':'acoustic','Türkçe akustik stüdyo performans':'acoustic',
    'tasavvuf klasik müzik':'sufi','yeni çıkan Türkçe şarkılar':'new',
    'K-pop':'kpop','Spor':'fitness','Parti':'party'
  };
  const editorialGenres={pop:'Pop',rock:'Rock',rap:'Rap & Hip-Hop',electronic:'Elektronik',
    jazz:'Caz',classical:'Klasik',acoustic:'Akustik',sufi:'Tasavvuf & Klasik'};
  function personalizeEditorial(items, genre) {
    const preferences = library.spotifyPreferences;
    if (!preferences || !genre || genre === 'new') return items;
    const names = new Set(preferences.artists.map(name => name.toLocaleLowerCase('tr')));
    const candidates = [];
    library.playlists.forEach(list => {
      if (list.name.toLocaleLowerCase('tr').includes(({rock:'rock',pop:'pop',rap:'rap',electronic:'elektronik',acoustic:'akustik',jazz:'caz',classical:'klasik',sufi:'tasavvuf'})[genre]||genre)) candidates.push(...list.tracks);
    });
    const sourceArtists = new Set(items.map(t => t.artist.toLocaleLowerCase('tr')));
    library.favorites.forEach(t => { if(sourceArtists.has(t.artist.toLocaleLowerCase('tr'))) candidates.push(t); });
    const week = Math.floor(Date.now()/604800000);
    const seen = new Set();
    const result = [...candidates, ...items].filter(t => {const key=selectionKey(t);if(!key||seen.has(key))return false;seen.add(key);return true;});
    const score = t => {
      let hash=0; for(const char of selectionKey(t)+week) hash=(Math.imul(hash,31)+char.charCodeAt(0))|0;
      return (names.has(t.artist.toLocaleLowerCase('tr'))?100:0)+(hash>>>0)%100;
    };
    return result.sort((a,b)=>score(b)-score(a)).slice(0,50);
  }
  function loadEditorial(query,callback){
    const id=++shelfId;
    shelfCallbacks.set(id,payload=>callback({...payload,items:personalizeEditorial(payload.items||[],editorialKeys[query])}));
    N.loadEditorialMusicList(editorialKeys[query],id);
  }
  function editorialRows(holder,items,max=50,genre=''){
    const snapshot=JSON.stringify(items.map(({title,artist,duration})=>({title,artist,duration,genre})));
    items.slice(0,max).forEach((item,index)=>{
      const row=create('div',undefined,'flex items-center gap-3 p-3 rounded-xl bg-surface-container-low');
      const image=create('img');
      image.loading='lazy';image.style.cssText='width:48px;height:48px;flex-shrink:0;border-radius:8px';
      artworkForTrack(image,item);
      const label=create('div',undefined,'flex-1 min-w-0');
      label.append(create('strong',item.title),create('p',item.artist));
      const play=button('▶',()=>N.playTurkeyChartQueue(snapshot,index));
      play.setAttribute('aria-label',item.title+' oynat');
      row.append(image,label);
      if(profileState().role!=='child')row.append(play);
      holder.append(row);
    });
  }
  function selectionChrome(panel,title,items,color,onPlay,showItems,total=items.length,detail='Haftalık seçki',sourceTracks=items){
    panel.classList.add('xm-selection-dialog');
    const warm=sourceTracks.filter(t=>t.title&&t.artist&&!String(t.id||'').startsWith('podcast:')&&!String(t.id||'').startsWith('local:')).slice(0,3).map(t=>({title:t.title,artist:t.artist,sourceId:t.id||'',duration:Number(t.duration)||0}));
    if(warm.length&&typeof N.warmMusicQueue==='function')N.warmMusicQueue(JSON.stringify(warm));
    panel.style.setProperty('--xm-list-color',color);
    panel.querySelector('h2')?.remove();
    const header=create('div',undefined,'xm-selection-header');
    const close=button('Kapat',()=>document.getElementById('xm-dialog')?.remove());
    close.className='xm-selection-close';close.setAttribute('aria-label','Kapat');
    header.append(close,create('h2',title));
    const hero=create('div',undefined,'xm-selection-hero');
    const cover=create('div',undefined,'xm-selection-cover');
    coverMosaic(cover,items);
    hero.append(cover,create('h3',title),create('p',total+' şarkı · '+detail,'text-on-surface-variant'));
    const tools=create('div',undefined,'xm-selection-tools');
    const input=create('input',undefined,'xm-input');
    input.type='search';input.placeholder='Listede şarkı ara';input.setAttribute('aria-label','Listede şarkı ara');
    const play=button('▶',onPlay);play.className='xm-selection-play';play.setAttribute('aria-label','Listeyi oynat');
    tools.append(input,play);
    const list=create('div',undefined,'xm-selection-list');
    function draw(){
      const term=input.value.trim().toLocaleLowerCase('tr');
      list.replaceChildren();
      showItems(list,term);
    }
    input.addEventListener('input',draw);
    panel.replaceChildren(header,hero,tools,list);
    queueMicrotask(()=>{if(panel.isConnected){coverMosaic(cover,items);tintFromArtwork(panel,items.find(t=>t.artwork)?.artwork);}});
    let pendingFrame=false;
    panel.addEventListener('scroll',()=>{
      if(pendingFrame)return;pendingFrame=true;
      requestAnimationFrame(()=>{pendingFrame=false;const offset=panel.scrollTop;panel.classList.toggle('xm-scrolled',offset>150);
        if(!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches){cover.style.transform='scale('+Math.max(.82,1-offset/900)+')';cover.style.opacity=String(Math.max(.35,1-offset/420));}
      });
    },{passive:true});
    draw();
  }
  function showEditorial(title,query){
    modal(title,panel=>{
      panel.textContent='Seçki yükleniyor…';
      loadEditorial(query,payload=>{
        if(!panel.isConnected)return;
        const items=payload.items||[];
        if(!items.length){panel.textContent='Seçki şu anda alınamıyor.';panel.append(button('Kapat',()=>document.getElementById('xm-dialog')?.remove()));return;}
        const genre=editorialGenres[editorialKeys[query]]||'';
        const snapshot=JSON.stringify(items.map(({title,artist,duration})=>({title,artist,duration,genre})));
        const color=({pop:'#653881',rock:'#78313a',rap:'#205c40',electronic:'#23576a',jazz:'#704c2b',classical:'#4b486a',acoustic:'#526344',sufi:'#4d5061',new:'#27557b',kpop:'#87314d',fitness:'#785130',party:'#713b78'})[editorialKeys[query]]||'#254b38';
        selectionChrome(panel,title,items,color,()=>N.playTurkeyChartQueue(snapshot,0),(holder,term)=>{
          items.forEach((item,index)=>{
            if(term && !(item.title+' '+item.artist).toLocaleLowerCase('tr').includes(term))return;
            const row=create('div',undefined,'flex items-center gap-3 p-3 rounded-xl bg-surface-container-low');row.dataset.xmSourceKey=selectionKey(item);
            const art=create('img');art.loading='lazy';art.style.cssText='width:48px;height:48px;flex-shrink:0;border-radius:8px';artworkForTrack(art,item);
            const label=create('div',undefined,'flex-1 min-w-0');label.append(create('strong',item.title),create('p',item.artist));
            row.append(art,label);
            if(profileState().role!=='child'){
              const play=button('▶',()=>N.playTurkeyChartQueue(snapshot,index));play.setAttribute('aria-label',item.title+' oynat');row.append(play);
            }
            holder.append(row);
          });
        });
        if(profileState().role==='child')panel.querySelector('.xm-selection-play').remove();
      });
    });
  }
  function openMusicShelf(title,query){
    if(editorialKeys[query]){showEditorial(title,query);return;}
    modal(title,(panel,close)=>{
      const detail=create('p','Kayıtlar getiriliyor…','text-on-surface-variant');panel.append(detail);
      requestMusicShelf(query,payload=>{
        if(!panel.isConnected)return;detail.replaceChildren();
        if(payload.error){detail.textContent=payload.error;return;}
        if(payload.stale)detail.append(create('p','Bağlantı yok; önceki liste gösteriliyor.'));
        if(!(payload.items||[]).length){detail.append(create('p','Sonuç bulunamadı.'));return;}
        (payload.items||[]).forEach(item=>detail.append(musicRow(item,title)));
      });
    });
  }
  function makeOwned(el,fn){
    if(!el)return;el.dataset.xmOwned='true';delete el.dataset.xmBlocked;el.removeAttribute('aria-disabled');
    if(el.tagName==='BUTTON'){el.disabled=false;el.querySelector('small')?.remove();}
    else {el.setAttribute('role','button');el.tabIndex=0;el.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();fn();}};}
    el.__xmOwnedRun=fn;
  }
  const discoveryStyle=create('style');
  discoveryStyle.textContent=`
    .xm-own-playlist{background:linear-gradient(180deg,#254b38,#181c1f 360px);}
    .xm-own-playlist-title{position:sticky;top:0;z-index:2;background:#183326;padding:12px;margin:-16px -16px 0}
    .xm-own-playlist-hero{text-align:center;padding:12px}.xm-own-playlist-hero strong{display:block;font-size:22px}
    .xm-selection-dialog{padding-top:0;background:linear-gradient(180deg,var(--xm-list-color,#254b38),#181c1f 360px);overscroll-behavior:contain;scrollbar-width:thin}
    .xm-selection-dialog .xm-selection-header{position:sticky;top:0;z-index:3;margin:0 -20px;padding:12px 16px;display:flex;align-items:center;gap:10px;background:var(--xm-list-color,#254b38);min-height:54px}
    .xm-selection-header h2{font-size:16px;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;opacity:0;transition:opacity .18s}
    .xm-selection-dialog.xm-scrolled .xm-selection-header h2{opacity:1}
    .xm-selection-close{border:0;background:transparent;color:#fff;font-size:12px;width:48px;height:42px}
    .xm-selection-hero{text-align:center;padding:10px 0 18px}.xm-selection-hero h3{font-size:22px;font-weight:700;margin:8px 0}
    .xm-selection-tools{display:flex;gap:8px;align-items:center;position:sticky;top:54px;z-index:2;background:#181c1ff2;padding:8px 0}
    .xm-selection-tools input{flex:1;min-width:0;margin:0}.xm-selection-play{border-radius:50%;width:46px;height:46px;background:#20df64;color:#0b1710;border:0}
    .xm-selection-cover{transition:transform .15s ease;will-change:transform}
    .xm-discovery-rail{display:flex;gap:12px;overflow-x:auto;padding:4px 0 10px;scroll-snap-type:x proximity}
    .xm-discovery-card{flex:0 0 140px;border:0;text-align:left;color:#fff;scroll-snap-align:start;background:transparent}
    .xm-discovery-art{position:relative;width:140px;height:140px;border-radius:14px;overflow:hidden;margin-bottom:8px;background:linear-gradient(140deg,var(--xm-card-color),#151b19)}
    .xm-discovery-card strong{font-size:14px}.xm-discovery-card small{display:block;color:#aabbb0;margin-top:4px}
    .xm-selection-title{position:sticky;top:0;z-index:2;margin:0 -20px;padding:16px 20px;font-size:18px;font-weight:700;background:#183326ee;backdrop-filter:blur(8px)}
    .xm-selection-cover{position:relative;width:min(62vw,220px);aspect-ratio:1;margin:22px auto;border-radius:14px;overflow:hidden;background:#183b2d}
    @media(prefers-reduced-motion:reduce){.xm-selection-title{backdrop-filter:none}}
  `;
  document.head.append(discoveryStyle);
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
      if(profileState().role!=='child'){
        [['K-pop','K-pop','#a32e59'],['Spor','Spor','#b45d2c'],['Parti','Parti','#653b87']].forEach(([name,query,color])=>{
          const card=button('',()=>openMusicShelf(name,query));
          card.className='relative rounded-2xl p-4 text-left overflow-hidden';
          card.style.cssText='min-height:112px;background:linear-gradient(135deg,'+color+',#19201d);color:white;';
          card.append(create('strong',name),create('p','Haftalık seçki','text-sm'));
          grid.append(card);
        });
      }
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
    const family=sections.find(s=>s.textContent.includes('Bu Hafta En Çok Dinlenen Tür'));
    if(family){
      const title=Array.from(family.querySelectorAll('*')).find(n=>n.children.length===0&&n.textContent.trim()==='Bu Hafta En Çok Dinlenen Tür');
      if(title)title.textContent='Bu Cihazda En Çok Dinlenen Tür';
      const value=Array.from(family.querySelectorAll('*')).find(n=>n.children.length===0&&n.textContent.includes('Anadolu Rock'));
      const chart=family.querySelector('button');
      let totals={items:[],total:0},lastStatId='';
      function refreshStats(){
        try{totals=JSON.parse(A.familyGenreStats()||'{}');}catch(_){totals={};}
        const rows=Array.isArray(totals.items)?totals.items:[],total=Number(totals.total)||0;
        if(value)value.textContent=total&&rows.length?rows[0].genre+' (%'+Math.round(rows[0].count*100/total)+')':'Henüz dinleme verisi yok';
      }
      function details(){
        refreshStats();modal('Bu cihazdaki dinlemeler',p=>{
          const total=Number(totals.total)||0;p.append(create('p','Tüm yerel profiller · çalma sayısı'));
          if(!total){p.append(create('p','Henüz dinleme verisi yok.'));return;}
          (totals.items||[]).forEach(row=>p.append(create('p',row.genre+' · %'+Math.round(row.count*100/total)+' ('+row.count+' çalma)')));
        });
      }
      if(chart)makeOwned(chart,details);
      family.style.cursor='pointer';family.setAttribute('role','button');family.tabIndex=0;
      family.onclick=e=>{if(!e.target.closest('button'))details();};
      family.onkeydown=e=>{if(e.key==='Enter'){e.preventDefault();details();}};
      refreshStats();
      window.addEventListener('xmusic:playback',e=>{const id=e.detail?.playing?e.detail.id:'';if(id&&id!==lastStatId){lastStatId=id;refreshStats();}});
    }
    const familySession=sections.find(s=>s.textContent.includes('Ortak Aile Oturumu'));if(familySession)familySession.style.display='none';
    const genres=document.getElementById('genre-filter-tray');
    if(genres)Array.from(genres.querySelectorAll('button')).forEach(b=>makeOwned(b,()=>{
      const label=b.textContent.trim();if(label==='Tümü'){document.querySelector('main h1')?.scrollIntoView?.({block:'start'});return;}
      openMusicShelf(label,'Türkçe '+label+' şarkılar');
    }));
    const personal=findTitle('Senin İçin Hazırlandı');
    if(personal){
      const grid=personal.children[1],summary=personal.querySelector('p');
      const week=Math.floor(Date.now()/604800000);
      function updatePersonalMix(){
        const seen=new Set(), tracks=[];
        [...library.favorites,...library.history].forEach(track=>{
          if(track.id && !track.id.startsWith('podcast:') && !seen.has(track.id)){
            seen.add(track.id);
            tracks.push(track);
          }
        });
        const selected=tracks.slice(0,10);
        const old=library.weeklyMusicMix;
        if(!old || old.week!==week || JSON.stringify(old.tracks)!==JSON.stringify(selected)){
          library.weeklyMusicMix={week,tracks:selected};
          if(selected.length)save();
        }
        if(summary)summary.textContent='Bu profilin favorileri ve dinleme geçmişi';
        grid.replaceChildren();
        if(!selected.length){
          grid.append(create('p','Dinlemeye başladığında liste burada oluşacak.','text-on-surface-variant'));
          return;
        }
        grid.className='grid grid-cols-2 gap-3';
        const showPersonal=(name,items)=>modal(name,panel=>{
          selectionChrome(panel,name,items,'#315342',()=>{const tracks=items.filter(t=>t.title&&t.artist).slice(0,1000).map(t=>({title:t.title,artist:t.artist}));if(tracks.length)N.playTurkeyChartQueue(JSON.stringify(tracks),0);},(holder,term)=>{
            items.filter(track=>!term||((track.title||'')+' '+(track.artist||'')).toLocaleLowerCase('tr').includes(term)).forEach(track=>holder.append(picturedTrackRow(track)));
          },items.length,'Bu profilin kayıtları');
          if(profileState().role==='child')panel.querySelector('.xm-selection-play')?.remove();
        });
        const collections=[
          ['Bu haftaki seçkilerin',selected,'#593c86'],
          ['Beğendiğin şarkılar',library.favorites.filter(t=>t.id&&!t.id.startsWith('podcast:')),'#a52b66'],
          ['Son dinlediklerin',library.history.filter(t=>t.id&&!t.id.startsWith('podcast:')),'#236578']
        ];
        collections.filter(([,items])=>items.length).forEach(([name,items,color])=>{
          const card=button('',()=>showPersonal(name,items));
          card.className='rounded-xl bg-surface-container-low p-3 text-left';
          const cover=create('div');
          cover.style.cssText='position:relative;width:100%;aspect-ratio:1;overflow:hidden;border-radius:12px;margin-bottom:12px;background:linear-gradient(145deg,'+color+',#101010)';
          coverMosaic(cover,items);
          card.append(cover,create('strong',name),create('p',items.length+' şarkı','text-body-sm text-on-surface-variant'));
          grid.append(card);coverMosaic(cover,items);tintFromArtwork(card,items.find(t=>t.artwork)?.artwork);
        });
        const all=personal.querySelector('button');
        if(all && !grid.contains(all))makeOwned(all,()=>showPersonal('Bu haftaki seçkilerin',selected));
      }
      updatePersonalMix();
      let lastPersonalTrack='';
      window.addEventListener('xmusic:playback',event=>{
        const value=event.detail||{};
        if(value.playing && value.id && value.id!==lastPersonalTrack){
          lastPersonalTrack=value.id;
          updatePersonalMix();
        }
      });
    }
    function liveSection(label,title,query,max){
      const section=findTitle(label);if(!section)return;
      const heading=section.querySelector('h2');if(heading)heading.textContent=title;
      const subtitle=section.querySelector('p');if(subtitle)subtitle.textContent=library.spotifyPreferences?'Kaynak seçkisi ve kişisel tercihler · haftalık yenilenir':'Apple Music seçkisi · haftalık yenilenir';
      const holder=section.children[1];if(!holder)return;holder.replaceChildren();
      const message=create('p','Yükleniyor…','text-on-surface-variant');holder.append(message);
      const all=section.querySelector('button');
      const isNew=editorialKeys[query]==='new';
      let items=[],sortByDate=false;
      function paint(){
        holder.replaceChildren();
        const ordered=sortByDate ? [...items].sort((left,right)=>(right.releaseDate||'').localeCompare(left.releaseDate||'')) : items;
        editorialRows(holder,ordered,max,editorialGenres[editorialKeys[query]]);
        if(!items.length)holder.append(create('p','Seçki şu anda alınamıyor.','text-on-surface-variant'));
      }
      if(all && isNew){
        all.textContent='Kaynak Sırası';
        makeOwned(all,()=>{
          if(!items.some(item=>item.releaseDate)){N.showToast('Yayın tarihi bilgisi alınamadı.');return;}
          sortByDate=!sortByDate;
          all.textContent=sortByDate?'En Yeni':'Kaynak Sırası';
          paint();
        });
      }else if(all)makeOwned(all,()=>openMusicShelf(title,query));
      loadEditorial(query,payload=>{
        items=payload.items||[];
        sortByDate=isNew&&items.some(item=>item.releaseDate);
        if(isNew&&all)all.textContent=sortByDate?'En Yeni':'Kaynak Sırası';
        if(payload.stale&&items.length&&subtitle)subtitle.textContent='Apple Music seçkisi · önceki liste gösteriliyor';
        paint();
      });
    }
    liveSection('Hi-Res & Stüdyo Kayıtları','Stüdyo ve akustik kayıtlar','Türkçe akustik stüdyo performans',5);
    liveSection('Yeni Çıkanlar','Yeni Çıkanlar','yeni çıkan Türkçe şarkılar',5);
    const popular=findTitle('Popüler Çalma Listeleri');
    if(popular) initTurkeyCharts(popular);
  }


  let chartCoverRequest=0;
  const chartCoverCallbacks=new Map();
  window.__xmusicChartCovers=(id,covers)=>{
    const callback=chartCoverCallbacks.get(id);
    chartCoverCallbacks.delete(id);
    if(callback)callback(covers);
  };
  function initTurkeyCharts(section){
    const grid=section.children[1];
    if(!grid)return;
    grid.replaceChildren();
    grid.className='grid grid-cols-2 gap-3';
    const subtitle=section.querySelector('p');
    if(subtitle)subtitle.textContent='Türkiye Top 50 · haftada bir yenilenir';
    const all=section.querySelector('button');
    if(all)all.hidden=true;

    const charts=[
      ['spotify','Spotify Türkiye','Günlük dinlenme'],
      ['apple','Apple Music Türkiye','Günlük dinlenme'],
      ['youtube','YouTube Türkiye','Haftalık izlenme'],
      ['shazam','Shazam Türkiye','En çok tanımlananlar']
    ];
    charts.forEach(([key,name,period])=>{
      let chart={};
      try{chart=JSON.parse(N.cachedTurkeyChart(key)||'{}')||{};}catch(_){}
      const date=data=>data.date ? 'Liste tarihi '+data.date :
        'Alınma tarihi '+new Date(data.checkedAt).toLocaleDateString('tr-TR');
      const status=create('p','','text-on-surface-variant');
      const card=button('',()=>{});
      card.className='rounded-xl bg-surface-container-low p-4 text-left';
      const cover=create('div');
      cover.style.cssText='position:relative;width:100%;aspect-ratio:1;overflow:hidden;border-radius:12px;margin-bottom:12px;background:linear-gradient(145deg,'+
        ({spotify:'#146b3b',apple:'#a73655',youtube:'#a12f34',shazam:'#275bb0'}[key])+',#101719)';
      coverMosaic(cover,[]);
      card.append(cover,create('strong',name),create('p',period+' · Kworb'),status);
      grid.append(card);
      let coverStamp=0;
      function render(loading){
        if(chart.items?.length && chart.checkedAt && chart.checkedAt!==coverStamp){
          const stamp=chart.checkedAt;
          coverStamp=stamp;
          const id=++chartCoverRequest;
          chartCoverCallbacks.set(id,covers=>{
            if(chart.checkedAt===stamp && Array.isArray(covers) && covers.length)
              coverMosaic(cover,covers.map(artwork=>({artwork})));
          });
          N.refreshTurkeyChartCover(key,stamp,id);
        }
        status.textContent=chart.items?.length ? date(chart) :
          loading ? 'Yükleniyor…' : 'Liste şu anda alınamıyor';
        card.disabled=!chart.items?.length;
        if(card.disabled)return;
        makeOwned(card,()=>modal(name,panel=>{
          const items=chart.items||[];
          const snapshot=JSON.stringify(items);
          const sourceColor=({spotify:'#146b3b',apple:'#87344c',youtube:'#862c34',shazam:'#275bb0'})[key];
          const artwork=Array.from(cover.querySelectorAll('img')).map(img=>({artwork:img.dataset.xmArtwork||''}));
          selectionChrome(panel,name,artwork.length?artwork:items,sourceColor,()=>N.playTurkeyChartQueue(snapshot,0),(holder,term)=>{
            items.forEach((item,index)=>{
              if(term && !(item.title+' '+item.artist).toLocaleLowerCase('tr').includes(term))return;
              const row=create('div',undefined,'flex items-center gap-3 py-2');row.dataset.xmSourceKey=selectionKey(item);
              const image=create('img');image.loading='lazy';image.style.cssText='width:42px;height:42px;border-radius:6px;object-fit:cover;flex:none';artworkForTrack(image,item);
              row.append(create('strong',String(item.rank),'text-primary'),image,create('span',item.artist+' – '+item.title,'flex-1'));
              if(profileState().role!=='child'){
                const play=button('▶',()=>N.playTurkeyChartQueue(snapshot,index));
                play.setAttribute('aria-label',item.title+' oynat');row.append(play);
              }
              holder.append(row);
            });
          },items.length,period+' · Kworb · '+date(chart),items);
          if(profileState().role==='child')panel.querySelector('.xm-selection-play').remove();
        }));
      }
      render(true);
      const id=++shelfId;
      shelfCallbacks.set(id,result=>{
        if(result.items?.length)chart=result;
        render(false);
      });
      N.refreshTurkeyChart(key,id);
    });
  }


  function initHome(){
    const greeting=document.querySelector('main h1');
    const profile=profileState(), person=profile.profiles?.find(p=>p.id===profile.active)?.name||'Ben';
    const hour=new Date().getHours(), day=Math.floor(Date.now()/86400000);
    const period=hour<5?'İyi geceler':hour<11?'Günaydın':hour<17?'İyi günler':hour<22?'İyi akşamlar':'İyi geceler';
    if(greeting)greeting.textContent=period+', '+person;
    const member=Array.from(document.querySelectorAll('main p')).find(p=>p.textContent.includes('4/6'));
    if(member)member.textContent='Aktif profil · '+person;
    if(greeting){
      let city=localStorage.getItem('xm-weather-city')||'İstanbul', request=0;
      const widget=create('div',undefined,'flex items-center gap-2 mt-1');
      const weather=create('span','Hava durumu yükleniyor…','text-on-surface-variant');
      const choose=button('Şehir değiştir',()=>modal('Hava durumu şehri',(panel,close)=>{
        const input=create('input',undefined,'xm-input');input.value=city;input.maxLength=80;input.placeholder='Şehir adı';input.setAttribute('aria-label','Şehir adı');
        panel.append(input,button('Kaydet',()=>{const value=input.value.trim();if(value.length<2){N.showToast('Şehir adı gir.');return;}city=value;localStorage.setItem('xm-weather-city',city);close();loadWeather();}));
      }));choose.style.cssText='font-size:12px;color:#5cf187;background:transparent;border:0;';widget.append(weather,choose);greeting.parentElement.parentElement.append(widget);
      window.__xmusicWeatherResult=(id,data)=>{
        if(id!==request)return;
        if(data.error){weather.textContent='Hava durumu alınamadı · '+city;return;}
        const c=Number(data.code), phrases=c===0?['Güneşe bir şarkı eşlik etsin ☀️','Gökyüzü açık, müziğin sesi açık ☀️']:c<=3?['Bulutların arasından müzik geçsin ☁️','Bugünün havasına güzel bir ritim ☁️']:c>=95?['Dışarıda fırtına, içeride müzik ⛈️']:c>=71&&c<=86?['Kar tanelerine eşlik eden melodiler ❄️']:c>=51?['Yağmurun ritmine bir şarkı ekle 🌧️','Yağmura müzik yakışır 🌧️']:['Biraz müzik havayı değiştirir 🎵'];
        weather.textContent=data.city+' · '+Math.round(data.temperature)+'° · '+phrases[day%phrases.length];
      };
      function loadWeather(){weather.textContent='Hava durumu yükleniyor…';N.fetchWeather(city,++request);}
      loadWeather();
    }
    try {
      const native=JSON.parse(A.loadPlaybackResume()||'{}');
      if(native.id && (!library.resume || (native.updatedAt||0)>(library.resume.updatedAt||0))){
        if(!native.feed && library.resume?.id===native.id)native.feed=library.resume.feed||'';
        library.resume=native;
      }
    } catch (_) {}
    const sections=Array.from(document.querySelectorAll('main section'));
    const recent=sections.find(s=>s.querySelector('h2')?.textContent.trim()==='Son Dinlenenler');
    const resume=sections.find(s=>s.textContent.includes('Kaldığın Yerden Devam Et'));
    const mixes=sections.find(s=>s.querySelector('h2')?.textContent.includes('Günlük Aile Miksi'));
    const pop=sections.find(s=>s.querySelector('h2')?.textContent.includes('Hi-Res Stüdyo'));
    if(!recent||!resume||!mixes||!pop)return;
    const grid=recent.querySelector('.grid'),sample=grid?.firstElementChild?.cloneNode(true);
    const more=recent.querySelector('button');if(more)makeOwned(more,()=>go('kitapl_m'));
    const resumeCard=resume.children[1],resumeInfo=resumeCard?.querySelector('h3');
    const resumeImage=resumeCard?.querySelector('img');
    const timeLabel=resume.firstElementChild?.lastElementChild;
    const resumeArtist=resumeInfo?.previousElementSibling?.lastElementChild;
    const progress=resumeCard?.querySelector('[style*="width:"]');
    const progressLabel=progress?.parentElement?.nextElementSibling;
    if(progressLabel)progressLabel.id='xm-resume-percentage';
    const resumeTag=resumeInfo?.previousElementSibling?.firstElementChild;if(resumeTag)resumeTag.style.display='none';
    let signature='';
    function playResume(track){
      if(!track?.id)return;
      if(state.id===track.id){if(!state.playing)N.togglePlayPause();else go('imdi_al_yor');return;}
      const position=Math.max(0,Number(track.position)||0);
      if(track.id.startsWith('experimental:')||track.id.startsWith('drive-source:'))
        N.resumeExperimentalSource(track.id.split(':')[1],track.title||'',track.artist||'',position);
      else if(track.id.startsWith('drive:'))N.resumeDriveTrack(track.id.slice(6),track.title||'',track.artist||'',position);
      else if(track.id.startsWith('local:'))A.resumeLocalTrack(JSON.stringify(track),position);
      else if(track.id.startsWith('audius:'))N.resumeAudiusTrack(track.id.slice(7),position);
      else if(track.id.startsWith('podcast:')&&track.feed)A.resumePodcast(track.feed,track.id,position);
      else playTrack(track);
    }
    function refreshHome(){
      const tracks=(library.history||[]).slice(0,6),saved=library.resume||tracks[0]||null;
      const last=saved&&state.id===saved.id&&!state.pending?Object.assign({},saved,{position:state.position,duration:state.duration,artwork:state.artwork||saved.artwork}):saved;
      const current=JSON.stringify(tracks);
      if(current!==signature){signature=current;
      grid.replaceChildren();
      if(!tracks.length)grid.append(create('p','Dinlediğin parçalar burada görünecek.','text-on-surface-variant'));
      tracks.forEach(track=>{
        if(!sample)return;
        const card=sample.cloneNode(true),name=card.querySelector('p'),art=card.querySelector('img'),play=card.querySelector('button');
        if(name)name.textContent=track.title||'Parça';
        artworkForTrack(art,track);
        makeOwned(card,()=>playTrack(track));if(play)makeOwned(play,()=>playTrack(track));
        grid.append(card);
      });
      }
      if(!last){
        if(resumeInfo)resumeInfo.textContent='Henüz parça dinlenmedi';
        if(resumeArtist)resumeArtist.textContent='Bir parça seçerek başlayabilirsin';
        setArtwork(resumeImage,'');
        if(timeLabel)timeLabel.textContent='';
        if(progress)progress.style.width='0%';
        if(progressLabel)progressLabel.textContent='';
        if(resumeCard){delete resumeCard.dataset.xmOwned;resumeCard.__xmOwnedRun=null;}
      }else{
        if(resumeInfo)resumeInfo.textContent=last.title||'Son parça';
        if(resumeArtist)resumeArtist.textContent=last.artist||'';
        artworkForTrack(resumeImage,last);
        const duration=Number(last.duration)||0,position=Number(last.position)||0;
        if(timeLabel)timeLabel.textContent=duration>0?Math.max(0,Math.ceil((duration-position)/60000))+' dk kaldı':'Son dinlenen parça';
        const percentage=duration>0?Math.min(100,Math.max(0,position/duration*100)):0;
        if(progress)progress.style.width=percentage+'%';
        if(progressLabel)progressLabel.textContent=duration>0?'%'+Math.floor(percentage):'';
        if(resumeCard)makeOwned(resumeCard,()=>playResume(last));
      }
    }
    window.__xmusicHomeRefresh=refreshHome;refreshHome();
    const mixGrid=mixes.children[1];mixGrid.replaceChildren();
    const summary=mixes.querySelector('p');if(summary)summary.textContent=familySyncState().enabled?'Ailenin paylaşılan beğeni ve dinlemeleri':'Bu cihazdaki paylaşılan yetişkin tercihleri';
    const mixCovers=[];
    [['Aile Beğenileri','favorites','from-secondary-container to-surface-container'],['Aile Son Dinlenenler','history','from-primary-container to-surface-container']].forEach(([name,kind,gradient])=>{
      const card=button('',()=>showFamilyMix(kind));card.className='flex-shrink-0 w-44 flex flex-col text-left group';
      const art=create('div',undefined,'relative w-44 h-44 rounded-2xl overflow-hidden bg-gradient-to-br '+gradient+' flex items-center justify-center mb-2.5');
      coverMosaic(art,mixTracks(kind));tintFromArtwork(card,mixTracks(kind).find(t=>t.artwork)?.artwork);mixCovers.push([art,kind]);card.append(art,create('strong',name),create('span','Gerçek profil kayıtları','text-on-surface-variant'));mixGrid.append(card);coverMosaic(art,mixTracks(kind));
    });
    const archiveCard=button('',()=>showArchiveSelections(archiveTracks));archiveCard.className='flex-shrink-0 w-44 flex flex-col text-left group';
    const archiveArt=create('div',undefined,'relative w-44 h-44 rounded-2xl overflow-hidden bg-gradient-to-br from-primary-container to-surface-container flex items-center justify-center mb-2.5');
    let archiveTracks=[],archiveRequest=0;coverMosaic(archiveArt,[]);archiveCard.append(archiveArt,create('strong','Arşivden Seçkiler'),create('span','Aile zevkinden ilhamla','text-on-surface-variant'));mixGrid.append(archiveCard);
    window.__xmusicArchiveSelections=(id,data)=>{if(id!==archiveRequest)return;archiveTracks=rankArchive(Array.isArray(data?.items)?data.items:[]);coverMosaic(archiveArt,archiveTracks);};
    function refreshMixes(){mixCovers.forEach(([art,kind])=>coverMosaic(art,mixTracks(kind)));if(N.loadArchiveSelections)N.loadArchiveSelections(++archiveRequest);}
    window.addEventListener('xmusic:family',refreshMixes);window.addEventListener('xmusic:archive',refreshMixes);
    let mixPlaybackSignature='';
    window.addEventListener('xmusic:playback',e=>{
      const v=e.detail||{},next=String(v.id||'')+'|'+String(v.artwork||'');
      if(v.playing&&next!==mixPlaybackSignature){mixPlaybackSignature=next;refreshMixes();}
    });
    refreshMixes();
    if(profileState().role!=='child'){
      const discover=create('section',undefined,'flex flex-col space-y-3');
      discover.append(create('h2','Keşfetmeye devam et','font-headline-sm text-headline-sm text-on-surface'));
      const rail=create('div',undefined,'xm-discovery-rail');
      [['Türkçe Pop','Türkçe pop hit şarkılar','#653881'],['Türkçe Rock','Türkçe rock şarkılar','#78313a'],['K-pop','K-pop','#a32e59'],['Spor','Spor','#b45d2c'],['Parti','Parti','#713b78']].forEach(([name,query,color])=>{
        const card=button('',()=>openMusicShelf(name,query));card.className='xm-discovery-card';
        const cover=create('div',undefined,'xm-discovery-art');cover.style.setProperty('--xm-card-color',color);
        coverMosaic(cover,[]);card.append(cover,create('strong',name),create('small','Apple Music seçkisi'));rail.append(card);
        let loaded=false;
        const load=()=>{
          if(loaded)return;loaded=true;
          loadEditorial(query,payload=>{if(cover.isConnected&&payload.items?.length)coverMosaic(cover,payload.items);});
        };
        if('IntersectionObserver' in window){
          const observer=new IntersectionObserver(entries=>{if(entries.some(entry=>entry.isIntersecting)){observer.disconnect();load();}},{rootMargin:'120px'});
          observer.observe(card);
        }
      });
      discover.append(rail);mixes.after(discover);
      const artists=create('section',undefined,'flex flex-col space-y-3');
      artists.append(create('h2','Son dinlediğin sanatçılar','font-headline-sm text-headline-sm text-on-surface'));
      const artistRail=create('div',undefined,'xm-discovery-rail');artists.append(artistRail);discover.after(artists);
      let artistSignature='';
      function refreshArtists(){
        const selected=[];const seen=new Set();
        [...library.history,...library.favorites].forEach(track=>{
          const name=(track.artist||'').trim(),key=name.toLocaleLowerCase('tr');
          if(!name||name==='Google Drive arşivi'||seen.has(key)||selected.length>=10)return;
          seen.add(key);selected.push({name,artwork:track.artwork||'',track});
        });
        const signature=JSON.stringify(selected);
        if(signature===artistSignature)return;artistSignature=signature;
        artistRail.replaceChildren();artists.hidden=!selected.length;
        selected.forEach(item=>{
          const card=button('',()=>isPodcastTrack(item.track)?openFeed({...podcastIdentity(item.track),url:podcastIdentity(item.track).feed}):search(item.name));card.className='xm-discovery-card';
          const image=create('img');image.loading='lazy';
          image.style.cssText='width:110px;height:110px;border-radius:50%;object-fit:cover;margin:0 auto 8px';
          artworkForTrack(image,item.track);
          card.append(image,create('strong',item.name),create('small','Son dinlenenlerden'));
          artistRail.append(card);
        });
      }
      refreshArtists();
      window.addEventListener('xmusic:playback',refreshArtists);
    }
    const familyChip=document.querySelector('[data-filter="family"]');if(familyChip){familyChip.disabled=false;delete familyChip.dataset.xmBlocked;familyChip.textContent='Aile Karışımı';makeOwned(familyChip,()=>showFamilyMix('favorites'));}
    const mixSettings=mixes.querySelector('button');if(mixSettings&&!mixGrid.contains(mixSettings))makeOwned(mixSettings,()=>go('ayarlar_ve_profil_y_netimi'));
    const popHeading=pop.querySelector('h2');if(popHeading)popHeading.textContent='Stüdyo & Türkçe Pop';
    const badge=popHeading?.nextElementSibling;if(badge)badge.style.display='none';
    const popSummary=pop.querySelector('p');if(popSummary)popSummary.textContent='Apple Music Türkçe Pop seçkisi · haftalık yenilenir';
    const browse=pop.querySelector('button');if(browse)makeOwned(browse,()=>openMusicShelf('Türkçe Pop','Türkçe pop hit şarkılar'));
    const list=pop.children[1];list.replaceChildren();list.append(create('p','Yükleniyor…','text-on-surface-variant'));
    loadEditorial('Türkçe pop hit şarkılar',payload=>{
      list.replaceChildren();
      if(payload.error){list.append(create('p',payload.error,'text-on-surface-variant'));return;}
      if(payload.stale)list.append(create('p','Önceki liste gösteriliyor.','text-on-surface-variant'));
      editorialRows(list,payload.items||[],5,'Pop');
      if(!(payload.items||[]).length)list.append(create('p','Sonuç bulunamadı.','text-on-surface-variant'));
    });
  }
  const importKey = track => selectionKey(track);
  window.__xmusicSpotifyPreview = payload => {
    if (payload.error) { N.showToast(payload.error); return; }
    if (profileState().role === 'child' || payload.profileId !== profileState().active) return;
    const files = payload.files || {};
    const incoming = {favorites: [], playlists: [], history: [], artists: [], podcastHistory: []};
    function music(title, artist, uri = '') {
      if (!title || !artist) return null;
      return {id: uri || 'spotify:metadata:' + encodeURIComponent(artist + '|' + title), title, artist, artwork: ''};
    }
    Object.entries(files).forEach(([name, data]) => {
      if (name === 'YourLibrary.json') {
        incoming.favorites = (data.tracks || []).map(t => music(t.track, t.artist, t.uri)).filter(Boolean);
        incoming.artists = (data.artists || []).map(t => t.name).filter(Boolean);
      } else if (name.startsWith('Playlist')) {
        (data.playlists || []).forEach(list => incoming.playlists.push({name: list.name, tracks: (list.items || []).map(item => {
          const t = item.track;
          return t && music(t.trackName, t.artistName, t.trackUri);
        }).filter(Boolean)}));
      } else if (name.startsWith('StreamingHistory_music')) {
        data.forEach(t => {
          const track = music(t.trackName, t.artistName);
          if (track) incoming.history.push({...track, importedAt: t.endTime, msPlayed: Math.max(0, Number(t.msPlayed) || 0)});
        });
      } else if (name.startsWith('StreamingHistory_podcast')) {
        data.forEach(t => incoming.podcastHistory.push({title: t.episodeName, artist: t.podcastName, date: t.endTime, msPlayed: Math.max(0, Number(t.msPlayed) || 0)}));
      }
    });
    function mergeTracks(target, additions) {
      const keys = new Map(target.map(track=>[importKey(track),track]));
      additions.forEach(track => { const key = importKey(track); if(!key)return; const current=keys.get(key); if(current){if(!current.artwork && track.artwork)current.artwork=track.artwork;}else{keys.set(key,track);target.push(track);} });
    }
    const baseline = JSON.stringify(library);
    const updated = JSON.parse(baseline);
    mergeTracks(updated.favorites, incoming.favorites);
    incoming.playlists.forEach(list => {
      if (!list.name) return;
      let target = updated.playlists.find(p => p.name.trim().toLocaleLowerCase('tr') === list.name.trim().toLocaleLowerCase('tr'));
      if (!target) { target = {id: 'import-' + Date.now().toString(36) + '-' + updated.playlists.length, name: list.name, tracks: []}; updated.playlists.push(target); }
      mergeTracks(target.tracks, list.tracks);
    });
    incoming.history.sort((a,b) => b.importedAt.localeCompare(a.importedAt));
    mergeTracks(updated.history, incoming.history);
    updated.spotifyPreferences ||= {artists: [], events: [], podcastEvents: []};
    const preferences = updated.spotifyPreferences;
    preferences.artists = [...new Set([...preferences.artists, ...incoming.artists])];
    const eventKeys = new Set(preferences.events.map(t => importKey(t) + '|' + t.importedAt + '|' + t.msPlayed));
    incoming.history.forEach(t => { const key = importKey(t) + '|' + t.importedAt + '|' + t.msPlayed; if (!eventKeys.has(key)) { eventKeys.add(key); preferences.events.push(t); } });
    const podcastKeys = new Set(preferences.podcastEvents.map(t => t.artist + '|' + t.title + '|' + t.date + '|' + t.msPlayed));
    incoming.podcastHistory.forEach(t => { const key = t.artist + '|' + t.title + '|' + t.date + '|' + t.msPlayed; if (!podcastKeys.has(key)) { podcastKeys.add(key); preferences.podcastEvents.push(t); } });
    (payload.shows || []).forEach(show => {
      if (updated.feeds.some(f => f.spotifyUri === show.uri || (show.match && f.url === show.match.feed) || f.title === show.title)) return;
      updated.feeds.push({title: show.title, spotifyUri: show.uri, url: show.match?.feed || '', artwork: show.match?.artwork || ''});
    });
    modal('Spotify aktarımını kontrol et', (panel, close) => {
      panel.append(create('p', 'Hedef profil: ' + (profileState().profiles || []).find(p => p.id === payload.profileId)?.name));
      panel.append(create('p', (updated.favorites.length - library.favorites.length) + ' yeni beğeni · ' + incoming.playlists.length + ' liste birleştirilecek · ' + incoming.history.length + ' müzik dinleme kaydı'));
      panel.append(create('p', incoming.artists.length + ' sanatçı tercihi · ' + (payload.shows || []).length + ' podcast programı · ' + incoming.podcastHistory.length + ' bölüm dinleme kaydı'));
      panel.append(create('p', 'Haftalık seçkiler bu tercihlerden yararlanır. Kaynak sıralamaları kendi kataloglarından yenilenir.'));
      const unresolved = (payload.shows || []).filter(s => !s.match).length;
      if (unresolved) panel.append(create('p', unresolved + ' podcast için kaynak seçimi gerekiyor.'));
      panel.append(button('İçe aktar', () => {
        if (profileState().active !== payload.profileId) { N.showToast('Profil değişti. Aktarımı yeniden başlat.'); return; }
        if(JSON.stringify(library)!==baseline){close();window.__xmusicSpotifyPreview(payload);N.showToast('Kitaplık güncellendi. Yeni önizlemeyi kontrol et.');return;}
        updated.spotifyImportedAt = Date.now();
        if (!A.saveLibrary(JSON.stringify(updated))) { N.showToast('Aktarım kaydedilemedi. Veri boyutunu veya depolama alanını kontrol et.'); return; }
        library = updated; close(); N.showToast('Spotify verileri kitaplığına eklendi; eksik kapaklar aranıyor.');
        [...library.favorites,...library.playlists.flatMap(list=>list.tracks||[]),...library.history].filter(t=>!t.artwork).forEach(t=>loadTrackArtwork(t,()=>{}));
      }));
    });
  };

  function initSettings(){
    if(profileState().role!=='child'){
      const section=create('section',undefined,'xm-panel');
      section.append(create('h2','Spotify verilerini içe aktar'),create('p','ZIP içindeki beğeniler, listeler ve geçmiş aktif profile birleştirilir.'),button('Spotify ZIP seç',()=>{N.showToast('ZIP okuma ve podcast eşleştirmesi tamamlanınca önizleme açılacak.');A.importSpotifyZip();}));
      document.querySelector('main')?.prepend(section);
    }
    // Şablondaki örnek çocuk güvenliği bilgileri mevcut özellik gibi görünmesin.
    document.querySelectorAll('main h4').forEach(h=>{
      const label=h.textContent.trim();
      if(/Ebeveyn PIN Kodu|Uygunsuz İçerik|Günlük Dinleme Süresi Sınırı|Ekolayzır|Ses Normalleştirme|Otomatik Çalma|Android Auto/.test(label)){
        const row=h.closest('.p-4');
        const description=row?.querySelector('p');if(description)description.textContent='Henüz kullanıma hazır değil';
        const badge=row?.querySelector('span.rounded-full');if(badge&&badge.textContent.trim()==='Etkin')badge.textContent='Hazır değil';
      }
    });
    const familyData=profileState();
    document.querySelectorAll('main h4').forEach(h=>{
      const label=h.textContent.trim(),row=h.closest('.p-4');if(!row)return;
      if(/Ebeveyn PIN Kodu|Uygunsuz İçerik|Günlük Dinleme Süresi Sınırı/.test(label)){
        row.replaceChildren(create('strong',label),create('p',label.includes('Uygunsuz')?'Çocukta yalnızca ebeveynin onayladığı kayıtlar çalınır; otomatik söz sansürü uygulanmaz.':'Profil bazında ebeveyn yönetimi'));
        row.append(button(label.includes('PIN')?'PIN ayarla':'Çocuk profillerini yönet',()=>label.includes('PIN')&&familyData.role==='admin'?parentPinForm():go('aile_profili_se_imi')));
      }
    });
    // Alt bilgi statik Stitch örneğinden değil, yüklü APK'nin gerçek sürümünden okunur.
    const stamp=document.querySelector('main footer p');
    if(stamp && typeof A.appVersionInfo==='function') stamp.textContent=A.appVersionInfo();
    const certification=stamp?.closest('footer')?.querySelectorAll('p')[1];
    if(certification) certification.textContent='Android Native Edition';
    const section=Array.from(document.querySelectorAll('main section')).find(s=>s.querySelector('h2')?.textContent.includes('Aile Profilleri'));
    if(section){
      const grid=section.querySelector('.grid');
      if(grid){
        grid.replaceChildren();
        const data=profileState(),active=data.profiles?.find(p=>p.id===data.active);
        const card=create('div',undefined,'relative p-3 bg-surface-container rounded-lg flex flex-col items-center text-center');
        const icon=create('span');icon.dataset.xmActiveAvatar='true';icon.append(avatarNode(A.activeAvatar()||''));
        card.append(icon,create('strong',active?.name||'Ben','font-label-lg text-label-lg text-on-surface'),create('span','Aktif profil','font-label-sm text-primary'));
        grid.append(card);
      }
      const detail=section.querySelector('h2')?.parentElement?.parentElement?.querySelector('p');if(detail)detail.textContent='Bu cihazdaki aktif profil';
      const badge=section.querySelector('.rounded-full');if(badge)badge.textContent='Bu cihaz';
    }
    const equalizer=Array.from(document.querySelectorAll('main h4')).find(h=>h.textContent.trim()==='Ekolayzır');
    const eqRow=equalizer?.closest('.p-4');if(eqRow){const info=eqRow.querySelector('p');if(info)info.textContent='Cihazın ses ayarlarını aç';makeOwned(eqRow,()=>A.openEqualizer());eqRow.setAttribute('role','button');eqRow.tabIndex=0;}
    refreshAvatar();
    const reset=Array.from(document.querySelectorAll('main button')).find(b=>b.textContent.includes('Geçmişi Sıfırla'));
    if(reset){
      reset.textContent='Bu profilin dinleme geçmişini sıfırla';
      makeOwned(reset,()=>confirmAction('Bu profilin dinleme geçmişi silinsin mi?',()=>{
        library.history=[];delete library.resume;delete library.weeklyMusicMix;A.clearPlaybackResume();if(save())N.showToast('Geçmiş silindi.');
      }));
    }
  }

  function initInlinePlayer(){
    const controls=document.getElementById('mainPlayPause')?.parentElement;
    if(!controls)return;
    const main=document.querySelector('main');
    const output=[...main.querySelectorAll('span')].find(n=>n.textContent.trim()==='Çıkış Cihazı');
    output?.parentElement?.parentElement?.parentElement?.remove();
    const lyrics=[...main.querySelectorAll('span')].find(n=>n.textContent.trim()==='Şarkı Sözleri');
    lyrics?.closest('.rounded-2xl')?.remove();
    const queueButton=main.querySelector('[aria-label="Çalma Sırası"]');
    const family=queueButton?.parentElement?.querySelector('.-space-x-2');
    family?.remove();
    queueButton?.remove();
    const panel=create('section',undefined,'xm-inline-queue');
    panel.id='xm-inline-queue';
    panel.innerHTML='<div class="xm-inline-head"><h2>Sıradaki şarkılar</h2><span id="xm-inline-count"></span></div><div id="xm-inline-items"></div>';
    const timer=[...main.querySelectorAll('button')].find(b=>/uyku sayacı|Uyku zamanlayıcısı/i.test(b.textContent));
    const timerRow=timer?.parentElement;
    const jumpToQueue=button('Sıraya git',()=>panel.scrollIntoView({behavior:'smooth',block:'start'}));
    jumpToQueue.className='xm-queue-jump';
    if(timerRow){timerRow.append(jumpToQueue);controls.after(timerRow);timerRow.after(panel);}
    else {controls.after(jumpToQueue);jumpToQueue.after(panel);}
    const css=create('style');
    css.textContent=`.xm-inline-queue{width:100%;margin:12px 0 20px;padding:16px;border-radius:18px;background:#171d1c}
    .xm-queue-jump{margin-left:auto;padding:10px 16px;border:0;border-radius:24px;background:#242424;color:#fff;font-weight:600}
    .xm-inline-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;color:#e4eee7}
    .xm-inline-head h2{font-size:16px;font-weight:700}.xm-inline-head span{font-size:12px;color:#a9bdb0}
    .xm-track{display:flex;align-items:center;gap:10px;min-height:58px;border-bottom:1px solid #29312e}
    .xm-track:last-child{border:0}.xm-track-current{color:#4efd7d}
    .xm-track-main{display:flex;flex-direction:column;min-width:0;flex:1;text-align:left;background:none;border:0;color:inherit}
    .xm-track-main strong,.xm-track-main small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
    .xm-track-main strong{font-size:14px}.xm-track-main small{font-size:12px;color:#a9bdb0}
    .xm-track-action{width:36px;height:40px;border:0;border-radius:10px;color:#a9d7b8;background:transparent;font-size:19px}
    .xm-track-action:disabled{opacity:.25}.xm-track-num{width:22px;text-align:center;color:#93a69a;font-size:12px}`;
    document.head.append(css);
    renderInlineQueue();
  }
  function renderInlineQueue(){
    const holder=document.getElementById('xm-inline-items');
    if(!holder)return;
    const rows=state.queue||[];
    const count=document.getElementById('xm-inline-count');
    if(count)count.textContent=rows.length+' parça';
    holder.replaceChildren();
    if(!rows.length){holder.append(create('p','Henüz sırada şarkı yok.','text-on-surface-variant'));return;}
    rows.forEach((track,index)=>{
      const row=create('div',undefined,'xm-track'+(index===state.index?' xm-track-current':''));
      row.append(create('span',index===state.index?'▶':String(index+1),'xm-track-num'));
      const image=create('img');image.style.cssText='width:40px;height:40px;object-fit:cover;border-radius:6px;flex:none';artworkForTrack(image,track);row.append(image);
      const play=button('',()=>A.queueAction('play',index,0));
      play.className='xm-track-main';
      play.setAttribute('aria-label',(track.title||'Şarkı')+' oynat');
      play.append(create('strong',track.title||'Şarkı'),create('small',(track.artist||'')+(track.availability==='unavailable'?' · Şimdilik kullanılamıyor':track.availability==='preparing'?' · Hazırlanıyor':'')));
      row.append(play);
      const up=button('↑',()=>A.queueAction('move',index,index-1));
      const down=button('↓',()=>A.queueAction('move',index,index+1));
      const remove=button('×',()=>A.queueAction('remove',index,0));
      [[up,'Yukarı taşı'],[down,'Aşağı taşı'],[remove,'Sıradan çıkar']].forEach(([b,label])=>{
        b.className='xm-track-action';b.setAttribute('aria-label',label);
      });
      up.disabled=index===0;down.disabled=index===rows.length-1;
      row.append(up,down,remove);holder.append(row);
    });
  }

  if(page!=='ayarlar_ve_profil_y_netimi')refreshAvatar();
  if(page==='imdi_al_yor')initInlinePlayer();
  if(page==='ana_sayfa')initHome();
  if(page==='ayarlar_ve_profil_y_netimi')initSettings();
  if(page==='arama_ve_ke_fet')initDiscoverShelves();
  if(page==='m_zik_merkezi')initMusicHub();
  if(page==='podcast_merkezi')renderPodcasts();
  if(page==='podcast_program_ve_b_l_m_detay')renderFeed();
  if(page==='i_ndirme_y_neticisi'){renderDownloads();setInterval(renderDownloads,1500);}
  if(page==='kitapl_m')renderLibrary();
  if(page==='alma_listesi_y_netimi')renderPlaylist();
  if(page==='oynatma_s_ras_queue')renderQueue();
  if(page==='uyku_zamanlay_c_s'){
    const title=document.querySelector('header h1');if(title)title.textContent='Uyku Zamanlayıcısı';
    const backdrop=document.querySelector('main .opacity-40');if(backdrop)backdrop.remove();
    const family=Array.from(document.querySelectorAll('main p')).find(n=>n.textContent.includes('Boran’ın Odası'));family?.parentElement?.remove();
    const desc=Array.from(document.querySelectorAll('main p')).find(n=>n.textContent.includes('kademeli olarak kısarak'));if(desc)desc.textContent='Müzik veya podcast seçtiğin süre sonunda durur.';
    const banner=document.getElementById('cancel-timer-btn')?.closest('.rounded-2xl');
    if(banner){const label=banner.querySelector('.flex.flex-col');if(label){label.replaceChildren();const status=create('p','Zamanlayıcı kapalı','font-headline-sm text-headline-sm text-on-surface');status.id='xm-sleep-status';label.append(status);}}
    const track=Array.from(document.querySelectorAll('main p')).find(n=>n.textContent.includes('Şu an çalan:'));
    if(track){track.textContent='Henüz parça seçilmedi';track.id='xm-timer-track';const remaining=track.nextElementSibling;if(remaining){remaining.id='xm-timer-track-remaining';remaining.textContent='';}}
    const slider=document.getElementById('duration-slider');slider?.addEventListener('input',e=>{
      timerMinutes=Number(e.target.value);const value=document.getElementById('custom-slider-val');if(value)value.textContent=timerMinutes+' dk';
      document.querySelectorAll('[data-minutes]').forEach(n=>{const selected=n.dataset.minutes===String(timerMinutes);n.setAttribute('aria-pressed',String(selected));n.classList.toggle('active-preset',selected);n.classList.toggle('bg-primary/15',selected);n.classList.toggle('ring-1',selected);n.classList.toggle('ring-primary/40',selected);n.classList.toggle('bg-surface-container',!selected);n.querySelector('.check-icon')?.classList.toggle('hidden',!selected);});
    });
    const fade=document.getElementById('toggle-fade');if(fade)fade.checked=false;
    const screen=document.getElementById('toggle-screen');if(screen){screen.checked=false;screen.disabled=true;screen.title='Ekran kilitleme henüz kullanılamıyor';const row=screen.closest('.flex.items-center.justify-between');if(row){const labels=row.querySelectorAll('span');labels.forEach(n=>{if(n.textContent.trim()==='Zamanlayıcı bittiğinde ekranı kilitle')n.textContent='Henüz kullanıma hazır değil';});}}
    window.addEventListener('xmusic:playback',e=>{const v=e.detail,t=document.getElementById('xm-timer-track'),r=document.getElementById('xm-timer-track-remaining');if(t)t.textContent=v.id?'Şu an seçili: '+(v.title||'Parça'):'Henüz parça seçilmedi';if(r){const sec=Math.max(0,Math.ceil(((v.duration||0)-(v.position||0))/1000));r.textContent=v.id&&v.duration>0?'Kalan çalma süresi: '+Math.floor(sec/60)+':'+String(sec%60).padStart(2,'0'):'';}});
  }
  if(page==='arama_ve_ke_fet')setTimeout(function seedSearch(){if(!window.__xmusicActionsInstalled){setTimeout(seedSearch,50);return;}const query=sessionStorage.getItem('xm-search');if(query!==null){sessionStorage.removeItem('xm-search');const input=document.getElementById('search-input');if(input){input.value=query;input.dispatchEvent(new Event('input',{bubbles:true}));input.focus();}}},0);
  window.addEventListener('xmusic:family',()=>{refreshAvatar();if(document.getElementById('xm-dialog'))return;if(page==='aile_profili_se_imi')renderProfiles();if(profileState().role==='child'&&['ana_sayfa','kitapl_m','ocuk_modu_kids_safe'].includes(page))renderChildLibrary();});
  if(profileState().role==='child'&&['ana_sayfa','kitapl_m','ocuk_modu_kids_safe'].includes(page))renderChildLibrary();
  window.__xmusicActionsAudit=()=>Array.from(document.querySelectorAll('button,a')).filter(b=>!b.closest('#xmusic-live-search')).map(b=>({label:b.getAttribute('aria-label')||b.textContent.trim(),action:b.dataset.xmAction|| (b.dataset.xmOwned?'owned':null),blocked:b.dataset.xmBlocked||null}));
})();







