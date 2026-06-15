package com.eteditor

import android.net.Uri
import java.net.IDN
import java.net.URI

internal fun resolveSosadLoginCookie(
    builtInParameterOverrides: Map<String, Map<String, String>>,
    savedBuiltInDefaultOverrides: Map<String, Map<String, String>>,
    insertChapterToolId: String,
    fetchInfoToolId: String,
    insertAuthKey: String,
    fetchAuthKey: String
): String {
    return builtInParameterOverrides[insertChapterToolId].orEmpty()[insertAuthKey].orEmpty()
        .ifBlank { builtInParameterOverrides[fetchInfoToolId].orEmpty()[fetchAuthKey].orEmpty() }
        .ifBlank { savedBuiltInDefaultOverrides[insertChapterToolId].orEmpty()[insertAuthKey].orEmpty() }
        .ifBlank { savedBuiltInDefaultOverrides[fetchInfoToolId].orEmpty()[fetchAuthKey].orEmpty() }
}

internal fun shouldShowSosadLoginPrompt(
    cookie: String,
    loginInvalid: Boolean
): Boolean {
    return cookie.trim().isBlank() || loginInvalid
}

internal fun isSosadLoginReady(
    cookie: String,
    loginInvalid: Boolean
): Boolean {
    return cookie.trim().isNotBlank() && !loginInvalid
}

internal fun buildInsertChapterSosadSourceUri(
    baseUri: String,
    query: String,
    startRaw: String,
    endRaw: String
): String {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return ""
    val start = startRaw.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val end = endRaw.toIntOrNull()?.takeIf { it >= start }?.toString().orEmpty()
    return "$baseUri?query=${Uri.encode(cleanQuery)}&start=$start&end=$end"
}

internal fun buildSosadInsertChapterImageHeaders(
    rawCookie: String,
    imageUrl: String,
    chapterUrl: String
): Map<String, String> {
    val cookie = normalizeSosadCookie(rawCookie)
    val headers = mutableMapOf<String, String>()
    if (cookie.isNotBlank() && isSosadAllowedHttpsUrl(imageUrl)) headers["Cookie"] = cookie
    if (chapterUrl.isNotBlank() && isSosadAllowedHttpsUrl(chapterUrl)) headers["Referer"] = chapterUrl.trim()
    return headers
}

internal fun buildSosadRequestHeaders(
    rawCookie: String,
    targetUrl: String,
    refererUrl: String = ""
): Map<String, String> {
    val cookie = normalizeSosadCookie(rawCookie)
    val headers = mutableMapOf<String, String>()
    if (cookie.isNotBlank() && isSosadAllowedHttpsUrl(targetUrl)) headers["Cookie"] = cookie
    if (refererUrl.isNotBlank() && isSosadAllowedHttpsUrl(refererUrl)) headers["Referer"] = refererUrl
    return headers
}

internal fun normalizeSosadCookie(rawCookie: String): String {
    return rawCookie.trim()
        .removePrefix("Cookie:")
        .removePrefix("cookie:")
        .trim()
}

internal fun upgradeSosadHttpScheme(url: String): String {
    val trimmed = url.trim()
    return if (trimmed.startsWith("http://", ignoreCase = true)) {
        "https://" + trimmed.substring("http://".length)
    } else {
        trimmed
    }
}

internal fun requireSosadAllowedHttpsUrl(url: String, label: String): String {
    val clean = url.trim()
    if (!isSosadAllowedHttpsUrl(clean)) {
        error("$label 必须是废文 HTTPS 链接")
    }
    return clean
}

internal fun requireSosadAllowedImageUrl(url: String, label: String): String {
    val clean = url.trim()
    if (!isSosadAllowedImageUrl(clean)) {
        error("$label 必须是支持的 HTTPS 图片链接")
    }
    return clean
}

internal fun isSosadAllowedHttpsUrl(url: String): Boolean {
    return sosadAllowedHost(url) != null
}

internal fun isSosadAllowedImageUrl(url: String): Boolean {
    return sosadAllowedImageHost(url) != null
}

internal fun isSosadSameHostHttpsRedirect(fromUrl: String, toUrl: String): Boolean {
    val fromHost = sosadAllowedHost(fromUrl) ?: return false
    val toHost = sosadAllowedHost(toUrl) ?: return false
    return fromHost == toHost
}

internal fun isSosadImageHttpsRedirect(fromUrl: String, toUrl: String): Boolean {
    val fromHost = sosadAllowedImageHost(fromUrl) ?: return false
    val toHost = sosadAllowedImageHost(toUrl) ?: return false
    return fromHost == toHost
}

private fun sosadAllowedHost(url: String): String? {
    val asciiHost = httpsAsciiHost(url) ?: return null
    return asciiHost.takeIf { it in SOSAD_ALLOWED_HOSTS }
}

private fun sosadAllowedImageHost(url: String): String? {
    val asciiHost = httpsAsciiHost(url) ?: return null
    return asciiHost.takeIf { it in SOSAD_ALLOWED_HOSTS || it in SOSAD_IMAGE_HOSTS }
}

private fun httpsAsciiHost(url: String): String? {
    val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
    if (!uri.scheme.orEmpty().equals("https", ignoreCase = true)) return null
    // 中文域名（如 废文.com）含非 ASCII 字符时 uri.host 返回 null，
    // 只能从 authority 取主机名，去掉可能的 userinfo@ 和 :端口后再转 punycode。
    val host = uri.host.orEmpty().ifBlank {
        uri.authority.orEmpty()
            .substringAfterLast('@')
            .substringBefore(':')
    }.trimEnd('.')
    if (host.isBlank()) return null
    return runCatching { IDN.toASCII(host).lowercase().trimEnd('.') }
        .getOrDefault(host.lowercase())
}

private val SOSAD_ALLOWED_HOSTS = setOf(
    "xn--pxtr7m.com",
    "www.xn--pxtr7m.com",
    "sosad.fun",
    "www.sosad.fun"
)

private val SOSAD_IMAGE_HOSTS = setOf(
    "i.ibb.co"
)

internal fun shouldMarkSosadLoginInvalid(message: String, error: Throwable? = null): Boolean {
    val combined = buildString {
        append(message)
        var current = error
        while (current != null) {
            append('\n')
            append(current.message.orEmpty())
            current = current.cause
        }
    }
    return combined.contains("登录", ignoreCase = true) ||
        combined.contains("登录状态", ignoreCase = true) ||
        combined.contains("权限", ignoreCase = true) ||
        combined.contains("cookie", ignoreCase = true) ||
        combined.contains("login", ignoreCase = true) ||
        combined.contains("401") ||
        combined.contains("403")
}
