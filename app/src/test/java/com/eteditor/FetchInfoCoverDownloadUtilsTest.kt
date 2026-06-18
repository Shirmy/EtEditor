package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class FetchInfoCoverDownloadUtilsTest {
    @Test
    fun buildFetchInfoCoverRequestUsesSosadHeadersAndRedirectPolicy() {
        val request = buildFetchInfoCoverRequest(
            parameters = fetchParameters(
                source = FETCH_INFO_SOURCE_SOSAD,
                authCookie = " Cookie: session=secret "
            ),
            coverUrl = "https://sosad.fun/covers/book.jpg?x=1"
        )

        assertEquals("https://sosad.fun/covers/book.jpg?x=1", request.url)
        assertEquals(mapOf("Cookie" to "session=secret"), request.headers)
        assertTrue(request.sameHostRedirectOnly)
    }

    @Test
    fun buildFetchInfoCoverRequestLeavesOtherSourcesUnchanged() {
        val request = buildFetchInfoCoverRequest(
            parameters = fetchParameters(
                source = FETCH_INFO_SOURCE_JJWXC,
                authCookie = "session=secret"
            ),
            coverUrl = "https://img.example.com/cover.jpg"
        )

        assertEquals("https://img.example.com/cover.jpg", request.url)
        assertEquals(emptyMap<String, String>(), request.headers)
        assertEquals(false, request.sameHostRedirectOnly)
    }

    @Test
    fun buildFetchInfoCoverRequestRejectsUnsupportedSosadCoverUrl() {
        assertThrows(IllegalStateException::class.java) {
            buildFetchInfoCoverRequest(
                parameters = fetchParameters(source = FETCH_INFO_SOURCE_SOSAD),
                coverUrl = "https://evil.com/cover.jpg"
            )
        }
    }

    private fun fetchParameters(
        source: String,
        authCookie: String = ""
    ): FetchInfoParameters {
        return FetchInfoParameters(
            source = source,
            searchMode = FETCH_INFO_SEARCH_TITLE,
            query = "Book",
            expectedAuthor = "",
            content = FETCH_INFO_CONTENT_COVER,
            fetchCatalog = false,
            fetchIntro = false,
            fetchCover = true,
            authCookie = authCookie,
            bodyRangeStart = 1,
            bodyRangeEnd = 0,
            catalogFilter = "",
            catalogFilterEnabled = true,
            autoTitleFormat = false,
            introFilter = "",
            writeCatalog = false,
            writeIntro = false,
            introTargetPath = DEFAULT_FETCH_INFO_INTRO_TARGET,
            writeCover = true
        )
    }
}
