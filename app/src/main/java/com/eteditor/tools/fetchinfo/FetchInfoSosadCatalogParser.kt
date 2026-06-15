package com.eteditor

internal fun fetchCompleteSosadCatalog(
    initialHtml: String,
    detailUrl: String
): List<FetchedCatalogItem> {
    return mergeSosadCatalogItems(parseSosadCatalog(initialHtml, detailUrl))
}

private fun parseSosadCatalog(html: String, baseUrl: String): List<FetchedCatalogItem> {
    val seen = mutableSetOf<String>()
    val rows = feiwenCatalogRows(html)
    return buildList {
        rows.forEach { row ->
            val fields = splitFeiwenFields(row)
            if (!isFeiwenCatalogFields(fields)) return@forEach
            val title = fields.getOrNull(0).orEmpty().cleanSosadCatalogText()
            val summary = fields.getOrNull(1).orEmpty().cleanSosadCatalogText()
            if (title.isBlank() || isSosadUiText(title)) return@forEach
            val words = fields.getOrNull(2).orEmpty()
            val published = fields.getOrNull(3).orEmpty()
            val modified = fields.getOrNull(4).orEmpty()
            val key = listOf(title, summary, words, published, modified).joinToString("::")
            if (!seen.add(key)) return@forEach
            val url = Regex(
                """href=["']([^"']*(?:/threads/(?:[^"'/?#]+\.)?\d+/posts/\d+|/posts/\d+)[^"']*)["']""",
                RegexOption.IGNORE_CASE
            ).find(row)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { absoluteUrl(it, baseUrl) }
                ?.takeIf(::isSosadAllowedHttpsUrl)
                .orEmpty()
            add(
                FetchedCatalogItem(
                    index = size + 1,
                    title = combineCatalogTitle(title, summary),
                    url = url,
                    sequence = (size + 1).toString(),
                    chapterTitle = title,
                    summary = summary
                )
            )
        }
        if (isEmpty()) {
            Regex(
                """<a[^>]+href=["']([^"']*/threads/(?:[^"'/?#]+\.)?\d+/posts/\d+[^"']*)["'][^>]*>(.*?)</a>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).findAll(html).forEach { match ->
                val title = match.groupValues[2].cleanHtmlText().cleanSosadCatalogText()
                val url = absoluteUrl(match.groupValues[1], baseUrl)
                if (title.isNotBlank() && !isSosadUiText(title) && isSosadAllowedHttpsUrl(url) && seen.add(url)) {
                    add(FetchedCatalogItem(index = size + 1, title = title, url = url, sequence = (size + 1).toString(), chapterTitle = title))
                }
            }
        }
    }
}

private fun mergeSosadCatalogItems(items: List<FetchedCatalogItem>): List<FetchedCatalogItem> {
    val seen = mutableSetOf<String>()
    return items.mapNotNull { item ->
        val title = item.title.cleanSosadCatalogText()
        val chapterTitle = item.chapterTitle.cleanSosadCatalogText()
        val summary = item.summary.cleanSosadCatalogText()
        if (!item.isVolume && title.isBlank() && chapterTitle.isBlank()) return@mapNotNull null
        if ((title.isNotBlank() && isSosadUiText(title)) ||
            (chapterTitle.isNotBlank() && isSosadUiText(chapterTitle))
        ) {
            return@mapNotNull null
        }
        val key = item.url.takeIf { it.isNotBlank() }
            ?: listOf(title, chapterTitle, summary).joinToString("::").lowercase()
        if (!seen.add(key)) return@mapNotNull null
        item.copy(
            index = seen.size,
            title = title.ifBlank { combineCatalogTitle(chapterTitle, summary) },
            sequence = seen.size.toString(),
            chapterTitle = chapterTitle.ifBlank { title },
            summary = summary
        )
    }
}

private fun feiwenCatalogRows(html: String): List<String> {
    val rows = mutableListOf<String>()
    listOf("tr", "li", "article", "section").forEach { tag ->
        Regex(
            """<$tag\b[^>]*>.*?</$tag>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(html).forEach { rows += it.value }
    }
    Regex(
        """<([a-z0-9]+)\b[^>]*class=["'][^"']*(?:chapter|chapter-item|thread-chapter|list-group-item)[^"']*["'][^>]*>.*?</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).findAll(html).forEach { rows += it.value }
    return rows
        .distinct()
}

private fun splitFeiwenFields(row: String): List<String> {
    val cells = Regex(
        """<t[dh]\b[^>]*>(.*?)</t[dh]>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).findAll(row)
        .map { it.groupValues[1].cleanHtmlBlock() }
        .toList()
        // 表格单元格按列定位，中间的空列（如空概要）要保留占位，
        // 否则列数不足 4 会被误判为非章节行而漏掉该章；只丢弃行首的结构性空单元格。
        .dropWhile { it.isBlank() }
    if (cells.size >= 4) return cells

    val direct = Regex(
        """<([a-z0-9]+)\b[^>]*>(.*?)</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).findAll(row)
        .map { it.groupValues[2].cleanHtmlBlock() }
        .filter { it.isNotBlank() }
        .toList()
    if (direct.size >= 4) return direct

    return row.cleanHtmlBlock()
        .split(Regex("""\n+|\t+|\s{2,}"""))
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

private fun isFeiwenCatalogFields(fields: List<String>): Boolean {
    if (fields.size < 4) return false
    if (isFeiwenHeader(fields)) return false
    val title = fields.firstOrNull().orEmpty()
    if (title.isBlank() || title.length > 80) return false
    val joined = fields.joinToString(" ")
    val hasWords = fields.any { field ->
        field.matches(Regex("""^\d+${'$'}""")) || field.matches(Regex("""^\d+\s*字.*"""))
    }
    val dateCount = Regex("""\d{4}[-/]\d{1,2}[-/]\d{1,2}|\d{1,2}[-/]\d{1,2}|\d{1,2}:\d{2}""")
        .findAll(joined)
        .count()
    return hasWords && dateCount >= 1
}

private fun isFeiwenHeader(fields: List<String>): Boolean {
    val joined = fields.joinToString(" ")
    return listOf("章节名", "概要", "字数", "发布时间", "最后修改").any { token ->
        joined.contains(token)
    }
}
