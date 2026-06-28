package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.EpubBook
import com.eteditor.core.EpubChapter
import com.eteditor.core.ManifestItem
import com.eteditor.core.updateEpubChapterHtmlEntry

internal data class InsertEpubChaptersResult(
    val insertPosition: Int,
    val insertedCount: Int,
    val insertedImages: Int,
    val renamedFiles: Int
)

internal fun targetTocLevelForInsert(book: EpubBook, insertPosition: Int): Int {
    val hasPreviousVolume = (insertPosition - 1 downTo 0).any { index ->
        book.chapters[index].isVolumeChapter()
    }
    return if (hasPreviousVolume) 1 else 0
}

internal fun importedEpubRoot(book: EpubBook, sourceName: String): String {
    val base = sourceName
        .substringBeforeLast('.')
        .replace(Regex("""[^A-Za-z0-9_\u4e00-\u9fff-]"""), "_")
        .trim('_')
        .ifBlank { "Source" }
    var index = 0
    while (true) {
        val root = if (index == 0) "Imported/$base/" else "Imported/${base}_$index/"
        val used = (book.entries.keys + book.manifest.values.map { it.path }).any { path ->
            path.startsWith(root, ignoreCase = true)
        }
        if (!used) return root
        index += 1
    }
}

internal fun defaultInsertedChapterPath(book: EpubBook, insertOffset: Int, chapterFileNameTemplate: EpubChapterFileNameTemplate? = null): String {
    val bodyChapterIndices = epubBodyChapterIndices(book)
    val referencePath = bodyChapterIndices.firstNotNullOfOrNull { index -> book.chapters.getOrNull(index)?.path }
        ?: book.chapters.firstOrNull()?.path
    val chapterDir = referencePath
        ?.substringBeforeLast('/', missingDelimiterValue = "")
        ?.let { if (it.isBlank()) "" else "$it/" }
        ?: "Text/"
    val (stem, extension) = if (chapterFileNameTemplate != null) {
        val number = (book.chapters.count { it.isEpubBodyNumberedChapter() } + 1 + insertOffset)
            .toString().padStart(chapterFileNameTemplate.numberWidth, '0')
        val s = "${chapterFileNameTemplate.prefix}$number${chapterFileNameTemplate.suffix}"
        val ext = chapterFileNameTemplate.extension.ifBlank { "xhtml" }
        s to ext
    } else {
        "InsertedChapter${alphabeticSuffix(insertOffset)}" to "xhtml"
    }
    return normalizeEpubPath("$chapterDir$stem.$extension")
}

