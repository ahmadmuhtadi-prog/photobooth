# Aplikasi Photobooth — Huawei MatePad Mini

Aplikasi photobooth Android (APK) lengkap: hitung mundur, live preview, multishot, kolase otomatis, watermark/bingkai, filter, QR unduh, unggah Google Drive, dan cetak dengan batas per sesi.

## Fitur

| Fitur | Cara kerja di aplikasi |
|---|---|
| Hitung mundur visual | Angka besar 3-2-1 (bisa 3/5/10 detik) beranimasi di tengah layar, lalu efek kilat putih saat jepret |
| Live preview | Layar jadi cermin digital dari kamera depan; filter langsung terlihat di pratinjau |
| Multishot | 1–4 jepretan otomatis per sesi, ada jeda dan penanda "Foto 2 dari 4" |
| Kolase otomatis | Strip vertikal 2x6 inci (600x1800 px) atau kartu 4x6 inci (1200x1800 px), 300 DPI |
| Watermark / bingkai | Judul (nama pengantin/perusahaan), sub judul, tanggal, logo, warna bingkai & teks, serta bingkai PNG transparan kustom |
| QR Code | QR unik per sesi. Mode Wi-Fi lokal (tanpa internet) + QR sambung Wi-Fi, atau tautan Google Drive |
| Cloud otomatis | Kolase diunggah ke Google Drive lewat Google Apps Script; yang gagal diantrikan dan dicoba ulang tiap 1 menit |
| Auto-cut & scaling | Setiap foto dipotong tengah agar pas dengan slot (tidak gepeng). Lembar cetak selalu rasio 4x6; strip 2x6 dicetak 2 buah berdampingan dengan tanda potong |
| Batas cetak | 0 (nonaktif) / 1 / 2 / 3 / 5 lembar per sesi |
| Filter | Normal, Hitam Putih, Sepia, Beauty (penghalus kulit), Hangat |
| Mode kios | Layar penuh, layar tetap menyala, kembali otomatis ke layar awal, menu operator dikunci PIN |

## Cara mendapatkan file APK

### Pilihan A — GitHub Actions (gratis, tanpa memasang apa pun)
1. Buat akun di https://github.com, lalu klik **New repository** (misal `photobooth`).
2. Klik **uploading an existing file**, lalu seret **seluruh isi** folder `PhotoboothApp` (termasuk folder `.github`). Klik **Commit changes**.
   - Jika folder `.github` tidak ikut terunggah (folder tersembunyi), buat manual: **Add file → Create new file**, nama `.github/workflows/build-apk.yml`, lalu tempel isi file tersebut.
3. Buka tab **Actions** → tunggu proses "Build APK Photobooth" selesai (±5–8 menit, tanda centang hijau).
4. Klik hasil build → bagian **Artifacts** → unduh **Photobooth-APK** (berisi `app-release.apk`).

### Pilihan B — Android Studio
1. Pasang Android Studio, pilih **Open** → folder `PhotoboothApp`, tunggu sinkronisasi.
2. Menu **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
3. APK ada di `app/build/outputs/apk/`.

### Memasang di MatePad Mini
1. Salin APK ke tablet (kabel USB, Huawei Share, atau unduh langsung).
2. Buka file APK → izinkan **Instal aplikasi dari sumber tidak dikenal** → Instal.
3. Buka **Photobooth**, izinkan akses kamera.

> Catatan: APK berjalan di MatePad dengan HarmonyOS 2/3/4 (kompatibel Android). Perangkat dengan **HarmonyOS NEXT (5.x)** tidak bisa memasang APK.

## Penggunaan

- **Layar awal**: tamu menyentuh layar → masuk ke live preview → pilih filter → tekan **MULAI FOTO**.
- **Menu operator**: tekan lama ±1 detik di **pojok kanan atas** layar awal, masukkan PIN (bawaan **1234**, segera ganti).
- Setelah mengubah pengaturan, tekan **SIMPAN**. Gunakan **Pratinjau Template** untuk melihat hasil kolase sebelum acara.
- Agar tamu tidak keluar dari aplikasi, aktifkan **Penyematan layar (Screen pinning)** di Pengaturan tablet → Keamanan.

## Berbagi digital

### 1. Wi-Fi lokal (tanpa internet) — aktif bawaan
Tablet menjalankan server mini. Tablet dan HP tamu harus satu jaringan:
- Paling mudah: nyalakan **hotspot** di HP/router khusus, sambungkan tablet ke sana.
- Isi **Nama Wi-Fi** dan **Sandi** di menu operator → layar hasil akan menampilkan 2 QR: (1) sambung Wi-Fi, (2) unduh foto.

### 2. Google Drive otomatis (perlu internet)
1. Buka https://script.google.com → **Proyek baru**, tempel isi `cloud/apps-script.gs`.
2. **Deploy → Deployment baru → Aplikasi web**: Jalankan sebagai **Saya**, Akses **Siapa saja**. Izinkan akses Drive.
3. Salin URL yang berakhiran `/exec` ke menu operator → aktifkan **Unggah otomatis ke Google Drive** → **Tes Unggah**.
4. Foto tersimpan di Drive: `Photobooth Uploads/<Judul Acara>/`. QR akan berisi tautan Drive.

Cara ini tidak memerlukan Google Play Services, sehingga cocok untuk tablet Huawei. Jika internet putus, QR otomatis beralih ke Wi-Fi lokal dan foto diunggah belakangan.

## Cetak
1. Pasang plugin layanan cetak sesuai merek printer (Canon PRINT, Epson iPrint/Epson Print Enabler, HP Print Service Plugin, atau Mopria Print Service) dari AppGallery/situs produsen.
2. Saat menekan **CETAK**, pilih printer dan ukuran kertas **4x6 in (10x15 cm)**, orientasi potret.
3. Format strip 2x6 akan tercetak 2 strip dalam 1 lembar 4x6 — potong di tengah (ada tanda potong).

Batas cetak menghitung berapa kali tombol CETAK ditekan per sesi. Jumlah salinan di dialog cetak Android tidak bisa dikunci aplikasi, jadi biarkan tetap 1.

## Membuat bingkai PNG sendiri
- Ukuran kanvas: **600 x 1800 px** (strip) atau **1200 x 1800 px** (4x6).
- Bagian tempat foto harus **transparan**. Simpan sebagai PNG, salin ke tablet, lalu pilih lewat **Pilih Bingkai PNG**.
- Gunakan **Pratinjau Template** untuk mengecek posisi foto.

## Struktur kode
```
app/src/main/java/id/photobooth/app/
  HomeActivity      layar sambutan + akses menu operator (PIN)
  BoothActivity     live preview, filter, hitung mundur, multishot
  ResultActivity    kolase, QR, cetak + batas cetak, kembali otomatis
  SettingsActivity  menu operator
  Collage           tata letak kolase, watermark, lembar cetak 4x6
  Filters           filter warna & beauty
  PhotoServer       server HTTP lokal untuk unduhan via QR
  Cloud             unggah Google Drive + antrean coba ulang
  Storage, QrUtil, NetUtil, Prefs, BoothApp, BaseActivity
cloud/apps-script.gs  skrip penerima unggahan di Google Drive
```
