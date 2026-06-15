package com.eteditor

internal data class JjwxcSearchItem(
    val novelId: String,
    val novelName: String,
    val authorName: String
)

internal data class GongzicpSearchItem(
    val id: String,
    val title: String,
    val author: String,
    val detailUrl: String
)

internal data class SosadSearchItem(
    val title: String,
    val author: String,
    val detailUrl: String
)

internal data class SearchChoiceResolution(
    val choice: FetchInfoSearchChoice? = null,
    val promptChoices: List<FetchInfoSearchChoice> = emptyList(),
    val skipReason: String? = null
)

internal fun preferredSearchChoiceByMetadata(
    choices: List<FetchInfoSearchChoice>,
    query: String,
    metadataTitle: String,
    metadataAuthor: String
): FetchInfoSearchChoice? {
    if (choices.size <= 1) return choices.firstOrNull()
    val normalizedAuthor = metadataAuthor.normalizeSearchText()
    if (normalizedAuthor.isBlank()) return null
    val authorMatches = choices.filter { choice ->
        choice.author.normalizeSearchText() == normalizedAuthor
    }
    if (authorMatches.size == 1) return authorMatches.first()
    val normalizedQuery = query.ifBlank { metadataTitle }.normalizeSearchText()
    val titleScopedChoices = authorMatches
        .filter { choice -> choice.title.normalizeSearchText() == normalizedQuery }
    return titleScopedChoices.singleOrNull()
}

internal fun resolveFetchInfoSearchChoiceByMetadata(
    choices: List<FetchInfoSearchChoice>,
    query: String,
    metadataTitle: String,
    metadataAuthor: String
): SearchChoiceResolution {
    if (choices.isEmpty()) return SearchChoiceResolution()
    val normalizedQuery = query.ifBlank { metadataTitle }.normalizeSearchText()
    val titleMatches = choices.filter { choice ->
        choice.title.normalizeSearchText() == normalizedQuery
    }
    if (titleMatches.isEmpty()) {
        return SearchChoiceResolution(skipReason = "没有匹配书名")
    }
    val normalizedAuthor = metadataAuthor.normalizeSearchText()
    if (normalizedAuthor.isBlank()) {
        return if (titleMatches.size == 1) {
            SearchChoiceResolution(choice = titleMatches.first())
        } else {
            SearchChoiceResolution(promptChoices = titleMatches)
        }
    }
    val authorMatches = titleMatches.filter { choice ->
        choice.author.normalizeSearchText() == normalizedAuthor
    }
    return when {
        authorMatches.size == 1 -> SearchChoiceResolution(choice = authorMatches.first())
        authorMatches.size > 1 -> SearchChoiceResolution(promptChoices = authorMatches)
        else -> SearchChoiceResolution(skipReason = "书名匹配但作者不匹配")
    }
}

internal fun distinctVisibleSearchChoices(choices: List<FetchInfoSearchChoice>): List<FetchInfoSearchChoice> {
    return choices.distinctBy { choice ->
        // 每本书是独立帖子，优先按网址去重，避免同名匿名/马甲作者被误判为重复而删除；
        // 仅在没有网址时才回退到「书名+作者」。
        choice.detailUrl.trim().ifBlank {
            choice.title.normalizeSearchText() + "::" + choice.author.normalizeSearchText()
        }
    }
}

internal fun needsJjwxcSearch(value: String): Boolean {
    if (value.isBlank()) return false
    if (extractJjwxcNovelId(value) != null) return false
    if (value.startsWith("http://", ignoreCase = true)) return false
    if (value.startsWith("https://", ignoreCase = true)) return false
    if (value.startsWith("onebook.php", ignoreCase = true)) return false
    if (value.matches(Regex("""\d+"""))) return false
    return true
}

internal fun needsGongzicpSearch(value: String): Boolean {
    if (value.isBlank()) return false
    if (extractGongzicpNovelId(value) != null) return false
    if (value.startsWith("http://", ignoreCase = true)) return false
    if (value.startsWith("https://", ignoreCase = true)) return false
    return true
}

internal fun normalizeGongzicpDetailUrl(value: String): String? {
    val query = value.trim()
    if (query.isBlank()) return null
    val id = extractGongzicpNovelId(query)
    return when {
        query.startsWith("http://", ignoreCase = true) || query.startsWith("https://", ignoreCase = true) -> query
        id != null -> "https://www.gongzicp.com/novel-$id.html"
        else -> null
    }
}

internal fun extractGongzicpNovelId(value: String): String? {
    return Regex("""(?:novel[-_/]?|novelid=|novel_id=)(\d+)""", RegexOption.IGNORE_CASE)
        .find(value)
        ?.groupValues
        ?.getOrNull(1)
        ?: value.takeIf { it.matches(Regex("""\d+""")) }
}

internal fun extractJjwxcNovelId(value: String): String? {
    return Regex("""(?:[?&]novelid=|novelid=)(\d+)""", RegexOption.IGNORE_CASE)
        .find(value)
        ?.groupValues
        ?.getOrNull(1)
}

internal fun extractJjwxcAuthor(value: String): String? {
    return Regex("""作者\s*[：:]\s*([^；;，,\n\r]+)""")
        .find(value)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

internal fun jjwxcSearchQueries(value: String): List<String> {
    val query = value.trim()
    return buildList {
        Regex("""《([^》]+)》""").find(query)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(::add)
        query
            .replace(Regex("""作者\s*[：:].*${'$'}"""), "")
            .trim()
            .trim('《', '》', '"', '\'', '“', '”')
            .takeIf { it.isNotBlank() }
            ?.let(::add)
        add(query)
    }.distinctBy { it.normalizeJjwxcSearchText() }
}

internal fun String.normalizeJjwxcSearchText(): String {
    return trim()
        .trim('《', '》', '"', '\'', '“', '”')
        .replace(Regex("""\s+"""), "")
}

internal fun String.normalizeSearchText(): String {
    return trim()
        .trim('《', '》', '"', '\'', '“', '”')
        .replace(Regex("""\s+"""), "")
        .lowercase()
}