internal fun insertChaptersIntoEpubBook(
    book: EpubBook,
    source: InsertChapterSourceData,
    selected: List<InsertableChapter>,
    positionMode: String,
    targetChapterIndex: Int?,
    currentChapterIndex: Int,
    onProgress: (Int, Int) -> Unit = { _, _ -> }
): InsertEpubChaptersResult {
    val insertPosition = resolveEpubInsertChapterPosition(book, positionMode, targetChapterIndex, currentChapterIndex)
    val targetCssPaths = epubCssPaths(book)
    val defaultTargetCssPath = defaultEpubStylesheetPath(book, targetCssPaths, insertPosition)
    if (source.sourceType == INSERT_CHAPTER_SOURCE_EPUB) {
        source.epubBook?.let { copyImportedEpubAssets(book, it, selected.map { chapter -> chapter.sourcePath }.toSet()) }
    }

    val preservingSourceVolumes = source.sourceType == INSERT_CHAPTER_SOURCE_EPUB &&
        selected.any { it.isVolume }
    val chapterFileNameTemplate = dominantEpubChapterFileNameTemplate(book, epubBodyChapterIndices(book))
    var number = book.chapters
        .take(insertPosition)
        .count { it.isEpubBodyNumberedChapter() } + 1
    val referenceStyle = epubInsertReferenceTitleStyle(book, insertPosition)
    val referenceNumbered = epubInsertReferenceIsNumbered(book, insertPosition)
    val defaultLevel = targetTocLevelForInsert(book, insertPosition)
    val opfDir = book.opfPath.substringBeforeLast('/', missingDelimiterValue = "").let {
        if (it.isBlank()) "" else "$it/"
    }
    val importedSourcePathsById = mutableMapOf<String, String>()
    val inserted = selected.mapIndexed { insertOffset, sourceChapter ->
        val isVolume = sourceChapter.isVolume
        val sourceParts = parseTitleFormatParts(sourceChapter.title)
        val shouldRenumber = !isVolume && referenceNumbered && sourceParts.prefix != null
        val numberedTitle = if (shouldRenumber) {
            renumberInsertedChapterTitle(sourceChapter.title, number++)
        } else {
            sourceChapter.title
        }
        val renderStyle = if (sourceParts.suffix.isNotBlank() && referenceStyle == TITLE_FORMAT_STYLE_NONE) {
            TITLE_FORMAT_STYLE_DOUBLE
        } else {
            referenceStyle
        }
        val renderedTitle = if (shouldRenumber) renderInsertedChapterTitle(numberedTitle, renderStyle) else null
        val title = renderedTitle?.plainTitle ?: numberedTitle
        val path = if (source.sourceType == INSERT_CHAPTER_SOURCE_EPUB && sourceChapter.sourcePath.isNotBlank()) {
            uniqueEpubEntryPath(book, sourceChapter.sourcePath)
        } else {
            uniqueEpubEntryPath(book, defaultInsertedChapterPath(book, insertOffset, chapterFileNameTemplate))
        }
        val id = uniqueManifestId(book, path.substringAfterLast('/').substringBeforeLast('.').ifBlank { "inserted" })
        if (source.sourceType == INSERT_CHAPTER_SOURCE_EPUB && sourceChapter.sourcePath.isNotBlank()) {
            importedSourcePathsById[id] = normalizeEpubPath(sourceChapter.sourcePath)
        }
        val chapterDir = path.substringBeforeLast('/', missingDelimiterValue = "").let {
            if (it.isBlank()) "" else "$it/"
        }
        val imagePathsByFileName = sourceChapter.imageResources.associate { image ->
            image.fileName to writeImageResourceToEpub(book, image.fileName, image.bytes, image.mediaType)
        }
        val sourceBodyHtml = sourceChapter.bodyBlocks
            .takeIf { it.isNotEmpty() }
            ?.let { blocks ->
                chapterHtml(title, blocks) { fileName ->
                    imagePathsByFileName[fileName]
                        ?.let { imagePath -> relativeEpubHref(chapterDir, imagePath) }
                        ?: "../Images/$fileName"
                }
            }
        val html = (if (renderedTitle != null) {
            val baseHtml = sourceChapter.html ?: sourceBodyHtml ?: chapterHtml(title, sourceChapter.text)
            updateHtmlTitleForFormat(
                html = baseHtml,
                plainTitle = renderedTitle.plainTitle,
                headingHtml = renderedTitle.headingHtml,
                styleCode = renderedTitle.styleCode
            )
        } else {
            sourceChapter.html
                ?.let { ChapterDetector.updateHtmlTitle(it, title) }
                ?: sourceBodyHtml
                ?: chapterHtml(title, sourceChapter.text)
        }).toCrlfLineEndings()
        val chapter = EpubChapter(
            id = id,
            href = relativeEpubHref(opfDir, path),
            path = path,
            originalPath = path,
            pathAliases = mutableSetOf(path),
            title = title,
            tocLevel = if (preservingSourceVolumes) sourceChapter.tocLevel else defaultLevel,
            html = html,
            wordCount = ChapterDetector.countHtmlChars(html)
        )
        updateEpubChapterHtmlEntry(book, chapter)
        book.manifest[id] = ManifestItem(
            id = id,
            href = chapter.href,
            mediaType = "application/xhtml+xml",
            path = path
        )
        onProgress(insertOffset + 1, selected.size)
        chapter
    }
    book.chapters.addAll(insertPosition, inserted)
    book.spineIds.addAll(insertPosition, inserted.map { it.id })
    if (!preservingSourceVolumes) {
        applyVolumeTocLevels(book)
    }
    val renamedFiles = resequenceEpubBodyChapterFileNames(
        book = book,
        targetIndices = epubBodyChapterIndices(book),
        preferredTemplate = chapterFileNameTemplate
    )
    rewriteMovedImportedChapterReferences(book, inserted, importedSourcePathsById, targetCssPaths, defaultTargetCssPath)
    return InsertEpubChaptersResult(
        insertPosition = insertPosition,
        insertedCount = inserted.size,
        insertedImages = selected.sumOf { chapter -> chapter.imageResources.size },
        renamedFiles = renamedFiles
    )
}

