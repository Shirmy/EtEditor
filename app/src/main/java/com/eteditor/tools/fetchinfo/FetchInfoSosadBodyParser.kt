package com.eteditor

internal fun parseSosadBodyDetail(html: String, baseUrl: String): FetchedSosadBody {
    val scopedHtml = sosadScopedBodyHtml(html, baseUrl)
    val block = sosadBodyHtmlBlock(scopedHtml, baseUrl)
    val text = block.cleanSosadBodyText()
    val baseBlocks = block.sosadBodyBlocks(baseUrl).ifEmpty {
        text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line -> FetchedSosadBodyBlock(text = line) }
    }
    val blocks = ensureSosadSpecialBlocks(baseBlocks, scopedHtml)
    return FetchedSosadBody(text = text, blocks = blocks)
}

private fun sosadScopedBodyHtml(html: String, baseUrl: String): String {
    return sosadPostId(baseUrl)
        ?.let { postId -> sosadPostScopedHtml(html, postId) }
        ?: html
}

private fun ensureSosadSpecialBlocks(
    blocks: List<FetchedSosadBodyBlock>,
    scopedHtml: String
): List<FetchedSosadBodyBlock> {
    val specials = sosadBodySpecialBlockRegex
        .findAll(scopedHtml.normalizeSosadBodyMarkup())
        .filterNot { match -> isSosadReactionBlock(match.value) }
        .mapNotNull { match -> sosadBodySpecialBlockText(match.value) }
        .mapNotNull { special ->
            val text = special.cleaned.lines.joinToString("\n").trim()
            text.takeIf { it.isNotBlank() }
                ?.let { FetchedSosadBodyBlock(text = it, cssClass = special.cssClass) }
        }
        .distinctBy { it.cssClass to it.text }
        .toList()
    if (specials.isEmpty()) return blocks
    val specialTexts = buildSet {
        specials.forEach { special ->
            add(special.text.trim())
            special.text.split('\n').forEach { line -> add(line.trim()) }
        }
    }
    val filtered = blocks.filterNot { block ->
        block.text.isNotBlank() && block.text.trim() in specialTexts
    }
    val warnings = specials.filter { it.cssClass == SOSAD_BODY_CSS_SYS }
    val authorNotes = specials.filter { it.cssClass == SOSAD_BODY_CSS_AUTHOR_NOTE }
    return warnings + filtered + authorNotes
}

