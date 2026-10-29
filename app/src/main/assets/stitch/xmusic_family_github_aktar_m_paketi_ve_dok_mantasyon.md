# XMUSIC FAMILY — GitHub / Production Aktarım Paketi & Mimari Dokümantasyon

Bu paket, **XMusic Family** projesinin tüm ekranlarını, tasarım tokenlarını, ortak bileşenlerini ve React + Capacitor + Android Media3 mimarisine geçiş rehberini içerir.

---

## 📂 1. Proje Dizin Yapısı (Önerilen Repository Ağacı)

```
xmusic-family/
├── README.md                           # Proje genel açıklaması ve kurulum rehberi
├── DESIGN.md                           # Tasarım sistemi tokenları, renk paleti ve tipografi
├── docs/
│   ├── ARCHITECTURE.md                 # Bilgi mimarisi ve Media3 / Capacitor entegrasyonu
│   ├── ROUTING.md                      # Ekranlar arası geçişler ve navigation haritası
│   └── COMPONENTS.md                   # Ortak bileşenler (MiniPlayer, AppShell, Header)
├── public/
│   ├── assets/
│   │   ├── icons/                      # Material Symbols & SVG vektör ikonlar
│   │   └── covers/                     # Albüm, podcast ve profil görselleri
├── screens/
│   ├── 01_onboarding.html              # İlk Kurulum ve Profil Oluşturma
│   ├── 02_home.html                    # Ana Sayfa (Home Feed)
│   ├── 03_search.html                  # Arama ve Keşfet
│   ├── 04_music_hub.html               # Müzik Merkezi (Hi-Res & Aile Miksi)
│   ├── 05_podcast_hub.html             # Podcast Merkezi
│   ├── 06_library.html                 # Kitaplığım & Çevrimdışı Arşiv
│   ├── 07_now_playing.html             # Şimdi Çalıyor (Full Screen Player)
│   ├── 08_queue.html                   # Oynatma Sırası (Queue Management)
│   ├── 09_sleep_timer.html             # Uyku Zamanlayıcısı Modal / Bottom Sheet
│   ├── 10_artist_detail.html           # Sanatçı Detay Ekranı (Barış Manço)
│   ├── 11_album_detail.html            # Albüm Detay Ekranı (Mançoloji)
│   ├── 12_podcast_detail.html          # Podcast Program & Bölüm Detayı
│   ├── 13_playlist_manage.html         # Çalma Listesi Oluşturma ve Sıralama
│   ├── 14_family_profiles.html         # Aile Profili Seçimi & PIN Yönetimi
│   ├── 15_kids_safe.html               # Çocuk Modu (Kids Safe UI)
│   ├── 16_download_manager.html        # İndirme Yöneticisi & Depolama
│   ├── 17_settings.html                # Ayarlar & Profil Yönetimi
│   └── 18_error_states.html            # Hata, Çevrimdışı ve Durum Ekranları
└── styles/
    └── tailwind.config.js              # Tasarım tokenları Tailwind konfigürasyonu
```

---

## 🎨 2. DESIGN.md (Tasarım Sistemi Tokenları)

```yaml
---
name: Nocturne Sound Glass
theme:
  color_mode: DARK
  font: Plus Jakarta Sans, sans-serif
  roundness: 8px / 16px / 24px (Material 3 Surface Cards)

colors:
  # Zemin ve Yüzeyler
  background: '#0B0F12'
  surface: '#101417'
  surface-dim: '#101417'
  surface-bright: '#363a3d'
  surface-container-lowest: '#0B0F12'
  surface-container-low: '#141B22'
  surface-container: '#181C1F'
  surface-container-high: '#1E252D'
  surface-container-highest: '#28303A'

  # Vurgu ve Marka Renkleri
  primary: '#20DF64'               # Canlı Neon Yeşil (Spotify / Material 3 Akustik Vurgu)
  primary-container: '#123D20'
  on-primary: '#003914'
  on-primary-container: '#7BFF98'

  # İkincil & Hata Renkleri
  secondary: '#8CE99A'
  error: '#FF5449'
  error-container: '#410002'
  
  # Tipografi ve Metin Kontrastı
  on-surface: '#F2F4F7'
  on-surface-variant: '#9AA4B2'
  outline: '#3E4957'
  outline-variant: '#2D3540'

typography:
  font-family: "'Plus Jakarta Sans', system-ui, -apple-system, sans-serif"
  display-large: "32px / 40px, SemiBold (700)"
  headline-medium: "22px / 28px, SemiBold (600)"
  title-medium: "16px / 24px, Medium (500)"
  body-medium: "14px / 20px, Regular (400)"
  label-small: "11px / 16px, Medium (500)"

spacing:
  grid: 4px / 8px katı spacing
  card-padding: 16px
  screen-padding: 16px
  bottom-nav-height: 64px
  mini-player-height: 64px
  touch-target-min: 48px
---
```

---

## 📱 3. Ekran Envanteri & Dosya Eşleştirmeleri

