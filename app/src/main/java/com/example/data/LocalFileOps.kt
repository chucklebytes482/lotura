package com.example.data

import java.io.File
import java.security.MessageDigest

/**
 * Operasi berkas lokal untuk penjelajah file dan editor.
 * Tidak bergantung pada Android, sehingga bisa diuji sebagai unit test biasa.
 */
object LocalFileOps {

    /** Batas ukuran berkas yang bisa dibuka di editor teks. */
    const val MAX_EDIT_BYTES = 2L * 1024L * 1024L

    private const val MAX_NAME_LENGTH = 255
    private const val SNIFF_BYTES = 8192

    data class Entry(
        val name: String,
        val file: File,
        val isDir: Boolean,
        val size: Long
    )

    /** Isi satu folder: folder dulu, lalu berkas, masing-masing diurutkan tanpa memperhatikan huruf besar/kecil. */
    fun list(dir: File): List<Entry> {
        if (!dir.isDirectory) return emptyList()
        val children = dir.listFiles() ?: return emptyList()
        return children
            .map { Entry(it.name, it, it.isDirectory, if (it.isFile) it.length() else 0L) }
            .sortedWith(compareBy<Entry>({ !it.isDir }, { it.name.lowercase() }))
    }

    /** Berkas teks biasa yang ukurannya masih di bawah batas editor. */
    fun isEditable(file: File): Boolean {
        if (!file.isFile || file.length() > MAX_EDIT_BYTES) return false
        val head = ByteArray(minOf(SNIFF_BYTES.toLong(), file.length()).toInt())
        file.inputStream().use { input ->
            var read = 0
            while (read < head.size) {
                val n = input.read(head, read, head.size - read)
                if (n < 0) break
                read += n
            }
        }
        return head.none { it == 0.toByte() }
    }

    fun readText(file: File): String = file.readText(Charsets.UTF_8)

    /** Tulis lewat berkas sementara lalu ganti, supaya isi lama tidak rusak jika proses berhenti di tengah. */
    fun writeText(file: File, text: String) {
        val parent = file.parentFile ?: error("Berkas tidak punya folder induk")
        parent.mkdirs()
        val tmp = File(parent, ".lotura_tmp_${System.nanoTime()}")
        try {
            tmp.writeText(text, Charsets.UTF_8)
            if (file.exists()) file.delete()
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    fun createFile(parent: File, rawName: String): File {
        val name = validName(rawName)
        val target = File(parent, name)
        if (target.exists()) throw IllegalArgumentException("\"$name\" sudah ada.")
        parent.mkdirs()
        if (!target.createNewFile()) throw IllegalStateException("Gagal membuat \"$name\".")
        return target
    }

    fun createFolder(parent: File, rawName: String): File {
        val name = validName(rawName)
        val target = File(parent, name)
        if (target.exists()) throw IllegalArgumentException("\"$name\" sudah ada.")
        if (!target.mkdirs()) throw IllegalStateException("Gagal membuat folder \"$name\".")
        return target
    }

    fun rename(file: File, rawNewName: String): File {
        val name = validName(rawNewName)
        val target = File(file.parentFile ?: error("Induk tidak ditemukan"), name)
        if (target.absolutePath == file.absolutePath) return file
        if (target.exists()) throw IllegalArgumentException("\"$name\" sudah ada.")
        if (!file.renameTo(target)) throw IllegalStateException("Gagal mengganti nama ke \"$name\".")
        return target
    }

    /** Hapus berkas atau folder (rekursif). */
    fun delete(file: File) {
        if (!file.exists()) return
        if (!file.deleteRecursively()) throw IllegalStateException("Gagal menghapus \"${file.name}\".")
    }

    /** Memastikan nama berkas aman dan tidak berisi karakter pemisah path. Mengembalikan nama yang sudah dirapikan. */
    fun validName(raw: String): String {
        val name = raw.trim()
        require(name.isNotEmpty()) { "Nama tidak boleh kosong." }
        require(name != "." && name != "..") { "Nama \"$name\" tidak diizinkan." }
        require(name.length <= MAX_NAME_LENGTH) { "Nama terlalu panjang." }
        require(name.none { it == '/' || it == '\\' || it == '\u0000' }) {
            "Nama tidak boleh berisi / atau \\."
        }
        return name
    }

    /** MD5 isi berkas dalam hex kecil, sama dengan md5Checksum yang dikirim Drive. */
    fun md5Hex(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** SHA-1 seperti Git: sha1("blob <ukuran>\0" + isi). Sama dengan sha blob di GitHub. */
    fun gitBlobSha(file: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update("blob ${file.length()}\u0000".toByteArray(Charsets.US_ASCII))
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
