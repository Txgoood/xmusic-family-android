# XMusic Family — Sabit Geliştirme Sırası
Tarih: 2026-10-29
Durum: 0/10 aşama kabul edildi. Aktif: 1.
Kural: Sırayı koru; her aşamada ilgili özellikleri kodda ve mümkün olan otomatik testlerde doğrula. Sadece başarılı derleme, işlevsel doğrulama sayılmaz. Kullanıcı onayı/testi gerektiğinde belirt. Orijinal 18 Stitch tasarımını koru. APK boyutu optimizasyonunu en sona bırak. Her başlatılan derleme için GitHub Actions linkini bildir.

1. Genel kullanım ve arayüz: 18 ekranda kaydırma, üst-alt menü ve geri, bütün butonlar, ayarlar erişimi ve gerçek tercih kaydı.
2. Müzik arama ve katalog: Audius canlı API, şarkı/sanatçı/albüm arama, gerçek kart verisi, hata ve boş sonuç yönetimi.
3. Müzik oynatıcı: gerçek URL ile çalma, duraklatma/ileri/geri/seek, arka plan/kilit ekranı, medya bildirimi.
4. Dinleme geçmişi ve devam etme: gerçek kayıt ve oynatma konumu, devam etme kartı, sıra ve geçmiş.
5. Kütüphane ve favoriler: favoriler, çalma listesi CRUD, detay sayfaları ve kalıcı depolama.
6. Podcast sistemi: RSS katalog, program/bölüm detayları, çalma ve devam etme, abonelikler.
7. İndirme ve çevrimdışı: yasal indirme URL'leri, kuyruk/ilerleme/iptal, arka plan, offline oynatma ve depolama.
8. Aile profilleri ve çocuk modu: profil CRUD, opsiyonel PIN, çocuk içerik filtresi, profil bazlı veri.
9. Bulut senkronizasyonu ve öneriler: Render/PostgreSQL gerçek yazma/okuma, cihazlar arası senkronizasyon, çatışma yönetimi, dinleme bazlı öneriler.
10. Genel kabul testi ve optimizasyon: 18 ekran ve tüm akışların uçtan uca testi, hata/performans, en son APK boyutu ve yayın hazırlığı.

Durumları yalnız doğrulanan işin ardından güncelle.

## Doğrulanan testler
- 2026-10-29, derleme #32: Audius şarkı araması, sorgu değişimi, şarkı/sanatçı gösterimi, sonuç bulunamadı ve internet hatası kullanıcı testinde 5/5 başarılı. Aşama 2 kısmi; sanatçı/albüm araması ve diğer katalog işlevleri henüz onaylanmadı.
- Ertelenen hata: menü geçişlerinde kısa süre görünen `notifications` metni (Aşama 1).

## 29 Ekim 2026 — Devir sonrası oynatıcı entegrasyonu
- Kullanıcıdan devralınan kabul: #45 Apple Türkiye sanatçı → albüm → şarkılar ve Albüm/EP/Single/Tümü filtreleri başarılı. #47/#48 ses, arka plan ve kilit ekranı kabulü yapılmadı.
- Başlangıç doğrulaması: main 3fe88071, son başarılı Actions #48.
- Media3 durumu tek kaynak: alt oynatıcı ve Şimdi Çalıyor ekranına metadata, kapak, süre, ilerleme, yüklenme ve hata aktarımı. Sayfa değişiminde durum yeniden gönderilir.
- JavaScript köprüsündeki player komutları ana iş parçacığına taşındı. Controller bağlantı yarışı ve Activity kapanışında kaynak temizliği düzenlendi.
- Audio focus, kulaklık çıkarılınca duraklama ve oynatma wake lock eklendi. Kullanılmayan, serbest bırakılmayan indirme cache'i kaldırıldı.
- Audius oynatma: API'nin is_streamable=true, is_stream_gated=false, silinmemiş/erişilebilir bildirdiği kayıtlar denenebilir; başlık/sanatçı benzerliğine göre katalog eşleştirmesi yapılmaz. API izni, yükleyenin tüm haklarının bağımsız doğrulaması anlamına gelmez. İndirme eklenmedi.
- Apple şarkıları açıkça yalnızca katalog olarak etiketlenir. Tam kayıt için yetkili kaynak/abonelik entegrasyonu gerekir. Radyo, isteğe bağlı şarkı kaynağı sayılmaz ve henüz eklenmedi.
- Orijinal 18 Stitch HTML dosyası korunur; etkileşim playback.js üzerinden bağlanır.
- Yerel DOM testi: 18 ekran üzerinde 61 kontrol geçti (boş durum, gerçek metadata, metin güvenliği, tek native komut, demo olaylarının bastırılması, süre, seek, hata, sıra yokken sonraki düğmesi).
- Android CI bu commit için ayrıca çalışır. Derleme sonucu ve gerçek cihaz testi ayrı doğrulamalardır; aşama 3 henüz kabul edilmedi.
- Bekleyen cihaz kontrolleri: gerçek Audius ses çıkışı, ekran değişiminde devam, duraklat/devam, seek, arka plan/kilit ekranı, ses odağı.
- Sıradaki iş: tek arama deneyimi; sanatçı/albüm katalog kimliği ile sağlayıcı kayıt kimliklerini ayrı tutan model, erişim durumları ve yalnızca güvenilir kayıt eşleştirmesi. Büyük kaynak seçim düğmeleri bu adımda kaldırılacak.
- Kalıcı sıra, sonraki parça listesi, dinleme geçmişi, indirme ve bulut senkronizasyonu henüz bağlı değil.

