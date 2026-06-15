package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorToolListStateUtilsTest {
    @Test
    fun appendUpdateDeleteAndMoveEditorToolsPreserveListState() {
        val added = appendNumberedEditorTool(
            tools = listOf(tool("tool-1", "A")),
            idPrefix = "tool-",
            nextNumber = 2,
            name = "B",
            toolId = "title_format",
            parameterOverrides = mapOf("style" to "02"),
            group = "格式"
        )
        val updated = updateEditorToolById(added.tools, "tool-2") { it.copy(name = "B2") }
        val moved = moveEditorToolById(updated!!.tools, "tool-2", "tool-1")
        val deleted = deleteEditorToolById(moved!!, "tool-1")

        assertEquals("tool-2", added.addedTool.id)
        assertEquals(3, added.nextNumber)
        assertEquals("B2", updated.updatedTool.name)
        assertEquals(listOf("tool-2", "tool-1"), moved.map { it.id })
        assertEquals(listOf("tool-2"), deleted?.map { it.id })
        assertNull(deleteEditorToolById(deleted.orEmpty(), "missing"))
        assertNull(moveEditorToolById(deleted.orEmpty(), "tool-2", "tool-2"))
    }

    @Test
    fun missingEditorToolUpdatesDeletesAndMovesReturnNullWithoutChangingList() {
        val tools = listOf(tool("tool-1", "A"), tool("tool-2", "B"))
        var updateCalled = false

        val updated = updateEditorToolById(tools, "missing") { tool ->
            updateCalled = true
            tool.copy(name = "Changed")
        }
        val deleted = deleteEditorToolById(tools, "missing")
        val movedFromMissing = moveEditorToolById(tools, "missing", "tool-1")
        val movedToMissing = moveEditorToolById(tools, "tool-1", "missing")

        assertNull(updated)
        assertNull(deleted)
        assertNull(movedFromMissing)
        assertNull(movedToMissing)
        assertEquals(false, updateCalled)
        assertEquals(listOf("A", "B"), tools.map { it.name })
    }

    @Test
    fun updateEditorToolByIdUpdatesEveryMatchingIdAndReturnsLastUpdatedTool() {
        val tools = listOf(
            tool("tool-1", "A"),
            tool("tool-1", "B"),
            tool("tool-2", "C")
        )

        val updated = updateEditorToolById(tools, "tool-1") { tool ->
            tool.copy(name = "${tool.name}!")
        }

        assertEquals(listOf("A!", "B!", "C"), updated?.tools?.map { it.name })
        assertEquals("B!", updated?.updatedTool?.name)
    }

    @Test
    fun upsertAndMoveEditorToolsKeepCurrentListSemanticsForMissingAndForwardMoves() {
        val tools = listOf(tool("tool-1", "A"), tool("tool-2", "B"), tool("tool-3", "C"))
        val upsertMissing = upsertEditorTool(
            tools = tools,
            tool = tool("missing", "Missing"),
            append = false
        )
        val movedForward = moveEditorToolById(tools, fromToolId = "tool-1", toToolId = "tool-3")

        assertEquals(listOf("tool-1", "tool-2", "tool-3"), upsertMissing.map { it.id })
        assertEquals(listOf("tool-2", "tool-3", "tool-1"), movedForward?.map { it.id })
    }

    @Test
    fun moveEditorToolByIdMovesBackwardBeforeTargetTool() {
        val tools = listOf(tool("tool-1", "A"), tool("tool-2", "B"), tool("tool-3", "C"))

        val moved = moveEditorToolById(tools, fromToolId = "tool-3", toToolId = "tool-1")

        assertEquals(listOf("tool-3", "tool-1", "tool-2"), moved?.map { it.id })
    }

    @Test
    fun editorToolGroupOptionsTrimDistinctAndSortGroups() {
        assertEquals(
            listOf("A", "B"),
            editorToolGroupOptions(
                listOf(
                    tool("1", "One", group = " B "),
                    tool("2", "Two", group = "A"),
                    tool("3", "Three", group = "B"),
                    tool("4", "Four", group = "")
                )
            )
        )
    }

    @Test
    fun replaceEditorToolPresetsForImportDropsReplacedTypesAndNormalizesIds() {
        val current = listOf(
            tool("keep-1", "Keep", toolId = "fetch_info"),
            tool("old-1", "Old", toolId = "text_replace")
        )
        val imported = listOf(
            tool("keep-1", "Imported A", toolId = "text_replace"),
            tool("new-id", "Imported B", toolId = "text_replace")
        )

        val result = replaceEditorToolPresetsForImport(
            currentTools = current,
            selectedToolId = "old-1",
            draftTool = tool("draft", "Draft", toolId = "text_replace"),
            replaceToolIds = setOf("text_replace"),
            importedTools = imported
        )

        assertEquals(listOf("keep-1", "tool-1", "new-id"), result.tools.map { it.id })
        assertEquals(null, result.selectedToolId)
        assertEquals(null, result.draftTool)
        assertEquals(listOf("fetch_info", "text_replace", "text_replace"), result.tools.map { it.toolId })
    }

    @Test
    fun replaceEditorToolPresetsForImportAssignsImportedIdsAgainstKeptTools() {
        val draft = tool("draft", "Draft", toolId = "fetch_info")
        val current = listOf(
            tool("tool-1", "Keep", toolId = "fetch_info"),
            tool("tool-5", "Old", toolId = "text_replace")
        )
        val imported = listOf(
            tool("tool-1", "Conflict", toolId = "text_replace"),
            tool("", "Blank", toolId = "text_replace")
        )

        val result = replaceEditorToolPresetsForImport(
            currentTools = current,
            selectedToolId = "missing",
            draftTool = draft,
            replaceToolIds = setOf("text_replace"),
            importedTools = imported
        )

        assertEquals(listOf("tool-1", "tool-2", "tool-3"), result.tools.map { it.id })
        assertEquals(listOf("Keep", "Conflict", "Blank"), result.tools.map { it.name })
        assertEquals(null, result.selectedToolId)
        assertEquals(draft, result.draftTool)
    }

    @Test
    fun replaceEditorToolPresetsForImportKeepsSelectionAndDraftOutsideReplacedTypes() {
        val draft = tool("draft", "Draft", toolId = "fetch_info")
        val current = listOf(
            tool("keep-1", "Keep", toolId = "fetch_info"),
            tool("old-1", "Old", toolId = "text_replace")
        )

        val result = replaceEditorToolPresetsForImport(
            currentTools = current,
            selectedToolId = "keep-1",
            draftTool = draft,
            replaceToolIds = setOf("text_replace"),
            importedTools = listOf(tool("new-1", "Imported", toolId = "text_replace"))
        )

        assertEquals("keep-1", result.selectedToolId)
        assertEquals(draft, result.draftTool)
        assertEquals(listOf("keep-1", "new-1"), result.tools.map { it.id })
    }

    @Test
    fun replaceEditorToolPresetsForImportAppendsWhenNoCurrentTypesAreReplaced() {
        val draft = tool("draft", "Draft", toolId = "fetch_info")
        val current = listOf(
            tool("tool-1", "Keep A", toolId = "fetch_info"),
            tool("tool-2", "Keep B", toolId = "generate_cover")
        )
        val imported = listOf(tool("tool-1", "Imported", toolId = "text_replace"))

        val result = replaceEditorToolPresetsForImport(
            currentTools = current,
            selectedToolId = "tool-2",
            draftTool = draft,
            replaceToolIds = setOf("text_replace"),
            importedTools = imported
        )

        assertEquals(listOf("tool-1", "tool-2", "tool-3"), result.tools.map { it.id })
        assertEquals(listOf("Keep A", "Keep B", "Imported"), result.tools.map { it.name })
        assertEquals("tool-2", result.selectedToolId)
        assertEquals(draft, result.draftTool)
    }

    @Test
    fun replaceTextReplacePresetsForImportAppliesNormalizerAndUniqueIds() {
        val result = replaceTextReplacePresetsForImport(
            importedPresets = listOf(
                tool("preset-1", "Empty", parameterOverrides = emptyMap()),
                tool("preset-1", "Valid", parameterOverrides = mapOf("find" to "A"))
            ),
            idPrefix = "txt-replace-",
            normalize = { preset -> preset.takeIf { it.parameterOverrides.isNotEmpty() } }
        )

        assertEquals(1, result.size)
        assertEquals("preset-1", result.single().id)
        assertEquals("Valid", result.single().name)
    }

    @Test
    fun replaceTextReplacePresetsForImportAssignsUniqueIdsAfterNormalizerKeepsDuplicates() {
        val result = replaceTextReplacePresetsForImport(
            importedPresets = listOf(
                tool("preset-1", "First", parameterOverrides = mapOf("find" to "A")),
                tool("preset-1", "Second", parameterOverrides = mapOf("find" to "B")),
                tool("", "Blank", parameterOverrides = mapOf("find" to "C"))
            ),
            idPrefix = "txt-replace-",
            normalize = { preset -> preset.takeIf { it.parameterOverrides.isNotEmpty() } }
        )

        assertEquals(listOf("preset-1", "txt-replace-1", "txt-replace-2"), result.map { it.id })
        assertEquals(listOf("First", "Second", "Blank"), result.map { it.name })
    }

    @Test
    fun mergedEditorToolParametersKeepsOnlyEditableOverrides() {
        val definition = ToolDefinition(
            id = "text_replace",
            title = "替换",
            category = "文本",
            description = "",
            implemented = true,
            parameters = listOf(
                ToolParameterDefinition("find", "查找", ""),
                ToolParameterDefinition("replace", "替换", "")
            )
        )
        val merged = mergedEditorToolParameters(
            tool = tool(
                id = "tool-1",
                name = "Replace",
                toolId = "text_replace",
                parameterOverrides = mapOf("find" to "A", "unknown" to "B")
            ),
            toolsById = mapOf("text_replace" to definition)
        )

        assertEquals(mapOf("find" to "A", "replace" to ""), merged)
        assertTrue(upsertEditorTool(listOf(tool("tool-1", "A")), tool("tool-1", "B"), append = false).single().name == "B")
    }

    @Test
    fun upsertEditorToolAppendAddsEvenWhenIdAlreadyExists() {
        val existing = listOf(tool("tool-1", "A"))
        val appended = upsertEditorTool(existing, tool("tool-1", "B"), append = true)

        assertEquals(listOf("A", "B"), appended.map { it.name })
    }

    private fun tool(
        id: String,
        name: String,
        group: String = "",
        toolId: String = "text_replace",
        parameterOverrides: Map<String, String> = emptyMap()
    ): EditorTool {
        return EditorTool(
            id = id,
            name = name,
            group = group,
            toolId = toolId,
            parameterOverrides = parameterOverrides
        )
    }
}
