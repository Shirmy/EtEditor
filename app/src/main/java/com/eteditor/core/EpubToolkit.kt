package com.eteditor.core

import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import java.util.zip.ZipOutputStream

object EpubToolkit {
    fun parse(bytes: ByteArray, originalName: String, calculateChapterWordCount: Boolean = true): EpubBook {
        val entries = unzip(bytes)
        val opfPath = findOpfPath(entries)
        val opfBytes = entries[opfPath] ?: error("未找到 OPF：$opfPath")
        val opfDoc = parseXml(opfBytes)
        val opfDir = opfPath.substringBeforeLast('/', missingDelimiterValue = "").let {
            if (it.isBlank()) "" else "$it/"
        }
        val metadataTitle = parseBookTitle(opfDoc)
        val metadataAuthor = parseBookAuthor(opfDoc)

        val manifest = linkedMapOf<String, ManifestItem>()
        opfDoc.elements("manifest").firstOrNull()?.children("item")?.forEach { item ->
            val id = item.attr("id")
            val href = item.attr("href")
            if (id.isNotBlank() && href.isNotBlank()) {
                val path = normalizePath(opfDir + href.substringBefore('#'))
                manifest[id] = ManifestItem(
                    id = id,
                    href = href,
                    mediaType = item.attr("media-type"),
                    path = path,
                    properties = item.attr("properties")
                )
            }
        }

        val spine = opfDoc.elements("spine").firstOrNull()
        val tocPath = findNcxPath(spine, manifest)
        val tocEntries = tocPath
            ?.let { path -> entries[path]?.let { parseNcxEntries(it, path) } }
            .orEmpty()
            .ifEmpty { parseNavEntries(entries, manifest) }

        val spineIds = spine
            ?.children("itemref")
            ?.mapNotNull { it.attr("idref").takeIf(String::isNotBlank) }
            .orEmpty()

        val chapters = spineIds.mapNotNull { id ->
            val item = manifest[id] ?: return@mapNotNull null
            if (!item.looksLikeHtml()) return@mapNotNull null
            val htmlBytes = entries[item.path] ?: return@mapNotNull null
            val html = normalizeEpubHtmlUtf8Declaration(decodeEpubHtmlBytes(htmlBytes))
            val tocEntry = tocEntries[item.path]
            val title = epubDirectoryTitle(item.path, html)
            EpubChapter(
                id = id,
                href = item.href,
                path = item.path,
                originalPath = item.path,
                pathAliases = mutableSetOf(item.path),
                title = title,
                tocLevel = tocEntry?.level ?: 0,
                html = html,
                wordCount = if (calculateChapterWordCount) {
                    ChapterDetector.countHtmlChars(html)
                } else {
                    EPUB_CHAPTER_WORD_COUNT_UNKNOWN
                }
            )
        }.toMutableList()

        return EpubBook(
            originalName = originalName,
            metadataTitle = metadataTitle,
            metadataAuthor = metadataAuthor,
            entries = entries,
            opfPath = opfPath,
            tocPath = tocPath,
            manifest = manifest,
            spineIds = spineIds.toMutableList(),
            chapters = chapters
        )
    }

