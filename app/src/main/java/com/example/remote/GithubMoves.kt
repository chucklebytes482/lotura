package com.example.remote

/** Aturan pindah dan ganti nama di GitHub. Dipisah dari RemoteFiles agar bisa diuji tanpa jaringan. Semua ref adalah path dari root repo. */
internal object GithubMoves {
    /** Pesan galat bila [targetFolder] tidak boleh menjadi tujuan; null bila aman. */
    fun invalidTarget(sourceRefs: List<String>, sourceFolder: String, targetFolder: String): String? = when {
        targetFolder == sourceFolder -> "Pilih folder lain sebagai tujuan."
        sourceRefs.any { targetFolder == it || targetFolder.startsWith("$it/") } ->
            "Folder tujuan ada di dalam item yang dipindah."
        else -> null
    }

    /**
     * Folder yang kehilangan semua isinya butuh .gitkeep, kalau tidak folder itu hilang dari GitHub.
     * Folder root tidak pernah perlu.
     */
    fun needsKeep(folder: String, remainingNames: List<String>): Boolean =
        folder.isNotEmpty() && remainingNames.isEmpty()
}
