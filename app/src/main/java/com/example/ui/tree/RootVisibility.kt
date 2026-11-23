package com.example.ui.tree

import com.example.sync.TreeItem

/**
 * Yang tampil di beranda: hanya folder dasar (depth 0).
 * Sub-folder dan file dasar tidak ditampilkan; isinya ikut tersinkron lewat folder dasar.
 * Item yang namanya diawali '.' disembunyikan.
 */
fun rootVisibleItems(items: List<TreeItem>): List<TreeItem> =
    items
        .filter { it.isFolder && it.depth == 0 && !it.name.startsWith(".") }
        .sortedBy { it.name.lowercase() }
