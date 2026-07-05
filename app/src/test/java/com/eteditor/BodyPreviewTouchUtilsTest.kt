package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun restoreOffsetIfSavedOnlyWhenSaveSucceeds() {
        assertEquals(128, restoreOffsetIfSaved(saved = true, bodyOffset = 128))
        assertEquals(null, restoreOffsetIfSaved(saved = false, bodyOffset = 128))
    }

    @Test
    fun previewTouchConsumesDoubleTapSuppressionOnlyWhileInteractive() {
        assertTrue(
            shouldConsumePreviewTouchEvent(
                interactive = true,
                suppressingDoubleTap = true
            )
        )
        assertFalse(
            shouldConsumePreviewTouchEvent(
                interactive = true,
                suppressingDoubleTap = false
            )
        )
        assertFalse(
            shouldConsumePreviewTouchEvent(
                interactive = false,
                suppressingDoubleTap = true
            )
        )
    }
}
