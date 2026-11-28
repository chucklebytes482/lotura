package com.example.remote

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object RemoteZip {
    /**
     * Menulis isi [dir] ke [out] sebagai zip. Semua entri diawali [rootName],
     * jadi saat dibuka hasilnya satu folder utuh bernama [rootName]. [comment] disimpan di zip,
     * dipakai untuk mengenali isi yang sama tanpa membuka entri.
     */
    fun zipDirectory(dir: File, rootName: String, out: File, comment: String? = null) {
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            if (comment != null) zip.setComment(comment)
            fun add(file: File, path: String) {
                if (file.isDirectory) {
                    file.listFiles().orEmpty().sortedBy { it.name }.forEach { add(it, "$path/${it.name}") }
                } else {
                    zip.putNextEntry(ZipEntry(path))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            add(dir, rootName)
        }
    }
}
