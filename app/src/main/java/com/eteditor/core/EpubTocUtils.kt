package com.eteditor.core

import org.w3c.dom.Element

internal data class TocEntry(
    val title: String,
    val level: Int
)

internal data class TocNode(
    val chapter: EpubChapter,
    val children: MutableList<TocNode> = mutableListOf()
)

internal fun findNcxPath(spine: Element?, manifest: Map<String, ManifestItem>): String? {
    spine?.attr("toc")
        ?.takeIf(String::isNotBlank)
        ?.let { tocId -> manifest[tocId]?.path }
        ?.let { return it }

    return manifest.values.firstOrNull { item ->
        item.mediaType.equals("application/x-dtbncx+xml", ignoreCase = true) ||
            item.path.endsWith(".ncx", ignoreCase = true) ||
            item.path.endsWith(".nxc", ignoreCase = true) ||
            item.href.endsWith(".ncx", ignoreCase = true) ||
            item.href.endsWith(".nxc", ignoreCase = true)
    }?.path
}

internal fun parseNcxEntries(bytes: ByteArray, ncxPath: String): Map<String, TocEntry> {
    return runCatching {
        val doc = parseXml(bytes)
        val ncxDir = ncxPath.substringBeforeLast('/', missingDelimiterValue = "").let {
            if (it.isBlank()) "" else "$it/"
        }
        val entries = linkedMapOf<String, TocEntry>()

        fun visit(navPoint: Element, level: Int) {
            val title = navPoint.children("navLabel")
                .firstOrNull()
                ?.children("text")
                ?.firstOrNull()
                ?.textContent
                ?.trim()
                .orEmpty()
            val src = navPoint.children("content").firstOrNull()?.attr("src").orEmpty()

            if (title.isNotBlank() && src.isNotBlank()) {
                val href = src.substringBefore('#')
                val entry = TocEntry(title = title, level = level.coerceAtLeast(0))
                val rawPath = normalizePath(ncxDir + href)
                if (!entries.containsKey(rawPath)) entries[rawPath] = entry
                val decodedPath = normalizePath(ncxDir + href.decodeUrl())
                if (!entries.containsKey(decodedPath)) entries[decodedPath] = entry
            }

            navPoint.children("navPoint").forEach { child ->
                visit(child, level + 1)
            }
        }

        doc.elements("navMap").firstOrNull()
            ?.children("navPoint")
            ?.forEach { navPoint ->
                visit(navPoint, 0)
            }

        if (entries.isEmpty()) {
            doc.elements("navPoint").forEach { navPoint ->
                visit(navPoint, 0)
            }
        }
        entries
    }.getOrDefault(emptyMap())
}

internal fun parseNavEntries(
    entries: Map<String, ByteArray>,
    manifest: Map<String, ManifestItem>
): Map<String, TocEntry> {
    val navItem = manifest.values.firstOrNull { it.isNavDocument() } ?: return emptyMap()
    val bytes = entries[navItem.path] ?: return emptyMap()
    return runCatching {
        val doc = parseXml(bytes)
        val navDir = navItem.path.substringBeforeLast('/', missingDelimiterValue = "").let {
            if (it.isBlank()) "" else "$it/"
        }
        val tocNav = doc.elements("nav").firstOrNull { nav ->
            val type = nav.attrAny("epub:type", "type")
            type.split(Regex("\\s+")).any { it.equals("toc", ignoreCase = true) }
        } ?: doc.elements("nav").firstOrNull()
        val rootList = tocNav?.children("ol")?.firstOrNull() ?: return@runCatching emptyMap()
        val result = linkedMapOf<String, TocEntry>()

        fun visitList(list: Element, level: Int) {
            list.children("li").forEach { item ->
                val anchor = item.children("a").firstOrNull()
                    ?: item.children("p").firstOrNull()?.children("a")?.firstOrNull()
                val label = anchor?.textContent?.trim()
                    ?: item.children("span").firstOrNull()?.textContent?.trim()
                    ?: item.children("p").firstOrNull()?.children("span")?.firstOrNull()?.textContent?.trim()
                    ?: ""
                val hrefAttr = anchor?.attr("href").orEmpty()
                if (label.isNotBlank() && hrefAttr.isNotBlank()) {
                    val href = hrefAttr.substringBefore('#')
                    val entry = TocEntry(title = label, level = level.coerceAtLeast(0))
                    val rawPath = normalizePath(navDir + href)
                    if (!result.containsKey(rawPath)) result[rawPath] = entry
                    val decodedPath = normalizePath(navDir + href.decodeUrl())
                    if (!result.containsKey(decodedPath)) result[decodedPath] = entry
                }
                item.children("ol").forEach { childList ->
                    visitList(childList, level + 1)
                }
            }
        }

        visitList(rootList, 0)
        result
    }.getOrDefault(emptyMap())
}

