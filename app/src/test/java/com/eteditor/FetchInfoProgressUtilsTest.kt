package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Test

class FetchInfoProgressUtilsTest {
    @Test
    fun fetchInfoSourceProgressClampsPhaseAndOverallRange() {
        assertEquals(0.02f, fetchInfoSourceProgress(sourceIndex = 0, sourceTotal = 0, phase = -1f), 0.0001f)
        assertEquals(0.5f, fetchInfoSourceProgress(sourceIndex = 1, sourceTotal = 4, phase = 1f), 0.0001f)
        assertEquals(0.96f, fetchInfoSourceProgress(sourceIndex = 2, sourceTotal = 3, phase = 1f), 0.0001f)
    }

    @Test
    fun fetchInfoSourceProgressClampsOutOfRangeSourceIndexAfterCombiningPhase() {
        assertEquals(0.02f, fetchInfoSourceProgress(sourceIndex = -1, sourceTotal = 3, phase = 0.5f), 0.0001f)
        assertEquals(0.96f, fetchInfoSourceProgress(sourceIndex = 10, sourceTotal = 3, phase = 0f), 0.0001f)
    }

    @Test
    fun fetchInfoProgressPhaseMapsUserVisibleMessages() {
        assertEquals(0.78f, fetchInfoProgressPhase("正在抓取目录 10/20"), 0.0001f)
        assertEquals(0.74f, fetchInfoProgressPhase("正在抓取简介"), 0.0001f)
        assertEquals(0.55f, fetchInfoProgressPhase("正在读取详情页"), 0.0001f)
        assertEquals(0.28f, fetchInfoProgressPhase("正在尝试搜索源：晋江"), 0.0001f)
        assertEquals(0.16f, fetchInfoProgressPhase("搜索中"), 0.0001f)
        assertEquals(0.42f, fetchInfoProgressPhase("等待中"), 0.0001f)
    }

    @Test
    fun fetchInfoProgressPhaseMapsBodyCoverAuthorAndMirrorMessages() {
        assertEquals(0.9f, fetchInfoProgressPhase(" 正在读取正文 "), 0.0001f)
        assertEquals(0.9f, fetchInfoProgressPhase("正在抓取正文 2/5"), 0.0001f)
        assertEquals(0.74f, fetchInfoProgressPhase("正在抓取封面"), 0.0001f)
        assertEquals(0.42f, fetchInfoProgressPhase("正在确认作者 1/3"), 0.0001f)
        assertEquals(0.28f, fetchInfoProgressPhase("搜索镜像 2/4：sosad.fun"), 0.0001f)
    }

    @Test
    fun fetchInfoProgressPhaseTrimsAttemptSearchSourceMessages() {
        assertEquals(0.28f, fetchInfoProgressPhase(" 正在尝试搜索源：晋江 "), 0.0001f)
    }

    @Test
    fun fetchInfoAutoSourcesAndContentLabelsFollowContentType() {
        assertEquals(
            listOf(FETCH_INFO_SOURCE_JJWXC, FETCH_INFO_SOURCE_GONGZICP, FETCH_INFO_SOURCE_SOSAD),
            fetchInfoAutoSources(FETCH_INFO_CONTENT_COVER)
        )
        assertEquals(
            listOf(FETCH_INFO_SOURCE_JJWXC, FETCH_INFO_SOURCE_GONGZICP, FETCH_INFO_SOURCE_SOSAD),
            fetchInfoAutoSources(FETCH_INFO_CONTENT_INTRO)
        )
        assertEquals(
            listOf(FETCH_INFO_SOURCE_JJWXC, FETCH_INFO_SOURCE_SOSAD),
            fetchInfoAutoSources(FETCH_INFO_CONTENT_CATALOG)
        )
        assertEquals(listOf(FETCH_INFO_SOURCE_JJWXC), fetchInfoAutoSources("unknown"))
        assertEquals("封面", fetchInfoContentLabel(FETCH_INFO_CONTENT_COVER))
        assertEquals("简介", fetchInfoContentLabel(FETCH_INFO_CONTENT_INTRO))
        assertEquals("目录", fetchInfoContentLabel(FETCH_INFO_CONTENT_CATALOG))
        assertEquals("内容", fetchInfoContentLabel("unknown"))
    }
}
