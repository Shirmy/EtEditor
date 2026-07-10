package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Test

class BodyReadOnlyPreviewUpdateUtilsTest {
    @Test
    fun readOnlyPreviewUpdateAppliesStableContentWhenContentChanges() {
        val action = readOnlyPreviewUpdateAction(
            contentChanged = true,
            configChanged = false,
            contentApplied = true,
            layoutChanged = false,
            positionChanged = false,
            layoutBusy = false,
            interactive = true
        )

        assertEquals(ReadOnlyPreviewUpdateAction.ApplyStableContent, action)
    }

    @Test
    fun readOnlyPreviewUpdateAppliesStableContentWhenConfigChanges() {
        val action = readOnlyPreviewUpdateAction(
            contentChanged = false,
            configChanged = true,
            contentApplied = true,
            layoutChanged = false,
            positionChanged = false,
            layoutBusy = false,
            interactive = true
        )

        assertEquals(ReadOnlyPreviewUpdateAction.ApplyStableContent, action)
    }

    @Test
    fun readOnlyPreviewUpdateReschedulesPendingContentWhenPositionChanges() {
        val action = readOnlyPreviewUpdateAction(
            contentChanged = false,
            configChanged = false,
            contentApplied = false,
            layoutChanged = false,
            positionChanged = true,
            layoutBusy = false,
            interactive = true
        )

        assertEquals(ReadOnlyPreviewUpdateAction.ApplyStableContent, action)
    }

    @Test
    fun readOnlyPreviewUpdateAppliesStableContentWhenLayoutChanges() {
        val action = readOnlyPreviewUpdateAction(
            contentChanged = false,
            configChanged = false,
            contentApplied = true,
            layoutChanged = true,
            positionChanged = false,
            layoutBusy = false,
            interactive = true
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
            positionChanged = true,
            layoutBusy = false,
            interactive = true
        )

        assertEquals(ReadOnlyPreviewUpdateAction.ApplyPositionOnly, action)
    }

    @Test
    fun readOnlyPreviewUpdateWaitsForLayoutBeforeApplyingPosition() {
        val action = readOnlyPreviewUpdateAction(
            contentChanged = false,
            configChanged = false,
            contentApplied = true,
            layoutChanged = false,
            positionChanged = true,
            layoutBusy = true,
            interactive = true
        )

        assertEquals(ReadOnlyPreviewUpdateAction.ApplyPositionAfterLayout, action)
    }

    @Test
    fun readOnlyPreviewUpdateDefersPositionOnlyWhileNotInteractive() {
        val action = readOnlyPreviewUpdateAction(
            contentChanged = false,
            configChanged = false,
            contentApplied = true,
            layoutChanged = false,
            positionChanged = true,
            layoutBusy = false,
            interactive = false
        )

        assertEquals(ReadOnlyPreviewUpdateAction.None, action)
    }

    @Test
    fun readOnlyPreviewUpdateDoesNothingWhenNothingChanges() {
        val action = readOnlyPreviewUpdateAction(
            contentChanged = false,
            configChanged = false,
            contentApplied = true,
            layoutChanged = false,
            positionChanged = false,
            layoutBusy = false,
            interactive = true
        )

        assertEquals(ReadOnlyPreviewUpdateAction.None, action)
    }
}