internal fun updateNcxTitles(
    bytes: ByteArray,
    ncxPath: String,
    chapters: List<EpubChapter>,
    options: EpubExportOptions
): ByteArray {
    return runCatching {
        val doc = parseXml(bytes)
        var changed = false
        doc.elements("docAuthor").forEach { author ->
            author.parentNode?.removeChild(author)
            changed = true
        }
        val ncxDir = ncxPath.substringBeforeLast('/', missingDelimiterValue = "").let {
            if (it.isBlank()) "" else "$it/"
        }
        val navMap = doc.elements("navMap").firstOrNull()
            ?: return@runCatching if (changed) serializeXml(doc) else bytes
        val namespace = navMap.namespaceURI ?: doc.documentElement?.namespaceURI
        var playOrder = 1

        fun createNavPoint(node: TocNode): Element {
            val navPoint = doc.createElementNS(namespace, "navPoint")
            navPoint.setAttribute("id", "navPoint-$playOrder")
            navPoint.setAttribute("playOrder", playOrder.toString())
            playOrder += 1

            val navLabel = doc.createElementNS(namespace, "navLabel")
            val text = doc.createElementNS(namespace, "text")
            text.textContent = tocTitle(node.chapter, options)
            navLabel.appendChild(text)
            navPoint.appendChild(navLabel)

            val content = doc.createElementNS(namespace, "content")
            content.setAttribute("src", relativeHref(ncxDir, node.chapter.path))
            navPoint.appendChild(content)

            node.children.forEach { child ->
                navPoint.appendChild(createNavPoint(child))
            }
            return navPoint
        }

        while (navMap.hasChildNodes()) {
            navMap.removeChild(navMap.firstChild)
        }
        val tocChapters = chapters.filterNot { it.isExcludedFromNcx(options) }
        val tocTree = buildTocTree(tocChapters)
        tocTree.forEach { node ->
            navMap.appendChild(createNavPoint(node))
        }
        val depth = (tocChapters.maxOfOrNull { it.tocLevel.coerceAtLeast(0) } ?: 0) + 1
        val depthMeta = doc.elements("meta").firstOrNull { meta ->
            meta.attr("name").equals("dtb:depth", ignoreCase = true)
        }
        if (depthMeta == null) {
            doc.elements("head").firstOrNull()?.let { head ->
                val meta = doc.createElementNS(namespace, "meta")
                meta.setAttribute("name", "dtb:depth")
                meta.setAttribute("content", depth.toString())
                head.appendChild(meta)
            }
        } else if (depthMeta.attr("content") != depth.toString()) {
            depthMeta.setAttribute("content", depth.toString())
        }
        serializeNcxXml(doc)
    }.getOrDefault(bytes)
}

