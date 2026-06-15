package com.eteditor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class GongzicpFetcher : FetchInfoFetcher {
    override val source: String = FetchInfoSources.GONGZICP

    override suspend fun searchChoices(
        parameters: FetchInfoParameters,
        onProgress: FetchInfoProgress
    ): List<FetchInfoSearchChoice> = withContext(Dispatchers.IO) {
        val query = parameters.query.trim()
        if (!needsGongzicpSearch(query)) return@withContext emptyList()
        onProgress("搜索中...")
        searchGongzicpItems(query, parameters.searchMode, onProgress).map { item ->
            FetchInfoSearchChoice(
                source = source,
                title = item.title,
                author = item.author,
                detailUrl = item.detailUrl
            )
        }
    }

    override suspend fun fetch(
        parameters: FetchInfoParameters,
        onProgress: FetchInfoProgress
    ): FetchedInfo = withContext(Dispatchers.IO) {
        val query = parameters.query.trim()
        val detailUrl = normalizeGongzicpDetailUrl(query) ?: run {
            onProgress("搜索中...")
            searchGongzicpItems(query, parameters.searchMode, onProgress).firstOrNull()?.detailUrl
        } ?: error("没有从长佩搜索结果找到作品，请改用作品 ID 或长佩详情页地址再试")
        val novelId = extractGongzicpNovelId(detailUrl)
            ?: error("没有识别到长佩作品 ID")
        onProgress("正在读取详情页")
        val html = runCatching { FetchHttpClient.getText(detailUrl) }.getOrDefault("")
        val infoJson = fetchGongzicpInfoJson(novelId)
        val catalogJson = if (parameters.fetchCatalog) {
            onProgress("正在抓取目录")
            fetchGongzicpCatalogJson(novelId)
        } else {
            null
        }
        val apiText = listOfNotNull(infoJson?.toString(), catalogJson?.toString()).joinToString("\n")
        val sourceText = "$html\n$apiText"
        val title = parseGongzicpTitleFromJson(infoJson)
            .ifBlank { parseGongzicpTitle(sourceText) }
        val author = parseGongzicpAuthorFromJson(infoJson)
            .ifBlank { parseGongzicpAuthor(sourceText) }
        val intro = if (parameters.fetchIntro) parseGongzicpIntro(infoJson, sourceText) else ""
        val coverUrl = if (parameters.fetchCover) {
            parseGongzicpCoverFromJson(infoJson, detailUrl)
                .ifBlank { parseGongzicpCover(sourceText, detailUrl) }
        } else {
            ""
        }
        FetchedInfo(
            source = source,
            query = query,
            resolvedUrl = detailUrl,
            title = title,
            author = author,
            intro = intro,
            coverUrl = coverUrl,
            catalog = if (parameters.fetchCatalog) parseGongzicpCatalog(catalogJson, detailUrl) else emptyList()
        )
    }

    private suspend fun searchGongzicpItems(
        query: String,
        searchMode: String,
        onProgress: FetchInfoProgress = {}
    ): List<GongzicpSearchItem> {
        if (query.isBlank()) error("请输入长佩书名、作者或详情页地址")
        val encoded = urlEncode(query)
        val urls = listOf(
            "$GONGZICP_WEB_API/search/novels?k=$encoded",
            "$GONGZICP_WEB_API/search/novels?k=$encoded&page=1",
            "$GONGZICP_WEB_API/search/novels?keyword=$encoded&page=1",
            "$GONGZICP_WEB_API/search/novels?key=$encoded&page=1",
            "https://www.gongzicp.com/search?keyword=$encoded",
            "https://www.gongzicp.com/search?key=$encoded",
            "https://www.gongzicp.com/search/$encoded",
            "https://www.gongzicp.com/api/search/novel?keyword=$encoded",
            "https://www.gongzicp.com/api/novel/search?keyword=$encoded"
        )
        val items = mutableListOf<GongzicpSearchItem>()
        urls.forEachIndexed { index, url ->
            onProgress("正在尝试搜索源 ${index + 1}/${urls.size}")
            val text = runCatching { FetchHttpClient.getText(url) }.getOrNull() ?: return@forEachIndexed
            items += parseGongzicpSearchItems(text, "https://www.gongzicp.com/")
        }
        return items
            .distinctBy { it.detailUrl }
            .sortForGongzicpMode(query, searchMode)
    }

    private fun parseGongzicpSearchItems(text: String, baseUrl: String): List<GongzicpSearchItem> {
        val fromLinks = Regex(
            """<a\b([^>]*)>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(text).mapNotNull { match ->
            val href = attr(match.value, "href")
            val id = extractGongzicpNovelId(href) ?: return@mapNotNull null
            GongzicpSearchItem(
                id = id,
                title = match.groupValues[2].cleanHtmlText(),
                author = "",
                detailUrl = absoluteUrl(href, baseUrl)
            )
        }.toList()

        val fromJson = Regex(
            """"(?:novel_id|novelId|id)"\s*:\s*"?(\d+)"?""",
            RegexOption.IGNORE_CASE
        ).findAll(text).map { match ->
            val id = match.groupValues[1]
            val windowStart = (match.range.first - 1200).coerceAtLeast(0)
            val windowEnd = (match.range.last + 1200).coerceAtMost(text.length)
            val window = text.substring(windowStart, windowEnd)
            GongzicpSearchItem(
                id = id,
                title = findJsonStringValue(window, "novelName", "novel_name", "title", "name"),
                author = findJsonStringValue(window, "authorName", "author_name", "author", "penName"),
                detailUrl = "https://www.gongzicp.com/novel-$id.html"
            )
        }.toList()

        val fromApiJson = parseGongzicpSearchItemsFromApiJson(text)

        return (fromApiJson + fromJson + fromLinks)
            .filter { it.id.isNotBlank() && (it.title.isNotBlank() || it.author.isNotBlank()) }
            .distinctBy { it.detailUrl }
    }

    private fun fetchGongzicpInfoJson(id: String): JSONObject? {
        val urls = listOf(
            "$GONGZICP_WEB_API/novel/novelInfo?id=$id",
            "$GONGZICP_WEB_API/novel/novelInfo?novel_id=$id",
            "https://www.gongzicp.com/api/novel/$id",
            "https://www.gongzicp.com/api/novel/info?novel_id=$id",
            "https://www.gongzicp.com/api/novel/info?novelId=$id",
            "https://www.gongzicp.com/api/novel/getNovelInfo?novel_id=$id",
            "https://www.gongzicp.com/api/novel/getNovelInfo?novelId=$id"
        )
        return urls.asSequence()
            .mapNotNull { url -> runCatching { JSONObject(FetchHttpClient.getText(url)) }.getOrNull() }
            .firstOrNull { json ->
                parseGongzicpTitleFromJson(json).isNotBlank() ||
                    parseGongzicpIntroFromJson(json).isNotBlank() ||
                    parseGongzicpCoverFromJson(json, "https://www.gongzicp.com/").isNotBlank()
            }
    }

    private fun fetchGongzicpCatalogJson(id: String): JSONObject? {
        val urls = listOf(
            "$GONGZICP_WEB_API/novel/chapterGetList?nid=$id",
            "$GONGZICP_WEB_API/novel/chapterGetList?novel_id=$id",
            "$GONGZICP_WEB_API/novel/chapterGetList?id=$id"
        )
        return urls.asSequence()
            .mapNotNull { url -> runCatching { JSONObject(FetchHttpClient.getText(url)) }.getOrNull() }
            .firstOrNull { json -> gongzicpJsonObjectArrays(json).any { it.length() > 0 } }
    }

    private fun parseGongzicpSearchItemsFromApiJson(text: String): List<GongzicpSearchItem> {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return emptyList()
        return gongzicpJsonObjectArrays(json)
            .flatMap { array ->
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val id = item.gongzicpString("novel_id", "novelId", "nid", "id")
                        .let { extractGongzicpNovelId(it) ?: it }
                        .takeIf { it.matches(Regex("""\d+""")) }
                        ?: return@mapNotNull null
                    val title = item.gongzicpString("novel_name", "novelName", "bookName", "title", "name")
                    val author = item.gongzicpString(
                        "author_nickname",
                        "novel_author",
                        "authorName",
                        "author_name",
                        "author",
                        "penName",
                        "writerName"
                    )
                    GongzicpSearchItem(
                        id = id,
                        title = title,
                        author = author,
                        detailUrl = "https://www.gongzicp.com/novel-$id.html"
                    )
                }
            }
            .filter { it.title.isNotBlank() || it.author.isNotBlank() }
            .distinctBy { it.detailUrl }
    }

    private fun parseGongzicpTitleFromJson(json: JSONObject?): String {
        return json?.gongzicpString(
            "novel_name",
            "novelName",
            "bookName",
            "title",
            "name"
        ).orEmpty()
    }

    private fun parseGongzicpAuthorFromJson(json: JSONObject?): String {
        return json?.gongzicpString(
            "author_nickname",
            "novel_author",
            "authorName",
            "author_name",
            "author",
            "penName",
            "writerName"
        ).orEmpty()
    }

    private fun parseGongzicpIntroFromJson(json: JSONObject?): String {
        val lines = mutableListOf<String>()
        addUniqueLine(lines, parseGongzicpIntroBodyFromJson(json))
        addUniqueLine(lines, parseGongzicpTagsFromJson(json))
        return lines.joinToString("\n").compactLines()
    }

    private fun parseGongzicpIntroBodyFromJson(json: JSONObject?): String {
        val raw = json?.gongzicpRawString(
            "novel_info",
            "novelInfo",
            "novel_desc",
            "novel_intro",
            "novelIntro",
            "intro",
            "introduction",
            "description",
            "summary",
            "brief"
        ).orEmpty()
        return raw.cleanGongzicpIntro()
    }

    private fun parseGongzicpIntro(json: JSONObject?, text: String): String {
        val lines = mutableListOf<String>()
        val introText = parseGongzicpIntroBodyFromJson(json)
            .ifBlank { parseGongzicpIntroBody(text) }
        val tagLine = parseGongzicpTagsFromJson(json)
            .ifBlank { parseGongzicpTags(text) }
        addUniqueLine(lines, introText)
        addUniqueLine(lines, tagLine)
        return lines.joinToString("\n").compactLines()
    }

    private fun parseGongzicpTagsFromJson(json: JSONObject?): String {
        val tags = json?.gongzicpStringList(
            "tags",
            "tag",
            "tagList",
            "tag_list",
            "labels",
            "label",
            "category",
            "categories",
            "channel",
            "channels",
            "novelTags",
            "novel_tags"
        ).orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "展开" && it != "收起" }
            .distinct()
        return if (tags.isEmpty()) "" else "标签：${tags.joinToString(" ")}"
    }

    private fun parseGongzicpCoverFromJson(json: JSONObject?, baseUrl: String): String {
        val raw = json?.gongzicpString(
            "novel_cover",
            "novelCover",
            "cover",
            "coverUrl",
            "cover_url",
            "pic",
            "picUrl",
            "image"
        ).orEmpty()
        return raw.takeIf { it.isNotBlank() }?.let { absoluteUrl(it, baseUrl) }.orEmpty()
    }

    private fun parseGongzicpCatalog(json: JSONObject?, baseUrl: String): List<FetchedCatalogItem> {
        if (json == null) return emptyList()
        val arrays = gongzicpJsonObjectArrays(json)
        return arrays.asSequence()
            .flatMap { array ->
                (0 until array.length()).asSequence().mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val id = item.gongzicpString("chapter_id", "chapterId", "cid", "id")
                    val sequence = item.gongzicpString(
                        "chapter_order",
                        "chapterOrder",
                        "order",
                        "chapter_index",
                        "chapterIndex",
                        "index",
                        "sort"
                    )
                    val chapterTitle = item.gongzicpString(
                        "chapter_name",
                        "chapterName",
                        "chapter_title",
                        "chapterTitle",
                        "name",
                        "title"
                    )
                    val summary = item.gongzicpString(
                        "summary",
                        "chapter_info",
                        "chapterInfo",
                        "intro",
                        "description"
                    )
                    val type = item.gongzicpString("type", "chapter_type", "chapterType", "kind")
                    val isVolume = type.contains("volume", ignoreCase = true) ||
                        type.contains("卷") ||
                        item.optBoolean("is_volume", false) ||
                        item.optBoolean("isVolume", false)
                    val title = if (isVolume) {
                        chapterTitle
                    } else {
                        combineCatalogTitle(chapterTitle, summary)
                    }
                    if (title.isBlank()) return@mapNotNull null
                    val url = when {
                        id.isBlank() -> ""
                        id.matches(Regex("""\d+""")) -> "https://m.gongzicp.com/read-$id.html"
                        else -> absoluteUrl(id, baseUrl)
                    }
                    FetchedCatalogItem(
                        index = index + 1,
                        title = title,
                        url = url,
                        sequence = if (isVolume) "" else sequence,
                        chapterTitle = chapterTitle,
                        summary = summary,
                        isVolume = isVolume
                    )
                }
            }
            .toList()
    }

    private fun parseGongzicpTitle(text: String): String {
        return findJsonStringValue(text, "novelName", "novel_name", "bookName", "title", "name")
            .ifBlank { parseMeta(text, "og:title") }
            .ifBlank { parseMeta(text, "title") }
            .ifBlank {
                Regex("""<title>(.*?)</title>""", RegexOption.IGNORE_CASE)
                    .find(text)?.groupValues?.getOrNull(1)?.cleanHtmlText()?.substringBefore("_").orEmpty()
            }
    }

    private fun parseGongzicpAuthor(text: String): String {
        return findJsonStringValue(text, "authorName", "author_name", "author", "penName")
    }

    private fun parseGongzicpIntro(text: String): String {
        val lines = mutableListOf<String>()
        addUniqueLine(lines, parseGongzicpIntroBody(text))
        addUniqueLine(lines, parseGongzicpTags(text))
        return lines.joinToString("\n").compactLines()
    }

    private fun parseGongzicpIntroBody(text: String): String {
        return gongzicpIntroCandidates(text)
            .asSequence()
            .map { it.cleanGongzicpIntro() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

    private fun gongzicpIntroCandidates(text: String): List<String> {
        val htmlCandidates = Regex(
            """<([a-z0-9]+)\b[^>]*class=["'][^"']*(?:novel-info-text|novel-intro|info-text)[^"']*["'][^>]*>(.*?)</\1>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(text)
            .map { it.groupValues[2].cleanHtmlBlock() }
            .toList()
        val htmlContentCandidates = Regex(
            """<([a-z0-9]+)\b[^>]*(?:class=["'][^"']*(?:content|text)[^"']*["']|ref=["']textHtml["'])[^>]*>(.*?)</\1>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(text)
            .map { it.groupValues[2].cleanHtmlBlock() }
            .filter { it.length in 80..5000 }
            .sortedByDescending { it.length }
            .toList()
        val jsonIntro = findJsonRawStringValue(
            text,
            "novelIntro",
            "novel_intro",
            "intro",
            "introduction",
            "description",
            "summary",
            "brief"
        )
        return htmlCandidates +
            parseMeta(text, "description") +
            parseMeta(text, "og:description") +
            htmlContentCandidates +
            jsonIntro
    }

    private fun parseGongzicpTags(text: String): String {
        val windows = Regex("""class=["'][^"']*tag-list[^"']*["']""", RegexOption.IGNORE_CASE)
            .findAll(text)
            .map { match ->
                text.substring(match.range.first, (match.range.first + 3000).coerceAtMost(text.length))
            }
            .toList()
        val tags = windows
            .flatMap { window ->
                Regex(
                    """<(?:a|span|div)\b[^>]*>(.*?)</(?:a|span|div)>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                ).findAll(window).map { it.groupValues[1].cleanHtmlText() }.toList()
            }
            .map { it.trim() }
            .filter { value ->
                value.isNotBlank() &&
                    value.length <= 40 &&
                    value != "展开" &&
                    value != "收起" &&
                    !value.contains("作品简介") &&
                    !value.contains("作品目录")
            }
            .distinct()
            .toList()
        return if (tags.isEmpty()) "" else "标签：${tags.joinToString(" ")}"
    }

    private fun parseGongzicpCover(text: String, baseUrl: String): String {
        val raw = findJsonStringValue(
            text,
            "cover",
            "coverUrl",
            "cover_url",
            "novelCover",
            "novel_cover",
            "pic",
            "picUrl",
            "image"
        ).ifBlank { parseMeta(text, "og:image") }
        return raw.takeIf { it.isNotBlank() }?.let { absoluteUrl(it, baseUrl) }.orEmpty()
    }

    private fun List<GongzicpSearchItem>.sortForGongzicpMode(
        query: String,
        searchMode: String
    ): List<GongzicpSearchItem> {
        val normalizedQuery = query.normalizeSearchText()
        if (normalizedQuery.isBlank()) return this
        val selector: (GongzicpSearchItem) -> String = if (searchMode == "author") {
            { it.author.normalizeSearchText() }
        } else {
            { it.title.normalizeSearchText() }
        }
        val scoped = filter { selector(it).contains(normalizedQuery) }.ifEmpty { this }
        return scoped.sortedWith(
            compareByDescending<GongzicpSearchItem> { selector(it) == normalizedQuery }
                .thenByDescending { selector(it).contains(normalizedQuery) }
                .thenBy { it.title.length }
        )
    }

    private fun JSONObject.gongzicpString(vararg keys: String): String {
        keys.forEach { key ->
            optJsonScalarString(opt(key))
                .cleanHtmlText()
                .takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return findGongzicpJsonScalar(this, keys.toSet())
            ?.cleanHtmlText()
            .orEmpty()
    }

    private fun JSONObject.gongzicpRawString(vararg keys: String): String {
        keys.forEach { key ->
            optJsonScalarString(opt(key))
                .takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return findGongzicpJsonScalar(this, keys.toSet()).orEmpty()
    }

    private fun JSONObject.gongzicpStringList(vararg keys: String): List<String> {
        val result = mutableListOf<String>()
        collectGongzicpJsonStrings(this, keys.toSet(), result)
        return result.map { it.cleanHtmlText() }.filter { it.isNotBlank() }
    }

    private fun gongzicpJsonObjectArrays(json: JSONObject): List<JSONArray> {
        val arrays = mutableListOf<JSONArray>()
        collectGongzicpObjectArrays(json, arrays)
        return arrays.distinctBy { System.identityHashCode(it) }
    }

    private fun collectGongzicpObjectArrays(value: Any?, arrays: MutableList<JSONArray>) {
        when (value) {
            is JSONObject -> {
                val iterator = value.keys()
                while (iterator.hasNext()) {
                    val key = iterator.next()
                    val child = value.opt(key)
                    if (child is JSONArray && child.containsGongzicpObject()) arrays += child
                    collectGongzicpObjectArrays(child, arrays)
                }
            }
            is JSONArray -> {
                if (value.containsGongzicpObject()) arrays += value
                for (index in 0 until value.length()) collectGongzicpObjectArrays(value.opt(index), arrays)
            }
        }
    }

    private fun JSONArray.containsGongzicpObject(): Boolean {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val hasNovel = item.gongzicpString("novel_id", "novelId", "nid", "id").isNotBlank() &&
                item.gongzicpString("novel_name", "novelName", "bookName", "title", "name").isNotBlank()
            val hasChapter = item.gongzicpString("chapter_id", "chapterId", "cid", "id").isNotBlank() &&
                item.gongzicpString("chapter_name", "chapterName", "chapter_title", "chapterTitle", "name", "title").isNotBlank()
            if (hasNovel || hasChapter) return true
        }
        return false
    }

    private fun findGongzicpJsonScalar(value: Any?, keys: Set<String>): String? {
        return when (value) {
            is JSONObject -> {
                val iterator = value.keys()
                while (iterator.hasNext()) {
                    val key = iterator.next()
                    val child = value.opt(key)
                    if (keys.any { it.equals(key, ignoreCase = true) }) {
                        optJsonScalarString(child).takeIf { it.isNotBlank() }?.let { return it }
                    }
                    findGongzicpJsonScalar(child, keys)?.let { return it }
                }
                null
            }
            is JSONArray -> {
                for (index in 0 until value.length()) {
                    findGongzicpJsonScalar(value.opt(index), keys)?.let { return it }
                }
                null
            }
            else -> null
        }
    }

    private fun collectGongzicpJsonStrings(value: Any?, keys: Set<String>, result: MutableList<String>) {
        when (value) {
            is JSONObject -> {
                val iterator = value.keys()
                while (iterator.hasNext()) {
                    val key = iterator.next()
                    val child = value.opt(key)
                    if (keys.any { it.equals(key, ignoreCase = true) }) {
                        when (child) {
                            is JSONArray -> {
                                for (index in 0 until child.length()) {
                                    optJsonScalarString(child.opt(index)).takeIf { it.isNotBlank() }?.let(result::add)
                                }
                            }
                            else -> optJsonScalarString(child).takeIf { it.isNotBlank() }?.let(result::add)
                        }
                    }
                    collectGongzicpJsonStrings(child, keys, result)
                }
            }
            is JSONArray -> {
                for (index in 0 until value.length()) collectGongzicpJsonStrings(value.opt(index), keys, result)
            }
        }
    }

    private fun optJsonScalarString(value: Any?): String {
        return when (value) {
            null, JSONObject.NULL -> ""
            is JSONObject, is JSONArray -> ""
            else -> value.toString().decodeHtmlEntities().trim()
        }
    }

    companion object {
        private const val GONGZICP_WEB_API = "https://webapi.gongzicp.com"
    }
}

