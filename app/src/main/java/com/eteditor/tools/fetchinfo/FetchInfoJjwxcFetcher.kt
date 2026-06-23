package com.eteditor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class JjwxcFetcher : FetchInfoFetcher {
    override val source: String = FetchInfoSources.JJWXC

    override suspend fun searchChoices(
        parameters: FetchInfoParameters,
        onProgress: FetchInfoProgress
    ): List<FetchInfoSearchChoice> = withContext(Dispatchers.IO) {
        val query = parameters.query.trim()
        if (!needsJjwxcSearch(query)) return@withContext emptyList()
        onProgress("搜索中...")
        val searchQueries = if (parameters.searchMode == "author") {
            listOf(query)
        } else {
            jjwxcSearchQueries(query)
        }
        val foundItems = mutableListOf<JjwxcSearchItem>()
        searchQueries.forEach { searchQuery ->
            foundItems += searchJjwxcItems(searchQuery, onProgress)
        }
        val items = foundItems
            .distinctBy { it.novelId }
            .sortForJjwxcMode(query, parameters.searchMode)
        items.map { item ->
            FetchInfoSearchChoice(
                source = source,
                title = item.novelName,
                author = item.authorName,
                detailUrl = "https://www.jjwxc.net/onebook.php?novelid=${item.novelId}"
            )
        }
    }

    override suspend fun fetch(
        parameters: FetchInfoParameters,
        onProgress: FetchInfoProgress
    ): FetchedInfo = withContext(Dispatchers.IO) {
        val query = parameters.query.trim()
        val novelId = extractJjwxcNovelId(query)
        val detailUrl = when {
            novelId != null -> "https://www.jjwxc.net/onebook.php?novelid=$novelId"
            query.startsWith("http://", ignoreCase = true) || query.startsWith("https://", ignoreCase = true) -> query
            query.startsWith("onebook.php", ignoreCase = true) -> "https://www.jjwxc.net/$query"
            query.contains("novelid=", ignoreCase = true) -> "https://www.jjwxc.net/onebook.php?$query"
            query.matches(Regex("""\d+""")) -> "https://www.jjwxc.net/onebook.php?novelid=$query"
            else -> {
                onProgress("搜索中...")
                findJjwxcDetailUrl(query, onProgress)
            }
        }
        onProgress("正在读取详情页")
        val html = FetchHttpClient.getText(detailUrl)
        val catalog = if (parameters.fetchCatalog) {
            onProgress("正在抓取目录")
            parseJjwxcCatalog(html, detailUrl)
        } else {
            emptyList()
        }
        FetchedInfo(
            source = source,
            query = query,
            resolvedUrl = detailUrl,
            title = parseJjwxcTitle(html),
            author = parseJjwxcAuthor(html),
            intro = if (parameters.fetchIntro) parseJjwxcIntro(html) else "",
            coverUrl = if (parameters.fetchCover) parseJjwxcCover(html, detailUrl) else "",
            catalog = catalog
        )
    }

    private suspend fun findJjwxcDetailUrl(query: String, onProgress: FetchInfoProgress = {}): String {
        if (query.isBlank()) error("请输入书名或详情页地址")
        val searchQueries = jjwxcSearchQueries(query)
        val expectedAuthor = extractJjwxcAuthor(query)
        searchQueries.forEach { searchQuery ->
            findJjwxcDetailUrlFromAjax(searchQuery, expectedAuthor, onProgress)?.let { return it }
        }
        searchQueries.forEach { searchQuery ->
            val encodedQueries = listOf(
                urlEncode(searchQuery, StandardCharsets.UTF_8),
                urlEncode(searchQuery, Charset.forName("GB18030"))
            ).distinct()
            encodedQueries.forEach { encoded ->
                val searchUrl = "https://www.jjwxc.net/search.php?kw=$encoded"
                val html = FetchHttpClient.getText(searchUrl)
                val match = Regex(
                    """href=["']([^"']*onebook\.php\?novelid=\d+[^"']*)["']""",
                    RegexOption.IGNORE_CASE
                ).find(html)
                if (match != null) return absoluteUrl(match.groupValues[1], searchUrl)
            }
        }
        error("没有从晋江搜索结果找到详情页：$query；已尝试：${searchQueries.joinToString(" / ")}；可改填 novelid 或 onebook.php 详情页")
    }

    private suspend fun findJjwxcDetailUrlFromAjax(
        query: String,
        expectedAuthor: String?,
        onProgress: FetchInfoProgress = {}
    ): String? {
        val items = searchJjwxcItems(query, onProgress).sortForJjwxcMode(query, "title")
        val normalizedQuery = query.normalizeJjwxcSearchText()
        val normalizedAuthor = expectedAuthor?.normalizeJjwxcSearchText().orEmpty()
        val selected = items.firstOrNull {
            normalizedAuthor.isNotBlank() &&
                it.novelName.normalizeJjwxcSearchText() == normalizedQuery &&
                it.authorName.normalizeJjwxcSearchText() == normalizedAuthor
        }
            ?: items.firstOrNull { it.novelName.normalizeJjwxcSearchText() == normalizedQuery }
            ?: items.firstOrNull { it.novelName.normalizeJjwxcSearchText().contains(normalizedQuery) }
            ?: items.firstOrNull()
        return selected?.novelId?.let { "https://www.jjwxc.net/onebook.php?novelid=$it" }
    }

    private suspend fun searchJjwxcItems(
        query: String,
        onProgress: FetchInfoProgress = {}
    ): List<JjwxcSearchItem> {
        val ajaxUrls = buildList {
            listOf(
                urlEncode(query, StandardCharsets.UTF_8),
                urlEncode(query, Charset.forName("GB18030"))
            ).distinct().forEach { encoded ->
                listOf("1", "0", "novelname", "all").forEach { type ->
                    add("https://www.jjwxc.net/search/search_ajax.php?action=search&keywords=$encoded&type=$type&version=1&getfull=1")
                }
            }
        }
        val items = mutableListOf<JjwxcSearchItem>()
        ajaxUrls.forEachIndexed { index, url ->
            onProgress("正在尝试搜索源 ${index + 1}/${ajaxUrls.size}")
            val text = runCatching { FetchHttpClient.getText(url) }.getOrNull() ?: return@forEachIndexed
            items += parseJjwxcSearchItems(text)
        }
        return items
            .distinctBy { it.novelId }
    }

    private fun parseJjwxcSearchItems(text: String): List<JjwxcSearchItem> {
        return runCatching {
            val root = JSONObject(text)
            val data = root.optJSONArray("data") ?: return@runCatching emptyList()
            buildList {
                for (index in 0 until data.length()) {
                    val item = data.optJSONObject(index) ?: continue
                    val novelId = item.optString("novelid").takeIf { it.isNotBlank() } ?: continue
                    add(
                        JjwxcSearchItem(
                            novelId = novelId,
                            novelName = item.optString("novelname"),
                            authorName = item.optString("authorname")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun List<JjwxcSearchItem>.sortForJjwxcMode(
        query: String,
        searchMode: String
    ): List<JjwxcSearchItem> {
        val normalizedQuery = query.normalizeJjwxcSearchText()
        if (normalizedQuery.isBlank()) return this
        val selector: (JjwxcSearchItem) -> String = if (searchMode == "author") {
            { it.authorName.normalizeJjwxcSearchText() }
        } else {
            { it.novelName.normalizeJjwxcSearchText() }
        }
        val scoped = filter { selector(it).contains(normalizedQuery) }.ifEmpty { this }
        return scoped.sortedWith(
            compareByDescending<JjwxcSearchItem> { selector(it) == normalizedQuery }
                .thenByDescending { selector(it).contains(normalizedQuery) }
                .thenBy { it.novelName.length }
        )
    }

    private fun parseJjwxcTitle(html: String): String {
        return Regex(
            """<span[^>]*itemprop=["']articleSection["'][^>]*>(.*?)</span>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)?.groupValues?.getOrNull(1)?.cleanHtmlText()
            ?: Regex("""<title>\s*《([^》]+)》""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.getOrNull(1)?.cleanHtmlText()
            ?: ""
    }

    private fun parseJjwxcAuthor(html: String): String {
        return Regex(
            """<span[^>]*itemprop=["']author["'][^>]*>(.*?)</span>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)?.groupValues?.getOrNull(1)?.cleanHtmlText()
            ?: Regex(
                """作者[：:]\s*</?[^>]*>\s*([^<\n\r]+)""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).find(html)?.groupValues?.getOrNull(1)?.cleanHtmlText()
            ?: ""
    }

    private fun parseJjwxcIntro(html: String): String {
        val lines = mutableListOf<String>()
        val introText = extractJjwxcIntroElement(html)
            .cleanHtmlBlock()
            .trimJjwxcIntroBoundary()
        introText
            .trimPresaleBlock()
            .cleanIntroBracketLines()
            .takeIf { it.isNotBlank() }
            ?.let(lines::add)

        val infoBlock = findJjwxcIntroInfoBlock(html)
        addUniqueLine(lines, getJjwxcContentTagLine(infoBlock))
        addUniqueLine(lines, getJjwxcKeywordLine(infoBlock, html))
        addUniqueLine(lines, getJjwxcInfoLine(infoBlock, "一句话简介"))
        addUniqueLine(lines, getJjwxcInfoLine(infoBlock, "立意"))

        return lines.joinToString("\n").compactLines()
    }

    private fun extractJjwxcIntroElement(html: String): String {
        val tag = Regex(
            """<([a-z0-9]+)\b[^>]*>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(html)
            .firstOrNull { match ->
                val openTag = match.value
                val id = attr(openTag, "id")
                val itemprop = attr(openTag, "itemprop")
                id.equals("novelintro", ignoreCase = true) ||
                    itemprop.split(Regex("\\s+")).any { it.equals("description", ignoreCase = true) }
            }
            ?: return ""
        return extractBalancedHtmlElement(html, tag.range.first, tag.groupValues[1])
            ?: extractJjwxcIntroElementFallback(html)
    }

    private fun extractJjwxcIntroElementFallback(html: String): String {
        return Regex(
            """<div[^>]*id=["']novelintro["'][^>]*>(.*?)</div>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)?.groupValues?.getOrNull(1)
            ?: Regex(
                """<[^>]*itemprop=["']description["'][^>]*>(.*?)</(?:div|span|td|p)>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).find(html)?.groupValues?.getOrNull(1)
            ?: ""
    }

    private fun extractBalancedHtmlElement(html: String, startIndex: Int, tagName: String): String? {
        val tagRegex = Regex(
            """</?\s*${Regex.escape(tagName)}\b[^>]*>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        var depth = 0
        tagRegex.findAll(html, startIndex).forEach { match ->
            val tag = match.value
            val closing = tag.startsWith("</")
            val selfClosing = tag.endsWith("/>")
            if (!closing) {
                depth += 1
                if (selfClosing) depth -= 1
            } else {
                depth -= 1
                if (depth == 0) {
                    return html.substring(startIndex, match.range.last + 1)
                }
            }
        }
        return null
    }

    private fun findJjwxcIntroInfoBlock(html: String): String {
        return Regex(
            """<([a-z0-9]+)\b[^>]*class=["'][^"']*smallreadbody[^"']*["'][^>]*>(.*?)</\1>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(html)
            .map { it.groupValues[2] }
            .firstOrNull { it.cleanHtmlText().contains("内容标签：") }
            .orEmpty()
    }

    private fun getJjwxcContentTagLine(infoBlock: String): String {
        if (infoBlock.isBlank()) return ""
        val tags = Regex(
            """<a\b[^>]*(?:href|rel)=["'][^"']*bookbase\.php\?bq=[^"']*["'][^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(infoBlock)
            .map { it.groupValues[1].cleanHtmlText() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        return if (tags.isEmpty()) "" else "内容标签：${tags.joinToString(" ")}"
    }

    private fun getJjwxcKeywordLine(infoBlock: String, html: String): String {
        if (infoBlock.isNotBlank()) {
            val text = infoBlock.cleanHtmlBlock()
            val parts = listOf("主角", "配角", "其它").mapNotNull { label ->
                findJjwxcInfoLineFromText(text, label).takeIf { it.isNotBlank() }
            }
            if (parts.isNotEmpty()) return "搜索关键字：${parts.joinToString(" ┃ ")}"
        }
        val meta = Regex(
            """<meta[^>]+(?:name|property)=["']keywords["'][^>]+content=["']([^"']*)["'][^>]*>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)?.groupValues?.getOrNull(1)?.decodeHtmlEntities().orEmpty()
        val start = meta.indexOf("主角：")
        if (start < 0) return ""
        val end = meta.indexOf('|', start).let { if (it < 0) meta.length else it }
        return "搜索关键字：" + meta.substring(start, end)
            .replace('，', ' ')
            .replace('、', ' ')
            .compactLines()
    }

    private fun getJjwxcInfoLine(infoBlock: String, label: String): String {
        if (infoBlock.isBlank()) return ""
        return Regex(
            """<span\b[^>]*>(.*?)</span>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(infoBlock)
            .map { it.groupValues[1].cleanHtmlText() }
            .firstOrNull { it.startsWith("$label：") || it.startsWith("$label:") }
            ?: findJjwxcInfoLineFromText(infoBlock.cleanHtmlBlock(), label)
    }

    private fun findJjwxcInfoLineFromText(text: String, label: String): String {
        return text.lines()
            .map { it.trim() }
            .firstOrNull { it.startsWith("$label：") || it.startsWith("$label:") }
            .orEmpty()
    }

    private fun parseJjwxcCover(html: String, baseUrl: String): String {
        val imageTag = Regex(
            """<img[^>]*itemprop=["']image["'][^>]*>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)?.value.orEmpty()
        val src = attr(imageTag, "src").ifBlank { attr(imageTag, "_src") }
        return src.takeIf { it.isNotBlank() }?.let { absoluteUrl(it, baseUrl) }.orEmpty()
    }

    private fun parseJjwxcCatalog(html: String, baseUrl: String): List<FetchedCatalogItem> {
        val seen = mutableSetOf<String>()
        val items = mutableListOf<FetchedCatalogItem>()
        Regex(
            """<tr\b[^>]*>.*?</tr>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(html).forEach { rowMatch ->
            val row = rowMatch.value
            val volumeTitle = Regex(
                """<b[^>]*class=["'][^"']*volumnfont[^"']*["'][^>]*>(.*?)</b>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).find(row)?.groupValues?.getOrNull(1)?.cleanHtmlText().orEmpty()
            if (volumeTitle.isNotBlank()) {
                items += FetchedCatalogItem(
                    index = items.size + 1,
                    title = volumeTitle,
                    isVolume = true
                )
                return@forEach
            }

            if (!row.contains("chapterid=", ignoreCase = true)) {
                // 锁章行：晋江保留整行但去掉跳转链接（带 redmanagertext 标记）。
                // 用空标题占位，避免后续章节按位置错位，最终只保留 EPUB 本地前缀。
                if (row.contains("redmanagertext", ignoreCase = true)) {
                    val lockedSequence = Regex(
                        """<td\b[^>]*>(.*?)</td>""",
                        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                    ).find(row)?.groupValues?.getOrNull(1)?.cleanHtmlText().orEmpty()
                    items += FetchedCatalogItem(
                        index = items.size + 1,
                        title = "",
                        sequence = lockedSequence
                    )
                }
                return@forEach
            }
            val cells = Regex(
                """<td\b[^>]*>(.*?)</td>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).findAll(row).map { it.groupValues[1] }.toList()
            if (cells.size < 3) return@forEach
            val sequence = cells[0].cleanHtmlText()
            val titleCell = cells[1]
            val titleLink = Regex(
                """<a\b([^>]*)>(.*?)</a>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).findAll(titleCell).firstOrNull { link ->
                val tag = link.value
                val target = attr(tag, "href").ifBlank { attr(tag, "rel") }
                target.contains("novelid=", ignoreCase = true) &&
                    target.contains("chapterid=", ignoreCase = true) &&
                    !link.groupValues[2].cleanHtmlText().startsWith("[")
            } ?: return@forEach
            val url = attr(titleLink.value, "href")
                .ifBlank { attr(titleLink.value, "rel") }
                .let { absoluteUrl(it, baseUrl) }
            val chapterTitle = titleLink.groupValues[2].cleanHtmlText()
                .ifBlank { titleCell.cleanHtmlText() }
            val summary = cells[2].cleanHtmlText()
            val title = combineCatalogTitle(chapterTitle, summary)
            val seenKey = url.ifBlank { "$sequence|$chapterTitle|$summary" }
            if (title.isNotBlank() && seen.add(seenKey)) {
                items += FetchedCatalogItem(
                    index = items.size + 1,
                    title = title,
                    url = url,
                    sequence = sequence,
                    chapterTitle = chapterTitle,
                    summary = summary
                )
            }
        }
        if (items.any { !it.isVolume }) return items

        Regex(
            """<a\b([^>]*)>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(html).forEach { match ->
            val target = attr(match.value, "href").ifBlank { attr(match.value, "rel") }
            if (!target.contains("novelid=", ignoreCase = true) || !target.contains("chapterid=", ignoreCase = true)) {
                return@forEach
            }
            val title = match.groupValues[2].cleanHtmlText()
            if (title.isBlank() || title.startsWith("[")) return@forEach
            val url = absoluteUrl(target, baseUrl)
            if (title.isNotBlank() && seen.add(url)) {
                items += FetchedCatalogItem(
                    index = items.size + 1,
                    title = title,
                    url = url,
                    chapterTitle = title
                )
            }
        }
        return items
    }
}
