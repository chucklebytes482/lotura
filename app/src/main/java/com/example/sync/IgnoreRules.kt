package com.example.sync

class IgnoreRules(rawPatterns: String) {

    private data class CompiledRule(
        val raw: String,
        val directoryOnly: Boolean,
        val anchoredPath: Boolean,
        val regex: Regex
    )

    private val rules: List<CompiledRule> = rawPatterns
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { pattern ->
            val dirOnly = pattern.endsWith("/")
            val clean = pattern.trimEnd('/').trimStart('/')
            val anchored = clean.contains("/")
            CompiledRule(
                raw = pattern,
                directoryOnly = dirOnly,
                anchoredPath = anchored,
                regex = globToRegex(clean)
            )
        }
        .toList()

    fun isIgnored(relativePath: String, isDirectory: Boolean = false): Boolean {
        val normalized = relativePath.replace('\\', '/').trim('/')
        if (normalized.isEmpty()) return false

        val segments = normalized.split('/')
        // Always ignore internal temp files created during atomic download
        if (segments.any { it.startsWith(".syncbebas_") }) return true

        for (rule in rules) {
            if (rule.directoryOnly) {
                // Match any directory segment or the directory itself
                val dirsToCheck = if (isDirectory) segments else segments.dropLast(1)
                if (rule.anchoredPath) {
                    val dirPath = dirsToCheck.joinToString("/")
                    if (dirPath.isNotEmpty() && (rule.regex.matches(dirPath) ||
                            dirPath.startsWith(rule.raw.trim('/')) && (dirPath.length == rule.raw.trim('/').length || dirPath[rule.raw.trim('/').length] == '/'))
                    ) {
                        return true
                    }
                } else {
                    if (dirsToCheck.any { rule.regex.matches(it) }) return true
                }
            } else {
                if (rule.anchoredPath) {
                    if (rule.regex.matches(normalized)) return true
                } else {
                    if (segments.any { rule.regex.matches(it) }) return true
                }
            }
        }
        return false
    }

    companion object {
        /** Pola default untuk folder hasil koneksi. */
        const val DEFAULT_PATTERNS = ".git/\n.DS_Store\nThumbs.db\n*.tmp\n*.swp\n*~\n.trash/"

        private fun globToRegex(glob: String): Regex {
            val sb = StringBuilder("^")
            var i = 0
            while (i < glob.length) {
                when (val ch = glob[i]) {
                    '*' -> {
                        if (i + 1 < glob.length && glob[i + 1] == '*') {
                            sb.append(".*")
                            i++
                        } else {
                            sb.append("[^/]*")
                        }
                    }
                    '?' -> sb.append("[^/]")
                    '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']', '\\' -> {
                        sb.append('\\').append(ch)
                    }
                    else -> sb.append(ch)
                }
                i++
            }
            sb.append("$")
            return Regex(sb.toString(), RegexOption.IGNORE_CASE)
        }
    }
}
