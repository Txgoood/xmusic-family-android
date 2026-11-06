(function () {
  'use strict';
  if (window.__xmusicPlaybackInstalled) return;
  window.__xmusicPlaybackInstalled = true;
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
  const full = location.pathname.includes('/imdi_al_yor/');
  const settingsMini = location.pathname.includes('/ayarlar_ve_profil_y_netimi/')
    ? document.querySelector('main .fixed.bottom-2.inset-x-2') : null;
  const mini = document.querySelector('aside') || settingsMini;
  if (settingsMini) {
    settingsMini.querySelector('[aria-label="Google Cast"]')?.remove();
    settingsMini.querySelector('.absolute.-bottom-1.-right-1')?.remove();
    const play = settingsMini.querySelector('#miniPlayBtn');
    if (play) { play.removeAttribute('onclick'); play.setAttribute('aria-label','Oynat veya Duraklat'); }
  }
  if (mini) mini.style.display = 'none';
  let state = {};
  let colorSource='';
  let colorGeneration=0;
  function updateMiniColor(address) {
    if(!mini || colorSource===address)return;
    colorSource=address;
    const generation=++colorGeneration;
    mini.style.backgroundImage='';
    mini.style.backgroundColor='';
    if(typeof address!=='string' || !address.startsWith('https://'))return;
    const image=new Image();
    image.crossOrigin='anonymous';
    image.onload=()=>{
      if(generation!==colorGeneration)return;
      try{
        const canvas=document.createElement('canvas');canvas.width=12;canvas.height=12;
        const context=canvas.getContext('2d',{willReadFrequently:true});
        if(!context)return;
        context.drawImage(image,0,0,12,12);
        const pixels=context.getImageData(0,0,12,12).data;
        let red=0,green=0,blue=0,count=0;
        for(let i=0;i<pixels.length;i+=4){
          if(pixels[i+3]<128)continue;
          red+=pixels[i];green+=pixels[i+1];blue+=pixels[i+2];count++;
        }
        if(!count)return;
        const scale=.34/count;
        mini.style.backgroundImage='none';
        mini.style.backgroundColor='rgb('+Math.round(red*scale)+','+Math.round(green*scale)+','+Math.round(blue*scale)+')';
        mini.style.transition='background-color 350ms ease';
      }catch(_){}
    };
    image.onerror=()=>{};
    image.src=address;
  }
  let artwork = full ? document.querySelector('main img') : null;
  if (full) {
    const header=document.querySelector('header');
    const headerTitle=header?.querySelector('h1');
    const coverFrame=artwork?.parentElement;
    if(header && headerTitle && coverFrame){
      const compact=document.createElement('div');compact.className='xm-now-compact';
      const compactTitle=document.createElement('strong'),compactArtist=document.createElement('small');
      compact.append(compactTitle,compactArtist);
      headerTitle.parentElement.appendChild(compact);
      const style=document.createElement('style');
      style.textContent=`.xm-now-compact{display:none;min-width:0;flex-direction:column;line-height:1.2}.xm-now-compact strong,.xm-now-compact small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:42vw}.xm-now-compact strong{font-size:14px}.xm-now-compact small{font-size:11px;opacity:.7;margin-top:3px}body.xm-now-scrolled header h1{display:none}body.xm-now-scrolled .xm-now-compact{display:flex}body.xm-now-scrolled header{background:#16241ef5;box-shadow:0 5px 20px #0004} @media(prefers-reduced-motion:reduce){.xm-now-cover-scroll{transform:none!important;opacity:1!important}}`;
      document.head.appendChild(style);coverFrame.classList.add('xm-now-cover-scroll');
      let frame=false;
      const onScroll=()=>{if(frame)return;frame=true;requestAnimationFrame(()=>{frame=false;
        const offset=window.scrollY||document.documentElement.scrollTop||0;
        document.body.classList.toggle('xm-now-scrolled',offset>160);
        if(!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches){
          coverFrame.style.transform='scale('+Math.max(.86,1-offset/1800)+')';
          coverFrame.style.opacity=String(Math.max(.5,1-offset/1000));
        }
      });};
      window.addEventListener('scroll',onScroll,{passive:true});
      window.__xmusicCompactTrack=(value)=>{compactTitle.textContent=value.title||'Şimdi Çalıyor';compactArtist.textContent=value.artist||'';};
    }
  }
  const text = (node, value) => { if (node) node.textContent = value; };
  const time = ms => { const n = Math.floor(Math.max(0, ms || 0) / 1000); return Math.floor(n / 60) + ':' + String(n % 60).padStart(2, '0'); };
  const title = full ? document.querySelector('main h3') : mini?.querySelector('p');
  const artist = full ? title?.parentElement?.nextElementSibling?.querySelector('p') : mini?.querySelectorAll('p')[1];
  const badge = title?.nextElementSibling;
  if (full && artwork) {
    const visualStyle=document.createElement('style');
    visualStyle.textContent='@keyframes xm-cover-motion{0%,100%{transform:scale(1)}50%{transform:scale(1.035)}} .xm-cover-motion{animation:xm-cover-motion 12s ease-in-out infinite;will-change:transform} @media (prefers-reduced-motion:reduce){.xm-cover-motion{animation:none!important}}';
    document.head.appendChild(visualStyle);
  }
  if (mini) {
    const box = mini.querySelector('.w-12.h-12') || (settingsMini && mini.querySelector('.w-11.h-11')); 
    if (box) {
      box.replaceChildren();
      artwork = document.createElement('img');
      artwork.style.cssText = 'width:100%;height:100%;object-fit:cover';
      box.appendChild(artwork);
    }
  }
  const status = document.createElement('p');
  status.setAttribute('role', 'status');
  status.style.cssText = 'font-size:11px;line-height:15px;color:#bbcbb8;overflow-wrap:anywhere;';
  if (full) document.getElementById('scrubberContainer')?.parentElement?.appendChild(status);
  else title?.parentElement?.parentElement?.appendChild(status);
  if (full) {
    text(document.querySelector('main h2')?.previousElementSibling, 'Oynatma kaynağı');
    const quality = artist?.parentElement?.querySelectorAll('span');
    quality?.forEach(node => { node.hidden = true; });
    // Örnek söz, Cast ve indirme durumları gerçek özellik gibi gösterilmez.
    document.querySelectorAll('main span, main p').forEach(node => {
      const value = node.textContent.trim();
      if (value === 'Salon Soundbar (Google Cast)') text(node, 'Cihazın ses çıkışı');
      if (value === 'Bağlı') text(node, '');
      if (value === 'Sırada (14)') {node.id='xmusic-queue-count';text(node,'Sıra');}
      if (value === '45 dk uyku sayacı') {node.id='xmusic-sleep-label';text(node,'Uyku zamanlayıcısı');}
      if (value.includes('Simsiyah gecenin')) text(node, 'Bu kayıt için şarkı sözleri bağlı değil.');
      if (value.includes('Gözlerim uzakta')) text(node, '');
    });
  }
  const controls = Array.from(document.querySelectorAll('button')).filter(b =>
    (full || mini?.contains(b)) && /Oynat veya Duraklat|Önceki Parça|Sonraki Parça/.test(b.getAttribute('aria-label') || ''));
  const scrubber = document.getElementById('scrubberContainer');
  // İlk yakalama dinleyicisi şablondaki demo olaylarını ve çift native komutları engeller.
  document.addEventListener('click', function (event) {
    const button = event.target.closest('button');
    if (controls.includes(button)) {
      event.preventDefault(); event.stopImmediatePropagation();
      if (button.disabled) return;
      const label = button.getAttribute('aria-label');
      if (label === 'Önceki Parça') window.Android.previous();
      else if (label === 'Sonraki Parça') window.Android.next();
      else window.Android.togglePlayPause();
      return;
    }
    if (scrubber?.contains(event.target)) {
      event.preventDefault(); event.stopImmediatePropagation();
      if (!state.seekable) return;
      const rect = scrubber.getBoundingClientRect();
      if (rect.width > 0) window.Android.seekTo(Math.round(Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width)) * state.duration));
      return;
    }
    if (full && button?.getAttribute('aria-label') === 'Kapat') {
      event.preventDefault(); event.stopImmediatePropagation(); window.Android.goBack(); return;
    }
    if ((full || mini?.contains(button)) && button && /Favorilere Ekle|İndirildi|Karışık Çal|Tekrar Modu|Bağlı Cihaz|Paylaş/.test(button.getAttribute('aria-label') || '')) {
      event.preventDefault(); event.stopImmediatePropagation(); window.Android.showToast('Bu özellik henüz bağlı değil.'); return;
    }
    if (mini?.contains(event.target) && !button && state.id) {
      event.preventDefault(); event.stopImmediatePropagation(); window.Android.navigateTo('imdi_al_yor');
    }
  }, true);
  window.__xmusicPlayback = function (value) {
    state = value;
    if(full)window.__xmusicCompactTrack?.(value);
    if (mini) mini.style.display = value.id ? '' : 'none';
    window.dispatchEvent(new CustomEvent('xmusic:playback', { detail: value }));
    text(title, value.title || 'Henüz parça seçilmedi');
    text(artist, value.artist || 'Keşfet bölümünden bir parça seç');
    const source = value.id?.startsWith('audius:') ? 'Audius' : /^(drive:|drive-source:)/.test(value.id||'') ? 'Drive' : value.id ? 'Akış' : '';
    text(badge, source);
    if (full) text(document.querySelector('main h2'), source || 'Parça seçilmedi');
    setArtwork(artwork,value.artwork,value.title);
    if(!full)updateMiniColor(value.artwork||'');
    if(full && artwork)artwork.classList.toggle('xm-cover-motion',!!value.id && !!value.playing && !document.hidden);
    text(status, value.error || (!value.ready ? 'Oynatıcı bağlanıyor…' : value.pending ? 'Ses yükleniyor…' : value.ended ? 'Bitti' : value.id ? (value.playing ? 'Çalıyor' : 'Duraklatıldı') : ''));
    controls.forEach(button => {
      const label = button.getAttribute('aria-label');
      button.disabled = !value.ready || !value.id || (label === 'Sonraki Parça' && !value.hasNext);
      button.style.opacity = button.disabled ? '0.45' : '1';
      if (label === 'Oynat veya Duraklat') {
        text(button.querySelector('span'), value.playing || value.pending ? 'pause' : 'play_arrow');
        button.setAttribute('aria-pressed', String(!!value.playing));
      }
    });
    const percentage = value.duration > 0 ? Math.min(100, value.position / value.duration * 100) : 0;
    const progress = full ? document.getElementById('progressBar') : mini?.querySelector('.absolute.bottom-0 > div');
    if (progress) progress.style.width = percentage + '%';
    const thumb = document.getElementById('scrubberThumb');
    if (thumb) thumb.style.left = 'calc(' + percentage + '% - 8px)';
    text(document.getElementById('currentTime'), time(value.position));
    text(document.getElementById('totalTime'), time(value.duration));
    if (full) document.querySelectorAll('main .animate-pulse').forEach(n => { n.style.animationPlayState = value.playing ? 'running' : 'paused'; });
  };
  window.__xmusicPlayback({ ready: false });
})();




