package com.eteditor

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkErrorUtilsTest {
    @Test
    fun networkAwareErrorMessageMapsKnownNetworkExceptions() {
        assertEquals(
            "抓取失败：网络不可用，请检查网络连接后重试（无法连接服务器）",
            networkAwareErrorMessage("抓取失败", UnknownHostException("host"))
        )
        assertEquals(
            "抓取失败：网络不可用，请检查网络连接后重试（连接超时）",
            networkAwareErrorMessage("抓取失败", SocketTimeoutException("timeout"))
        )
        assertEquals(
            "抓取失败：网络不可用，请检查网络连接后重试（连接失败）",
            networkAwareErrorMessage("抓取失败", ConnectException("refused"))
        )
        assertEquals(
            "抓取失败：网络不可用，请检查网络连接后重试（网络连接中断）",
            networkAwareErrorMessage("抓取失败", SocketException("reset"))
        )
        assertEquals(
            "抓取失败：网络不可用，请检查网络连接后重试（没有可用网络路由）",
            networkAwareErrorMessage("抓取失败", NoRouteToHostException("route"))
        )
    }

    @Test
    fun networkAwareErrorMessageSearchesNestedCausesAndHostMessages() {
        val nested = IllegalStateException("outer", UnknownHostException("host"))
        val messageOnly = IllegalArgumentException("Unable to resolve host example.com")
        val noAddress = IllegalArgumentException("No address associated with hostname")

        assertEquals(
            "保存失败：网络不可用，请检查网络连接后重试（无法连接服务器）",
            networkAwareErrorMessage("保存失败", nested)
        )
        assertEquals(
            "保存失败：网络不可用，请检查网络连接后重试（无法连接服务器）",
            networkAwareErrorMessage("保存失败", messageOnly)
        )
        assertEquals(
            "保存失败：网络不可用，请检查网络连接后重试（无法连接服务器）",
            networkAwareErrorMessage("保存失败", noAddress)
        )
    }

    @Test
    fun networkAwareErrorMessageUsesFirstNetworkReasonInCauseChain() {
        val connectThenHost = ConnectException("refused").also { error ->
            error.initCause(UnknownHostException("host"))
        }
        val timeoutNestedInSocket = SocketException("socket").also { error ->
            error.initCause(SocketTimeoutException("timeout"))
        }

        assertEquals(
            "同步失败：网络不可用，请检查网络连接后重试（连接失败）",
            networkAwareErrorMessage("同步失败", connectThenHost)
        )
        assertEquals(
            "同步失败：网络不可用，请检查网络连接后重试（网络连接中断）",
            networkAwareErrorMessage("同步失败", timeoutNestedInSocket)
        )
    }

    @Test
    fun networkAwareErrorMessageMatchesHostMessagesCaseInsensitivelyInNestedCauses() {
        val nestedMessage = IllegalStateException(
            "outer",
            IllegalArgumentException("unable TO RESOLVE host example.com")
        )
        val nestedNoAddress = IllegalStateException(
            "outer",
            IllegalArgumentException("NO ADDRESS associated WITH hostname")
        )

        assertEquals(
            "同步失败：网络不可用，请检查网络连接后重试（无法连接服务器）",
            networkAwareErrorMessage("同步失败", nestedMessage)
        )
        assertEquals(
            "同步失败：网络不可用，请检查网络连接后重试（无法连接服务器）",
            networkAwareErrorMessage("同步失败", nestedNoAddress)
        )
    }

    @Test
    fun networkAwareErrorMessageFallsBackToOriginalErrorMessage() {
        assertEquals(
            "执行失败：bad input",
            networkAwareErrorMessage("执行失败", IllegalArgumentException("bad input"))
        )
        assertEquals(
            "执行失败：IllegalStateException",
            networkAwareErrorMessage("执行失败", IllegalStateException())
        )
    }
}
