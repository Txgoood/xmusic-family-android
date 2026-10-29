# XMusic Family — Ekran Kontrolleri

Bu liste statik tasarım kontrollerinin durumunu kaydeder. Aktif/bağlı sayısı gerçek cihaz kabulü değildir; bazı kartlar gerçek içerik aramasına yönlendirir, bazı filtreler yalnız arayüz durumunu değiştirir. Kaynaksız demo kaydı oynatılmış kabul edilmez.

| Ekran | Bağlı / mevcut UI kontrolü | Engelli kontrol |
|---|---:|---:|
| aile_profili_se_imi | 3 | 0 |
| alb_m_detay | 14 | 1 |
| alma_listesi_y_netimi | 3 | 0 |
| ana_sayfa | 28 | 1 |
| arama_ve_ke_fet | 22 | 0 |
| ayarlar_ve_profil_y_netimi | 10 | 0 |
| hata_ve_durum_ekranlar | 24 | 3 |
| i_lk_kurulum_ve_profil_olu_turma | 4 | 0 |
| i_ndirme_y_neticisi | 11 | 0 |
| imdi_al_yor | 14 | 0 |
| kitapl_m | 11 | 0 |
| m_zik_merkezi | 30 | 5 |
| ocuk_modu_kids_safe | 3 | 0 |
| oynatma_s_ras_queue | 7 | 0 |
| podcast_merkezi | 11 | 0 |
| podcast_program_ve_b_l_m_detay | 3 | 0 |
| sanat_detay | 19 | 6 |
| uyku_zamanlay_c_s | 12 | 0 |

## Gerçek işlevi eklenen akışlar

- Yerel yetişkin profilleri; opsiyonel PIN (tuzlu PBKDF2), 5 yanlış denemede 1 dakika bekleme; profil bazlı kitaplık.
- Favori ekle/kaldır; çalma listesi oluştur/ad değiştir/sil; parça ekle/çıkar/sırala; yerel geçmiş.
- Arama sonucundan gerçek Media3 kuyruğuna ekle; kuyruğu oynat/sırala/temizle; karışık/tekrar/hız; uyku zamanlayıcısı.
- Android SAF ile kullanıcının seçtiği yerel ses dosyası; sesli arama için Android tanıma ekranı; sistem paylaşım seçicisi.
- HTTPS RSS ekle/takibi bırak; gerçek enclosure bölümleri; oynatma ve kuyruk; Android DownloadManager ile podcast indirme/iptal/sil/çevrimdışı oynatma.
- Sahte Cast düğmesi yerine açıkça Cihaz Ses Ayarları. Cast uygulandı sayılmaz.

## Hâlâ tamamlanmayanlar

- Güvenli çocuk profili ve onaylı içerik listesi: hazır olmadığı açıkça gösterilir.
- Render/Postgres aile senkronizasyonu, davet, ortak aile miksleri.
- Ticari müzik tam kayıt/indirme erişimi, sağlayıcı yetkilendirmesi, gerçek kalite seçimi.
- Sanatçı/albüm detay şablonlarının katalog kimliklerine tam bağlanması ve tek birleşik arama.
- Kalıcı oynatma konumu ve uygulama yeniden açılınca otomatik devam; geçmiş şimdilik kayıt kimliği/titel düzeyinde.
- Ayar anahtarlarının bir bölümü yalnız tercih kaydeder; ilgili hizmetler tamamlanmadı.
- Ekran düzeni gerçek kitaplık/sıra/podcast/profil verisine göre çalışma anında güncellenir; orijinal 18 HTML dosyası değiştirilmez.

## Açıkça engellenen özgün kontroller

- alb_m_detay / Favorilere Ekle: Gerçek veri bağlantısı bekliyor
- ana_sayfa / diversity_1 Aile Karışımı : Aile senkronizasyonu bağlı değil
- hata_ve_durum_ekranlar / cell_wifi Hücresel Veri ile İndir : Yetkili indirme / kalite kaynağı bağlı değil
- hata_ve_durum_ekranlar / family_restroom Aile Ortak Miksi : Aile senkronizasyonu bağlı değil
- hata_ve_durum_ekranlar / podcasts Haftalık Bilim Sohbetleri : Gerçek veri bağlantısı bekliyor
- m_zik_merkezi / Stüdyo Kalite Seçici: Yetkili indirme / kalite kaynağı bağlı değil
- m_zik_merkezi / Aile DJ Miksini Başlat: Aile senkronizasyonu bağlı değil
- m_zik_merkezi / İndirildi: Yetkili indirme / kalite kaynağı bağlı değil
- m_zik_merkezi / Çevrimdışı İndir: Yetkili indirme / kalite kaynağı bağlı değil
- m_zik_merkezi / Çevrimdışı İndir: Yetkili indirme / kalite kaynağı bağlı değil
- sanat_detay / check Takip Ediliyor : Gerçek sanatçı / podcast kaydı bağlı değil
- sanat_detay / Takip Et: Gerçek sanatçı / podcast kaydı bağlı değil
- sanat_detay / Takip Et: Gerçek sanatçı / podcast kaydı bağlı değil
- sanat_detay / Takip Et: Gerçek sanatçı / podcast kaydı bağlı değil
- sanat_detay / Takip Et: Gerçek sanatçı / podcast kaydı bağlı değil
- sanat_detay / Favorilere Ekle: Gerçek veri bağlantısı bekliyor

## Doğrulama

- DOM: oynatıcı 61; uygulama akışları 34 kontrol geçti. İki dinleyicinin tek native komut üretmesi, saklanan listeler, sıra, zamanlayıcı, RSS olayları ve yerel dosya kaydı kapsanır.
- Android CI: profil PIN ve RSS ayrıştırıcı testleri bu değişiklikle eklenmiştir; çalıştırma sonucu Actions üzerinden takip edilir.
- Ağ ve cihaz testleri bu kontrollerden ayrıdır. Gerçek RSS ses/indirme, Android dosya seçici, ses tanıma ve arka plan zamanlayıcısı cihaz onayı bekler.
