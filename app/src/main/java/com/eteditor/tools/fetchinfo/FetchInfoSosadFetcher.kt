package com.eteditor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SosadFetcher : FetchInfoFetcher {
    override val source: String = FetchInfoSources.SOSAD

    override suspend fun searchChoices(
        parameters: FetchInfoParameters,
        onProgress: FetchInfoProgress
    ): List<FetchInfoSearchChoice> = withContext(Dispatchers.IO) {
        val query = upgradeSosadHttpScheme(parameters.query)
        if (!needsSosadSearch(query)) return@withContext emptyList()
        onProgress("搜索中...")
        val headers = parameters.authHeaders()
        val items = searchSosadItems(
            query = query,
            headers = headers,
            searchMode = parameters.searchMode,
            expectedAuthor = parameters.expectedAuthor,
            onProgress = onProgress
        )
        items.map { item ->
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
        val query = upgradeSosadHttpScheme(parameters.query)
        val headers = parameters.authHeaders()
        if (isSosadPostUrl(query)) {
            return@withContext fetchSingleSosadPost(source, parameters, query, headers, onProgress)
        }
        if (!query.startsWith("https://", ignoreCase = true) || isSosadSearchUrl(query)) {
            onProgress("搜索中...")
        }
        val detailUrl = resolveSosadDetailUrl(query, headers, parameters.expectedAuthor, onProgress)
        onProgress("正在读取详情页")
        val html = FetchHttpClient.getText(detailUrl, headers, ::isSosadSameHostHttpsRedirect)
        val introBlock = if (parameters.fetchIntro || parameters.fetchCover) {
            onProgress(if (parameters.fetchIntro) "正在抓取简介" else "正在抓取封面")
            parseSosadIntroBlock(html)
        } else {
            ""
        }
        val catalog = if (parameters.fetchCatalog) {
            onProgress("正在抓取目录")
            fetchCompleteSosadCatalog(html, detailUrl)
        } else {
            emptyList()
        }
        FetchedInfo(
            source = source,
            query = query,
            resolvedUrl = detailUrl,
            title = parseSosadTitle(html),
            author = parseSosadSearchAuthor(html),
            intro = if (parameters.fetchIntro) parseSosadIntroText(html, introBlock) else "",
            coverUrl = if (parameters.fetchCover) parseSosadIntroCover(introBlock, detailUrl) else "",
            catalog = catalog
        )
    }

    suspend fun fetchBodyDetailsForUrls(
        parameters: FetchInfoParameters,
        urls: List<String>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Map<String, FetchedSosadBody> = withContext(Dispatchers.IO) {
        val headers = parameters.authHeaders()
        val uniqueUrls = urls
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { requireSosadAllowedHttpsUrl(it, "废文正文链接") }
            .distinct()
        if (uniqueUrls.isEmpty()) error("没有可抓取的废文正文链接")
        val total = uniqueUrls.size
        buildMap {
            uniqueUrls.forEachIndexed { index, url ->
                put(
                    url,
                    parseSosadBodyDetail(
                        FetchHttpClient.getText(url, headers, ::isSosadSameHostHttpsRedirect),
                        url
                    )
                )
                onProgress(index + 1, total)
            }
        }
    }
}

private suspend fun fetchSingleSosadPost(
    source: String,
    parameters: FetchInfoParameters,
    postUrl: String,
    headers: Map<String, String>,
    onProgress: FetchInfoProgress
): FetchedInfo {
    val safePostUrl = requireSosadAllowedHttpsUrl(postUrl, "废文正文链接")
        .takeIf(::isSosadPostUrl)
        ?: error("废文正文链接必须是帖子页")
    onProgress("正在读取正文")
    val html = FetchHttpClient.getText(safePostUrl, headers, ::isSosadSameHostHttpsRedirect)
    val title = parseSosadPostTitle(html)
        .ifBlank { parseSosadTitle(html) }
        .ifBlank { "废文正文" }
    val catalog = if (parameters.fetchCatalog) {
        listOf(
            FetchedCatalogItem(
                index = 1,
                title = title,
                url = safePostUrl,
                sequence = "1",
                chapterTitle = title
            )
        )
    } else {
        emptyList()
    }
    return FetchedInfo(
        source = source,
        query = parameters.query.trim(),
        resolvedUrl = safePostUrl,
        title = title,
        author = parseSosadSearchAuthor(html),
        intro = "",
        coverUrl = "",
        catalog = catalog
    )
}

private fun FetchInfoParameters.authHeaders(): Map<String, String> {
    val cookie = normalizeSosadCookie(authCookie)
    return if (cookie.isBlank()) emptyMap() else mapOf("Cookie" to cookie)
}
