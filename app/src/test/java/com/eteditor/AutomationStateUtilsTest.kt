package com.eteditor

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationStateUtilsTest {
    @Test
    fun automationGroupModelsCleanAddRenameMoveAndDeleteGroups() {
        val chains = listOf(
            chain("chain-1", "A", group = "旧组"),
            chain("chain-2", "B", group = "")
        )
        val added = addAutomationChainGroupModel(listOf("旧组"), " 新组 ")
        val renamed = renameAutomationChainGroupModel(added.groups, chains, "旧组", "重命名")
        val deleted = deleteAutomationChainGroupModel(renamed.groups, renamed.chains, "重命名")

        assertEquals("新组", added.groupName)
        assertEquals(listOf("重命名", "新组"), renamed.groups)
        assertEquals(listOf("重命名", ""), renamed.chains.map { it.group })
        assertEquals(listOf("新组"), deleted.groups)
        assertEquals(listOf("", ""), deleted.chains.map { it.group })
        assertFalse(addAutomationChainGroupModel(emptyList(), "未分组").success)
        assertNull(moveAutomationChainGroupModel(listOf("A", "B"), 0, 9))
        assertEquals(listOf("B", "A"), moveAutomationChainGroupModel(listOf("A", "B"), 0, 1))
    }

    @Test
    fun automationGroupModelsRejectReservedDuplicateAndBlankRenameTargets() {
        val chains = listOf(
            chain("chain-1", "A", group = "旧组"),
            chain("chain-2", "B", group = "已使用")
        )

        val reserved = renameAutomationChainGroupModel(listOf("旧组"), chains, "未分组", "新组")
        val blank = renameAutomationChainGroupModel(listOf("旧组"), chains, "旧组", " 未分组 ")
        val duplicate = renameAutomationChainGroupModel(listOf("旧组"), chains, "旧组", "已使用")
        val unchanged = renameAutomationChainGroupModel(listOf("旧组"), chains, "旧组", "旧组")

        assertFalse(reserved.success)
        assertEquals("未分组不能改名", reserved.message)
        assertFalse(blank.success)
        assertEquals("请输入分组名", blank.message)
        assertFalse(duplicate.success)
        assertEquals("分组已存在", duplicate.message)
        assertTrue(unchanged.success)
        assertEquals(listOf("旧组"), unchanged.groups)
        assertEquals(chains, unchanged.chains)
    }

    @Test
    fun automationGroupRenameAddsUnsavedUsedGroupToSavedGroups() {
        val chains = listOf(
            chain("chain-1", "A", group = "临时组"),
            chain("chain-2", "B", group = "保留组"),
            chain("chain-3", "C", group = "")
        )

        val renamed = renameAutomationChainGroupModel(
            groups = listOf("保留组"),
            chains = chains,
            group = "临时组",
            newName = " 归档 "
        )

        assertTrue(renamed.success)
        assertEquals("归档", renamed.groupName)
        assertEquals(listOf("保留组", "归档"), renamed.groups)
        assertEquals(listOf("归档", "保留组", ""), renamed.chains.map { it.group })
    }

    @Test
    fun moveAutomationChainWithinDisplayGroupReordersOnlyMatchingGroup() {
        val chains = listOf(
            chain("a", "A", group = "G"),
            chain("x", "X", group = "Other"),
            chain("b", "B", group = "G"),
            chain("c", "C", group = "G")
        )

        val moved = moveAutomationChainWithinDisplayGroupModel(
            chains = chains,
            group = "G",
            chainIds = listOf("a", "b", "c"),
            fromIndex = 2,
            toIndex = 0
        )

        assertEquals(listOf("c", "x", "a", "b"), moved?.map { it.id })
        assertNull(moveAutomationChainWithinDisplayGroupModel(chains, "G", listOf("a"), 0, 0))
    }

    @Test
    fun moveAutomationChainWithinDisplayGroupHandlesUngroupedDisplayGroup() {
        val chains = listOf(
            chain("a", "A", group = ""),
            chain("g", "G", group = "Group"),
            chain("b", "B", group = ""),
            chain("c", "C", group = "")
        )

        val moved = moveAutomationChainWithinDisplayGroupModel(
            chains = chains,
            group = "",
            chainIds = listOf("a", "b", "c"),
            fromIndex = 0,
            toIndex = 2
        )

        assertEquals(listOf("b", "g", "c", "a"), moved?.map { it.id })
        assertNull(moveAutomationChainWithinDisplayGroupModel(chains, "", listOf("a", "b"), -1, 1))
    }

    @Test
    fun automationGroupJsonAndNumberHelpersNormalizeGroupsAndIds() {
        val groups = parseAutomationChainGroups(JSONArray().put(" A ").put("未分组").put("A").put(""))
        val chains = listOf(
            chain("chain-2", "A", steps = listOf(step("step-3"))),
            chain("custom", "B", steps = listOf(step("step-8")))
        )

        assertEquals(listOf("A", "未分组"), groups)
        assertEquals("""["A","未分组"]""", automationChainGroupsToJsonArray(groups).toString())
        assertEquals(3, nextAutomationChainNumberFor(chains))
        assertEquals(9, nextAutomationStepNumberFor(chains))
        assertTrue(listOf(chain("chain-1", "", steps = emptyList())).isLegacyDefaultAutomationChains())
    }

    @Test
    fun automationGroupJsonAndNumberHelpersCoerceValuesAndFallbackFromCollectionSizes() {
        val groups = parseAutomationChainGroups(
            JSONArray()
                .put(" A ")
                .put(123)
                .put("未分组 ")
                .put("未分组")
                .put("B")
        )
        val chains = listOf(
            chain("custom-a", "A", steps = listOf(step("custom-step"))),
            chain("custom-b", "B", steps = emptyList())
        )

        assertEquals(listOf("A", "123", "未分组", "B"), groups)
        assertEquals(3, nextAutomationChainNumberFor(chains))
        assertEquals(1, nextAutomationStepNumberFor(chains))
        assertFalse(emptyList<AutomationChain>().isLegacyDefaultAutomationChains())
        assertFalse(listOf(chain("chain-1", "Named", steps = emptyList())).isLegacyDefaultAutomationChains())
    }

    @Test
    fun automationGroupOptionsAndDisplayMovesKeepUngroupedOutOfSavedGroups() {
        val chains = listOf(
            chain("chain-1", "A", group = "A"),
            chain("chain-2", "B", group = "C"),
            chain("chain-3", "NoGroup", group = "")
        )

        val options = automationChainGroupOptions(
            savedGroups = listOf("A", "未分组", "B"),
            chains = chains
        )
        val movedDisplayGroups = moveAutomationChainDisplayGroupModel(
            displayGroups = listOf("A", "", "B", "A"),
            fromIndex = 2,
            toIndex = 0
        )

        assertEquals("", cleanAutomationChainGroup(" 未分组 "))
        assertEquals(listOf("A", "B", "C"), options)
        assertEquals(listOf("B", "A"), movedDisplayGroups)
        assertNull(moveAutomationChainDisplayGroupModel(listOf("A"), 0, 9))
    }

    @Test
    fun deleteAutomationChainGroupModelClearsUsedGroupEvenWhenGroupWasNotSaved() {
        val chains = listOf(
            chain("chain-1", "A", group = "临时组"),
            chain("chain-2", "B", group = "保留组")
        )

        val deleted = deleteAutomationChainGroupModel(
            groups = listOf("保留组"),
            chains = chains,
            group = "临时组"
        )
        val ungrouped = deleteAutomationChainGroupModel(
            groups = listOf("保留组"),
            chains = chains,
            group = "未分组"
        )

        assertTrue(deleted.success)
        assertEquals(listOf("保留组"), deleted.groups)
        assertEquals(listOf("", "保留组"), deleted.chains.map { it.group })
        assertFalse(ungrouped.success)
    }

    @Test
    fun automationChainDraftAndRemovalUpdateSelection() {
        val draft = createAutomationChainDraftState(selectedChainId = "chain-1", nextNumber = 3)
        val saved = saveAutomationChainDraftState(listOf(chain("chain-1", "A")), draft.draft, "新链")
        val removed = removeAutomationChainById(saved.chains, selectedChainId = "chain-3", chainId = "chain-3")
        val removedLast = removeAutomationChainById(listOf(chain("only", "Only")), selectedChainId = "only", chainId = "only")
        val updatedChains = updateAutomationChainById(saved.chains, chain("chain-3", "改名"))

        assertEquals("chain-3", draft.selectedChainId)
        assertEquals("chain-1", draft.previousSelectedChainId)
        assertEquals(4, draft.nextNumber)
        assertEquals("新链", saved.savedChain.name)
        assertEquals("chain-3", saved.selectedChainId)
        assertEquals("chain-1", removed?.selectedChainId)
        assertEquals(listOf("chain-1"), removed?.chains?.map { it.id })
        assertEquals("chain-1", selectedAutomationChainIdAfterDraftDiscard(saved.chains, "chain-1"))
        assertEquals("chain-1", selectedAutomationChainIdAfterDraftDiscard(saved.chains, "missing"))
        assertEquals("", selectedAutomationChainIdAfterDraftDiscard(emptyList(), "missing"))
        assertEquals("", removedLast?.selectedChainId)
        assertEquals(listOf("A", "改名"), updatedChains.map { it.name })
        assertNull(removeAutomationChainById(saved.chains, selectedChainId = "chain-1", chainId = "missing"))
    }

    @Test
    fun automationChainDraftSavePreservesDraftGroupAndSteps() {
        val existing = chain("chain-1", "Existing")
        val draft = chain(
            id = "chain-2",
            name = "",
            group = "常用",
            steps = listOf(step("step-1"), step("step-2"))
        )

        val saved = saveAutomationChainDraftState(
            chains = listOf(existing),
            draft = draft,
            cleanName = "新链"
        )

        assertEquals(listOf("chain-1", "chain-2"), saved.chains.map { it.id })
        assertEquals("chain-2", saved.selectedChainId)
        assertEquals("新链", saved.savedChain.name)
        assertEquals("常用", saved.savedChain.group)
        assertEquals(listOf("step-1", "step-2"), saved.savedChain.steps.map { it.id })
    }

    @Test
    fun automationChainRemovalKeepsSelectionWhenRemovingOtherChain() {
        val chains = listOf(
            chain("chain-1", "A"),
            chain("chain-2", "B"),
            chain("chain-3", "C")
        )

        val removed = removeAutomationChainById(
            chains = chains,
            selectedChainId = "chain-2",
            chainId = "chain-1"
        )

        assertEquals("chain-1", removed?.removedChain?.id)
        assertEquals("chain-2", removed?.selectedChainId)
        assertEquals(listOf("chain-2", "chain-3"), removed?.chains?.map { it.id })
    }

    @Test
    fun automationStepModelsAppendMoveRemoveUpdateAndConfirmCurrentStep() {
        val chain = chain("chain-1", "Chain")
        val added = appendNumberedAutomationStep(chain, 5, "步骤", "text_replace", mapOf("find" to "A"))
        val second = appendNumberedAutomationStep(added.chain, 6, "步骤二", "title_format", emptyMap())
        val moved = moveAutomationStepAt(second.chain, 1, 0)
        val updatedStep = moved!!.steps[0].copy(name = "更新")
        val updated = updateAutomationStepById(moved, updatedStep)
        val request = AutomationConfirmationRequest("chain-1", 0, updatedStep.id, updatedStep.toolId, "确认")

        assertEquals("step-5", added.step.id)
        assertEquals(6, added.nextNumber)
        assertEquals(listOf("step-6", "step-5"), moved.steps.map { it.id })
        assertEquals("更新", updated.steps[0].name)
        assertEquals(listOf("step-6"), removeAutomationStepAt(updated, 1)?.steps?.map { it.id })
        assertNull(removeAutomationStepAt(updated, 9))
        assertTrue(isAutomationConfirmationStepCurrent(updated, request))
        assertEquals(updatedStep, automationConfirmationStepForRequest(listOf(updated), request))
    }

    @Test
    fun automationStepAppendKeepsPresetIdOverridesAndChainMetadata() {
        val chain = chain(
            id = "chain-1",
            name = "Chain",
            group = "常用",
            steps = listOf(step("step-1"))
        )

        val added = appendNumberedAutomationStep(
            chain = chain,
            nextNumber = 12,
            name = "预设替换",
            toolId = "text_replace",
            parameterOverrides = mapOf("find" to "A"),
            presetId = "preset-1"
        )

        assertEquals("chain-1", added.chain.id)
        assertEquals("Chain", added.chain.name)
        assertEquals("常用", added.chain.group)
        assertEquals(13, added.nextNumber)
        assertEquals(listOf("step-1", "step-12"), added.chain.steps.map { it.id })
        assertEquals(
            AutomationStep(
                id = "step-12",
                name = "预设替换",
                toolId = "text_replace",
                parameterOverrides = mapOf("find" to "A"),
                presetId = "preset-1"
            ),
            added.step
        )
    }

    @Test
    fun automationStepUpdateKeepsChainUnchangedWhenStepIdIsMissing() {
        val chain = chain(
            id = "chain-1",
            name = "Chain",
            steps = listOf(step("step-1"), step("step-2"))
        )

        val updated = updateAutomationStepById(
            chain = chain,
            updated = AutomationStep(id = "missing", name = "Missing", toolId = "fetch_info")
        )

        assertEquals(chain, updated)
    }

    @Test
    fun automationConfirmationRequestMatchesByChainIndexAndStepIdOnly() {
        val target = step("step-2")
        val chain = chain(
            id = "chain-1",
            name = "Chain",
            steps = listOf(step("step-1"), target)
        )
        val request = AutomationConfirmationRequest(
            chainId = "chain-1",
            stepIndex = 1,
            stepId = "step-2",
            toolId = "missing_tool",
            label = "旧确认"
        )

        assertEquals(target, automationConfirmationStepForRequest(chain, request))
        assertEquals(target, automationConfirmationStepForRequest(listOf(chain), request))
        assertTrue(isAutomationConfirmationStepCurrent(chain, request))
        assertNull(automationConfirmationStepForRequest(listOf(chain.copy(id = "other")), request))
    }

    @Test
    fun automationStepModelsRejectInvalidMovesAndStaleConfirmationRequests() {
        val chain = chain(
            id = "chain-1",
            name = "Chain",
            steps = listOf(step("step-1"), step("step-2"))
        )
        val staleRequest = AutomationConfirmationRequest(
            chainId = "chain-1",
            stepIndex = 0,
            stepId = "step-2",
            toolId = "text_replace",
            label = "确认"
        )

        assertNull(moveAutomationStepAt(chain, 0, 0))
        assertNull(moveAutomationStepAt(chain, -1, 0))
        assertNull(moveAutomationStepAt(chain, 0, 9))
        assertNull(automationConfirmationStepForRequest(chain, staleRequest))
        assertNull(automationConfirmationStepForRequest(emptyList(), staleRequest))
        assertFalse(isAutomationConfirmationStepCurrent(chain, staleRequest))
    }

    @Test
    fun automationConfirmationRequestRejectsOutOfRangeStepIndexEvenWhenStepIdExists() {
        val chain = chain(
            id = "chain-1",
            name = "Chain",
            steps = listOf(step("step-1"), step("step-2"))
        )
        val request = AutomationConfirmationRequest(
            chainId = "chain-1",
            stepIndex = 9,
            stepId = "step-2",
            toolId = "text_replace",
            label = "确认"
        )

        assertNull(automationConfirmationStepForRequest(chain, request))
        assertFalse(isAutomationConfirmationStepCurrent(chain, request))
    }

    @Test
    fun automationRunStatusesInitializeAndUpdateByStepId() {
        val chain = chain(
            id = "chain-1",
            name = "Chain",
            steps = listOf(step("step-1"), step("step-2"))
        )
        val initial = initialAutomationRunStepStatuses(chain)
        val updated = updatedAutomationRunStepStatuses(
            statuses = initial,
            step = chain.steps[1],
            state = AutomationRunStepState.NeedsConfirmation,
            message = "等待确认"
        )

        assertEquals(
            mapOf(
                "step-1" to AutomationRunStepStatus("step-1"),
                "step-2" to AutomationRunStepStatus("step-2")
            ),
            initial
        )
        assertEquals(
            AutomationRunStepStatus("step-2", AutomationRunStepState.NeedsConfirmation, "等待确认"),
            updated["step-2"]
        )
    }

    @Test
    fun automationRunStepProgressCreatesStatusAndClampsProgress() {
        val step = step("step-1")

        val low = updatedAutomationRunStepProgress(
            statuses = emptyMap(),
            step = step,
            progress = -0.5f,
            progressText = "准备"
        )
        val high = updatedAutomationRunStepProgress(
            statuses = low,
            step = step,
            progress = 2f,
            progressText = "执行"
        )
        val none = updatedAutomationRunStepProgress(
            statuses = high,
            step = step,
            progress = null,
            progressText = "等待"
        )

        assertEquals(0f, low["step-1"]?.progress)
        assertEquals("准备", low["step-1"]?.progressText)
        assertEquals(1f, high["step-1"]?.progress)
        assertEquals("执行", high["step-1"]?.progressText)
        assertNull(none["step-1"]?.progress)
        assertEquals("等待", none["step-1"]?.progressText)
    }

    @Test
    fun automationRunStepStatusKeepsProgressOnlyForActiveStates() {
        val step = step("step-1")
        val progress = updatedAutomationRunStepProgress(
            statuses = emptyMap(),
            step = step,
            progress = 0.4f,
            progressText = "抓取 1/2"
        )

        val running = updatedAutomationRunStepStatuses(
            statuses = progress,
            step = step,
            state = AutomationRunStepState.Running,
            message = "运行中"
        )
        val needsConfirmation = updatedAutomationRunStepStatuses(
            statuses = running,
            step = step,
            state = AutomationRunStepState.NeedsConfirmation,
            message = "等待确认"
        )
        val completed = updatedAutomationRunStepStatuses(
            statuses = needsConfirmation,
            step = step,
            state = AutomationRunStepState.Completed,
            message = "已完成"
        )

        assertEquals(0.4f, running["step-1"]?.progress)
        assertEquals("抓取 1/2", running["step-1"]?.progressText)
        assertEquals(0.4f, needsConfirmation["step-1"]?.progress)
        assertEquals("抓取 1/2", needsConfirmation["step-1"]?.progressText)
        assertNull(completed["step-1"]?.progress)
        assertEquals("", completed["step-1"]?.progressText)
        assertEquals("已完成", completed["step-1"]?.message)
    }

    @Test
    fun automationRunStepStatusClearsStepProgressWhenFailed() {
        val step = step("step-1")
        val progress = updatedAutomationRunStepProgress(
            statuses = emptyMap(),
            step = step,
            progress = 0.7f,
            progressText = "处理 7/10"
        )

        val failed = updatedAutomationRunStepStatuses(
            statuses = progress,
            step = step,
            state = AutomationRunStepState.Failed,
            message = "处理失败"
        )

        assertEquals(AutomationRunStepState.Failed, failed["step-1"]?.state)
        assertEquals("处理失败", failed["step-1"]?.message)
        assertNull(failed["step-1"]?.progress)
        assertEquals("", failed["step-1"]?.progressText)
    }

    @Test
    fun automationRunStepStatusClearsStepProgressWhenUserConfirmed() {
        val step = step("step-1")
        val progress = updatedAutomationRunStepProgress(
            statuses = emptyMap(),
            step = step,
            progress = 1f,
            progressText = "加载预览 1/1"
        )

        val confirmed = updatedAutomationRunStepStatuses(
            statuses = progress,
            step = step,
            state = AutomationRunStepState.Confirmed,
            message = "已确认，继续执行"
        )

        assertEquals(AutomationRunStepState.Confirmed, confirmed["step-1"]?.state)
        assertEquals("已确认，继续执行", confirmed["step-1"]?.message)
        assertNull(confirmed["step-1"]?.progress)
        assertEquals("", confirmed["step-1"]?.progressText)
    }

    @Test
    fun automationRunTerminalStateCountsAndPrefixesFollowMessages() {
        val counts = listOf(
            AutomationRunStepState.Completed,
            AutomationRunStepState.Skipped,
            AutomationRunStepState.Failed,
            AutomationRunStepState.Running
        ).fold(AutomationRunTerminalCounts()) { current, state ->
            updatedAutomationRunTerminalCounts(current, state)
        }

        assertEquals(AutomationRunTerminalCounts(executed = 1, skipped = 1, failed = 1), counts)
        assertEquals(AutomationRunStepState.Skipped, automationTerminalStateForSuccessMessage("没有匹配内容"))
        assertEquals(AutomationRunStepState.Completed, automationTerminalStateForSuccessMessage("已修改 2 处"))
        assertEquals(AutomationRunStepState.Skipped, automationTerminalStateForFailureMessage("修改 0 章"))
        assertEquals(AutomationRunStepState.Failed, automationTerminalStateForFailureMessage("解析失败"))
        assertTrue(automationStatusMeansSkipped("无需修改"))
        assertFalse(automationStatusMeansSkipped(""))
        assertEquals("完成", automationRunTerminalLogPrefix(AutomationRunStepState.Completed))
        assertEquals("跳过", automationRunTerminalLogPrefix(AutomationRunStepState.Skipped))
        assertEquals("失败", automationRunTerminalLogPrefix(AutomationRunStepState.Failed))
    }

    @Test
    fun automationRunTerminalStateDetectsAllSkipMarkersAndNonTerminalPrefixesFallback() {
        listOf(
            "没有启用任何规则",
            "没有可读取章节",
            "没有可处理内容",
            "无匹配结果",
            "修改 0 处"
        ).forEach { message ->
            assertTrue(automationStatusMeansSkipped(message))
            assertEquals(AutomationRunStepState.Skipped, automationTerminalStateForSuccessMessage(message))
            assertEquals(AutomationRunStepState.Skipped, automationTerminalStateForFailureMessage(message))
        }
        assertFalse(automationStatusMeansSkipped("已修改 10 处"))
        assertEquals("完成", automationRunTerminalLogPrefix(AutomationRunStepState.Waiting))
        assertEquals("完成", automationRunTerminalLogPrefix(AutomationRunStepState.Running))
    }

    private fun chain(
        id: String,
        name: String,
        group: String = "",
        steps: List<AutomationStep> = emptyList()
    ): AutomationChain {
        return AutomationChain(id = id, name = name, group = group, steps = steps)
    }

    private fun step(id: String): AutomationStep {
        return AutomationStep(id = id, name = id, toolId = "text_replace")
    }
}
