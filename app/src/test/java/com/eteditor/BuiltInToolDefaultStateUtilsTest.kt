package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Test

class BuiltInToolDefaultStateUtilsTest {
    @Test
    fun updateBuiltInToolOverridesAddsUpdatesAndRemovesEmptyOverrides() {
        val added = updateBuiltInToolOverrides(
            currentOverrides = emptyMap(),
            toolId = "text_replace",
            overrides = mapOf("find" to "A")
        )
        val removed = updateBuiltInToolOverrides(
            currentOverrides = added,
            toolId = "text_replace",
            overrides = emptyMap()
        )

        assertEquals(mapOf("text_replace" to mapOf("find" to "A")), added)
        assertEquals(emptyMap<String, Map<String, String>>(), removed)
    }

    @Test
    fun updateBuiltInToolOverridesReplacesOnlyTargetToolAndPreservesOthers() {
        val current = mapOf(
            "text_replace" to mapOf("find" to "Old", "replace" to "Keep?"),
            "fetch_info" to mapOf("query" to "Book")
        )

        val updated = updateBuiltInToolOverrides(
            currentOverrides = current,
            toolId = "text_replace",
            overrides = mapOf("find" to "New")
        )

        assertEquals(
            mapOf(
                "text_replace" to mapOf("find" to "New"),
                "fetch_info" to mapOf("query" to "Book")
            ),
            updated
        )
    }

    @Test
    fun saveBuiltInDefaultParameterOverridesUsesCleanerBeforeSaving() {
        val saved = saveBuiltInDefaultParameterOverrides(
            savedDefaults = mapOf("fetch_info" to mapOf("query" to "Book")),
            toolId = "text_replace",
            overrides = mapOf("find" to "A", "default" to ""),
            cleanOverridesForSave = { _, overrides -> overrides.filterValues { it.isNotBlank() } }
        )

        assertEquals(
            mapOf(
                "fetch_info" to mapOf("query" to "Book"),
                "text_replace" to mapOf("find" to "A")
            ),
            saved
        )
    }

    @Test
    fun saveBuiltInDefaultParameterOverridesRemovesSavedToolWhenCleanerReturnsEmpty() {
        val saved = saveBuiltInDefaultParameterOverrides(
            savedDefaults = mapOf(
                "text_replace" to mapOf("find" to "Old"),
                "fetch_info" to mapOf("query" to "Book")
            ),
            toolId = "text_replace",
            overrides = mapOf("find" to ""),
            cleanOverridesForSave = { _, _ -> emptyMap() }
        )

        assertEquals(
            mapOf("fetch_info" to mapOf("query" to "Book")),
            saved
        )
    }

    @Test
    fun resetBuiltInParameterOverridesFromDefaultsResetsOneToolOrAllTools() {
        val current = mapOf(
            "text_replace" to mapOf("find" to "Current"),
            "fetch_info" to mapOf("query" to "Current")
        )
        val saved = mapOf(
            "text_replace" to mapOf("find" to "Saved")
        )

        assertEquals(saved, resetBuiltInParameterOverridesFromDefaults(current, saved, null))
        assertEquals(
            mapOf(
                "text_replace" to mapOf("find" to "Saved"),
                "fetch_info" to mapOf("query" to "Current")
            ),
            resetBuiltInParameterOverridesFromDefaults(current, saved, "text_replace")
        )
        assertEquals(
            mapOf("text_replace" to mapOf("find" to "Current")),
            removeBuiltInDefaultParameterOverrides(current, "fetch_info")
        )
    }

    @Test
    fun resetBuiltInParameterOverridesFromDefaultsAllToolsUsesSavedDefaultsSnapshot() {
        val current = mapOf(
            "text_replace" to mapOf("find" to "Current"),
            "fetch_info" to mapOf("query" to "Current")
        )
        val saved = mapOf(
            "title_format" to mapOf("style" to "Saved")
        )

        assertEquals(saved, resetBuiltInParameterOverridesFromDefaults(current, saved, null))
    }

    @Test
    fun resetBuiltInParameterOverridesFromDefaultsRemovesCurrentToolWhenSavedDefaultIsMissing() {
        val current = mapOf(
            "text_replace" to mapOf("find" to "Current"),
            "fetch_info" to mapOf("query" to "Current")
        )
        val saved = mapOf(
            "fetch_info" to mapOf("query" to "Saved")
        )

        assertEquals(
            mapOf("fetch_info" to mapOf("query" to "Current")),
            resetBuiltInParameterOverridesFromDefaults(current, saved, "text_replace")
        )
    }
}
