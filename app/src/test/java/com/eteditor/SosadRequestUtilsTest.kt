package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SosadRequestUtilsTest {
    @Test
    fun resolveSosadLoginCookiePrefersRuntimeInsertThenFetchThenSavedDefaults() {
        assertEquals(
            "insert-runtime",
            resolveSosadLoginCookie(
                builtInParameterOverrides = mapOf(
                    "insert" to mapOf("insert_auth" to "insert-runtime"),
                    "fetch" to mapOf("fetch_auth" to "fetch-runtime")
                ),
                savedBuiltInDefaultOverrides = mapOf(
                    "insert" to mapOf("insert_auth" to "insert-saved"),
                    "fetch" to mapOf("fetch_auth" to "fetch-saved")
                ),
                insertChapterToolId = "insert",
                fetchInfoToolId = "fetch",
                insertAuthKey = "insert_auth",
                fetchAuthKey = "fetch_auth"
            )
        )
        assertEquals(
            "fetch-saved",
            resolveSosadLoginCookie(
                builtInParameterOverrides = emptyMap(),
                savedBuiltInDefaultOverrides = mapOf("fetch" to mapOf("fetch_auth" to "fetch-saved")),
                insertChapterToolId = "insert",
                fetchInfoToolId = "fetch",
                insertAuthKey = "insert_auth",
                fetchAuthKey = "fetch_auth"
            )
        )
    }

    @Test
    fun resolveSosadLoginCookieSkipsBlankRuntimeInsertCookie() {
        assertEquals(
            "fetch-runtime",
            resolveSosadLoginCookie(
                builtInParameterOverrides = mapOf(
                    "insert" to mapOf("insert_auth" to " "),
                    "fetch" to mapOf("fetch_auth" to "fetch-runtime")
                ),
                savedBuiltInDefaultOverrides = mapOf(
                    "insert" to mapOf("insert_auth" to "insert-saved"),
                    "fetch" to mapOf("fetch_auth" to "fetch-saved")
                ),
                insertChapterToolId = "insert",
                fetchInfoToolId = "fetch",
                insertAuthKey = "insert_auth",
                fetchAuthKey = "fetch_auth"
            )
        )
    }

    @Test
    fun sosadLoginPromptAndReadyFollowCookieAndInvalidState() {
        assertTrue(shouldShowSosadLoginPrompt("", loginInvalid = false))
        assertTrue(shouldShowSosadLoginPrompt("session=abc", loginInvalid = true))
        assertFalse(shouldShowSosadLoginPrompt("session=abc", loginInvalid = false))
        assertTrue(isSosadLoginReady(" session=abc ", loginInvalid = false))
        assertFalse(isSosadLoginReady("session=abc", loginInvalid = true))
    }

    @Test
    fun buildInsertChapterSosadSourceUriReturnsBlankForBlankQuery() {
        assertEquals(
            "",
            buildInsertChapterSosadSourceUri(
                baseUri = INSERT_CHAPTER_SOSAD_URI,
                query = " ",
                startRaw = "1",
                endRaw = "2"
            )
        )
    }

    @Test
    fun allowedHttpsUrlAcceptsOnlyConfiguredHosts() {
        assertTrue(isSosadAllowedHttpsUrl("https://sosad.fun/thread/1"))
        assertTrue(isSosadAllowedHttpsUrl(" https://www.sosad.fun/thread/1 "))
        assertTrue(isSosadAllowedHttpsUrl("https://xn--pxtr7m.com/thread/1"))

        assertFalse(isSosadAllowedHttpsUrl("http://sosad.fun/thread/1"))
        assertFalse(isSosadAllowedHttpsUrl("https://\u5e9f\u6587.com/thread/1"))
        assertFalse(isSosadAllowedHttpsUrl("https://sosad.fun.evil.com/thread/1"))
        assertFalse(isSosadAllowedHttpsUrl("https://evil.com/thread/1"))
        assertFalse(isSosadAllowedHttpsUrl("not a url"))
    }

    @Test
    fun requestHeadersDoNotSendCookieToDisallowedTarget() {
        val headers = buildSosadRequestHeaders(
            rawCookie = " Cookie: session=secret ",
            targetUrl = "https://evil.com/thread/1",
            refererUrl = "https://sosad.fun/thread/source"
        )

        assertFalse(headers.containsKey("Cookie"))
        assertEquals("https://sosad.fun/thread/source", headers["Referer"])
    }

    @Test
    fun requestHeadersNormalizeCookieAndOnlySendRefererToAllowedHosts() {
        assertEquals("session=secret", normalizeSosadCookie(" cookie: session=secret "))
        assertEquals("COOKIE: session=secret", normalizeSosadCookie(" COOKIE: session=secret "))

        val headers = buildSosadRequestHeaders(
            rawCookie = "Cookie: session=secret",
            targetUrl = "https://sosad.fun/thread/1",
            refererUrl = "https://www.sosad.fun/thread/source"
        )

        assertEquals("session=secret", headers["Cookie"])
        assertEquals("https://www.sosad.fun/thread/source", headers["Referer"])
    }

    @Test
    fun imageHeadersAllowConfiguredImageHostButRejectDisallowedReferer() {
        val headers = buildSosadInsertChapterImageHeaders(
            rawCookie = "session=secret",
            imageUrl = "https://i.ibb.co/image.png",
            chapterUrl = "https://evil.com/thread/source"
        )

        assertFalse(headers.containsKey("Cookie"))
        assertFalse(headers.containsKey("Referer"))
    }

    @Test
    fun imageUrlAllowsImageHostButRegularHttpsUrlDoesNot() {
        assertTrue(isSosadAllowedImageUrl("https://i.ibb.co/image.png"))
        assertFalse(isSosadAllowedHttpsUrl("https://i.ibb.co/image.png"))
        assertTrue(isSosadAllowedImageUrl("https://sosad.fun/images/pic.jpg"))
        assertFalse(isSosadAllowedImageUrl("http://i.ibb.co/image.png"))
    }

    @Test
    fun requireAllowedUrlsTrimAndRejectUnsupportedHosts() {
        assertEquals(
            "https://sosad.fun/thread/1",
            requireSosadAllowedHttpsUrl(" https://sosad.fun/thread/1 ", "废文链接")
        )
        assertEquals(
            "https://i.ibb.co/image.png",
            requireSosadAllowedImageUrl(" https://i.ibb.co/image.png ", "图片")
        )
        assertThrows(IllegalStateException::class.java) {
            requireSosadAllowedHttpsUrl("https://evil.com/thread/1", "废文链接")
        }
        assertThrows(IllegalStateException::class.java) {
            requireSosadAllowedImageUrl("https://evil.com/image.png", "图片")
        }
    }

    @Test
    fun imageRedirectRequiresSameAllowedImageHost() {
        assertTrue(
            isSosadImageHttpsRedirect(
                fromUrl = "https://i.ibb.co/a.png",
                toUrl = "https://i.ibb.co/b.png"
            )
        )
        assertFalse(
            isSosadImageHttpsRedirect(
                fromUrl = "https://i.ibb.co/a.png",
                toUrl = "https://evil.com/b.png"
            )
        )
    }

    @Test
    fun sameHostRedirectRejectsCrossHostTargets() {
        assertTrue(
            isSosadSameHostHttpsRedirect(
                fromUrl = "https://sosad.fun/thread/1",
                toUrl = "https://sosad.fun/thread/2"
            )
        )
        assertFalse(
            isSosadSameHostHttpsRedirect(
                fromUrl = "https://sosad.fun/thread/1",
                toUrl = "https://www.sosad.fun/thread/2"
            )
        )
        assertFalse(
            isSosadSameHostHttpsRedirect(
                fromUrl = "https://sosad.fun/thread/1",
                toUrl = "https://evil.com/thread/2"
            )
        )
    }

    @Test
    fun shouldMarkSosadLoginInvalidSearchesMessagesAndNestedCauses() {
        assertTrue(shouldMarkSosadLoginInvalid("403 forbidden"))
        assertTrue(
            shouldMarkSosadLoginInvalid(
                message = "抓取失败",
                error = IllegalStateException("outer", IllegalArgumentException("cookie expired"))
            )
        )
        assertFalse(shouldMarkSosadLoginInvalid("网络超时", IllegalStateException("timeout")))
    }
}
