# XMusic Family - Teknik Analiz ve Mimari Dokümanı

## 1. Mimari Seçim
**Önerilen Yaklaşım: B) Tamamen Native Kotlin + Jetpack Compose + Media3**

- **Gerekçe:**
    - **Performans:** Native Kotlin, Media3 (ExoPlayer) ile doğrudan etkileşime girerek en yüksek ses kalitesini ve düşük gecikmeyi sağlar.
    - **Arka Plan:** Android arka plan kısıtlamalarına native `MediaSessionService` ile en iyi uyum.
    - **Bakım:** Tek bir dil (Kotlin) ile geliştirme ve bakım kolaylığı.

## 2. Sistem Bileşenleri
- **Native Playback Service:** `MediaSessionService` kullanılarak kilit ekranı ve bildirim kontrolü.
- **Source Adapter Interface:** Farklı müzik/podcast kaynaklarını entegre etmek için soyut katman.
- **Offline Download Manager:** `WorkManager` kullanılarak güvenilir indirme.

## 3. Güvenilirlik Senaryoları
- **İnternet Kopması:** ExoPlayer'ın `LoadErrorHandlingPolicy` kullanılarak yeniden deneme stratejileri.
- **Uygulamanın Arka Plana Geçmesi:** Foreground Service kullanımı.

## 4. Performans Hedefleri
- **Sıcak Açılış:** p95 < 2s.
- **Yerel Arama:** p95 < 300ms.
- **Kesintisiz Dinleme:** 2 saatlik test.

## 5. Teknoloji Yığını (Stack)
- **UI:** Jetpack Compose (M3)
- **Playback:** Android Media3
- **Database:** Room (Yerel), Firestore (Aile verisi/Senkronizasyon)
- **Networking:** Retrofit + Moshi
- **Dependency Injection:** Hilt
- **Asynchrony:** Coroutines / Flow