internal fun copyImportedEpubAssets(
    target: EpubBook,
    source: EpubBook,
    selectedChapterPaths: Set<String>
) {
    val sourceChapterPaths = source.chapters.map { it.path }.toSet()
    val skippedPaths = buildSet {
        add("mimetype")
        add(source.opfPath)
        source.tocPath?.let(::add)
        addAll(selectedChapterPaths)
        addAll(sourceChapterPaths)
    }
    val opfDir = target.opfPath.substringBeforeLast('/', missingDelimiterValue = "").let {
        if (it.isBlank()) "" else "$it/"
    }
    source.entries.forEach { (path, bytes) ->
        if (path in skippedPaths) return@forEach
        if (path.startsWith("META-INF/", ignoreCase = true)) return@forEach
        if (isHtmlPath(path)) return@forEach
        val sourceItem = source.manifest.values.firstOrNull { it.path == path }
        if (path.endsWith(".css", ignoreCase = true) || sourceItem?.mediaType?.equals("text/css", ignoreCase = true) == true) {
            return@forEach
        }
        val targetPath = normalizeEpubPath(path)
        if (!target.entries.containsKey(targetPath)) {
            target.entries[targetPath] = bytes
        }
        if (target.manifest.values.none { it.path == targetPath }) {
            val id = uniqueManifestId(target, targetPath.substringAfterLast('/').substringBeforeLast('.').ifBlank { "asset" })
            target.manifest[id] = ManifestItem(
                id = id,
                href = relativeEpubHref(opfDir, targetPath),
                mediaType = sourceItem?.mediaType?.ifBlank { guessMediaType(targetPath) } ?: guessMediaType(targetPath),
                path = targetPath,
                properties = sourceItem?.properties
                    .orEmpty()
                    .split(Regex("\\s+"))
                    .filterNot { it.equals("cover-image", ignoreCase = true) }
                    .joinToString(" ")
            )
        }
    }
}

private fun alphabeticSuffix(index: Int): String {
    var value = index.coerceAtLeast(0)
    val chars = ArrayDeque<Char>()
    do {
        chars.addFirst(('A'.code + (value % 26)).toChar())
        value = value / 26 - 1
    } while (value >= 0)
    return chars.joinToString("")
}

internal fun rewriteMovedImportedChapterReferences(
    book: EpubBook,
    inserted: List<EpubChapter>,
    importedSourcePathsById: Map<String, String>,
    targetCssPaths: List<String>,
    defaultTargetCssPath: String?
) {
    if (importedSourcePathsById.isEmpty()) return
    inserted.forEach { chapter ->
        val sourcePath = importedSourcePathsById[chapter.id] ?: return@forEach
        val updated = rewriteMovedImportedHtmlReferences(
            html = chapter.html,
            originalHtmlPath = sourcePath,
            newHtmlPath = chapter.path,
            book = book,
            targetCssPaths = targetCssPaths,
            defaultTargetCssPath = defaultTargetCssPath
        ).toCrlfLineEndings()
        if (updated != chapter.html) {
            chapter.html = updated
            updateEpubChapterHtmlEntry(book, chapter)
        }
    }
}

private fun rewriteMovedImportedHtmlReferences(
    html: String,
    originalHtmlPath: String,
    newHtmlPath: String,
    book: EpubBook,
    targetCssPaths: List<String>,
    defaultTargetCssPath: String?
): String {
    val originalDir = originalHtmlPath.substringBeforeLast('/', missingDelimiterValue = "").let {
        if (it.isBlank()) "" else "$it/"
    }
    val newDir = newHtmlPath.substringBeforeLast('/', missingDelimiterValue = "").let {
        if (it.isBlank()) "" else "$it/"
    }
    val attributePattern = Regex("""\b(href|src|poster|xlink:href)\s*=\s*(['"])(.*?)\2""", RegexOption.IGNORE_CASE)
    return attributePattern.replace(html) { match ->
        val attributeName = match.groupValues[1]
        val quote = match.groupValues[2]
        val value = match.groupValues[3]
        val rewritten = rewriteMovedImportedReferenceValue(
            value = value,
            originalDir = originalDir,
            newDir = newDir,
            book = book,
            targetCssPaths = targetCssPaths,
            defaultTargetCssPath = defaultTargetCssPath
        )
        if (rewritten == value) {
            match.value
        } else {
            "$attributeName=$quote${rewritten.escapeXmlAttribute(quote)}$quote"
        }
    }
}

