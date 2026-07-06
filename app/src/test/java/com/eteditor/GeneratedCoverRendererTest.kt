package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Test

class GeneratedCoverRendererTest {
    @Test
    fun generatedCoverSingleColumnFontSizeUsesMaxMiddleAndMinValues() {
        assertEquals(300f, generatedCoverSingleColumnFontSize(1))
        assertEquals(220f, generatedCoverSingleColumnFontSize(5))
        assertEquals(140f, generatedCoverSingleColumnFontSize(9))
    }

    @Test
    fun generatedCoverTitleLayoutModeUsesSingleColumnForShortPlainTitle() {
        assertEquals(
            GeneratedCoverTitleLayoutMode.SingleColumn,
            generatedCoverTitleLayoutMode("一二三四五六七八九")
        )
    }

    @Test
    fun generatedCoverTitleLayoutModeUsesTwoColumnsForLongPlainTitle() {
        assertEquals(
            GeneratedCoverTitleLayoutMode.TwoColumn,
            generatedCoverTitleLayoutMode("一二三四五六七八九十")
        )
    }

    @Test
    fun generatedCoverTitleLayoutModeUsesHorizontalForContinuousLetters() {
        assertEquals(
            GeneratedCoverTitleLayoutMode.Horizontal,
            generatedCoverTitleLayoutMode("一二AB三四")
        )
    }

    @Test
    fun fitGeneratedCoverHorizontalFontSizeShrinksOversizeWordToAvailableWidth() {
        assertEquals(
            75f,
            fitGeneratedCoverHorizontalFontSize(
                initialFontSize = 150f,
                minFontSize = 20f,
                availableWidth = 800f,
                widestWordWidth = 1600f
            )
        )
    }

    @Test
    fun fitGeneratedCoverHorizontalFontSizeKeepsInitialSizeWhenWordFits() {
        assertEquals(
            150f,
            fitGeneratedCoverHorizontalFontSize(
                initialFontSize = 150f,
                minFontSize = 20f,
                availableWidth = 800f,
                widestWordWidth = 600f
            )
        )
    }
}
