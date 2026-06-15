package com.eteditor

import com.eteditor.core.EpubBook
import com.eteditor.core.ManifestItem

internal fun EpubBook.mutableDeepCopy(): EpubBook {
    return copy(
        metadataItems = metadataItems.map { item ->
            item.copy(attributes = item.attributes.toMap())
        }.toMutableList(),
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
