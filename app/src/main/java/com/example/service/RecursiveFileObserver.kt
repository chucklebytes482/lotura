package com.example.service

import android.os.Build
import android.os.FileObserver
import com.example.sync.IgnoreRules
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Watches a root directory and all its nested subdirectories for file modifications,
 * creations, moves, and deletions (e.g., when Pilcrow or Google Docs writes to a file).
 */
class RecursiveFileObserver(
    private val rootDir: File,
    private val ignoreRules: IgnoreRules,
    private val onFileChanged: (relativePath: String) -> Unit
) {
    private val observers = ConcurrentHashMap<String, SingleDirObserver>()

    companion object {
        private const val WATCH_MASK =
            FileObserver.CLOSE_WRITE or
                FileObserver.MOVED_TO or
                FileObserver.MOVED_FROM or
                FileObserver.DELETE or
                FileObserver.CREATE
    }

    fun startWatching() {
        if (!rootDir.exists() || !rootDir.isDirectory) return
        attachRecursive(rootDir)
    }

    fun stopWatching() {
        observers.values.forEach { runCatching { it.stopWatching() } }
        observers.clear()
    }

    private fun attachRecursive(dir: File) {
        val canonical = runCatching { dir.canonicalPath }.getOrNull() ?: return
        if (observers.containsKey(canonical)) return

        val relDir = toRelativePath(canonical)
        if (relDir.isNotEmpty() && ignoreRules.isIgnored(relDir, isDirectory = true)) return

        val observer = createObserver(dir)
        observers[canonical] = observer
        observer.startWatching()

        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                attachRecursive(child)
            }
        }
    }

    private fun createObserver(dir: File): SingleDirObserver {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            SingleDirObserver(dir, WATCH_MASK) { event, childName ->
                handleEvent(dir, event, childName)
            }
        } else {
            @Suppress("DEPRECATION")
            SingleDirObserver(dir.absolutePath, WATCH_MASK) { event, childName ->
                handleEvent(dir, event, childName)
            }
        }
    }

    private fun handleEvent(parentDir: File, event: Int, childName: String?) {
        if (childName.isNullOrBlank()) return
        val target = File(parentDir, childName)
        val rel = toRelativePath(target.absolutePath)
        if (rel.isEmpty()) return

        val isDir = target.isDirectory
        if (ignoreRules.isIgnored(rel, isDirectory = isDir)) return

        val masked = event and FileObserver.ALL_EVENTS
        if (isDir && (masked == FileObserver.CREATE || masked == FileObserver.MOVED_TO)) {
            attachRecursive(target)
            return
        }

        if (!isDir) {
            onFileChanged(rel)
        }
    }

    private fun toRelativePath(absolutePath: String): String {
        val rootPath = runCatching { rootDir.canonicalPath }.getOrDefault(rootDir.absolutePath)
            .trimEnd(File.separatorChar)
        val clean = absolutePath.trimEnd(File.separatorChar)
        if (!clean.startsWith(rootPath)) return ""
        return clean.removePrefix(rootPath)
            .trimStart(File.separatorChar)
            .replace(File.separatorChar, '/')
    }

    private class SingleDirObserver : FileObserver {
        private val callback: (Int, String?) -> Unit

        @Suppress("DEPRECATION")
        constructor(path: String, mask: Int, callback: (Int, String?) -> Unit) : super(path, mask) {
            this.callback = callback
        }

        constructor(file: File, mask: Int, callback: (Int, String?) -> Unit) : super(file, mask) {
            this.callback = callback
        }

        override fun onEvent(event: Int, path: String?) {
            callback(event, path)
        }
    }
}
