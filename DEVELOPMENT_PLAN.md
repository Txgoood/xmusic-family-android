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


## Aktif olmayan kontrolleri bağlama — ikinci geliştirme
- Kullanıcı isteği: tüm bağlantı/butonların gerçek işlemlere bağlanması. Kapsam ve kalan işler docs/CONTROL_AUDIT.md içinde.
- Yerel yetişkin profili ve opsiyonel PIN; profil bazlı favori/liste/geçmiş; gerçek oynatma kuyruğu; servis tabanlı zamanlayıcı; yerel ses açma; sesli arama bağlantısı eklendi.
- RSS podcast akışı, gerçek Android indirme listesi, indirme iptal/sil/çevrimdışı oynatma eklendi.
- 18 ekran tarandı. 61 oynatıcı + 34 uygulama DOM kontrolü geçti; profil ve RSS için 6 Android birim testi eklendi.
- Engelli kontroller tamamlandı sayılmadı. Güvenli çocuk modu, bulut, ticari müzik kaynak izinleri, kalite ve gerçek sanatçı/album detay bağları açık.
- Kaynak seçilmemiş demo kartlar gerçek aramaya yönlenir; sahte parça oynatılmaz. Kullanıcı cihaz kabulü bekliyor.


## Kaynak motoru cihaz testi — 30 Ekim 2026
- #53: Arama sonuçları göründü; gerçek ses başarısız.
- #55: Kullanıcı ses isteğinde HTTP 403 bildirdi. Başlık aktarımı sorunu çözmedi.
- #56: Kullanıcı kaynak çözümlemede genel hata bildirdi. HLS yalnız başına doğrulanmadı.
- Yeni deneme: Kurulu yt-dlp için günlük kararlı sürüm kontrolü, ayrı HLS/varsayılan denemeleri, motor sürümü ve hata ayrıntısı.
- Oynatma, indirme ve Drive aktarımı tamamlandı sayılmaz. Geri dönüş: fd51e66716f0cf7b90f3006ab899a1d68210a197.

- #57: Kullanıcı seçilen parçanın sesle çalıştığını bildirdi. Arka plan, kilit ekranı, diğer kayıtlar ve çevrimdışı indirme henüz doğrulanmadı.

- #59: Kullanıcı sanatçı/albüm/şarkı geçişini ve oynatmayı başarılı bildirdi; başlatmada bazen 5–10 saniye bekleme var.
- Hız denemesi: Çözülmüş bağlantıyı bellekte 90 saniye tut; oynatma hatasında sil. Bu ses dosyası önbelleği/çevrimdışı indirme değildir.
- Drive OAuth ve yükleme entegrasyonu henüz bulunmuyor; Drive aktarımı hazır değildir.

- #60: Kullanıcı aynı parçanın ikinci açılışının hızlandığını doğruladı. İlk açılış ve Drive arşivi ayrıca bekliyor.

- #62: Kullanıcı daha fazla sonuç özelliğinin çalıştığını bildirdi.
- Drive: Dosya yükleme istemcisi eklendi; kullanıcı yetkilendirmesi, indirme kuyruğu, hesap ve klasör seçimi, yeniden deneme ve cihaz testi henüz tamamlanmadı.

- #66: Kullanıcı Google Drive bağlantısının tamamlanamadığını bildirdi. Hata kodu önceki sürümde gizlendi; OAuth ve imza yapılandırması doğrulanmış değil.

- #67 OAuth: Kullanıcı Google Cloud Android OAuth kaydını ve Drive API etkinleştirmesini tamamladı; hesap izni başarıyla alındı.
- Sonraki test: Uygulamanın kendi XMusic Family klasörünü bulma/oluşturma ve telefondan seçilmiş ses dosyasını yükleme. Ön planda çalışma, 250 MB sınırı. Mevcut farklı klasör seçimi, Drive oynatma, otomatik kaynak indirme henüz yok.

