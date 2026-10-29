# XMusic Family - Ürün Gereksinim Dokümanı (PRD)

## 1. Genel Bakış
XMusic Family, aileler için özel olarak tasarlanmış, abonelik gerektirmeyen, Türkçe arayüzlü bir müzik ve podcast platformudur. Dağıtım APK üzerinden yapılır.

## 2. R1 — Aile ve Kullanıcı Profilleri
- **Kullanıcı Senaryosu:** Baba, aile hesabına giriş yapar ve çocukları için ayrı profiller oluşturur. Herkesin kendi favorileri ve geçmişi olur.
- **Teknik Gereksinim:** Yerel profil yönetimi (Room DB), cihazlar arası Firestore senkronizasyonu.
- **Kabul Kriteri:** Profil değiştirildiğinde favoriler ve öneriler anında güncellenmeli.
- **Öncelik:** Yüksek.

## 3. R2 — Ana Sayfa
- **Kullanıcı Senaryosu:** Kullanıcı, uygulamayı açtığında kişiselleştirilmiş "Günlük Mix" listesini görür.
- **Teknik Gereksinim:** Öneri motorundan gelen verilerin Jetpack Compose ile dinamik render edilmesi.
- **Öncelik:** Orta.

*(... ve diğer bölümler R3-R9)*

## 4. Ekran Envanteri
1. Giriş / Kayıt Ekranı
2. Ana Sayfa (Dashboard)
3. Profil Seçim/Yönetim Ekranı
4. Müzik/Podcast Arama ve Keşif
5. Oynatıcı Ekranı (Mini Player & Full Screen)
6. İndirmeler ve Çevrimdışı Kütüphane
7. Ayarlar ve Ebeveyn Kontrolü

## 5. Veri Modeli Taslağı
- **Family:** ID, Name, CreatedAt
- **Profile:** ID, FamilyID, Name, AvatarURL, PIN_Hash, IsChild
- **Track:** ID, Title, Artist, Album, SourceURL, Duration
- **DownloadJob:** ID, ProfileID, TrackID, Status, Progress

## 6. MVP / V1 / V2 Ayrımı
- **MVP:** Temel oynatıcı (ExoPlayer), Profil yönetimi, Arama, Yerel kütüphane.
- **V1:** İndirme yönetimi, Podcast desteği, Öneri motoru.
- **V2:** Aile paylaşımı, Hibrit öneri yaklaşımı.
