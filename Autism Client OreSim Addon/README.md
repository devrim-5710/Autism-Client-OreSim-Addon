# OreSim Addon

**AUTISM Client** için addon: dünyanın seed'ini kullanarak vanilla cevher üretimini simüle eder ve konumlarını kutu içinde gösterir.

Nora Tweaks'teki OreSim modülünden (o da Meteor Rejects'ten uyarlanmıştır) AUTISM Client API'sine port edilmiştir.

- **Mod adı:** OreSim Addon (`oresim-addon`)
- **Yapımcı:** theflex5710
- **Hedef:** Minecraft 26.2 / Fabric Loader 0.19.3+ / AUTISM Client 4.4+

## Özellikler

- Elmas, demir, altın, redstone, lapis, bakır, zümrüt, kuvars ve antik kalıntı için vanilla generation simülasyonu
- Simülasyon gerçek vanilla `WorldgenRandom` algoritmasını birebir takip eder (aynı çağrı sırası, aynı sonuçlar)
- Chunk yüklendikçe otomatik hesaplama; sunucu blok güncellemesi gönderince yanlış tahminler listeden düşer
- Dünya değişince otomatik yeniden yükleme
- Seed saklama: tek oyunculu dünyalarda seed otomatik okunur; sunucular için `.seed-world <seed>` ile kaydedilir (dünya/sunucu bazında `.minecraft/config/autism/oresim-addon-seeds.json` dosyasına yazılır)

## Ayarlar

| Ayar | Açıklama |
|---|---|
| Chunk Range | Oyuncu çevresinde kaç chunk yarıçapında çizim yapılacak (1–16) |
| Air Check | Simüle edilen konumların hava kontrolü: `On Load` / `Recheck` / `Off` |
| Ores | Hangi cevherlerin gösterileceği (grup ayarı) |

## Komut

```
.seed-world                -> mevcut dünyanın kayıtlı seed'ini gösterir
.seed-world <seed>         -> mevcut sunucu için seed kaydeder
.seed-world list           -> kayıtlı tüm seedleri listeler
.seed-world delete         -> mevcut dünyanın kaydını siler
```

Not: Metin seed'leri sayıya çevrilir (`String.hashCode`), vanilla ile aynı kural.

## Kurulum

1. [AUTISM Client](https://modrinth.com/mod) 4.4+ kurulu olmalı (Fabric Loader 0.19.3+, Fabric API 0.152.2+)
2. `build/libs/OreSim-Addon-1.0.0-26.2.jar` dosyasını `.minecraft/mods` klasörüne at
3. Oyun içinde AUTISM modül menüsünde **OreSim Addon > OreSim** modülünü aç

Sunucuda kullanmak için önce seed'i girin; modül açıldığında seed yoksa komut hatırlatılır.

## Geliştirme / Derleme

Gereksinimler: JDK 25, Gradle wrapper dahil.

```powershell
# 1) Önce AUTISM Client artifact'ını mavenLocal'a yayınla (bir kez):
cd ..\Autism-Client
.\gradlew.bat publishToMavenLocal --no-daemon

# 2) Addon'u derle:
cd ..\"OreSim Addon"
.\gradlew.bat build --no-daemon
```

Çıktı: `build/libs/OreSim-Addon-<surum>-26.2.jar`

Proje yapısı:

```
src/main/java/com/theflex5710/oresim/
├── OreSimAddon.java        # autism entrypoint (AutismAddon)
├── OreSimInit.java         # fabric client entrypoint
├── commands/SeedCommand    # .seed-world komutu
├── mixin/                  # 3 placement accessor + LevelRenderer render hook
├── modules/OreSimModule    # ana modül (simülasyon + event handling)
├── render/OreSimRenderer   # kutu çizimi (AutismWorldGeometry)
└── utils/{Ore,SeedStore}   # vanilla ore config okuma + seed kalıcılığı
```

## Kaynaklar ve Teşekkürler

- [Meteor Rejects](https://github.com/AntiCope/meteor-rejects/) — özgün OreSim fikri ve vein simülasyon kodu
- [Nora Tweaks](https://github.com/) — 26.2 uyarlaması ve accessor mixin'ler (CC0)
- [AUTISM Client](https://github.com/) — addon API'si ve render yardımcıları

Bu proje Meteor Rejects'in başlık yorumlarında istenen atıf koşullarını korur.
