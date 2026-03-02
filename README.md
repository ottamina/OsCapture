<h1 align="center">
  📸 OsCapture
</h1>

<p align="center">
  <b>Hareket algılayarak otomatik fotoğraf çeken akıllı Android kamera uygulaması</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Platform">
  <img src="https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Compose">
</p>

<p align="center">
  <img src="https://img.shields.io/github/license/ottamina/OsCapture?style=flat-square" alt="License">
  <img src="https://img.shields.io/github/stars/ottamina/OsCapture?style=flat-square" alt="Stars">
  <img src="https://img.shields.io/github/forks/ottamina/OsCapture?style=flat-square" alt="Forks">
</p>

---

## 🚀 Proje Hakkında

**OsCapture**, kamera görüntüsünü gerçek zamanlı analiz ederek sahnedeki nesneler belirli bir süre hareketsiz kaldığında **otomatik olarak** fotoğraf çeken bir Android uygulamasıdır. Özellikle vahşi yaşam gözlemi, timelapse başlangıçları veya sabit kadraj gerektiren otomatik çekim senaryoları için tasarlanmıştır.

Fotoğraf çekildikten sonra sistem kendini kilitler; bu sayede aynı sahnenin gereksiz yere onlarca fotoğrafının çekilmesi engellenir. Yeni bir hareket algılandığında sistem tekrar aktif hale gelir.

## ✨ Temel Özellikler

- **Piksel Bazlı Hareket Analizi:** Ardışık karelerin Y-plane piksellerini (parlaklık) örnekleyerek düşük güç tüketimiyle hassas hareket tespiti.
- **Dinamik Bekleme Süresi:** 0.5 saniyeden 10 saniyeye kadar ayarlanabilir hareketsizlik eşiği.
- **Akıllı Kilit Mekanizması:** Fotoğraf çekimi sonrası otomatik kilit — yeni bir hareket algılanmadan tekrar çekim yapmaz.
- **Gerçek Zamanlı Durum Takibi:** State Machine tabanlı arayüz ile uygulamanın o an ne yaptığını (bekliyor, hareket var, zamanlıyor, kilitli) anlık görme.
- **Modern UI:** Material 3 ve Jetpack Compose ile temiz, hızlı ve kullanıcı dostu arayüz.

## 🛠 Teknik Mimari (State Machine)

Uygulama, güvenilir bir çekim süreci için aşağıdaki durum makinesini kullanır:

```mermaid
graph TD
    IDLE((IDLE)) -- "Başlat Butonu" --> WAITING[WAITING_FOR_MOTION]
    WAITING -- "Hareket Algılandı" --> MOTION[MOTION_ACTIVE]
    MOTION -- "Sahne Durağanlaştı" --> TIMER[STILLNESS_TIMER]
    TIMER -- "Tekrar Hareket" --> MOTION
    TIMER -- "Süre Doldu" --> LOCKED[LOCKED / ÇEKİM]
    LOCKED -- "Yeni Hareket" --> MOTION
    any[Herhangi Bir Durum] -- "Durdur Butonu" --> IDLE
```

## 🏗 Geliştiriciler İçin

Bu proje artık **açık kaynak** bir projedir. Katkıda bulunabilir, fork edebilir veya kendi projelerinizde referans alabilirsiniz.

### Gereksinimler
- Android Studio Ladybug veya üzeri
- JDK 17+
- Android Device (API 24+)

### Nasıl Derlenir?
1. Repoyu klonlayın:
   ```bash
   git clone https://github.com/ottamina/OsCapture.git
   ```
2. Android Studio ile projeyi açın.
3. `local.properties` dosyasının SDK yolunuzu içerdiğinden emin olun.
4. Gradle senkronizasyonunu bekleyin ve cihazınıza yükleyin.

## 📱 İndirme

Derlenmiş en güncel sürümü (APK) [`releases`](releases/) klasöründe bulabilirsiniz:

👉 **[OsCapture-v1.0.apk](releases/OsCapture-v1.0.apk)**

## 📚 Teknolojiler

- **Kotlin:** Modern Android geliştirme dili.
- **Jetpack Compose:** Deklaratif UI kütüphanesi.
- **CameraX:** Kamera önizleme, analiz ve fotoğraf çekimi için Google kütüphanesi.
- **Coroutines & Handler:** Asenkron işlemler ve zamanlayıcılar için.

## 📄 Lisans

Bu proje **MIT Lisansı** altında korunmaktadır. Daha fazla bilgi için [LICENSE](LICENSE) dosyasına göz atabilirsiniz.

---

<p align="center">
  Geliştiren: <b><a href="https://github.com/ottamina">Osman Teksoy</a></b>
</p>
