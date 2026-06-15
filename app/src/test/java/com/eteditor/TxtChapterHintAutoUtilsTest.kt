package com.eteditor

import com.eteditor.core.TxtChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TxtChapterHintAutoUtilsTest {
    @Test
    fun autoThresholdsUseNormalChapterMedian() {
        val chapters = listOf(
            chapter("序章", 200),
            chapter("第1章", 3000),
            chapter("第2章", 3200),
            chapter("第3章", 3100),
            chapter("第4章", 3300),
            chapter("第5章", 3050),
            chapter("第6章", 3150),
            chapter("第7章", 3250),
            chapter("第8章", 3000),
            chapter("第9章", 12000, listOf("疑似缺章"))
        )

        val thresholds = txtAutoChapterThresholds(chapters)

        assertEquals(3125, thresholds?.medianWordCount)
        assertEquals(1563, thresholds?.shortThreshold)
        assertEquals(6250, thresholds?.longThreshold)
        assertEquals(8, thresholds?.sampleCount)
    }

    @Test
    fun autoThresholdsReturnNullWhenNormalSampleIsTooSmall() {
        val chapters = listOf(
            chapter("第1章", 3000),
            chapter("第2章", 3100),
            chapter("第3章", 3200)
        )

        assertNull(txtAutoChapterThresholds(chapters))
    }

    @Test
    fun autoHintStatsExposeReadonlyValuesAndHits() {
        val chapters = listOf(
            chapter("第1章", 3000),
            chapter("第2章", 3000),
            chapter("第3章", 3000),
            chapter("第4章", 3000),
            chapter("第5章", 3000),
            chapter("第6章", 3000),
            chapter("第7章", 3000),
            chapter("第8章", 3000),
            chapter("第9章", 1000),
            chapter("第10章", 9000)
        )

        val stats = txtAutoChapterHintStats(chapters)

        assertEquals(10, stats.sampleCount)
        assertEquals(3000, stats.medianWordCount)
        assertEquals(1500, stats.shortThreshold)
        assertEquals(6000, stats.longThreshold)
        assertEquals(1, stats.shortHitCount)
        assertEquals(1, stats.longHitCount)
    }

    @Test
    fun autoHintStatsKeepSampleCountWhenSampleIsTooSmall() {
        val chapters = listOf(
            chapter("第1章", 3000),
            chapter("第2章", 3100),
            chapter("第3章", 3200)
        )

        val stats = txtAutoChapterHintStats(chapters)

        assertEquals(3, stats.sampleCount)
        assertNull(stats.medianWordCount)
        assertNull(stats.shortThreshold)
        assertNull(stats.longThreshold)
        assertEquals(0, stats.shortHitCount)
        assertEquals(0, stats.longHitCount)
    }

    @Test
    fun refreshStatusesUsesAutoThresholdsWhenModeIsAuto() {
        val chapters = listOf(
            chapter("第1章", 3000),
            chapter("第2章", 3000),
            chapter("第3章", 3000),
            chapter("第4章", 3000),
            chapter("第5章", 3000),
            chapter("第6章", 3000),
            chapter("第7章", 3000),
            chapter("第8章", 3000),
            chapter("第9章", 1000),
            chapter("第10章", 9000)
        )

        val refreshed = refreshTxtChapterStatuses(
            chapters = chapters,
            config = TxtChapterDetectionConfig(
                rulesText = "",
                shortThreshold = 1000,
                longThreshold = 10000,
                hiddenLineIndices = emptySet(),
                hintMode = TXT_CHAPTER_HINT_MODE_AUTO
            )
        )

        assertEquals(listOf("短章"), refreshed[8].status)
        assertEquals(listOf("超长章"), refreshed[9].status)
    }

    private fun chapter(
        title: String,
        wordCount: Int,
        status: List<String> = emptyList()
    ): TxtChapter {
        return TxtChapter(
            index = 1,
            lineIndex = 0,
            endLineIndex = 1,
            title = title,
            wordCount = wordCount,
            status = status
        )
    }
}
