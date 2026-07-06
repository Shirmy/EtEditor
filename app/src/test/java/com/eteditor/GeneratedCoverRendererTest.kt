package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Test

class GeneratedCoverRendererTest {
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
