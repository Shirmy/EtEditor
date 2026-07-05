package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyActionDialogUiUtilsTest {
    @Test
    fun paragraphEditDialogLayoutKeepsEditorRoomAndActionArea() {
        val metrics = paragraphEditDialogLayoutMetrics()

        assertTrue(metrics.minDialogHeightDp >= 240)
        assertTrue(metrics.maxDialogHeightDp <= 430)
        assertTrue(metrics.maxDialogHeightDp >= metrics.minDialogHeightDp + 120)
        assertTrue(metrics.minEditorHeightDp >= 120)
        assertTrue(metrics.headerActionSizeDp in 32..40)
        assertTrue(metrics.editorPaddingDp >= 8)
    }

    @Test
    fun paragraphEditDialogHeightShrinksWhenKeyboardTakesSpace() {
        val metrics = paragraphEditDialogLayoutMetrics()

        assertEquals(
            metrics.maxDialogHeightDp,
            paragraphEditDialogHeightDp(screenHeightDp = 900, imeBottomDp = 0, metrics = metrics)
        )
        assertEquals(
            308,
            paragraphEditDialogHeightDp(screenHeightDp = 640, imeBottomDp = 300, metrics = metrics)
        )
        assertEquals(
            metrics.minDialogHeightDp,
            paragraphEditDialogHeightDp(screenHeightDp = 420, imeBottomDp = 300, metrics = metrics)
        )
    }
}