private fun sosadBodyHtmlBlock(scopedHtml: String, baseUrl: String): String {
    val candidates = mutableListOf<Pair<String, String>>()
    sosadBodyCandidateBlocks(scopedHtml)
        .map { block -> block to block.cleanSosadBodyText() }
        .filter { (block, text) ->
            text.length >= 10 ||
                Regex("""<img\b""", RegexOption.IGNORE_CASE).containsMatchIn(block)
        }
        .forEach { candidate -> candidates += candidate }
    if (candidates.isNotEmpty()) {
        return candidates.maxByOrNull { (block, text) ->
            text.length + Regex("""<img\b""", RegexOption.IGNORE_CASE).findAll(block).count() * 10
        }?.first.orEmpty()
    }
    return Regex("""<body\b[^>]*>(.*?)</body>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .find(scopedHtml)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()
}

private fun sosadBodyCandidateBlocks(scopedHtml: String): List<String> {
    return listOf(scopedHtml, scopedHtml.normalizeSosadBodyMarkup())
        .distinct()
        .flatMap { source ->
            sosadBodyElementBlocks(source) +
                sosadBodyClassRegions(source) +
                sosadChapterTitleRegions(source)
        }
        .map { block -> block.trimToSosadChapterBodyStart() }
        .filter { it.isNotBlank() }
        .distinct()
}

private fun sosadBodyElementBlocks(source: String): List<String> {
    val articleBlocks = Regex(
        """<article\b[^>]*>(.*?)</article>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).findAll(source)
        .mapNotNull { match -> match.groupValues.getOrNull(1) }
        .toList()
    val bodyClassBlocks = Regex(
        """<([a-z0-9]+)\b[^>]+class=["'][^"']*(?:message-body|post-body|bbWrapper|thread-body|chapter-content|post-content|content)[^"']*["'][^>]*>(.*?)</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).findAll(source)
        .mapNotNull { match -> match.groupValues.getOrNull(2) }
        .toList()
    return articleBlocks + bodyClassBlocks
}

private fun sosadBodyClassRegions(source: String): List<String> {
    return Regex(
        """<[a-z0-9]+\b[^>]+class=["'][^"']*(?:message-body|post-body|bbWrapper|thread-body|chapter-content|post-content)[^"']*["'][^>]*>""",
        RegexOption.IGNORE_CASE
    ).findAll(source)
        .map { match ->
            val end = sosadBodyRegionEnd(source, match.range.last + 1)
            source.substring(match.range.first, end)
        }
        .toList()
}

private fun sosadChapterTitleRegions(source: String): List<String> {
    return sosadChapterTitleStartRegex.findAll(source)
        .map { match ->
            val end = sosadBodyRegionEnd(source, match.range.last + 1)
            source.substring(match.range.first, end)
        }
        .toList()
}

private fun sosadBodyRegionEnd(source: String, startIndex: Int): Int {
    return sosadBodyRegionStopRegex.find(source, startIndex)
        ?.range
        ?.first
        ?: source.length
}

private fun String.trimToSosadChapterBodyStart(): String {
    val match = sosadChapterTitleStartRegex.find(this) ?: return this
    val titleEnd = Regex("""</h[1-6]\s*>""", RegexOption.IGNORE_CASE)
        .find(this, match.range.last + 1)
        ?.range
        ?.last
        ?.plus(1)
    return substring(titleEnd ?: match.range.first)
}

private val sosadChapterTitleStartRegex = Regex(
    """<h[1-6]\b[^>]+class=["'][^"']*chapter-title[^"']*["'][^>]*>""",
    RegexOption.IGNORE_CASE
)

private val sosadBodyRegionStopRegex = Regex(
    """<(?:nav|form)\b|<[a-z0-9]+\b[^>]+class=["'][^"']*(?:message-footer|post-footer|message-attribution|message-actionBar|reactions|signature|share|pagination|pageNav|breadcrumb)[^"']*["']""",
    RegexOption.IGNORE_CASE
)

private fun sosadPostScopedHtml(html: String, postId: String): String? {
    val idMatch = Regex("""\bid\s*=\s*["']post${Regex.escape(postId)}["']""", RegexOption.IGNORE_CASE)
        .find(html)
        ?: return null
    val start = html.lastIndexOf("<div", idMatch.range.first).takeIf { it >= 0 } ?: idMatch.range.first
    val nextPost = Regex("""<div\b[^>]*\bid\s*=\s*["']post\d+["']""", RegexOption.IGNORE_CASE)
        .find(html, idMatch.range.last + 1)
        ?.range
        ?.first
        ?: html.length
    return html.substring(start, nextPost)
}

internal fun String.cleanSosadBodyText(): String {
    return cleanSosadBodyTextLines().lines.joinToString("\n").trim()
}

private data class SosadCleanBodyLines(
    val lines: List<String>,
    val stopped: Boolean
)

private fun String.cleanSosadBodyTextLines(): SosadCleanBodyLines {
    val hiddenBlocks = Regex(
        """<(script|style|nav|header|footer|form|button)\b.*?</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    val lines = mutableListOf<String>()
    var stopped = false
    normalizeSosadBodyMarkup()
        .replace(hiddenBlocks, "")
        .replace(sosadHiddenSpanRegex, "")
        .cleanHtmlBlock()
        .lines()
        .map { line -> line.trim() }
        .forEach { line ->
            if (stopped) return@forEach
            if (line.isBlank()) return@forEach
            if (isSosadBodyStopLine(line)) {
                stopped = true
                return@forEach
            }
            if (
                !isSosadUiText(line) &&
                !line.equals("登录", ignoreCase = true) &&
                !line.equals("注册", ignoreCase = true) &&
                !line.contains("cookie", ignoreCase = true)
            ) {
                lines.addSosadBodyLine(line)
            }
        }
    return SosadCleanBodyLines(lines = lines, stopped = stopped)
}

private fun String.sosadBodyBlocks(baseUrl: String): List<FetchedSosadBodyBlock> {
    val hiddenBlocks = Regex(
        """<(script|style|nav|header|footer|form|button)\b.*?</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    val source = normalizeSosadBodyMarkup()
        .replace(hiddenBlocks, "")
        .replace(sosadHiddenSpanRegex, "")
    val result = mutableListOf<FetchedSosadBodyBlock>()
    val seenImageUrls = mutableSetOf<String>()
    var cursor = 0
    var stopped = false

    fun appendText(segment: String): Boolean {
        val cleaned = segment.cleanSosadBodyTextLines()
        cleaned.lines.forEach { line -> result += FetchedSosadBodyBlock(text = line) }
        return cleaned.stopped
    }

    for (match in sosadBodyBlockTokenRegex.findAll(source)) {
        if (appendText(source.substring(cursor, match.range.first))) {
            stopped = true
            break
        }
        val token = match.value
        if (isSosadReactionBlock(token)) {
            cursor = match.range.last + 1
            continue
        }
        val specialBlock = sosadBodySpecialBlockText(token)
        val imageUrl = if (specialBlock == null) sosadBodyImageUrl(token, baseUrl) else ""
        if (specialBlock != null) {
            val text = specialBlock.cleaned.lines.joinToString("\n").trim()
            if (text.isNotBlank()) {
                result += FetchedSosadBodyBlock(text = text, cssClass = specialBlock.cssClass)
            }
            if (specialBlock.cleaned.stopped) {
                stopped = true
                break
            }
        } else if (imageUrl.isNotBlank()) {
            val imageKey = imageUrl.trim().lowercase()
            if (seenImageUrls.add(imageKey)) {
                result += FetchedSosadBodyBlock(imageUrl = imageUrl)
            }
        } else if (appendText(token)) {
            stopped = true
            break
        }
        cursor = match.range.last + 1
    }
    if (!stopped) appendText(source.substring(cursor))
    return result
}

private data class SosadSpecialBodyBlock(
    val cleaned: SosadCleanBodyLines,
    val cssClass: String
)

private fun sosadBodySpecialBlockText(tag: String): SosadSpecialBodyBlock? {
    val token = tag.trim()
    val cssClass = sosadBodySpecialCssClass(token) ?: return null
    val cleaned = token.cleanSosadBodyTextLines()
    if (cleaned.lines.isEmpty() && !cleaned.stopped) return null
    return SosadSpecialBodyBlock(cleaned = cleaned, cssClass = cssClass)
}

private fun sosadBodySpecialCssClass(tag: String): String? {
    if (!sosadBodySpecialBlockRegex.matches(tag)) return null
    val classes = attr(tag, "class")
        .lowercase()
        .split(Regex("""\s+"""))
        .filter { it.isNotBlank() }
        .toSet()
    return when {
        classes.containsAll(setOf("text-center", "grayout", "warning-tag")) -> SOSAD_BODY_CSS_SYS
        classes.containsAll(setOf("text-left", "grayout")) -> SOSAD_BODY_CSS_AUTHOR_NOTE
        else -> null
    }
}

// 废文网"新鲜表态"(点赞名单)和"新鲜打赏"(打赏名单)块与作者的话同样使用 text-left+grayout，
// 仅靠 class 区分不开，这里再按内容特征识别：以"新鲜表态"或"新鲜打赏"开头，
// 或主体是指向 /users/数字 的链接（去掉链接后几乎无正文）。
private fun isSosadReactionBlock(tag: String): Boolean {
    val token = tag.trim()
    val plain = token.sosadPlainLineText().replace(Regex("""\s+"""), "")
    if (plain.startsWith("新鲜表态") || plain.startsWith("新鲜打赏")) return true
    val userLinkCount = sosadReactionUserLinkRegex.findAll(token).count()
    if (userLinkCount < 2) return false
    val withoutLinks = token
        .replace(sosadAnchorRegex, "")
        .sosadPlainLineText()
        .replace(Regex("""\s+"""), "")
        .removePrefix("新鲜表态：")
        .removePrefix("新鲜表态")
        .removePrefix("新鲜打赏：")
        .removePrefix("新鲜打赏")
    return withoutLinks.length <= 4
}

private val sosadReactionUserLinkRegex = Regex(
    """href\s*=\s*["'][^"']*/users/\d+""",
    RegexOption.IGNORE_CASE
)

private val sosadAnchorRegex = Regex(
    """<a\b[^>]*>.*?</a>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)

private fun sosadBodyImageUrl(tag: String, baseUrl: String): String {
    val token = tag.trim()
    if (token.startsWith("<img", ignoreCase = true)) {
        return sosadBodyImageTagUrl(token, baseUrl)
    }
    if (token.startsWith("<a", ignoreCase = true)) {
        return sosadBodyLinkedImageUrl(token, baseUrl)
    }
    sosadBodyImageUrlFromObfuscatedText(token)?.let { return it }
    return sosadBodyImageUrlFromRaw(token, baseUrl, requireImageExtension = true)
}

private fun sosadBodyImageTagUrl(tag: String, baseUrl: String): String {
    val raw = listOf(
        attr(tag, "src"),
        attr(tag, "data-src"),
        attr(tag, "data-original"),
        attr(tag, "data-url"),
        attr(tag, "srcset").substringBefore(',').substringBefore(' ')
    ).firstOrNull { it.isNotBlank() } ?: return ""
    return sosadBodyImageUrlFromRaw(raw, baseUrl, requireImageExtension = false)
}

private fun sosadBodyLinkedImageUrl(tag: String, baseUrl: String): String {
    return sosadBodyImageUrlFromRaw(attr(tag, "href"), baseUrl, requireImageExtension = true)
}

private fun sosadBodyImageUrlFromRaw(
    raw: String,
    baseUrl: String,
    requireImageExtension: Boolean
): String {
    val decoded = raw.decodeHtmlEntities().trim()
    val url = if (baseUrl.isBlank()) decoded else absoluteUrl(decoded, baseUrl)
    val lower = url.lowercase()
    return url.takeIf {
        isSosadAllowedImageUrl(url) &&
            (!requireImageExtension || lower.isSupportedSosadImageUrlText()) &&
            !lower.startsWith("data:") &&
            !lower.contains("avatar") &&
            !lower.contains("emoji") &&
            !lower.contains("emoticon") &&
            !lower.contains("smilie") &&
            !lower.contains("icon")
    }.orEmpty()
}

private fun sosadBodyImageUrlFromObfuscatedText(raw: String): String? {
    val match = sosadBodyObfuscatedIbbImageRegex.find(raw) ?: return null
    val imageId = match.groupValues.getOrNull(1).orEmpty().trim()
    val name = match.groupValues.getOrNull(2).orEmpty()
        .trim()
        .replace(Regex("""\s+"""), "-")
    val extension = match.groupValues.getOrNull(3).orEmpty().lowercase()
    if (imageId.isBlank() || name.isBlank() || extension.isBlank()) return null
    val url = "https://i.ibb.co/$imageId/$name.$extension"
    return url.takeIf(::isSosadAllowedImageUrl)
}

private fun String.isSupportedSosadImageUrlText(): Boolean {
    return Regex("""\.(?:jpe?g|png|webp|gif)(?:[?#].*)?$""", RegexOption.IGNORE_CASE).containsMatchIn(this)
}

private val sosadBodyBlockTokenRegex = Regex(
    """<([a-z0-9]+)\b[^>]+class=["'][^"']*\bgrayout\b[^"']*["'][^>]*>.*?</\1>|<a\b[^>]*\bhref\s*=\s*["'][^"']+["'][^>]*>.*?</a>|<img\b[^>]*>|https://[^\s"'<>]+\.(?:jpe?g|png|webp|gif)(?:\?[^\s"'<>]*)?|https\s+i\s+ibb\s+co\s+[^\s"'<>]+\s+[^\r\n<>]+?\s+(?:jpe?g|png|webp|gif)\b""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)

private val sosadBodySpecialBlockRegex = Regex(
    """<([a-z0-9]+)\b[^>]+class=["'][^"']*\bgrayout\b[^"']*["'][^>]*>.*?</\1>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)

private val sosadBodyObfuscatedIbbImageRegex = Regex(
    """https\s+i\s+ibb\s+co\s+([^\s"'<>]+)\s+([^\r\n<>]+?)\s+(jpe?g|png|webp|gif)\b""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)

private fun String.normalizeSosadBodyMarkup(): String {
    return if (sosadEscapedBodyMarkupRegex.containsMatchIn(this)) decodeHtmlEntities() else this
}

private val sosadEscapedBodyMarkupRegex = Regex(
    """&lt;/?(?:p|br|h[1-6]|a|img|div|section|article)\b""",
    RegexOption.IGNORE_CASE
)

private val sosadHiddenSpanRegex = Regex(
    """<span\b[^>]*\bclass\s*=\s*["'][^"']*\bhidden\b[^"']*["'][^>]*>.*?</span>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)

private fun MutableList<String>.addSosadBodyLine(line: String) {
    val clean = line.trim()
    if (isNotEmpty() && clean.startsWith("Warning", ignoreCase = true)) {
        this[lastIndex] = "${this[lastIndex]} $clean".replace(Regex("""\s{2,}"""), " ").trim()
    } else {
        add(clean)
    }
}

private fun isSosadBodyStopLine(line: String): Boolean {
    return line.sosadPlainLineText().replace(Regex("""\s+"""), "") == "进入论坛模式"
}

private fun String.sosadPlainLineText(): String {
    return decodeHtmlEntities()
        .replace(Regex("""</?[^>]+>"""), "")
        .trim()
}

internal fun String.cleanSosadCatalogText(): String {
    return compactLines()
        .lines()
        .map { it.trim() }
        .filter { it.isNotBlank() && !isSosadUiText(it) }
        .joinToString(" ")
        .replace(Regex("""\s{2,}"""), " ")
        .trim()
}

internal fun isSosadUiText(value: String): Boolean {
    val compact = value
        .replace(Regex("""\s+"""), "")
        .trim()
    if (compact.isBlank()) return true
    return compact in setOf(
        "展开",
        "收起",
        "展开全部",
        "点击展开全文",
        "进入论坛",
        "进入论坛模式",
        "进入讨论",
        "进入帖子",
        "查看全部",
        "查看更多",
        "更多"
    )
}

internal fun isSosadCatalogNavigationText(value: String): Boolean {
    val compact = value
        .replace(Regex("""\s+"""), "")
        .trim()
    return isSosadUiText(compact) ||
        compact in setOf("首页", "上一页", "下一页", "下页", "末页") ||
        compact.matches(Regex("""(?:第)?\d+(?:页)?"""))
}
