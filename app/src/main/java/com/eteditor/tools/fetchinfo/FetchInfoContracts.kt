package com.eteditor

internal const val FETCH_INFO_PARAM_SOURCE = "fetch_source"
internal const val FETCH_INFO_PARAM_QUERY = "fetch_query"
internal const val FETCH_INFO_PARAM_CONTENT = "fetch_content"
internal const val FETCH_INFO_PARAM_AUTH_COOKIE = "fetch_auth_cookie"
internal const val FETCH_INFO_PARAM_CATALOG_FILTER = "catalog_filter"
internal const val FETCH_INFO_PARAM_CATALOG_FILTER_ENABLED = "catalog_filter_enabled"
internal const val FETCH_INFO_PARAM_AUTO_TITLE_FORMAT = "fetch_auto_title_format"
internal const val FETCH_INFO_PARAM_INTRO_FILTER = "intro_filter"
internal const val FETCH_INFO_PARAM_INTRO_TARGET = "intro_target"
internal const val FETCH_INFO_SOURCE_JJWXC = "jjwxc"
internal const val FETCH_INFO_SOURCE_GONGZICP = "gongzicp"
internal const val FETCH_INFO_SOURCE_SOSAD = "sosad"
internal const val FETCH_INFO_SEARCH_TITLE = "title"
internal const val FETCH_INFO_SEARCH_AUTHOR = "author"
internal const val FETCH_INFO_SEARCH_KEYWORD = "keyword"
internal const val FETCH_INFO_CONTENT_CATALOG = "catalog"
internal const val FETCH_INFO_CONTENT_INTRO = "intro"
internal const val FETCH_INFO_CONTENT_COVER = "cover"
internal const val DEFAULT_FETCH_INFO_INTRO_TARGET = "OEBPS/Text/Section0002.html"

object FetchInfoSources {
    const val JJWXC = "jjwxc"
    const val GONGZICP = "gongzicp"
    const val SOSAD = "sosad"

    val options = listOf(
        JJWXC to "晋江",
        GONGZICP to "长佩",
        SOSAD to "废文"
    )

    fun label(source: String): String {
        return options.firstOrNull { it.first == source }?.second ?: source
    }
}

interface FetchInfoFetcher {
    val source: String
    suspend fun searchChoices(
        parameters: FetchInfoParameters,
        onProgress: FetchInfoProgress = {}
    ): List<FetchInfoSearchChoice> = emptyList()

    suspend fun fetch(
        parameters: FetchInfoParameters,
        onProgress: FetchInfoProgress = {}
    ): FetchedInfo
}

typealias FetchInfoProgress = suspend (String) -> Unit

object FetchInfoFetcherFactory {
    fun create(source: String): FetchInfoFetcher {
        return when (source) {
            FetchInfoSources.GONGZICP -> GongzicpFetcher()
            FetchInfoSources.SOSAD -> SosadFetcher()
            else -> JjwxcFetcher()
        }
    }
}
