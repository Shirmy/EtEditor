package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.EpubBook
import com.eteditor.core.ManifestItem
import com.eteditor.core.decodeEpubHtmlBytes
import com.eteditor.core.encodeEpubHtmlUtf8
import com.eteditor.core.normalizeEpubHtmlUtf8Declaration

internal fun writeCoverToEpub(book: EpubBook, coverUrl: String, bytes: ByteArray, contentType: String) {
    if (bytes.isEmpty()) error("封面内容为空")
    val mediaType = coverMediaType(coverUrl, contentType)
    val size = imageSize(bytes) ?: error("无法解析封面图片")
    validateImageDimensions(size, "封面图片")
    val extension = coverExtension(mediaType)
    val existing = findCoverManifestItem(book)
    val opfDir = book.opfPath.substringBeforeLast('/', missingDelimiterValue = "").let {
        if (it.isBlank()) "" else "$it/"
    }
    val oldPath = existing?.path
    val path = coverImagePath(book, existing, opfDir, extension)
    val id = existing?.id ?: uniqueManifestId(book, "cover-image")
    if (oldPath != null && oldPath != path) {
        book.entries.remove(oldPath)
    }
    book.entries[path] = bytes
    book.manifest[id] = ManifestItem(
        id = id,
        href = relativeEpubHref(opfDir, path),
        mediaType = mediaType,
        path = path,
        properties = "cover-image"
    )
    syncCoverHtmlPages(book, oldPath, path, mediaType, size)
}

internal fun writeImageResourceToEpub(
    book: EpubBook,
    fileName: String,
    bytes: ByteArray,
    mediaType: String
): String {
    if (bytes.isEmpty()) error("图片内容为空")
    validateInsertImageMediaType(mediaType)
    val size = imageSize(bytes) ?: error("无法解析图片文件")
    validateImageDimensions(size, "图片文件")
    val opfDir = book.opfPath.substringBeforeLast('/', missingDelimiterValue = "").let {
        if (it.isBlank()) "" else "$it/"
    }
    val imageDir = epubCoverImageDirectory(book, opfDir)
    val cleanFileName = fileName.substringAfterLast('/').ifBlank { "image.${coverExtension(mediaType)}" }
    val path = normalizeEpubPath(imageDir + cleanFileName)
    val stem = cleanFileName.substringBeforeLast('.', missingDelimiterValue = cleanFileName).ifBlank { "image" }
    val id = imageManifestId(book, stem, path)
    book.manifest
        .filter { (itemId, item) -> itemId != id && item.path.equals(path, ignoreCase = true) }
        .keys
        .toList()
        .forEach { itemId -> book.manifest.remove(itemId) }
    book.entries[path] = bytes
    book.manifest[id] = ManifestItem(
        id = id,
        href = relativeEpubHref(opfDir, path),
        mediaType = mediaType,
        path = path
    )
    return path
}

internal fun nextCustomInsertImageFileName(book: EpubBook, extension: String): String {
    return nextCustomInsertImageFileName(book, extension, mutableSetOf())
}

internal fun nextCustomInsertImageFileName(
    book: EpubBook,
    extension: String,
    reservedStems: MutableSet<String>
): String {
    val opfDir = book.opfPath.substringBeforeLast('/', missingDelimiterValue = "").let {
        if (it.isBlank()) "" else "$it/"
    }
    val imageDir = epubCoverImageDirectory(book, opfDir)
    val usedStems = (
        book.entries.keys.asSequence() +
            book.manifest.values.asSequence().map { it.path }
        )
        .map { normalizeEpubPath(it) }
        .filter { it.startsWith(imageDir, ignoreCase = true) }
        .map { it.substringAfterLast('/').substringBeforeLast('.').lowercase() }
        .toSet() + book.manifest.keys.map { it.lowercase() }.toSet()
    var index = 1
    while (true) {
        val stem = index.toString().padStart(2, '0')
        val key = stem.lowercase()
        if (key !in usedStems && key !in reservedStems) {
            reservedStems += key
            return "$stem.$extension"
        }
        index += 1
    }
}

