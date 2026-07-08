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
            positionChanged = false
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
            positionChanged = false
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
            positionChanged = true
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
            positionChanged = false
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

    @Test
    fun readOnlyPreviewUpdateDoesNothingWhenNothingChanges() {
        val action = readOnlyPreviewUpdateAction(
            contentChanged = false,
            configChanged = false,
            contentApplied = true,
            layoutChanged = false,
            positionChanged = false
        )

        assertEquals(ReadOnlyPreviewUpdateAction.None, action)
    }
}
