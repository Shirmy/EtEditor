package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Test

class FetchInfoSourceUtilsTest {
    @Test
    fun fetchInfoSourceLabelsAndFactoryFallbackFollowCurrentSources() {
        assertEquals("晋江", FetchInfoSources.label(FETCH_INFO_SOURCE_JJWXC))
        assertEquals("长佩", FetchInfoSources.label(FETCH_INFO_SOURCE_GONGZICP))
        assertEquals("废文", FetchInfoSources.label(FETCH_INFO_SOURCE_SOSAD))
        assertEquals("unknown", FetchInfoSources.label("unknown"))

        assertEquals(FETCH_INFO_SOURCE_JJWXC, FetchInfoFetcherFactory.create(FETCH_INFO_SOURCE_JJWXC).source)
        assertEquals(FETCH_INFO_SOURCE_GONGZICP, FetchInfoFetcherFactory.create(FETCH_INFO_SOURCE_GONGZICP).source)
        assertEquals(FETCH_INFO_SOURCE_SOSAD, FetchInfoFetcherFactory.create(FETCH_INFO_SOURCE_SOSAD).source)
        assertEquals(FETCH_INFO_SOURCE_JJWXC, FetchInfoFetcherFactory.create("unknown").source)
    }
}
