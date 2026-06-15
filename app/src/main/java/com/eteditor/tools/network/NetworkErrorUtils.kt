package com.eteditor

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal fun networkAwareErrorMessage(prefix: String, error: Throwable): String {
    val networkReason = networkErrorReason(error)
    if (networkReason != null) {
        return "$prefix：网络不可用，请检查网络连接后重试（$networkReason）"
    }
    return "$prefix：${error.message ?: error.javaClass.simpleName}"
}

internal fun networkUnavailableMessageForContext(context: Context, prefix: String): String? {
    return if (isNetworkAvailable(context)) {
        null
    } else {
        "$prefix：网络不可用，请检查网络连接后重试"
    }
}

private fun isNetworkAvailable(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return true
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return true
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private fun networkErrorReason(error: Throwable): String? {
    var current: Throwable? = error
    while (current != null) {
        when (current) {
            is UnknownHostException -> return "无法连接服务器"
            is NoRouteToHostException -> return "没有可用网络路由"
            is SocketTimeoutException -> return "连接超时"
            is ConnectException -> return "连接失败"
            is SocketException -> return "网络连接中断"
        }
        val message = current.message.orEmpty()
        if (message.contains("Unable to resolve host", ignoreCase = true) ||
            message.contains("No address associated with hostname", ignoreCase = true)
        ) {
            return "无法连接服务器"
        }
        current = current.cause
    }
    return null
}
