package com.eteditor

import java.net.URI

internal fun needsSosadSearch(query: String): Boolean {
    if (query.isBlank()) return false
    if (query.startsWith("http://", ignoreCase = true) || query.startsWith("https://", ignoreCase = true)) {
        return isSosadSearchUrl(query)
    }
    return true
}

internal suspend fun resolveSosadDetailUrl(
    query: String,
    headers: Map<String, String>,
    expectedAuthor: String = "",
    onProgress: FetchInfoProgress = {}
): String {
    if (query.isBlank()) error("请输入书名或废文页面地址")
    if (!query.startsWith("http://", ignoreCase = true) &&
        !query.startsWith("https://", ignoreCase = true)
    ) {
        return findSosadDetailUrl(query, headers, expectedAuthor, onProgress)
    }
    if (!isSosadSearchUrl(query)) return requireSosadThreadDetailUrl(query)
    val html = FetchHttpClient.getText(query, headers, ::isSosadSameHostHttpsRedirect)
    return parseSosadSearchItems(html, query)
        .firstOrNull()
        ?.detailUrl
        ?: findSosadDetailUrlInSearchHtml(html, query)
        ?: error("没有从废文搜索结果找到页面")
}

private suspend fun findSosadDetailUrl(
    query: String,
    headers: Map<String, String>,
    expectedAuthor: String = "",
    onProgress: FetchInfoProgress = {}
): String {
    if (query.isBlank()) error("请输入书名或废文页面地址")
    searchSosadItems(query, headers, "title", expectedAuthor, onProgress).firstOrNull()?.detailUrl?.let { return it }
    val searchUrls = sosadSearchUrls(query)
    for ((index, searchUrl) in searchUrls.withIndex()) {
        onProgress("搜索镜像 ${index + 1}/${searchUrls.size}：${sosadSearchHostLabel(searchUrl)}")
        val html = runCatching {
            FetchHttpClient.getText(searchUrl, headers, ::isSosadSameHostHttpsRedirect)
        }.getOrNull()
            ?: continue
        findSosadDetailUrlInSearchHtml(html, searchUrl)?.let { return it }
    }
    error("没有从废文搜索结果找到页面")
}

internal suspend fun searchSosadItems(
    query: String,
    headers: Map<String, String>,
    searchMode: String,
    expectedAuthor: String = "",
    onProgress: FetchInfoProgress = {}
): List<SosadSearchItem> {
    if (query.isBlank()) error("请输入书名或废文页面地址")
    val searchUrls = sosadSearchUrls(query)
    val isSearchUrlQuery = query.startsWith("http://", ignoreCase = true) ||
        query.startsWith("https://", ignoreCase = true)
    val normalizedQuery = query.normalizeSearchText()
    val normalizedExpectedAuthor = expectedAuthor.normalizeSearchText()
    val fallbackItems = mutableListOf<SosadSearchItem>()
    for ((index, searchUrl) in searchUrls.withIndex()) {
        onProgress("搜索镜像 ${index + 1}/${searchUrls.size}：${sosadSearchHostLabel(searchUrl)}")
        val html = runCatching {
            FetchHttpClient.getText(searchUrl, headers, ::isSosadSameHostHttpsRedirect)
        }.getOrNull()
            ?: continue
        val items = parseSosadSearchItems(html, searchUrl)
            .distinctBy { item -> sosadThreadId(item.detailUrl) ?: item.detailUrl }
            .sortForSosadMode(query, searchMode)
            .let { sorted ->
                fillSosadExactTitleAuthors(
                    items = sorted,
                    normalizedQuery = normalizedQuery,
                    headers = headers,
                    onProgress = onProgress
                )
            }
        if (isSearchUrlQuery) return items
        val titleMatches = items.filter { item ->
            item.title.normalizeSearchText() == normalizedQuery
        }
        val authorMatches = if (normalizedExpectedAuthor.isNotBlank()) {
            titleMatches.filter { item ->
                item.author.normalizeSearchText() == normalizedExpectedAuthor
            }
        } else {
            emptyList()
        }
        when {
            authorMatches.isNotEmpty() -> return authorMatches
            normalizedExpectedAuthor.isBlank() && titleMatches.isNotEmpty() -> return titleMatches
        }
        fallbackItems += items
    }
    return fallbackItems
        .distinctBy { item -> sosadThreadId(item.detailUrl) ?: item.detailUrl }
        .sortForSosadMode(query, searchMode)
}

private fun sosadSearchUrls(query: String): List<String> {
    return if (query.startsWith("http://", ignoreCase = true) ||
        query.startsWith("https://", ignoreCase = true)
    ) {
        listOf(query.trim()).filter(::isSosadSearchUrl)
    } else {
        SOSAD_BASE_URLS.map { baseUrl -> "$baseUrl/search?search=${urlEncode(query)}" }
    }
}