private fun epubCoverImageDirectory(book: EpubBook, opfDir: String): String {
    val coverDir = findCoverManifestItem(book)
        ?.path
        ?.substringBeforeLast('/', missingDelimiterValue = "")
        ?.takeIf { it.isNotBlank() }
        ?.let { "$it/" }
    return normalizeEpubPath(coverDir ?: "${opfDir}Images/").let { path ->
        if (path.isBlank() || path.endsWith("/")) path else "$path/"
    }
}

private fun imageManifestId(book: EpubBook, preferred: String, targetPath: String): String {
    val cleanBase = preferred
        .replace(Regex("""[^A-Za-z0-9_.-]"""), "_")
        .ifBlank { "image" }
    val existing = book.manifest[cleanBase]
    if (existing == null || existing.path.equals(targetPath, ignoreCase = true)) return cleanBase
    var counter = 1
    while (true) {
        val candidate = "${cleanBase}_$counter"
        val candidateItem = book.manifest[candidate]
        if (candidateItem == null || candidateItem.path.equals(targetPath, ignoreCase = true)) return candidate
        counter += 1
    }
}

private fun coverImagePath(
    book: EpubBook,
    existing: ManifestItem?,
    opfDir: String,
    extension: String
): String {
    if (existing == null) return uniqueEpubEntryPath(book, opfDir + "Images/cover.$extension")
    val currentExtension = existing.path.substringAfterLast('.', missingDelimiterValue = "")
    if (currentExtension.equals(extension, ignoreCase = true)) return existing.path
    val preferred = existing.path.substringBeforeLast('.', missingDelimiterValue = existing.path) + ".$extension"
    val used = (book.entries.keys + book.manifest.values.map { it.path })
        .filterNot { it.equals(existing.path, ignoreCase = true) }
        .map { it.lowercase() }
        .toSet()
    if (preferred.lowercase() !in used) return preferred
    return uniqueEpubEntryPath(book, opfDir + "Images/cover.$extension")
}

private fun syncCoverHtmlPages(
    book: EpubBook,
    oldCoverPath: String?,
    newCoverPath: String,
    mediaType: String,
    size: Pair<Int, Int>?
) {
    val htmlPaths = (book.entries.keys + book.chapters.map { it.path })
        .distinct()
        .filter { it.isHtmlEntryPath() && (it.isCoverHtmlPath() || it.isSection0001HtmlPath()) }
    htmlPaths.forEach { htmlPath ->
        val html = book.chapters.firstOrNull { it.path == htmlPath }?.html
            ?: decodeEpubHtmlBytes(book.entries[htmlPath] ?: return@forEach)
        val updated = syncCoverHtmlPage(
            html = html,
            htmlPath = htmlPath,
            oldCoverPath = oldCoverPath,
            newCoverPath = newCoverPath,
            mediaType = mediaType,
            size = size,
            updateDimensions = htmlPath.isCoverHtmlPath()
        )
        if (updated == html) return@forEach
        val normalizedUpdated = normalizeEpubHtmlUtf8Declaration(updated)
        book.entries[htmlPath] = encodeEpubHtmlUtf8(normalizedUpdated)
        book.chapters.firstOrNull { it.path == htmlPath }?.let { chapter ->
            chapter.html = normalizedUpdated
            chapter.wordCount = ChapterDetector.countHtmlChars(normalizedUpdated)
        }
    }
}