| Ekran Adı | Stitch ID | Hedef Dosya Adı | Açıklama / Kritik Bileşenler |
| :--- | :--- | :--- | :--- |
| **İlk Kurulum ve Onboarding** | `SCREEN_2` | `screens/01_onboarding.html` | Profil rolü seçimi (Yetişkin/Çocuk), müzik zevki hapları, Android izin kartları |
| **Hata ve Durum Ekranları** | `SCREEN_4` | `screens/18_error_states.html` | Çevrimdışı mod, internet yok, oynatma ve ağ hata sekmeleri |
| **Uyku Zamanlayıcısı** | `SCREEN_6` | `screens/09_sleep_timer.html` | 5-60 dk süreler, özel slider, kademeli fade-out ve ekran kilitleme |
| **Oynatma Sırası (Queue)** | `SCREEN_8` | `screens/08_queue.html` | Sürükle-bırak tutamaçları, parçayı listeden çıkarma, aile DJ miksi |
| **Podcast Program & Bölüm** | `SCREEN_10` | `screens/12_podcast_detail.html` | Program afişi, dinleme geçmişi barı, çevrimdışı indirme durumu |
| **Ayarlar ve Profil** | `SCREEN_12` | `screens/17_settings.html` | Hi-Res ses ayarı, ekolayzır, dahili depolama temizleyici, ebeveyn PIN |
| **Çocuk Modu (Kids Safe)** | `SCREEN_14` | `screens/15_kids_safe.html` | 64dp+ butonlar, güvenli masal/şarkı listesi, PIN korumalı çıkış |
| **Çalma Listesi Yönetimi** | `screens/13_playlist_manage.html` | Kapak değiştirici, başlık/açıklama düzenleme, parça sırası yönetimi |
| **Albüm Detayı** | `SCREEN_18` | `screens/11_album_detail.html` | Mançoloji stüdyo kapağı, 24-bit/96kHz FLAC rozeti, indirilme durumu |
| **Sanatçı Detayı** | `SCREEN_20` | `screens/10_artist_detail.html` | Barış Manço, popüler şarkılar, diskografi ve benzer sanatçılar |
| **Aile Profili Seçimi** | `SCREEN_22` | `screens/14_family_profiles.html` | 4 profil kartı, biyometrik geçiş, çocuk kilidi ve yeni üye ekleme |
| **Arama ve Keşfet** | `SCREEN_24` | `screens/03_search.html` | Akıllı birleşik arama, sesli mikrofon, tür kartları ve canlı filtreler |
| **Kitaplığım** | `SCREEN_26` | `screens/06_library.html` | Beğenilenler, indirilenler, çalma listeleri, yerel cihaz arşivi |
| **Müzik Merkezi** | `SCREEN_28` | `screens/04_music_hub.html` | Hi-Fi 24-bit canlı akış, Aile DJ Miksi #42, tür vitrinleri |
| **İndirme Yöneticisi** | `SCREEN_30` | `screens/16_download_manager.html` | Anlık MB/s ve yüzde göstergeli indirme sırası, hücresel koruma uyarısı |
| **Şimdi Çalıyor (Full Player)**| `SCREEN_32` | `screens/07_now_playing.html` | Dinamik renk gradyanı, thumb-zone kontrolleri, senkronize sözler |
| **Podcast Merkezi** | `SCREEN_34` | `screens/05_podcast_hub.html` | Dinlemeye devam et, 15sn/30sn atlama, eğitici çocuk podcastleri |
| **Ana Sayfa (Home Feed)** | `SCREEN_36` | `screens/02_home.html` | Dinamik selamlama, 6'lı son dinlenenler, Aile Miksi, Hi-Res vitrini |

---

## 🧩 4. Ortak Bileşenler (Shared Components)

### A. Persistent Mini Player (Tüm Ana Ekranlarda Sabit)
```html
<!-- Mini Oynatıcı Bileşeni (Alt navigasyonun hemen üstünde 64dp yükseklik) -->
<div class="fixed bottom-16 left-0 right-0 z-40 px-3 pb-1">
  <div class="flex items-center justify-between p-2 rounded-2xl bg-[#141B22]/95 backdrop-blur-md border border-white/10 shadow-2xl cursor-pointer" onclick="navigateTo('now_playing')">
    <div class="flex items-center space-x-3 overflow-hidden">
      <img src="assets/covers/donence.jpg" class="w-11 h-11 rounded-lg object-cover flex-shrink-0" alt="Dönence Cover" />
      <div class="truncate">
        <div class="flex items-center space-x-1.5">
          <span class="text-sm font-semibold text-white truncate">Dönence</span>
          <span class="px-1.5 py-0.5 text-[9px] font-bold bg-[#20DF64]/20 text-[#20DF64] rounded">HI-RES</span>
        </div>
        <p class="text-xs text-neutral-400 truncate">Barış Manço</p>
      </div>
    </div>
    <div class="flex items-center space-x-2 flex-shrink-0 pr-1">
      <button class="p-2 text-neutral-400 hover:text-white" aria-label="Favori">
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"/></svg>
      </button>
      <button class="w-9 h-9 rounded-full bg-[#20DF64] text-[#0B0F12] flex items-center justify-center font-bold" aria-label="Oynat / Duraklat">
        <svg class="w-5 h-5 fill-current" viewBox="0 0 24 24"><path d="M6 4h4v16H6V4zm8 0h4v16h-4V4z"/></svg>
      </button>
    </div>
  </div>
  <!-- İlerleme Çizgisi -->
  <div class="w-full bg-white/10 h-1 rounded-full mt-0.5 overflow-hidden">
    <div class="bg-[#20DF64] h-full rounded-full" style="width: 52%"></div>
  </div>
</div>
```

