<h1 align="center">
  OsCapture
</h1>

<p align="center">
  <b>Hareket algılayarak otomatik fotoğraf çeken Android kamera uygulaması</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" alt="Platform">
  <img src="https://img.shields.io/badge/Min%20SDK-24-brightgreen" alt="Min SDK">
  <img src="https://img.shields.io/github/license/ottamina/OsCapture" alt="License">
</p>

---

## Nedir?

OsCapture, kamera görüntüsünü gerçek zamanlı analiz ederek sahnedeki nesneler belirli bir süre hareketsiz kaldığında **otomatik olarak tek bir fotoğraf** çeken bir Android uygulamasıdır. Fotoğraf çekildikten sonra sistem kilitlenir; yeni bir hareket algılanmadan ikinci bir fotoğraf çekilmez.

## Özellikler

| Özellik | Açıklama |
|---|---|
| **Piksel Bazlı Hareket Algılama** | Ardışık frame'lerin Y-plane piksellerini karşılaştırarak hassas hareket tespiti |
| **Ayarlanabilir Bekleme Süresi** | Slider ile 0.5s — 10s arası hareketsizlik süresi ayarlama |
| **Kilitleme Mekanizması** | Fotoğraf sonrası otomatik kilit — yeni hareket olmadan tekrar çekim yok |
| **Otomatik & Tekli Çekim** | Koşullar sağlandığında yalnızca 1 fotoğraf |
| **Başlat / Durdur Butonu** | Tek tuşla otomatik çekimi aktifleştir/deaktifleştir |
| **Durum Göstergesi** | Ekranda anlık state machine durumu |
| **Fotoğraf Sayacı** | Oturum boyunca kaç fotoğraf çekildiğini gösterir |

## Durum Makinesi (State Machine)

```
                  Baslat
   ┌──────┐ ──────────────► ┌───────────────────┐
   │ IDLE │                 │ WAITING_FOR_MOTION │
   └──────┘ ◄────────────── └─────────┬─────────┘
              Durdur                  │ Hareket algılandı
                                      ▼
                              ┌───────────────┐
                     ┌───────►│ MOTION_ACTIVE │◄────────┐
                     │        └───────┬───────┘         │
                     │                │ Sahne durağan    │
                     │                ▼                  │
                     │      ┌─────────────────┐         │
                     │      │ STILLNESS_TIMER │─────────┘
                     │      └────────┬────────┘ Tekrar hareket
                     │               │ Timer doldu
                     │               ▼
                     │        ┌──────────┐
                     └────────│  LOCKED  │  Fotoğraf çekildi
                 Yeni hareket └──────────┘
```

## Teknolojiler

- **Kotlin** — Ana programlama dili
- **Jetpack Compose** — Modern deklaratif UI
- **CameraX** — Kamera preview, analiz ve fotoğraf çekimi
- **Material 3** — UI bileşenleri

## İndirme

APK dosyasını [`releases`](releases/) klasöründen indirebilirsiniz:

**[OsCapture-v1.0.apk](releases/OsCapture-v1.0.apk)**

### Kurulum

1. APK dosyasını Android cihazınıza indirin
2. "Bilinmeyen kaynaklardan yükleme" iznini verin
3. APK'yı açıp kurun

## Kullanım

1. Uygulamayı aç, kamera izni ver
2. Alt paneldeki **slider** ile bekleme süresini ayarla (varsayılan 0.5s)
3. **Başlat** butonuna bas
4. Kamerayı sahneye tut — hareket algılanınca durum göstergesi değişir
5. Sahne durağanlaştığında belirlenen süre sonunda otomatik fotoğraf çekilir
6. Yeni fotoğraf için sahnede tekrar hareket olması gerekir

## Lisans

Bu proje [MIT License](LICENSE) altında lisanslanmıştır.

---

<p align="center">
  Made with care by <a href="https://github.com/ottamina">Osman Teksoy</a>
</p>
