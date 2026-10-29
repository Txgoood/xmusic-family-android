# XMUSIC FAMILY — Android UI/UX Design System & Product Specification

## 1. Ürün Tasarım Vizyonu & Felsefesi
- **Platform**: Android Native (Jetpack Compose / Material 3 Audio Media guidelines).
- **Tema & Atmosfer**: Dark Mode öncelikli (#0B0F12 derin arka plan, #141B22 yüzey kartları, #20DF64 canlı elektrik yeşili vurgu).
- **Tek Elle Kolay Kullanım (Thumb Zone)**: Alt çubuk, mini oynatıcı ve sık kullanılan eylemler ekranın alt 40%'lık alanında.
- **Tipografi & Spacing**: 4/8px katı spacing grid, 16px/20px yuvarlatılmış kartlar, Türkçe karakter uyumu ve metin kırılma toleransları.

---

## 2. Bilgi Mimarisi & Alt Navigasyon (5 Ana Sekme)
1. **Ana Sayfa (`home`)**: Günün saatine göre selamlama, son çalınanlar hızlı erişim ızgarası (6'lı kart), Günlük Mix, Haftalık Keşif, Aile ortak listeleri, Türkçe Pop & Akustik, Podcast önerileri.
2. **Keşfet / Ara (`search`)**: Akıllı birleşik arama, Sesli arama, Tür kartları (Pop, Rock, Rap, Çocuk & Masal, Podcast), Son aramalar & Canlı filtre sekmeleri (Tümü, Şarkılar, Sanatçılar, Podcast).
3. **Müzik (`music`)**: Özel müzik kürasyonları, Popüler Listeler, Aile DJ Mix, Yeni Çıkanlar, Yüksek Çözünürlüklü (Lossless / Hi-Res) stüdyo kayıtları.
4. **Podcast (`podcast`)**: Bölüm keşfi, Takip edilen yayınlar, Hızlı 15s/30s atlama, Uyku zamanlayıcısı, Ebeveyn onaylı eğitici çocuk podcastleri.
5. **Kitaplığım (`library`)**: Beğenilen Şarkılar, İndirilenler (Çevrimdışı Mod), Çalma Listeleri, Takip Edilen Sanatçılar, Yerel Dosyalar, Depolama ve Aile Profilleri geçişi.

---

## 3. Persistent Mini Oynatıcı & Tam Ekran Player
- **Mini Player**: Tüm ekranların alt navigasyon çubuğu üstünde yapışık kalır (Bottom Navigation Bar'ın hemen üzerinde 64dp yükseklik). Albüm kapağı, Şarkı/Sanatçı, Oynat/Duraklat, Favori kalp ikonu, Akıcı ilerleme çizgisi. Yukarı kaydırma (Swipe-up) veya dokunma ile tam ekrana genişler.
- **Full Player (Now Playing)**: Dinamik albüm kapağı rengine uyarlanan blurred gradient arka plan, Lossless 24-bit 96kHz rozeti, Çalma sırası (Queue), Şarkı sözleri (Synced Lyrics), Ses çıkış cihazı (Google Cast / Bluetooth), Uyku sayacı, Şarkı indirme toggle'ı.

---

## 4. Aile Profilleri & Ebeveyn Denetimi
- Çoklu profil: Anne, Baba, Genç, Çocuk (Kids Safe Mode).
- Çocuk Profilinde: Büyük dokunmatik hedefler, otomatik Explicit içerik filtresi, PIN korumalı profil geçişi, eğitici masal/şarkı listeleri.

---

## 5. Çevrimdışı İndirme Yöneticisi & Hata Durumları
- Spotify standardında indirme kuyruğu: İlerleme yüzdesi, MB/sn hız, kalan süre, çevrimdışı önbellek temizleme, hücresel veri koruma kilidi.
- Hata Durumları: Açık Türkçe hata kartları (Bağlantı koptu, Çevrimdışı moddasınız, Lisans kısıtlı kaynak alternatifleri).