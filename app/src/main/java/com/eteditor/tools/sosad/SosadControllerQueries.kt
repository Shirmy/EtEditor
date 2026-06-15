package com.eteditor

private const val SOSAD_INSERT_CHAPTER_TOOL_ID = "insert_chapter"
private const val SOSAD_FETCH_INFO_TOOL_ID = "fetch_info"

fun EditorController.sosadLoginCookie(): String {
    val resolvedCookie = resolveSosadLoginCookie(
        builtInParameterOverrides = builtInParameterOverrides,
        savedBuiltInDefaultOverrides = savedBuiltInDefaultOverrides,
        insertChapterToolId = SOSAD_INSERT_CHAPTER_TOOL_ID,
        fetchInfoToolId = SOSAD_FETCH_INFO_TOOL_ID,
        insertAuthKey = INSERT_CHAPTER_PARAM_SOSAD_AUTH_COOKIE,
        fetchAuthKey = FETCH_INFO_PARAM_AUTH_COOKIE
    )
    if (resolvedCookie.isNotBlank() && !sosadLoginInvalid) return resolvedCookie

    val webViewCookie = readSosadLoginCookie()
    if (webViewCookie.isBlank()) return resolvedCookie
    rememberSosadLoginCookieForRuntime(webViewCookie)
    clearSosadLoginInvalid()
    return webViewCookie
}

fun EditorController.shouldShowSosadLogin(cookie: String): Boolean {
    return shouldShowSosadLoginPrompt(cookie, sosadLoginInvalid)
}

fun EditorController.sosadLoginReady(cookie: String): Boolean {
    return isSosadLoginReady(cookie, sosadLoginInvalid)
}

internal fun EditorController.clearSosadLoginInvalid() {
    sosadLoginInvalid = false
}

internal fun EditorController.markSosadLoginInvalidIfNeeded(message: String, error: Throwable? = null) {
    if (shouldMarkSosadLoginInvalid(message, error)) {
        sosadLoginInvalid = true
    }
}

private fun EditorController.rememberSosadLoginCookieForRuntime(cookie: String) {
    val cleanCookie = cookie.trim()
    if (cleanCookie.isBlank()) return
    val nextOverrides = rememberSosadLoginCookieForRuntimeTool(
        currentOverrides = rememberSosadLoginCookieForRuntimeTool(
            currentOverrides = builtInParameterOverrides,
            toolId = SOSAD_INSERT_CHAPTER_TOOL_ID,
            key = INSERT_CHAPTER_PARAM_SOSAD_AUTH_COOKIE,
            cookie = cleanCookie
        ),
        toolId = SOSAD_FETCH_INFO_TOOL_ID,
        key = FETCH_INFO_PARAM_AUTH_COOKIE,
        cookie = cleanCookie
    )
    if (nextOverrides != builtInParameterOverrides) {
        builtInParameterOverrides = nextOverrides
    }
}

private fun EditorController.rememberSosadLoginCookieForRuntimeTool(
    currentOverrides: Map<String, Map<String, String>>,
    toolId: String,
    key: String,
    cookie: String
): Map<String, Map<String, String>> {
    val toolOverrides = currentOverrides[toolId].orEmpty()
    if (toolOverrides[key] == cookie) return currentOverrides
    return updateBuiltInToolOverrides(
        currentOverrides = currentOverrides,
        toolId = toolId,
        overrides = cleanBuiltInParameterOverrides(toolId, toolOverrides + (key to cookie))
    )
}
