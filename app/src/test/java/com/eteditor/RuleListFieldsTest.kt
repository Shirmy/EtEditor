package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Test

class RuleListFieldsTest {
    @Test
    fun ruleDragTargetPositionCoercesWithinListBounds() {
        assertEquals(0, ruleDragTargetPosition(currentPosition = 2, dragOffsetPx = -500f, itemCount = 5, stepPx = 48f))
        assertEquals(4, ruleDragTargetPosition(currentPosition = 2, dragOffsetPx = 500f, itemCount = 5, stepPx = 48f))
        assertEquals(2, ruleDragTargetPosition(currentPosition = 2, dragOffsetPx = 20f, itemCount = 5, stepPx = 48f))
    }

    @Test
    fun ruleListScrollByDeltaFollowsDragDeltaForLazyList() {
        assertEquals(-8f, ruleListScrollByDeltaForDragDelta(-8f), 0.001f)
        assertEquals(8f, ruleListScrollByDeltaForDragDelta(8f), 0.001f)
        assertEquals(0f, ruleListScrollByDeltaForDragDelta(0f), 0.001f)
    }
}
