package com.eteditor.core

enum class DocumentKind {
    None,
    Epub,
    Txt
}

data class ChapterInfo(
    val index: Int,
    val title: String,
    val wordCount: Int,
    val source: String,
    val fileName: String,
    val tocLevel: Int = 0,
    val isVolume: Boolean = false,
    val lineNumber: Int = 0,
    val status: List<String> = emptyList()
)

data class TxtChapter(
    val index: Int,
    val lineIndex: Int,
    val endLineIndex: Int,
    val title: String,
    val wordCount: Int,
    val startIndex: Int = 0,
    val bodyStartIndex: Int = 0,
    val endIndex: Int = 0,
    val number: Int? = null,
    val status: List<String> = emptyList()
)

data class TxtDocument(
    val originalName: String,
    var text: String,
    val encoding: String,
    var chapters: List<TxtChapter>
)

data class ManifestItem(
    val id: String,
    var href: String,
    val mediaType: String,
    var path: String,
    val properties: String = ""
)

data class EpubChapter(
    val id: String,
    var href: String,
    var path: String,
    val originalPath: String,
    val pathAliases: MutableSet<String>,
    var title: String,
    var tocLevel: Int = 0,
    var html: String,
    var wordCount: Int
)

data class EpubBook(
    val originalName: String,
    var metadataTitle: String,
    var metadataAuthor: String,
    val entries: LinkedHashMap<String, ByteArray>,
    val opfPath: String,
    val tocPath: String?,
    val manifest: MutableMap<String, ManifestItem>,
    val spineIds: MutableList<String>,
    val chapters: MutableList<EpubChapter>
)

data class EpubExportOptions(
    val hideSection0001FromNcx: Boolean = true
)

data class CheckReport(
    val errors: List<String>,
    val warnings: List<String>
) {
    val ok: Boolean get() = errors.isEmpty()
}

data class ReplaceResult(
    val filesChanged: Int,
    val replacements: Int
)
