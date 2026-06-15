package com.eteditor

import android.content.ContentResolver
import android.net.Uri
import com.eteditor.core.ChapterDetector
import com.eteditor.core.EpubToolkit
import com.eteditor.core.TextCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val INSERT_TXT_NUMERIC_CHAPTER_TITLE_MAX_LENGTH = 120
private val INSERT_TXT_NUMERIC_CHAPTER_REGEX = Regex("""^第\s*[0-9]+\s*章.*${'$'}""")

internal suspend fun loadInsertChapterSource(
    contentResolver: ContentResolver,
    sourceType: String,
    sourceUri: String
): InsertChapterSourceData {
    val uri = Uri.parse(sourceUri)
    val name = displayName(contentResolver, uri).ifBlank { sourceUri.substringAfterLast('/') }
    if (
        sourceType == INSERT_CHAPTER_SOURCE_UPLOAD &&
        !isInsertChapterSourceFileAllowed(name, contentResolver.getType(uri).orEmpty())
    ) {
        error(INSERT_CHAPTER_SOURCE_FILE_ERROR)
    }
    val bytes = withContext(Dispatchers.IO) {
        contentResolver.readUriBytesLimited(
            uri = uri,
            maxBytes = insertChapterSourceMaxBytes(sourceType, name),
            label = "来源文件",
            openError = "无法打开输入文件"
        )
    }
    return withContext(Dispatchers.Default) {
        when (sourceType) {
            INSERT_CHAPTER_SOURCE_TXT -> parseInsertSourceTxt(sourceUri, name, bytes)
            INSERT_CHAPTER_SOURCE_UPLOAD -> when (detectInsertChapterUploadSourceType(name, bytes)) {
                INSERT_CHAPTER_SOURCE_EPUB -> parseInsertSourceEpub(sourceUri, name, bytes)
                else -> parseInsertSourceTxt(sourceUri, name, bytes)
            }
            else -> parseInsertSourceEpub(sourceUri, name, bytes)
        }
    }
}

private fun insertChapterSourceMaxBytes(sourceType: String, name: String): Long {
    return when (sourceType) {
        INSERT_CHAPTER_SOURCE_TXT -> TXT_DOCUMENT_MAX_BYTES
        INSERT_CHAPTER_SOURCE_EPUB -> EPUB_DOCUMENT_MAX_BYTES
        else -> if (name.endsWith(".txt", ignoreCase = true)) TXT_DOCUMENT_MAX_BYTES else EPUB_DOCUMENT_MAX_BYTES
    }
}

internal fun detectInsertChapterUploadSourceType(name: String, bytes: ByteArray): String {
    val lowerName = name.lowercase()
    if (lowerName.endsWith(".txt")) return INSERT_CHAPTER_SOURCE_TXT
    if (lowerName.endsWith(".epub")) return INSERT_CHAPTER_SOURCE_EPUB
    val looksLikeZip = bytes.size >= 2 &&
        bytes[0] == 'P'.code.toByte() &&
        bytes[1] == 'K'.code.toByte()
    return if (looksLikeZip) {
        INSERT_CHAPTER_SOURCE_EPUB
    } else {
        INSERT_CHAPTER_SOURCE_TXT
    }
}

internal fun parseInsertSourceEpub(sourceUri: String, name: String, bytes: ByteArray): InsertChapterSourceData {
    val book = EpubToolkit.parse(bytes, name.baseName("source"))
    val importableChapters = book.chapters.filterNot { chapter -> chapter.isCoverSection0001Or0002() }
    val chapters = importableChapters.mapIndexed { index, chapter ->
        val body = ChapterDetector.extractBodyMarkup(chapter.html)
        val nextLevel = importableChapters.getOrNull(index + 1)?.tocLevel ?: chapter.tocLevel
        val inferredVolume = chapter.isVolumeChapter() ||
            (chapter.tocLevel == 0 && nextLevel > chapter.tocLevel && chapter.wordCount <= 200)
        InsertableChapter(
            sourceIndex = index,
            title = chapter.title.ifBlank { chapter.path.substringAfterLast('/').substringBeforeLast('.') },
            fileName = chapter.path.substringAfterLast('/'),
            sourcePath = chapter.path,
            html = chapter.html,
            text = ChapterDetector.stripHtml(body),
            wordCount = chapter.wordCount,
            tocLevel = chapter.tocLevel,
            isVolume = inferredVolume
        )
    }
    return InsertChapterSourceData(
        sourceUri = sourceUri,
        sourceType = INSERT_CHAPTER_SOURCE_EPUB,
        originalName = name,
        chapters = chapters,
        epubBook = book
    )
}

internal fun parseInsertSourceTxt(sourceUri: String, name: String, bytes: ByteArray): InsertChapterSourceData {
    val (text, _) = TextCodec.decode(bytes)
    val hits = detectInsertTxtNumericChapters(text)
    val chapters = if (hits.isEmpty()) {
        listOf(
            InsertableChapter(
                sourceIndex = 0,
                title = name.substringBeforeLast('.').ifBlank { "未命名章节" },
                fileName = name,
                sourcePath = name,
                html = null,
                text = text.trim(),
                wordCount = ChapterDetector.countVisibleChars(text),
                tocLevel = 0,
                isVolume = false
            )
        )
    } else {
        hits.mapIndexed { index, hit ->
            val end = hits.getOrNull(index + 1)?.titleStartOffset ?: text.length
            val body = text.substring(
                hit.bodyStartOffset.coerceIn(0, text.length),
                end.coerceIn(0, text.length)
            ).trim()
            InsertableChapter(
                sourceIndex = index,
                title = hit.title.ifBlank { "第${index + 1}章" },
                fileName = "TXT-${(index + 1).toString().padStart(4, '0')}",
                sourcePath = "TXT-${index + 1}",
                html = null,
                text = body,
                wordCount = ChapterDetector.countVisibleChars(body),
                tocLevel = 0,
                isVolume = false
            )
        }
    }
    return InsertChapterSourceData(
        sourceUri = sourceUri,
        sourceType = INSERT_CHAPTER_SOURCE_TXT,
        originalName = name,
        chapters = chapters
    )
}

private fun detectInsertTxtNumericChapters(text: String): List<InsertTxtChapterHit> {
    val hits = mutableListOf<InsertTxtChapterHit>()
    var lineStart = 0
    while (lineStart < text.length) {
        var lineEnd = lineStart
        while (lineEnd < text.length && text[lineEnd] != '\n' && text[lineEnd] != '\r') {
            lineEnd++
        }
        val nextLineStart = when {
            lineEnd >= text.length -> text.length
            text[lineEnd] == '\r' && lineEnd + 1 < text.length && text[lineEnd + 1] == '\n' -> lineEnd + 2
            else -> lineEnd + 1
        }
        val title = text.substring(lineStart, lineEnd).trim()
        if (
            title.isNotEmpty() &&
            title.length <= INSERT_TXT_NUMERIC_CHAPTER_TITLE_MAX_LENGTH &&
            INSERT_TXT_NUMERIC_CHAPTER_REGEX.matches(title)
        ) {
            hits += InsertTxtChapterHit(
                title = title,
                titleStartOffset = lineStart,
                bodyStartOffset = nextLineStart
            )
        }
        if (lineEnd >= text.length) break
        lineStart = nextLineStart
    }
    return hits
}
