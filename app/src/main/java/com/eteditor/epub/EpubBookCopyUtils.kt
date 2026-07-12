package com.eteditor

import com.eteditor.core.EpubBook
import com.eteditor.core.ManifestItem

internal fun EpubBook.mutableDeepCopy(): EpubBook {
    return copy(
        entries = LinkedHashMap<String, ByteArray>().also { copy ->
            entries.forEach { (path, bytes) -> copy[path] = bytes.copyOf() }
        },
        manifest = LinkedHashMap<String, ManifestItem>().also { copy ->
            manifest.forEach { (id, item) -> copy[id] = item.copy() }
        },
        spineIds = spineIds.toMutableList(),
        chapters = chapters.map { chapter ->
            chapter.copy(pathAliases = chapter.pathAliases.toMutableSet())
        }.toMutableList()
    )
}

internal fun EpubBook.mutableStructureCopy(): EpubBook {
    // Entry byte arrays are shared with the source book. Treat them as read-only:
    // any content change must replace the map value with a newly created ByteArray.
    return copy(
        entries = LinkedHashMap(entries),
        manifest = LinkedHashMap<String, ManifestItem>().also { copy ->
            manifest.forEach { (id, item) -> copy[id] = item.copy() }
        },
        spineIds = spineIds.toMutableList(),
        chapters = chapters.map { chapter ->
            chapter.copy(pathAliases = chapter.pathAliases.toMutableSet())
        }.toMutableList()
    )
}
