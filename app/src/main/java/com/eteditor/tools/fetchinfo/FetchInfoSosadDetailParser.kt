package com.eteditor

internal fun parseSosadIntroBlock(html: String): String {
    val hiddenBlocks = Regex(
        """<(script|style|nav|header|footer|form|button)\b.*?</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    val source = html.replace(hiddenBlocks, "")
    val patterns = listOf(
        """<([a-z0-9]+)\b[^>]+class=["'][^"']*article-body[^"']*["'][^>]*>(.*?)</\1>""",
        """<([a-z0-9]+)\b[^>]+class=["'][^"']*main-text[^"']*["'][^>]*>(.*?)</\1>""",
        """<article\b[^>]*>(.*?)</article>""",
        """<div[^>]+class=["'][^"']*(?:message-body|post-body|bbWrapper|thread-body|post-content|content)[^"']*["'][^>]*>(.*?)</div>""",
        """<section[^>]+class=["'][^"']*(?:message-body|post-body|thread-body|post-content|content)[^"']*["'][^>]*>(.*?)</section>"""
    )
    patterns.forEach { pattern ->
        Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(source)
            .mapNotNull { match -> match.groupValues.lastOrNull() }
            .firstOrNull { block -> block.cleanSosadBodyText().length >= 8 }
            ?.let { return it }
    }
    return Regex("""<body\b[^>]*>(.*?)</body>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .find(source)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()
}

internal fun parseSosadIntroText(html: String, introBlock: String): String {
    parseSosadProfileIntroText(html).takeIf { it.isNotBlank() }?.let { return it }
    if (introBlock.isBlank()) return ""
    val withoutCatalog = introBlock
        .replace(Regex("""<table\b.*?</table>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        .replace(Regex("""<ol\b.*?</ol>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        .replace(Regex("""<ul\b.*?</ul>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
    return withoutCatalog.cleanSosadBodyText()
        .lines()
        .filterNot { line -> isSosadCatalogNavigationText(line) }
        .joinToString("\n")
        .trim()
}

private fun parseSosadProfileIntroText(html: String): String {
    val lines = mutableListOf<String>()
    addUniqueLine(lines, parseSosadProfileIntroTags(html))
    addUniqueLine(lines, parseSosadMainText(html))
    return lines.joinToString("\n").compactLines()
}

private fun parseSosadProfileIntroTags(html: String): String {
    val tags = mutableListOf<String>()
    fun scanTagLinks(block: String) {
        Regex("""<a\b([^>]*)>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(block)
            .forEach { match ->
                val tag = match.value
                val href = attr(tag, "href")
                if (href.contains("/channels/", ignoreCase = true) ||
                    href.contains("/tag/", ignoreCase = true)
                ) {
                    val value = normalizeSosadIntroTag(
                        attr(tag, "title").ifBlank { match.groupValues[2].cleanHtmlText() }
                    )
                    if (value.isNotBlank() && !isSosadIntroTagIgnored(value) && value !in tags) {
                        tags += value
                    }
                }
            }
    }
    val blocks = (sosadElementBlocksByClass(html, "article-body") +
        sosadHtmlRegionByClass(html, "article-body"))
        .filter { it.isNotBlank() }
    blocks.forEach(::scanTagLinks)
    if (tags.isEmpty()) scanTagLinks(html)
    return tags.joinToString("、")
}

private fun parseSosadMainText(html: String): String {
    val blocks = sosadElementBlocksByClass(html, "main-text")
        .ifEmpty { listOf(sosadHtmlRegionByClass(html, "main-text")) }
        .filter { it.isNotBlank() }
    return blocks
        .mapNotNull { block ->
            val paragraphs = Regex(
                """<p\b[^>]*>(.*?)</p>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).findAll(block)
                .map { it.groupValues[1].cleanHtmlText() }
                .filter { it.isNotBlank() }
                .toList()
            paragraphs.joinToString("\n")
                .ifBlank { block.cleanSosadBodyText() }
                .compactLines()
                .takeIf { it.isNotBlank() }
        }
        .firstOrNull()
        .orEmpty()
}

private fun sosadElementBlocksByClass(html: String, className: String): List<String> {
    return Regex(
        """<([a-z0-9]+)\b[^>]+class=["'][^"']*${Regex.escape(className)}[^"']*["'][^>]*>(.*?)</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).findAll(html)
        .mapNotNull { it.groupValues.getOrNull(2) }
        .toList()
}

private fun sosadHtmlRegionByClass(html: String, className: String): String {
    val match = Regex(
        """<[a-z0-9]+\b[^>]+class=["'][^"']*${Regex.escape(className)}[^"']*["'][^>]*>""",
        RegexOption.IGNORE_CASE
    ).find(html) ?: return ""
    return html.substring(match.range.first, (match.range.first + 24000).coerceAtMost(html.length))
}

private fun normalizeSosadIntroTag(value: String): String {
    return when (val clean = value.cleanHtmlText().trim()) {
        "Original Novel" -> "原创小说"
        "Happy Ending" -> "HE"
        else -> clean
    }
}

private fun isSosadIntroTagIgnored(value: String): Boolean {
    return value in setOf("长篇", "中篇", "短篇", "完结", "连载", "暂停", "评鉴列表", "评荐列表")
}

internal fun parseSosadIntroCover(introBlock: String, baseUrl: String): String {
    if (introBlock.isBlank()) return ""
    return Regex("""<img\b([^>]*)>""", RegexOption.IGNORE_CASE)
        .findAll(introBlock)
        .mapNotNull { match ->
            val tag = match.value
            val raw = listOf(
                attr(tag, "src"),
                attr(tag, "data-src"),
                attr(tag, "data-original"),
                attr(tag, "data-url"),
                attr(tag, "srcset").substringBefore(' ')
            ).firstOrNull { it.isNotBlank() } ?: return@mapNotNull null
            absoluteUrl(raw.decodeHtmlEntities().trim(), baseUrl)
        }
        .firstOrNull { url ->
            val lower = url.lowercase()
            !lower.startsWith("data:") &&
                isSosadAllowedHttpsUrl(url) &&
                !lower.contains("avatar") &&
                !lower.contains("emoji") &&
                !lower.contains("emoticon") &&
                !lower.contains("smilie") &&
                !lower.contains("icon")
        }
        .orEmpty()
}

internal fun parseSosadTitle(html: String): String {
    return Regex(
        """<(?:h1|[^>]+class=["'][^"']*(?:thread-title|title)[^"']*["'][^>]*)>(.*?)</[^>]+>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).find(html)?.groupValues?.getOrNull(1)?.cleanHtmlText()?.takeIf { it.isNotBlank() }
        ?: Regex("""<title>(.*?)</title>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.cleanHtmlText()
            ?.substringBefore(" - ")
            .orEmpty()
}

internal fun parseSosadPostTitle(html: String): String {
    return Regex(
        """<h[1-6]\b[^>]+class=["'][^"']*chapter-title[^"']*["'][^>]*>(.*?)</h[1-6]>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).findAll(html)
        .map { match -> match.groupValues.getOrNull(1).orEmpty().cleanHtmlText().cleanSosadCatalogText() }
        .firstOrNull { it.isNotBlank() && !isSosadUiText(it) }
        ?: Regex(
            """<strong\b[^>]*class=["'][^"']*\bh[1-6]\b[^"']*["'][^>]*>(.*?)</strong>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(html)
            .map { match -> match.groupValues.getOrNull(1).orEmpty().cleanHtmlText().cleanSosadCatalogText() }
            .firstOrNull { it.isNotBlank() && !isSosadUiText(it) }
            .orEmpty()
}