- Sabit imzalama: GitHub secret eklendi; #71 APK imzası kontrol edildi. Kullanıcı yeni OAuth imzasıyla bağlanıp gerçek MP3 yüklemesini doğruladı.
- Drive arşivi: Sayfalı listeleme ve Media3 üzerinden yetkili dosya oynatma eklendi; cihaz testi bekliyor. Bağlantı izni bu oturum için alınmalı.
- Otomatik dinleme arşivi/indirme kuyruğu ve çevrimdışı kalıcı dosyalar henüz yok.

- #72: Kullanıcı Drive arşivindeki dosyanın uygulamada çaldığını doğruladı.
- Kalıcı yükleme kuyruğu: WorkManager ile seçilmiş yerel ses dosyası, hesap kimliği kontrolü, bağlantı bekleme ve en fazla üç deneme. Anahtarlar iş verisine kaydedilmez. Yeni yüklemeler SHA-256 ile etiketlenir; eski etiketsiz dosyalar içerik eşleştirmesine dahil değildir. Bu cihazda paralel aynı içerik yüklemesi kilitlenir.
- Otomatik dinleme kaydı ve kaynak dosyası indirme henüz bu kuyruğa bağlı değil. Kuyruk cihazda doğrulanmadı; Android zorla durdurma ve süre sınırlamaları geçerlidir.

- #75: Kullanıcı kalıcı yerel dosya yükleme kuyruğunun çalıştığını doğruladı.
- Otomatik arşiv denemesi: Hesap yeniden bağlandıktan sonra gerçek experimental oynatma başlangıcı WorkManager işini tetikler; ses yt-dlp/FFmpeg ile hazırlanıp Drive'a yüklenir. Kaynak kimliği Drive appProperties alanına ve hesap bazlı yerel eşleştirmeye yazılır. Tekrar aynı YouTube kaydı seçilirse tamamlanmış Drive kopyası tercih edilir.
- Varsayılan otomatik mod açık; Ayarlar'dan aç/kapat ve yükleme kuyruğu durum ekranı mevcut. Hesap izni gerektiğinde iş hata verir; uygulama zorla durdurulursa Android işleri yeniden başlatmayı bekletebilir. Kaynak indirme, FFmpeg cihaz desteği, otomatik yükleme ve tekrar Drive oynatma henüz uçtan uca cihaz testinden geçmedi.


- #80: Kullanıcı otomatik arşivleme düzeltmesinin çalıştığını doğruladı. Çalışan geri dönüş noktası: `93b4a3a0fe695e71880452022dc645241a33aa55`.
- Kuyruk görünürlüğü: Otomatik indirme, ses hazırlama, Drive yükleme ve sunucu onayı aşamaları WorkManager ilerleme verisiyle gösterilir. Açık kuyruk penceresi değişiklikleri canlı izler; kapandığında gözlemci kaldırılır. Yükleme yüzdesi gönderilen baytlardan hesaplanır; başarı yalnız Drive dosya kimliği döndükten sonra bildirilir. Aynı aşamanın güncellemeleri saniyede birle sınırlıdır. Yeni görünüm cihazda henüz doğrulanmadı.


## Keşfet ve son aramalar — 30 Ekim 2026
- Kullanıcı #84 canlı Drive kuyruğunu ve #86 birleşik Keşfet aramasını başarılı bildirdi. Son onaylı arayüz noktası: `5ea3629a30d1a9a899d9280a24be52b95874617b`.
- Son Aramalar artık demo etiketleri yerine profilin yerel geçmişini kullanır. En yeni 12 sorgu, Türkçe büyük/küçük harf ve boşluk normalleştirmesiyle tekilleştirilir. Yazım durakladığında, Enter/odak çıkışı veya sonuç seçimiyle kaydedilir; yeniden arama, tek kayıt silme ve tümünü temizleme bağlandı.
- Geçmiş kitaplıktan ayrı anahtarda saklanır; profil silindiğinde birlikte silinir. Cihazlar arası senkronizasyon bu değişiklik kapsamında değildir. Android kalıcılık/profil ayrımı testleri ve Keşfet DOM testleri eklendi; yeni özelliğin cihaz kabulü bekliyor.
