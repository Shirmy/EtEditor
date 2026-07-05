package com.eteditor

import org.junit.Assert.assertTrue
import org.junit.Test

class BodyActionDialogUiUtilsTest {
    @Test
    fun paragraphEditDialogLayoutKeepsEditorRoomAndActionArea() {
        val metrics = paragraphEditDialogLayoutMetrics()

        assertTrue(metrics.minEditorHeightDp >= 160)
        assertTrue(metrics.maxEditorHeightDp > metrics.minEditorHeightDp)
        assertTrue(metrics.maxDialogHeightDp >= metrics.maxEditorHeightDp + 120)
        assertTrue(metrics.editorPaddingDp >= 8)
    }
}
