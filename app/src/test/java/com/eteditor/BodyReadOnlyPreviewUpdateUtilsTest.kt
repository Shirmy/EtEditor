package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Test

class BodyReadOnlyPreviewUpdateUtilsTest {
    @Test
    fun readOnlyPreviewUpdateReschedulesPendingContentWhenPositionChanges() {
        val action = readOnlyPreviewUpdateAction(
            contentChanged = false,
            configChanged = false,
            contentApplied = false,
            layoutChanged = false,
            positionChanged = true
        )

        assertEquals(ReadOnlyPreviewUpdateAction.ApplyStableContent, action)
    }

    @Test
    fun readOnlyPreviewUpdateAppliesPositionOnlyAfterContentIsApplied() {
        val action = readOnlyPreviewUpdateAction(
            contentChanged = false,
            configChanged = false,
            contentApplied = true,
            layoutChanged = false,
            positionChanged = true
        )

        assertEquals(ReadOnlyPreviewUpdateAction.ApplyPositionOnly, action)
    }
}