private fun rewriteMovedImportedReferenceValue(
    value: String,
    originalDir: String,
    newDir: String,
    book: EpubBook,
    targetCssPaths: List<String>,
    defaultTargetCssPath: String?
): String {
    if (value.isBlank() || value.startsWith("#") || value.startsWith("//")) return value
    val pathEnd = value.indexOfAny(charArrayOf('#', '?')).let { index ->
        if (index < 0) value.length else index
    }
    val pathPart = value.substring(0, pathEnd)
    val suffix = value.substring(pathEnd)
    if (pathPart.isBlank() || pathPart.startsWith("/")) return value
    val schemeIndex = pathPart.indexOf(':')
    val slashIndex = pathPart.indexOf('/')
    if (schemeIndex >= 0 && (slashIndex < 0 || schemeIndex < slashIndex)) return value
    if (pathPart.endsWith(".css", ignoreCase = true)) {
        val targetCssPath = matchingTargetCssPath(pathPart, targetCssPaths) ?: defaultTargetCssPath
        return targetCssPath?.let { path -> relativeEpubHref(newDir, path) + suffix } ?: value
    }
    val resolved = normalizeEpubPath(originalDir + pathPart)
    val exists = book.entries.containsKey(resolved) ||
        book.manifest.values.any { item -> item.path.equals(resolved, ignoreCase = true) }
    if (!exists) return value
    return relativeEpubHref(newDir, resolved) + suffix
}

internal fun epubCssPaths(book: EpubBook): List<String> {
    return (book.manifest.values.asSequence()
        .filter { item -> item.mediaType.equals("text/css", ignoreCase = true) || item.path.endsWith(".css", ignoreCase = true) }
        .map { item -> item.path } +
        book.entries.keys.asSequence().filter { path -> path.endsWith(".css", ignoreCase = true) })
        .map(::normalizeEpubPath)
        .filter { path -> path.isNotBlank() }
        .distinctBy { path -> path.lowercase() }
        .toList()
}

internal fun defaultEpubStylesheetPath(
    book: EpubBook,
    cssPaths: List<String>,
    insertPosition: Int
): String? {
    if (cssPaths.isEmpty()) return null
    val cssPathKeys = cssPaths.map { path -> normalizeEpubPath(path).lowercase() }.toSet()
    val before = (insertPosition - 1 downTo 0).asSequence()
    val after = (insertPosition until book.chapters.size).asSequence()
    return (before + after)
        .mapNotNull { index -> book.chapters.getOrNull(index) }
        .flatMap { chapter -> chapterStylesheetPaths(chapter).asSequence() }
        .firstOrNull { path -> normalizeEpubPath(path).lowercase() in cssPathKeys }
        ?: cssPaths.firstOrNull()
}

private fun chapterStylesheetPaths(chapter: EpubChapter): List<String> {
    val htmlDir = chapter.path.substringBeforeLast('/', missingDelimiterValue = "").let {
        if (it.isBlank()) "" else "$it/"
    }
    return Regex("""<link\b[^>]*\bhref\s*=\s*(['"])(.*?)\1[^>]*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .findAll(chapter.html)
        .mapNotNull { match ->
            val tag = match.value
            val href = match.groupValues.getOrNull(2).orEmpty()
            val isStylesheet = Regex("""\brel\s*=\s*(['"])[^'"]*\bstylesheet\b[^'"]*\1""", RegexOption.IGNORE_CASE)
                .containsMatchIn(tag) || href.substringBefore('#').substringBefore('?').endsWith(".css", ignoreCase = true)
            if (!isStylesheet) return@mapNotNull null
            val pathPart = href.substringBefore('#').substringBefore('?')
            if (!isLocalEpubReferencePath(pathPart)) return@mapNotNull null
            normalizeEpubPath(htmlDir + pathPart)
        }
        .toList()
}

private fun isLocalEpubReferencePath(pathPart: String): Boolean {
    if (pathPart.isBlank() || pathPart.startsWith("/") || pathPart.startsWith("//")) return false
    val schemeIndex = pathPart.indexOf(':')
    val slashIndex = pathPart.indexOf('/')
    return schemeIndex < 0 || (slashIndex >= 0 && schemeIndex > slashIndex)
}

private fun matchingTargetCssPath(pathPart: String, targetCssPaths: List<String>): String? {
    if (targetCssPaths.isEmpty()) return null
    val normalizedReference = normalizeEpubPath(pathPart).lowercase()
    val fileName = normalizedReference.substringAfterLast('/').takeIf { it.isNotBlank() } ?: return null
    return targetCssPaths.firstOrNull { path ->
        normalizeEpubPath(path).lowercase() == normalizedReference
    } ?: targetCssPaths.firstOrNull { path ->
        val normalizedPath = normalizeEpubPath(path).lowercase()
        normalizedPath.endsWith("/$normalizedReference")
    } ?: targetCssPaths.firstOrNull { path ->
        path.substringAfterLast('/').equals(fileName, ignoreCase = true)
    }
}
