(function () {
  'use strict';
  if (window.__xmusicPlaybackInstalled) return;
  window.__xmusicPlaybackInstalled = true;
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
  const text = (node, value) => { if (node) node.textContent = value; };
  const time = ms => { const n = Math.floor(Math.max(0, ms || 0) / 1000); return Math.floor(n / 60) + ':' + String(n % 60).padStart(2, '0'); };
  const title = full ? document.querySelector('main h3') : mini?.querySelector('p');
  const artist = full ? title?.parentElement?.nextElementSibling?.querySelector('p') : mini?.querySelectorAll('p')[1];
  const badge = title?.nextElementSibling;
  let artwork = full ? document.querySelector('main img') : null;
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
    if (mini) mini.style.display = value.id ? '' : 'none';
    window.dispatchEvent(new CustomEvent('xmusic:playback', { detail: value }));
    text(title, value.title || 'Henüz parça seçilmedi');
    text(artist, value.artist || 'Keşfet bölümünden bir parça seç');
    const source = value.id?.startsWith('audius:') ? 'Audius' : /^(drive:|drive-source:)/.test(value.id||'') ? 'Drive' : value.id ? 'Akış' : '';
    text(badge, source);
    if (full) text(document.querySelector('main h2'), source || 'Parça seçilmedi');
    if (artwork) {
      const url = value.artwork?.startsWith('https://') ? value.artwork : '';
      if (url && artwork.getAttribute('src') !== url) artwork.src = url;
      if (!url) artwork.removeAttribute('src');
      artwork.style.visibility = url ? 'visible' : 'hidden';
      artwork.alt = value.title || '';
    }
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