    fun export(book: EpubBook, options: EpubExportOptions = EpubExportOptions()): ByteArray {
        val currentPaths = book.chapters.map { it.path }.toSet()
        book.chapters.forEach { chapter ->
            if (chapter.originalPath != chapter.path && chapter.originalPath !in currentPaths) {
                book.entries.remove(chapter.originalPath)
            }
            updateEpubChapterHtmlEntry(book, chapter)
        }
        book.entries[book.opfPath]?.let { opf ->
            book.entries[book.opfPath] = updateOpfPackage(opf, book)
        }
        refreshTocEntries(book, options)

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val mimetype = book.entries["mimetype"]
            if (mimetype != null) {
                zip.putStoredEntry("mimetype", mimetype)
            }

            book.entries.forEach { (name, data) ->
                if (name == "mimetype") return@forEach
                zip.putDeflatedEntry(name, data)
            }
        }
        return out.toByteArray()
    }

    fun refreshTocEntries(book: EpubBook, options: EpubExportOptions = EpubExportOptions()) {
        book.tocPath?.let { tocPath ->
            book.entries[tocPath]?.let { ncx ->
                book.entries[tocPath] = updateNcxTitles(ncx, tocPath, book.chapters, options)
            }
        }
        book.manifest.values
            .filter { it.isNavDocument() }
            .forEach { nav ->
                book.entries[nav.path]?.let { navBytes ->
                    book.entries[nav.path] = updateNavLinks(navBytes, nav.path, book.chapters, options)
                }
            }
    }

    fun check(book: EpubBook, options: EpubExportOptions = EpubExportOptions()): CheckReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (!book.entries.containsKey(book.opfPath)) errors += "OPF 文件缺失：${book.opfPath}"
        if (book.tocPath != null && !book.entries.containsKey(book.tocPath)) errors += "toc.ncx 文件缺失：${book.tocPath}"
        if (book.spineIds.isEmpty()) errors += "OPF spine 为空"
        if (book.chapters.isEmpty()) errors += "未从 spine 中解析到 HTML 章节"
        if (book.tocPath == null) {
            val hasNav = book.manifest.values.any { it.isNavDocument() }
            warnings += if (hasNav) {
                "未找到 toc.ncx，已使用 EPUB3 NAV 回退"
            } else {
                "未找到 toc.ncx，章节标题使用 HTML 回退"
            }
        }

        val missing = book.manifest.values.filter { it.looksLikeHtml() && !book.entries.containsKey(it.path) }
        if (missing.isNotEmpty()) errors += "manifest 中有 ${missing.size} 个 HTML 文件缺失"

        if (!book.entries.containsKey("mimetype")) warnings += "缺少 EPUB mimetype 条目"
        val blankTitles = book.chapters.count { it.title.isBlank() }
        if (blankTitles > 0) warnings += "$blankTitles 个章节标题为空"
        val zeroText = book.chapters.count { it.wordCount == 0 }
        if (zeroText > 0) warnings += "$zeroText 个章节正文为空或无法统计字数"
        val duplicateTitles = book.chapters.groupingBy { it.title }.eachCount().filter { it.key.isNotBlank() && it.value > 1 }
        if (duplicateTitles.isNotEmpty()) warnings += "发现 ${duplicateTitles.size} 组重复章节标题"
        if (hiddenSection0001Count(book, options) > 0) warnings += "目录隐藏项：Section0001"

        return CheckReport(errors, warnings)
    }

    fun hiddenSection0001Count(book: EpubBook, options: EpubExportOptions = EpubExportOptions()): Int {
        if (!options.hideSection0001FromNcx || book.tocPath == null) return 0
        return book.chapters.count { chapter ->
            chapter.isSection0001Chapter()
        }
    }

    private fun findOpfPath(entries: Map<String, ByteArray>): String {
        entries["META-INF/container.xml"]?.let { container ->
            runCatching {
                val doc = parseXml(container)
                doc.elements("rootfile")
                    .firstOrNull()
                    ?.attr("full-path")
                    ?.takeIf(String::isNotBlank)
            }.getOrNull()?.let { return normalizePath(it) }
        }
        return entries.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
            ?: error("EPUB 中没有找到 OPF")
    }

    private fun parseBookTitle(doc: org.w3c.dom.Document): String {
        val metadata = doc.elements("metadata").firstOrNull()
        return metadata
            ?.children("title")
            ?.firstOrNull()
            ?.textContent
            ?.trim()
            .orEmpty()
            .ifBlank {
                doc.elements("title")
                    .firstOrNull()
                    ?.textContent
                    ?.trim()
                    .orEmpty()
            }
    }

    private fun parseBookAuthor(doc: org.w3c.dom.Document): String {
        val metadata = doc.elements("metadata").firstOrNull()
        return metadata
            ?.children("creator")
            ?.firstOrNull()
            ?.textContent
            ?.trim()
            .orEmpty()
            .ifBlank {
                doc.elements("creator")
                    .firstOrNull()
                    ?.textContent
                    ?.trim()
                    .orEmpty()
            }
    }

    private fun updateOpfPackage(bytes: ByteArray, book: EpubBook): ByteArray {
        return runCatching {
            val doc = parseXml(bytes)
            var changed = false

            doc.elements("manifest").firstOrNull()?.let { manifestNode ->
                val existingIds = mutableSetOf<String>()
                val removedItems = mutableListOf<Element>()
                manifestNode.children("item").forEach { item ->
                    val id = item.attr("id")
                    existingIds += id
                    val manifestItem = book.manifest[id] ?: run {
                        removedItems += item
                        changed = true
                        return@forEach
                    }
                    if (item.attr("href") != manifestItem.href) {
                        item.setAttribute("href", manifestItem.href)
                        changed = true
                    }
                    if (item.attr("media-type") != manifestItem.mediaType) {
                        item.setAttribute("media-type", manifestItem.mediaType)
                        changed = true
                    }
                    if (manifestItem.properties.isNotBlank() && item.attr("properties") != manifestItem.properties) {
                        item.setAttribute("properties", manifestItem.properties)
                        changed = true
                    }
                }
                removedItems.forEach { item -> manifestNode.removeChild(item) }

                book.manifest.values.forEach { manifestItem ->
                    if (manifestItem.id in existingIds) return@forEach
                    val item = doc.createElementNS(manifestNode.namespaceURI, "item")
                    item.setAttribute("id", manifestItem.id)
                    item.setAttribute("href", manifestItem.href)
                    item.setAttribute("media-type", manifestItem.mediaType)
                    if (manifestItem.properties.isNotBlank()) {
                        item.setAttribute("properties", manifestItem.properties)
                    }
                    manifestNode.appendChild(item)
                    changed = true
                }
            }

            val coverItem = book.manifest.values.firstOrNull { item ->
                item.properties.split(Regex("\\s+")).any { it.equals("cover-image", ignoreCase = true) } ||
                    item.id.equals("cover", ignoreCase = true) ||
                    item.id.equals("cover-image", ignoreCase = true)
            }
            if (coverItem != null) {
                doc.elements("metadata").firstOrNull()?.let { metadata ->
                    val meta = doc.elements("meta").firstOrNull { node ->
                        node.attr("name").equals("cover", ignoreCase = true)
                    } ?: doc.createElementNS(metadata.namespaceURI, "meta").also { node ->
                        node.setAttribute("name", "cover")
                        metadata.appendChild(node)
                        changed = true
                    }
                    if (meta.attr("content") != coverItem.id) {
                        meta.setAttribute("content", coverItem.id)
                        changed = true
                    }
                }
            }

            doc.elements("metadata").firstOrNull()?.let { metadata ->
                rewriteMetadataToTitleAndCreator(doc, metadata, book)
                changed = true
            }

            doc.elements("spine").firstOrNull()?.let { spineNode ->
                val existingItemRefs = spineNode.children("itemref")
                val existingOrder = existingItemRefs.mapNotNull { itemRef ->
                    itemRef.attr("idref").takeIf(String::isNotBlank)
                }
                if (existingOrder != book.spineIds) {
                    val existingAttributes = existingItemRefs.associate { itemRef ->
                        itemRef.attr("idref") to itemRef.attributes.toAttributeMap()
                    }
                    existingItemRefs.forEach { itemRef -> spineNode.removeChild(itemRef) }
                    book.spineIds.forEach { id ->
                        val itemRef = doc.createElementNS(spineNode.namespaceURI, "itemref")
                        existingAttributes[id]?.forEach { (name, value) ->
                            if (name != "idref") itemRef.setAttribute(name, value)
                        }
                        itemRef.setAttribute("idref", id)
                        spineNode.appendChild(itemRef)
                    }
                    changed = true
                }
            }
            if (changed) serializeOpfXml(doc) else bytes
        }.getOrDefault(bytes)
    }

    private fun rewriteMetadataToTitleAndCreator(
        doc: org.w3c.dom.Document,
        metadata: Element,
        book: EpubBook
    ) {
        while (metadata.firstChild != null) {
            metadata.removeChild(metadata.firstChild)
        }
        val dcNamespace = "http://purl.org/dc/elements/1.1/"
        val title = doc.createElementNS(dcNamespace, "dc:title").apply {
            textContent = book.metadataTitle
        }
        val creator = doc.createElementNS(dcNamespace, "dc:creator").apply {
            textContent = book.metadataAuthor
        }
        metadata.appendChild(title)
        metadata.appendChild(creator)
    }

    private fun ManifestItem.looksLikeHtml(): Boolean {
        return mediaType.contains("xhtml", ignoreCase = true) ||
            mediaType.contains("html", ignoreCase = true) ||
            path.endsWith(".xhtml", ignoreCase = true) ||
            path.endsWith(".html", ignoreCase = true) ||
            path.endsWith(".htm", ignoreCase = true)
    }


}

private const val EPUB_CHAPTER_WORD_COUNT_UNKNOWN = -1