internal fun updateNavLinks(
    bytes: ByteArray,
    navPath: String,
    chapters: List<EpubChapter>,
    options: EpubExportOptions
): ByteArray {
    return runCatching {
        val doc = parseXml(bytes)
        val navDir = navPath.substringBeforeLast('/', missingDelimiterValue = "").let {
            if (it.isBlank()) "" else "$it/"
        }
        val tocNav = doc.elements("nav").firstOrNull { nav ->
            val type = nav.attrAny("epub:type", "type")
            type.split(Regex("\\s+")).any { it.equals("toc", ignoreCase = true) }
        }
        var changed = false

        if (tocNav != null) {
            val namespace = tocNav.namespaceURI ?: doc.documentElement?.namespaceURI

            fun createList(nodes: List<TocNode>): Element {
                val list = doc.createElementNS(namespace, "ol")
                nodes.forEach { node ->
                    val item = doc.createElementNS(namespace, "li")
                    val anchor = doc.createElementNS(namespace, "a")
                    anchor.setAttribute("href", relativeHref(navDir, node.chapter.path))
                    anchor.textContent = navTitle(node.chapter, options)
                    item.appendChild(anchor)
                    if (node.children.isNotEmpty()) {
                        item.appendChild(createList(node.children))
                    }
                    list.appendChild(item)
                }
                return list
            }

            val tocChapters = chapters.filterNot { it.isExcludedFromNcx(options) }
            val newList = createList(buildTocTree(tocChapters))
            val oldList = tocNav.children("ol").firstOrNull()
            if (oldList == null) {
                tocNav.appendChild(newList)
            } else {
                tocNav.replaceChild(newList, oldList)
            }
            changed = true
        }

        val chaptersByPath = linkedMapOf<String, EpubChapter>()
        chapters.forEach { chapter ->
            chaptersByPath[chapter.path] = chapter
            chaptersByPath[chapter.originalPath] = chapter
            chapter.pathAliases.forEach { path -> chaptersByPath[path] = chapter }
        }
        doc.elements("a").forEach { anchor ->
            val hrefAttr = anchor.attr("href")
            if (hrefAttr.isBlank()) return@forEach
            val href = hrefAttr.substringBefore('#')
            val fragment = hrefAttr.substringAfter('#', missingDelimiterValue = "")
            val rawPath = normalizePath(navDir + href)
            val decodedPath = normalizePath(navDir + href.decodeUrl())
            val chapter = chaptersByPath[rawPath] ?: chaptersByPath[decodedPath] ?: return@forEach
            val updatedHref = relativeHref(navDir, chapter.path).let {
                if (fragment.isBlank()) it else "$it#$fragment"
            }
            if (updatedHref != hrefAttr) {
                anchor.setAttribute("href", updatedHref)
                changed = true
            }
        }
        if (changed) serializeXml(doc) else bytes
    }.getOrDefault(bytes)
}

internal fun EpubChapter.isSection0001Chapter(): Boolean {
    val file = path.substringAfterLast('/').substringBefore('#')
    return file.equals("Section0001.xhtml", ignoreCase = true) ||
        file.equals("Section0001.html", ignoreCase = true)
}

internal fun ManifestItem.isNavDocument(): Boolean {
    return properties.split(Regex("\\s+")).any { it.equals("nav", ignoreCase = true) } ||
        path.endsWith("nav.xhtml", ignoreCase = true) ||
        path.endsWith("nav.html", ignoreCase = true)
}

private fun buildTocTree(chapters: List<EpubChapter>): List<TocNode> {
    val roots = mutableListOf<TocNode>()
    val stack = mutableListOf<TocNode>()
    chapters.forEach { chapter ->
        if (chapter.isEpubCoverDirectoryCandidate()) {
            val node = TocNode(chapter)
            roots += node
            stack.clear()
            return@forEach
        }
        val level = chapter.tocLevel.coerceAtLeast(0).coerceAtMost(stack.size)
        while (stack.size > level) {
            stack.removeAt(stack.lastIndex)
        }
        val node = TocNode(chapter)
        if (level == 0 || stack.isEmpty()) {
            roots += node
        } else {
            stack.last().children += node
        }
        stack += node
    }
    return roots
}

private fun tocTitle(chapter: EpubChapter, options: EpubExportOptions): String {
    return chapter.epubDirectoryTitle(useOwnSection0001Title = !options.hideSection0001FromNcx)
}

private fun navTitle(chapter: EpubChapter, options: EpubExportOptions): String {
    return chapter.epubDirectoryTitle(useOwnSection0001Title = !options.hideSection0001FromNcx)
}

private fun EpubChapter.isExcludedFromNcx(options: EpubExportOptions): Boolean {
    return options.hideSection0001FromNcx && isSection0001Chapter()
}