private suspend fun fillSosadExactTitleAuthors(
    items: List<SosadSearchItem>,
    normalizedQuery: String,
    headers: Map<String, String>,
    onProgress: FetchInfoProgress = {}
): List<SosadSearchItem> {
    val authorCheckTotal = items.count { item ->
        item.author.isBlank() && item.title.normalizeSearchText() == normalizedQuery
    }
    if (authorCheckTotal == 0) return items
    var authorCheckIndex = 0
    return items.map { item ->
        if (item.author.isNotBlank() || item.title.normalizeSearchText() != normalizedQuery) {
            item
        } else {
            authorCheckIndex += 1
            onProgress("正在确认作者 $authorCheckIndex/$authorCheckTotal")
            item.copy(author = fetchSosadDetailAuthor(item.detailUrl, headers))
        }
    }
}

private fun parseSosadSearchItems(html: String, searchUrl: String): List<SosadSearchItem> {
    // 每条搜索结果是一个独立的 <article> 卡片，标题链接与作者都在卡片内部。
    // 先按卡片解析，作者只在本卡片内查找，避免窗口越界吃到相邻卡片的注册作者，
    // 导致匿名马甲作者被顶掉。没有 article 标签时回退到窗口解析。
    val articleItems = Regex(
        """<article\b[^>]*>(.*?)</article>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).findAll(html)
        .mapNotNull { match -> parseSosadSearchItemFromArticle(match.groupValues[1], searchUrl) }
        .toList()
    if (articleItems.isNotEmpty()) return articleItems
    return Regex(
        """<a\b([^>]*)>(.*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).findAll(html).mapNotNull { match ->
        val href = attr(match.value, "href")
        val detailUrl = absoluteUrl(href, searchUrl)
        if (!isSosadThreadDetailUrl(detailUrl)) {
            return@mapNotNull null
        }
        val title = match.groupValues[2]
            .cleanHtmlText()
            .cleanSosadCatalogText()
            .takeIf { it.isNotBlank() && !isSosadUiText(it) }
            ?: return@mapNotNull null
        val window = html.substring(
            (match.range.first - 900).coerceAtLeast(0),
            (match.range.last + 1200).coerceAtMost(html.length)
        )
        SosadSearchItem(
            title = title,
            author = parseSosadSearchAuthor(window),
            detailUrl = detailUrl
        )
    }.toList()
}

private fun parseSosadSearchItemFromArticle(
    articleBlock: String,
    searchUrl: String
): SosadSearchItem? {
    val titleMatch = Regex(
        """<a\b([^>]*)>(.*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).findAll(articleBlock).firstOrNull { match ->
        isSosadThreadDetailUrl(absoluteUrl(attr(match.value, "href"), searchUrl))
    } ?: return null
    val detailUrl = absoluteUrl(attr(titleMatch.value, "href"), searchUrl)
    val title = titleMatch.groupValues[2]
        .cleanHtmlText()
        .cleanSosadCatalogText()
        .takeIf { it.isNotBlank() && !isSosadUiText(it) }
        ?: return null
    return SosadSearchItem(
        title = title,
        author = parseSosadSearchAuthor(articleBlock),
        detailUrl = detailUrl
    )
}

private fun sosadSearchHostLabel(url: String): String {
    return runCatching { URI(url).host.orEmpty() }
        .getOrDefault("")
        .ifBlank { url.substringBefore('/').ifBlank { url } }
}

internal fun parseSosadSearchAuthor(window: String): String {
    val candidates = buildList {
        Regex(
            """<a\b[^>]+href=["'][^"']*/(?:members|users?|u)/[^"']+["'][^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(window).forEach { add(it.groupValues[1]) }
        Regex(
            """<span\b[^>]*class=["'][^"']*pull-right[^"']*["'][^>]*>\s*<span[^>]*>(.*?)</span>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(window).forEach { add(it.groupValues[1]) }
        Regex(
            """<(?:a|span|div)\b[^>]*(?:class|data-author|data-user|data-username|data-anonymous|rel)=["'][^"']*(?:author|username|user|poster|anonymous)[^"']*["'][^>]*>(.*?)</(?:a|span|div)>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(window).forEach { add(it.groupValues[1]) }
        Regex(
            """匿名马甲\s*[:：]?\s*([^<\n\r]{1,40})""",
            RegexOption.IGNORE_CASE
        ).findAll(window.cleanHtmlBlock()).forEach { add(it.groupValues[1]) }
        Regex(
            """(?:作者|楼主|Author|author)\s*[:：]?\s*([^<\n\r]{1,40})""",
            RegexOption.IGNORE_CASE
        ).findAll(window.cleanHtmlBlock()).forEach { add(it.groupValues[1]) }
    }
    return candidates.asSequence()
        .map { it.cleanHtmlText().cleanSosadCatalogText() }
        .firstOrNull { it.isNotBlank() && !isSosadUiText(it) }
        .orEmpty()
}

private fun fetchSosadDetailAuthor(detailUrl: String, headers: Map<String, String>): String {
    if (!isSosadThreadDetailUrl(detailUrl)) return ""
    return runCatching {
        parseSosadSearchAuthor(
            FetchHttpClient.getText(detailUrl, headers, ::isSosadSameHostHttpsRedirect).take(12000)
        )
    }.getOrDefault("")
}

private fun List<SosadSearchItem>.sortForSosadMode(
    query: String,
    searchMode: String
): List<SosadSearchItem> {
    val normalizedQuery = query.normalizeSearchText()
    if (normalizedQuery.isBlank() ||
        query.startsWith("http://", ignoreCase = true) ||
        query.startsWith("https://", ignoreCase = true)
    ) {
        return this
    }
    val selector: (SosadSearchItem) -> String = if (searchMode == "author") {
        { it.author.normalizeSearchText() }
    } else {
        { it.title.normalizeSearchText() }
    }
    val scoped = filter { item ->
        selector(item).contains(normalizedQuery) ||
            item.title.normalizeSearchText().contains(normalizedQuery) ||
            item.author.normalizeSearchText().contains(normalizedQuery)
    }.ifEmpty { this }
    return scoped.sortedWith(
        compareByDescending<SosadSearchItem> { selector(it) == normalizedQuery }
            .thenByDescending { it.title.normalizeSearchText() == normalizedQuery }
            .thenByDescending { selector(it).contains(normalizedQuery) }
            .thenBy { it.title.length }
    )
}

private fun findSosadDetailUrlInSearchHtml(html: String, searchUrl: String): String? {
    return parseSosadSearchItems(html, searchUrl).firstOrNull()?.detailUrl ?: Regex(
        """href=["']([^"']*/threads/(?:[^"'/?#]+\.)?\d+(?:/profile)?/?(?:[?#][^"']*)?)["']""",
        RegexOption.IGNORE_CASE
    ).findAll(html)
        .mapNotNull { match -> match.groupValues.getOrNull(1)?.let { absoluteUrl(it, searchUrl) } }
        .firstOrNull { url -> isSosadThreadDetailUrl(url) }
}

internal fun isSosadSearchUrl(url: String): Boolean {
    if (!isSosadAllowedHttpsUrl(url)) return false
    return runCatching { URI(url).path.orEmpty().trimEnd('/') }
        .getOrDefault("")
        .equals("/search", ignoreCase = true)
}

internal fun isSosadThreadDetailUrl(url: String): Boolean {
    if (!isSosadAllowedHttpsUrl(url)) return false
    val path = runCatching { URI(url).path.orEmpty() }.getOrDefault(url)
    return sosadThreadId(url) != null && !path.contains("/posts/", ignoreCase = true)
}

internal fun isSosadPostUrl(url: String): Boolean {
    return isSosadAllowedHttpsUrl(url) && sosadPostId(url) != null
}

private fun requireSosadThreadDetailUrl(url: String): String {
    val clean = requireSosadAllowedHttpsUrl(url, "废文详情页链接")
    if (!isSosadThreadDetailUrl(clean)) {
        error("废文详情页链接必须是作品详情页")
    }
    return clean
}

private fun sosadThreadId(url: String): String? {
    val path = runCatching { URI(url).path.orEmpty() }.getOrDefault(url)
    return Regex("""/threads/(?:[^/?#]*\.)?(\d+)""", RegexOption.IGNORE_CASE)
        .find(path)
        ?.groupValues
        ?.getOrNull(1)
}

internal fun sosadPostId(url: String): String? {
    val path = runCatching { URI(url).path.orEmpty() }.getOrDefault(url)
    return Regex("""/(?:threads/(?:[^/?#]*\.)?\d+/)?posts/(\d+)""", RegexOption.IGNORE_CASE)
        .find(path)
        ?.groupValues
        ?.getOrNull(1)
}

private const val SOSAD_BASE_URL = "https://xn--pxtr7m.com"

private val SOSAD_BASE_URLS = listOf(
    SOSAD_BASE_URL,
    "https://sosad.fun",
    "https://www.xn--pxtr7m.com",
    "https://www.sosad.fun"
)
