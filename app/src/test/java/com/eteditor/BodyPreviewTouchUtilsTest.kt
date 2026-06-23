package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Test

class BodyPreviewTouchUtilsTest {
    @Test
    fun adjustPreviewDoubleTapYMovesBottomEdgeTapInsideTextArea() {
        val adjusted = adjustPreviewDoubleTapY(
            y = 488f,
            editorHeight = 500,
            rowHeight = 24
        )

        assertEquals(464f, adjusted, 0.01f)
    }

    @Test
    fun adjustPreviewDoubleTapYKeepsNormalTextTapUnchanged() {
        val adjusted = adjustPreviewDoubleTapY(
            y = 410f,
            editorHeight = 500,
            rowHeight = 24
        )

        assertEquals(410f, adjusted, 0.01f)
    }
}