### B. Beş Sekmeli Alt Navigasyon (Bottom Navigation Bar)
```html
<nav class="fixed bottom-0 left-0 right-0 h-16 bg-[#0B0F12]/95 backdrop-blur-lg border-t border-white/5 flex items-center justify-around z-50 px-2">
  <a href="02_home.html" class="flex flex-col items-center justify-center text-[#20DF64]">
    <span class="material-symbols-rounded text-2xl">home</span>
    <span class="text-[10px] font-medium mt-0.5">Ana Sayfa</span>
  </a>
  <a href="03_search.html" class="flex flex-col items-center justify-center text-neutral-400 hover:text-white">
    <span class="material-symbols-rounded text-2xl">search</span>
    <span class="text-[10px] font-medium mt-0.5">Keşfet</span>
  </a>
  <a href="04_music_hub.html" class="flex flex-col items-center justify-center text-neutral-400 hover:text-white">
    <span class="material-symbols-rounded text-2xl">album</span>
    <span class="text-[10px] font-medium mt-0.5">Müzik</span>
  </a>
  <a href="05_podcast_hub.html" class="flex flex-col items-center justify-center text-neutral-400 hover:text-white">
    <span class="material-symbols-rounded text-2xl">podcasts</span>
    <span class="text-[10px] font-medium mt-0.5">Podcast</span>
  </a>
  <a href="06_library.html" class="flex flex-col items-center justify-center text-neutral-400 hover:text-white">
    <span class="material-symbols-rounded text-2xl">library_music</span>
    <span class="text-[10px] font-medium mt-0.5">Kitaplığım</span>
  </a>
</nav>
```

---

## 🛠️ 5. React + Capacitor + Android Media3 Entegrasyon Mimarisi

İleride yapılacak geliştirmede mimari şu şekilde ayrıştırılmalıdır:

1. **Frontend Katmanı (React + Tailwind CSS)**:
   - Bu paketteki HTML dosyalarındaki DOM yapısı doğrudan React JSX bileşenlerine (`HomeView.tsx`, `NowPlayingView.tsx` vb.) dönüştürülecektir.
   - `MiniPlayer` bileşeni React Router yapısında en üst layout seviyesinde tutulacaktır (`<Outlet />` üzerinde).
2. **Capacitor Köprüsü (Native Bridge)**:
   - Arka planda müzik çalma, kilit ekranı kontrolleri ve bildirim çubuğu için standart WebView audio yerine `@capacitor-community/media` veya özel bir `MediaSessionPlugin` kullanılacaktır.
3. **Android Media3 (ExoPlayer Service)**:
   - Android tarafında `MediaSessionService` oluşturulacak; FLAC/Lossless oynatma, gapless playback ve Bluetooth/Cast kontrolleri doğrudan native Media3 engine tarafından yönetilecektir.
   - Kids Safe modu etkinleştirildiğinde Media3 filtre servisi devreye girecektir.

---

## 📥 6. Kod ve Varlık Dışa Aktarım Talimatları (Adım Adım)

Stitch arayüzünde tüm kaynak kodları ve varlıkları bilgisayarınıza / GitHub repo'nuza aktarmak için:

1. **Kodları İndirme**:
   - Tuval üzerindeki herhangi bir ekrana tıklayıp sağ üst köşedeki **"Export / Code"** butonunu kullanarak ekranın saf HTML/CSS kaynak kodunu kopyalayabilir veya `.html` olarak kaydedebilirsiniz.
   - Yukarıdaki **Ekran Envanteri Tablosu**'ndaki dosya isimlerini kullanarak her ekranı `screens/` klasörüne yerleştiriniz.
2. **Görsel Varlıklar ve Placeholder'lar**:
   - Tasarımlarda kullanılan albüm kapakları, sanatçı portreleri ve masal çizimleri Stitch DataStore içerisinde yerel olarak barındırılmaktadır.
   - HTML kodlarında yer alan `src="{{DATA:IMAGE:...}}"` veya yerel blob bağlantıları yerine, projenin `public/assets/covers/` klasörüne telifsiz veya lisanslı kendi görsellerinizi (örn: `baris_manco.jpg`, `donence.jpg`) ekleyip dosya yollarını güncelleyiniz.
3. **İkonlar**:
   - Tasarımda Google **Material Symbols Rounded** ve saf inline SVG'ler kullanılmıştır. HTML `<head>` tagine `https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded` linkini eklediğinizde tüm ikonlar eksiksiz olarak görüntülenecektir.
