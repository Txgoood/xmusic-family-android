# Aile bağlantısı

## Bir defalık kurulum

Her telefonda aynı ortak Drive müzik klasörünü seçin ve Otomatik ortak arşiv bağlantısını açın. Herkes kendi Google hesabını kullanabilir; klasör kişiye Düzenleyici olarak paylaşılmış olmalı.

1. Kurucu telefonda yönetici profiline geçin, ebeveyn PIN’i belirleyin. Fatma ve çocuk profillerini burada ekleyin.
2. Aile Profilleri → Aile bağlantısı → Bu telefonda aileyi oluştur.
3. Aynı menüden aile bağlantı kodunu kopyalayın. Bu kod gizli bir erişim anahtarıdır.
4. Diğer telefonda verileri korunacak mevcut profili aktif seçin. Aile bağlantısı → kodu yapıştırın → Aileye katıl → Fatma veya ilgili çocuk profilini seçin.
5. Yetişkinler Aile bağlantısı ekranından miks paylaşımını açıp kapatabilir.

## Eşitlenenler

Kurucunun tanımladığı profil kimlikleri, adlar, roller, PIN doğrulayıcıları, çocuk yaş grubu, süre sınırı, onaylı/reddedilmiş içerikler ve bekleyen istekler eşitlenir. Yetişkinlerin paylaşmayı seçtiği beğeni ve son dinlemeler aile miksinde birleştirilir. Bu işlem kişisel kitaplıkların karşılıklı tam yedeği değildir. Yerel cihaz dosyalarının URI adresleri paylaşılmaz. Profil görseli aktif kullanıldığı cihazdan paylaşılır. Çocuk geçmişi mikse gönderilmez.

## Çocuk denetimi

Çocuk cihazında şarkı isteği oluşturulur. Kurucu telefonda Çocuk → Onaylı içerikleri yönet bölümünde görülür. Onaylanan kayıt çocuk cihazına sonraki eşitlemede gelir. Kuralları yalnızca kurucu cihaz imzalayabilir. İmzalar katılan cihazlarda doğrulanır; yalnızca ortak klasörde düzenleme iznine sahip olmak çocuk kurallarını değiştirmek için yeterli değildir.

## Çalışma ve sınırlar

Eşitleme uygulama açıkken yaklaşık 60 saniyede bir ve elle Şimdi eşitle ile çalışır. Uygulama kapalıyken anlık bildirim veya arka plan eşitlemesi sunulmaz. Çevrimdışı son alınan kurallar ve bilinen süre kullanımı geçerlidir; farklı cihazlardaki kullanım yeniden bağlanınca birleşir. Saat, tarih veya Android uygulama verilerine müdahaleye karşı işletim sistemi düzeyinde koruma iddiası yoktur.

Veriler ortak klasörde AES-GCM ile şifrelenir. Kurallar kurucuya ait RSA anahtarıyla imzalanır. Özel imza anahtarı Drive’a veya katılım koduna eklenmez. Kurucu uygulamayı silmemeli; güncelleme mevcut uygulamanın üzerine kurulmalıdır. Şimdilik kurucu anahtarını başka telefona taşıma ve anahtar kurtarma akışı yoktur.

Her cihaz ayrı veri dosyasını yazar. Değişmeyen dosyalar tekrar yüklenmez; okuma önbelleği Drive modifiedTime alanıyla doğrulanır. Kurucu politikası tek yazarlıdır. Eski kişisel kitaplık kaynak kopyası ve katılım öncesi profil listesi cihazda korunur.

## Doğrulama

FamilySyncStoreTest şifreleme, imza, eski politika reddi, kütüphane geçişi, rol kısıtları, istek birleştirme ve süre toplamını kontrol eder. FamilyDriveClientTest iki ayrı cihaz tercihini aynı sahte Drive üzerinde çalıştırır: karşılıklı miks, çocuk isteği, kurucu onayı ve çocuk cihazına uygulanması. Google OAuth ve gerçek Drive izinleri cihaz testi gerektirir. Arayüz testleri orijinal 18 Stitch ekranını kullanır.
