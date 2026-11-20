# Panduan Penamaan Aplikasi Unik (Bahasa Euskara)

Standar singkat penamaan aplikasi dan `applicationId` menggunakan kosakata **Euskara (Basque)** agar unik, berkarakter, dan bebas dari akhiran acak.

---

## 1. Aturan Utama

1. **Bahasa Euskara (Isolat):** Tidak berkerabat dengan bahasa Inggris/Eropa lainnya sehingga probabilitas nama kembar sangat rendah.
2. **Ritme 3 Suku Kata:** Prioritaskan kata berpola 3 ketukan dengan akhiran vokal terbuka (`-a`, `-i`, `-u`) seperti *Lo-tu-ra* atau *Tek-la-tu*.
3. **Ringkas & Tanpa Diakritik:** Maksimal **1 kata** (4–8 huruf) menggunakan alfabet Latin standar tanpa tanda aksen.
4. **Format `applicationId`:** Gunakan langsung `com.aistudio.<kata_euskara>` *(contoh: `com.aistudio.lotura`)*.

---

## 2. Referensi Cepat (3 Suku Kata)

| Tema Aplikasi | Kata Euskara | Pemenggalan | Arti | `applicationId` |
| :--- | :--- | :--- | :--- | :--- |
| **Mind Map / Ide** | **Lotura** | *Lo-tu-ra* | Tautan / koneksi | `com.aistudio.lotura` |
| **Mind Map / Ide** | **Ardatza** | *Ar-dat-za* | Poros / sumbu | `com.aistudio.ardatza` |
| **Mind Map / Ide** | **Adarra** | *A-dar-ra* | Cabang / ranting | `com.aistudio.adarra` |
| **Mind Map / Ide** | **Sormena** | *Sor-me-na* | Kreativitas | `com.aistudio.sormena` |
| **Ketik / Teks** | **Teklatu** | *Tek-la-tu* | Papan ketik | `com.aistudio.teklatu` |
| **Ketik / Teks** | **Idatzi** | *I-dat-zi* | Menulis / ketik | `com.aistudio.idatzi` |
| **Ketik / Teks** | **Ikurra** | *I-kur-ra* | Simbol / tanda | `com.aistudio.ikurra` |
| **Utilitas Ringan** | **Arina** | *A-ri-na* | Ringan / gesit | `com.aistudio.arina` |

---

## 3. Templat Prompt AI

```text
Carikan 10 kata asli bahasa Euskara (Basque) untuk nama aplikasi [fungsi aplikasi].
Syarat:
1. Tepat 3 suku kata dengan ritme enak diucapkan (seperti Lo-tu-ra).
2. Alfabet Latin standar tanpa tanda diakritik dan mudah dilafalkan.
3. Sertakan pemenggalan, arti harfiah, dan format: com.aistudio.[namakata]
```

---

## 4. Checklist Ganti `applicationId`

- [ ] Ubah `namespace` & `applicationId` di `app/build.gradle.kts` serta paket di `proguard-rules.pro`.
- [ ] Daftarkan `applicationId` baru + **SHA-1 & SHA-256** (debug & release) ke Firebase / Google Cloud Console jika memakai layanan Google, lalu perbarui `google-services.json`.