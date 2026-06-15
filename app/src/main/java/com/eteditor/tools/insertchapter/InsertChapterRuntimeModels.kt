package com.eteditor

import com.eteditor.core.EpubBook

data class InsertChapterSourceItem(
    val sourceIndex: Int,
    val title: String,
    val fileName: String,
    val wordCount: Int,
    val tocLevel: Int,
    val isVolume: Boolean
)

data class InsertChapterSourcePreview(
    val sourceUri: String,
    val sourceType: String,
    val items: List<InsertChapterSourceItem>
)

internal data class InsertChapterSourceData(
    val sourceUri: String,
    val sourceType: String,
    val originalName: String,
    val chapters: List<InsertableChapter>,
    val epubBook: EpubBook? = null
)

internal data class InsertableChapter(
    val sourceIndex: Int,
    val title: String,
    val fileName: String,
    val sourcePath: String,
    val html: String?,
    val text: String,
    val wordCount: Int,
    val tocLevel: Int,
    val isVolume: Boolean,
    val bodyBlocks: List<InsertChapterBodyBlock> = emptyList(),
    val imageResources: List<InsertChapterImageResource> = emptyList()
)

internal data class InsertChapterBodyBlock(
    val text: String = "",
    val imageFileName: String = "",
    val cssClass: String = ""
)

internal data class InsertChapterImageResource(
    val url: String,
    val fileName: String,
    val bytes: ByteArray,
    val mediaType: String
)

internal typealias InsertChapterProgressCallback = (phase: String, completed: Int, total: Int) -> Unit

internal data class InsertTxtChapterHit(
    val title: String,
    val titleStartOffset: Int,
    val bodyStartOffset: Int
)