private fun syncCoverHtmlPage(
    html: String,
    htmlPath: String,
    oldCoverPath: String?,
    newCoverPath: String,
    mediaType: String,
    size: Pair<Int, Int>?,
    updateDimensions: Boolean
): String {
    val htmlDir = htmlPath.substringBeforeLast('/', missingDelimiterValue = "").let {
        if (it.isBlank()) "" else "$it/"
    }
    val newHref = relativeEpubHref(htmlDir, newCoverPath)
    var updated = Regex(
        """<(img|image|object|source)\b[^>]*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).replace(html) { match ->
        syncCoverImageTag(
            tag = match.value,
            htmlPath = htmlPath,
            oldCoverPath = oldCoverPath,
            newCoverPath = newCoverPath,
            newHref = newHref,
            mediaType = mediaType,
            size = size,
            updateDimensions = updateDimensions
        )
    }
    if (updateDimensions && size != null) {
        updated = Regex("""<svg\b[^>]*>""", RegexOption.IGNORE_CASE).replace(updated) { match ->
            setXmlAttribute(match.value, "viewBox", "0 0 ${size.first} ${size.second}")
        }
    }
    return updated
}

private fun syncCoverImageTag(
    tag: String,
    htmlPath: String,
    oldCoverPath: String?,
    newCoverPath: String,
    newHref: String,
    mediaType: String,
    size: Pair<Int, Int>?,
    updateDimensions: Boolean
): String {
    var matchedCoverImage = false
    var updated = Regex(
        """\b(src|href|xlink:href|data)\s*=\s*(['"])([^'"]*)(['"])""",
        RegexOption.IGNORE_CASE
    ).replace(tag) { match ->
        val attribute = match.groupValues[1]
        val quote = match.groupValues[2]
        val value = match.groupValues[3]
        if (isCoverImageReference(value, htmlPath, oldCoverPath, newCoverPath)) {
            matchedCoverImage = true
            "$attribute=$quote$newHref$quote"
        } else {
            match.value
        }
    }
    if (!matchedCoverImage) return tag
    updated = Regex("""\btype\s*=\s*(['"])image/[^'"]*\1""", RegexOption.IGNORE_CASE)
        .replace(updated) { match -> "type=${match.groupValues[1]}$mediaType${match.groupValues[1]}" }
    if (updateDimensions && size != null) {
        updated = setXmlAttribute(updated, "width", size.first.toString())
        updated = setXmlAttribute(updated, "height", size.second.toString())
    }
    return updated
}

private fun isCoverImageReference(
    value: String,
    htmlPath: String,
    oldCoverPath: String?,
    newCoverPath: String
): Boolean {
    val clean = value.substringBefore('#').substringBefore('?')
    if (clean.startsWith("data:", ignoreCase = true)) return false
    if (!clean.isImageHref()) return false
    val htmlDir = htmlPath.substringBeforeLast('/', missingDelimiterValue = "").let {
        if (it.isBlank()) "" else "$it/"
    }
    val resolved = normalizeEpubPath(htmlDir + clean)
    return resolved == newCoverPath ||
        (oldCoverPath != null && resolved == oldCoverPath) ||
        (oldCoverPath == null && (htmlPath.isCoverHtmlPath() || htmlPath.isSection0001HtmlPath())) ||
        clean.substringAfterLast('/').substringBeforeLast('.').contains("cover", ignoreCase = true)
}

private fun String.isHtmlEntryPath(): Boolean {
    return endsWith(".xhtml", ignoreCase = true) ||
        endsWith(".html", ignoreCase = true) ||
        endsWith(".htm", ignoreCase = true)
}

private fun String.isCoverHtmlPath(): Boolean {
    val stem = substringAfterLast('/').substringBeforeLast('.')
    return stem.equals("cover", ignoreCase = true) ||
        stem.equals("coverpage", ignoreCase = true) ||
        stem.equals("cover-page", ignoreCase = true)
}

private fun String.isSection0001HtmlPath(): Boolean {
    return substringAfterLast('/').substringBeforeLast('.').equals("Section0001", ignoreCase = true)
}

private fun String.isImageHref(): Boolean {
    val path = substringBefore('#').substringBefore('?')
    return path.endsWith(".jpg", ignoreCase = true) ||
        path.endsWith(".jpeg", ignoreCase = true) ||
        path.endsWith(".png", ignoreCase = true) ||
        path.endsWith(".webp", ignoreCase = true) ||
        path.endsWith(".gif", ignoreCase = true)
}

private fun findCoverManifestItem(book: EpubBook): ManifestItem? {
    return book.manifest.values.firstOrNull { item ->
        item.properties.split(Regex("\\s+")).any { it.equals("cover-image", ignoreCase = true) } ||
            item.id.contains("cover", ignoreCase = true) && item.mediaType.startsWith("image/", ignoreCase = true) ||
            item.path.substringAfterLast('/').contains("cover", ignoreCase = true) && item.mediaType.startsWith("image/", ignoreCase = true)
    }
}
