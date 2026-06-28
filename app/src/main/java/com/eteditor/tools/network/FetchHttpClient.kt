package com.eteditor

import com.eteditor.core.TextCodec
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

object FetchHttpClient {
    fun getText(
        url: String,
        headers: Map<String, String> = emptyMap(),
        redirectValidator: ((String, String) -> Boolean)? = null,
        maxBytes: Long = HTTP_TEXT_RESPONSE_MAX_BYTES
    ): String {
        val response = getBytes(url, headers, redirectValidator, maxBytes)
        val contentType = response.contentType
        val charsetName = Regex("""charset=([^;]+)""", RegexOption.IGNORE_CASE)
            .find(contentType)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.trim('"', '\'')
        if (!charsetName.isNullOrBlank()) {
            val charset = runCatching { Charset.forName(charsetName) }.getOrNull()
            if (charset != null) return String(response.bytes, charset)
        }
        val headBytes = response.bytes.copyOfRange(0, minOf(response.bytes.size, 4096))
        val head = String(headBytes, StandardCharsets.ISO_8859_1)
        val metaCharset = Regex("""charset=["']?([A-Za-z0-9_\-]+)""", RegexOption.IGNORE_CASE)
            .find(head)
            ?.groupValues
            ?.getOrNull(1)
        val charset = metaCharset
            ?.let { runCatching { Charset.forName(it) }.getOrNull() }
            ?: StandardCharsets.UTF_8
        return runCatching { String(response.bytes, charset) }
            .getOrElse { TextCodec.decode(response.bytes).first }
    }

    fun getBytes(
        url: String,
        headers: Map<String, String> = emptyMap(),
        redirectValidator: ((String, String) -> Boolean)? = null,
        maxBytes: Long = HTTP_TEXT_RESPONSE_MAX_BYTES
    ): HttpBytes {
        var attempt = 0
        while (true) {
            try {
                return fetchBytesOnce(url, headers, redirectValidator, maxBytes)
            } catch (networkError: IOException) {
                // 仅对网络层抖动(连接/读取超时、连接中断、临时无法解析主机等)自动重试。
                // HTTP 状态错误、重定向被拒、体量超限等确定性失败抛的不是 IOException,会直接上抛、不重试。
                if (attempt >= MAX_RETRY_COUNT) throw networkError
                attempt += 1
                try {
                    Thread.sleep(RETRY_BACKOFF_MILLIS * attempt)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw networkError
                }
            }
        }
    }

    private fun fetchBytesOnce(
        url: String,
        headers: Map<String, String>,
        redirectValidator: ((String, String) -> Boolean)?,
        maxBytes: Long
    ): HttpBytes {
        var currentUrl = url
        var redirectCount = 0
        while (true) {
            val parsedUrl = URL(currentUrl)
            // 按真实主机名(而非"网址里含某段字串")判断是否长佩,避免把长佩专用请求头
            // 误加到名字里碰巧含 gongzicp.com 的别的地址上;与下面建立连接用同一把解析,口径一致。
            val host = parsedUrl.host.orEmpty().lowercase().trimEnd('.')
            val isGongzicpApiHost = host == "webapi.gongzicp.com"
            val isGongzicpHost = host == "gongzicp.com" || host.endsWith(".gongzicp.com")
            val connection = (parsedUrl.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = redirectValidator == null
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 EtEditor/1.0")
                setRequestProperty(
                    "Accept",
                    if (isGongzicpApiHost) {
                        "application/json,text/plain,*/*"
                    } else {
                        "text/html,application/xhtml+xml,application/xml,image/*,*/*"
                    }
                )
                setRequestProperty("Accept-Encoding", "gzip, deflate")
                if (isGongzicpHost) {
                    setRequestProperty("Referer", "https://www.gongzicp.com/")
                    setRequestProperty("Origin", "https://www.gongzicp.com")
                }
                headers.forEach { (name, value) ->
                    if (name.isNotBlank() && value.isNotBlank()) {
                        setRequestProperty(name, value)
                    }
                }
            }
            try {
                val status = connection.responseCode
                if (redirectValidator != null && status in HTTP_REDIRECT_STATUSES) {
                    val location = connection.getHeaderField("Location").orEmpty()
                    if (location.isBlank()) error("HTTP $status 缺少重定向地址")
                    val nextUrl = absoluteUrl(location, currentUrl)
                    if (!redirectValidator(currentUrl, nextUrl)) {
                        error("已拒绝不安全重定向：$nextUrl")
                    }
                    redirectCount += 1
                    if (redirectCount > MAX_REDIRECT_COUNT) error("重定向次数过多")
                    currentUrl = nextUrl
                    continue
                }
                if (status !in 200..299) error("HTTP $status")
                connection.contentLengthLong
                    .takeIf { it >= 0L }
                    ?.let { size -> requireSizeWithinLimit(size, maxBytes, "网络响应") }
                val raw = connection.inputStream.use { input ->
                    val encoding = connection.contentEncoding.orEmpty().lowercase()
                    when {
                        encoding.contains("gzip") -> GZIPInputStream(input).use { it.readBytesLimited(maxBytes, "网络响应") }
                        encoding.contains("deflate") -> InflaterInputStream(input).use { it.readBytesLimited(maxBytes, "网络响应") }
                        else -> input.readBytesLimited(maxBytes, "网络响应")
                    }
                }
                return HttpBytes(raw, connection.contentType.orEmpty())
            } finally {
                connection.disconnect()
            }
        }
    }

    private val HTTP_REDIRECT_STATUSES = setOf(
        HttpURLConnection.HTTP_MOVED_PERM,
        HttpURLConnection.HTTP_MOVED_TEMP,
        HttpURLConnection.HTTP_SEE_OTHER,
        307,
        308
    )

    private const val MAX_REDIRECT_COUNT = 5
    private const val MAX_RETRY_COUNT = 2
    private const val RETRY_BACKOFF_MILLIS = 400L
}
