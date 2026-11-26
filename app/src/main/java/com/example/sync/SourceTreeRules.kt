package com.example.sync

/**
 * Aturan murni untuk pohon per sumber. Tidak ada Android, tidak ada I/O, jadi bisa diuji langsung.
 * Kunci item berbentuk "sourceKey|path". Awalan "$sourceKey|" dipakai untuk mengenali
 * semua item milik satu sumber, dan "$sourceKey|$parentPath/" untuk anak sebuah folder.
 */
object SourceTreeRules {

    const val DRIVE_PREFIX = "drive:"
    const val GITHUB_PREFIX = "github:"

    fun isDriveSource(sourceKey: String): Boolean = sourceKey.startsWith(DRIVE_PREFIX)

    /** Email dari kunci sumber Drive. */
    fun driveEmailOf(sourceKey: String): String = sourceKey.removePrefix(DRIVE_PREFIX)

    /** fullName repo dari kunci sumber GitHub. */
    fun githubFullNameOf(sourceKey: String): String = sourceKey.removePrefix(GITHUB_PREFIX)

    /** Label untuk log: "Drive · email" atau "GitHub · owner/repo". */
    fun sourceLabel(sourceKey: String): String =
        if (isDriveSource(sourceKey)) {
            "Drive · ${driveEmailOf(sourceKey)}"
        } else {
            "GitHub · ${githubFullNameOf(sourceKey)}"
        }

    /** True jika [key] adalah sumber [sourceKey] sendiri, atau item di dalamnya. */
    fun ownsKey(sourceKey: String, key: String): Boolean =
        key == sourceKey || key.startsWith("$sourceKey|")

    /** Awalan kunci untuk semua anak langsung dan turunan dari folder [parentPath] (kosong = akar). */
    fun childPrefix(sourceKey: String, parentPath: String): String =
        if (parentPath.isEmpty()) "$sourceKey|" else "$sourceKey|$parentPath/"

    /** Path item anak di bawah [parentPath]. */
    fun childPath(parentPath: String, name: String): String =
        if (parentPath.isEmpty()) name else "$parentPath/$name"

    /** Membuang kunci loaded yang milik sumber [sourceKey] (termasuk folder di dalamnya). */
    fun loadedWithoutSource(loaded: Set<String>, sourceKey: String): Set<String> =
        loaded.filterNot { ownsKey(sourceKey, it) }.toSet()

    /** Membuang kunci loaded yang berawalan [prefix]. */
    fun loadedWithoutPrefix(loaded: Set<String>, prefix: String): Set<String> =
        loaded.filterNot { it.startsWith(prefix) }.toSet()

    /**
     * Mengganti isi satu level Drive. Item lama di bawah [parentPath] dibuang bersama keturunannya.
     * Untuk akar (parentPath kosong), semua item sumber itu dibuang.
     */
    fun replaceDriveLevel(
        existing: List<TreeItem>,
        sourceKey: String,
        parentPath: String,
        fresh: List<TreeItem>
    ): List<TreeItem> {
        val prefix = childPrefix(sourceKey, parentPath)
        val kept = existing.filterNot {
            it.key.startsWith(prefix) || (parentPath.isEmpty() && it.sourceKey == sourceKey)
        }
        return kept + fresh
    }
}
