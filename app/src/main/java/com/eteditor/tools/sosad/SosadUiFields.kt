package com.eteditor

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.net.IDN
import java.net.URI

private const val SOSAD_LOGIN_URL = "https://xn--pxtr7m.com/"

// 登录窗允许停留的站点(含其子域名):废文中文域名的 punycode 形式与 sosad.fun。
private val SOSAD_LOGIN_ALLOWED_DOMAINS = listOf("sosad.fun", "xn--pxtr7m.com")

// 判断登录网页要跳转的地址是否仍在废文站点内。
// 站内(含子域名)且为 http/https 才放行;空地址/about: 视为内部放行;其余一律拦下。
private fun isSosadLoginNavigationAllowed(url: String): Boolean {
    val trimmed = url.trim()
    if (trimmed.isBlank()) return true
    if (trimmed.startsWith("about:", ignoreCase = true)) return true
    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return false
    val scheme = uri.scheme.orEmpty().lowercase()
    if (scheme != "http" && scheme != "https") return false
    // 中文域名(如 废文.com)含非 ASCII 字符时 uri.host 返回 null,
    // 只能从 authority 取主机名,去掉可能的 userinfo@ 和 :端口后再转 punycode。
    val rawHost = uri.host.orEmpty().ifBlank {
        uri.authority.orEmpty()
            .substringAfterLast('@')
            .substringBefore(':')
    }.trimEnd('.')
    if (rawHost.isBlank()) return false
    val host = runCatching { IDN.toASCII(rawHost).lowercase().trimEnd('.') }
        .getOrDefault(rawHost.lowercase())
    return SOSAD_LOGIN_ALLOWED_DOMAINS.any { host == it || host.endsWith(".$it") }
}

@Composable
fun SosadLoginField(
    hasCookie: Boolean,
    loginInvalid: Boolean = false,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RowShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "废文登录",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when {
                        loginInvalid -> "登录已失效"
                        hasCookie -> "已登录"
                        else -> "未登录"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (loginInvalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onLogin,
                shape = ControlShape,
                contentPadding = CompactButtonPadding,
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.AutoMirrored.Outlined.ExitToApp, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text(if (hasCookie || loginInvalid) "重新登录" else "登录废文")
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SosadLoginDialog(
    onDismiss: () -> Unit,
    onCookie: (String) -> Unit
) {
    var currentUrl by remember { mutableStateOf(SOSAD_LOGIN_URL) }
    var message by remember { mutableStateOf("") }
    val webViewHolder = remember { arrayOfNulls<WebView>(1) }
    val configuration = LocalConfiguration.current
    val webHeight = when {
        configuration.screenHeightDp < 640 -> 360.dp
        configuration.screenHeightDp < 800 -> 460.dp
        else -> 560.dp
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = DialogBorder,
            modifier = Modifier.adaptiveDialogWidth(AdaptiveDialogWidth.Wide)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "登录废文",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(19.dp))
                    }
                }
                Surface(
                    shape = RowShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(webHeight)
                        .clipToBounds()
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            CookieManager.getInstance().setAcceptCookie(true)
                            WebView(context).apply {
                                webViewHolder[0] = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        // 把登录网页限制在废文站点内:站内地址放行,
                                        // 外站、非网页协议(intent/tel 等)一律拦下不加载,
                                        // 避免登录窗被用来打开任意外部网址。
                                        val target = request?.url?.toString().orEmpty()
                                        return !isSosadLoginNavigationAllowed(target)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        currentUrl = url.orEmpty().ifBlank { SOSAD_LOGIN_URL }
                                    }
                                }
                                loadUrl(SOSAD_LOGIN_URL)
                            }
                        }
                    )
                }
                if (message.isNotBlank()) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                ButtonRow {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = ControlShape,
                        contentPadding = CompactButtonPadding,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("关闭")
                    }
                    Button(
                        onClick = {
                            val cookie = readSosadLoginCookie(webViewHolder[0]?.url ?: currentUrl)
                            if (cookie.isBlank()) {
                                message = "没有获取到登录信息"
                            } else {
                                onCookie(cookie)
                            }
                        },
                        shape = ControlShape,
                        contentPadding = CompactButtonPadding,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("完成登录")
                    }
                }
            }
        }
    }
}

internal fun readSosadLoginCookie(currentUrl: String = ""): String {
    val manager = CookieManager.getInstance()
    manager.flush()
    return listOf(
        currentUrl,
        SOSAD_LOGIN_URL,
        "https://www.xn--pxtr7m.com/",
        "https://sosad.fun/",
        "https://www.sosad.fun/"
    )
        .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        .mapNotNull { url -> manager.getCookie(url)?.trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
}
