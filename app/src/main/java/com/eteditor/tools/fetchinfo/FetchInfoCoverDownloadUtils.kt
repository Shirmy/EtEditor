package com.eteditor

data class FetchInfoCoverRequest(
    val url: String,
    val headers: Map<String, String>,
    val sameHostRedirectOnly: Boolean
)

internal fun buildFetchInfoCoverRequest(
    parameters: FetchInfoParameters,
    coverUrl: String
): FetchInfoCoverRequest {
    val resolvedUrl = if (parameters.source == FETCH_INFO_SOURCE_SOSAD) {
        requireSosadAllowedHttpsUrl(coverUrl, "废文封面链接")
    } else {
        coverUrl
    }
    val headers = if (parameters.source == FETCH_INFO_SOURCE_SOSAD) {
        buildSosadRequestHeaders(parameters.authCookie, resolvedUrl)
    } else {
        emptyMap()
    }
    return FetchInfoCoverRequest(
        url = resolvedUrl,
        headers = headers,
        sameHostRedirectOnly = parameters.source == FETCH_INFO_SOURCE_SOSAD
    )
}
