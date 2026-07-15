# TXT Consecutive Save Test Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a regression test proving that a TXT save which renames the file makes the renamed location the target of the next save.

**Architecture:** Extract the write-then-optional-rename sequence into a small generic orchestration helper used by the existing TXT save path. The helper returns the active location and rename outcome, allowing a plain JVM test to execute two consecutive saves with fake storage operations while production continues using Android storage.

**Tech Stack:** Kotlin, coroutines, JUnit 4, existing Android application code.

## Global Constraints

- Only adjust TXT original-file save behavior and its tests.
- Do not change user-visible save, rename, progress, or error behavior.
- Do not add dependencies or require an emulator/device.
- Run only directly related unit tests; do not build, package, or install the app.

---

### Task 1: Consecutive TXT Save Target Regression

**Files:**
- Modify: `app/src/main/java/com/eteditor/document/DocumentFileNameUtils.kt`
- Modify: `app/src/main/java/com/eteditor/document/DocumentSaveController.kt`
- Test: `app/src/test/java/com/eteditor/DocumentUtilityTest.kt`

**Interfaces:**
- Produces: `TxtWriteRenameResult<T>(activeSource, renamed, failureReason)`
- Produces: `writeAndMaybeRenameTxt(source, currentFileName, target, write, rename)`
- Consumes: existing TXT rename target calculation and existing Android write/rename operations.

- [ ] **Step 1: Write the failing consecutive-save test**

Add a coroutine-based unit test that records write locations, returns a new location from the first rename, feeds the returned active location into a second save, and verifies that the writes are exactly the old location followed by the new location.

```kotlin
@Test
fun consecutiveTxtSavesUseRenamedLocationAfterFirstSave() = runBlocking {
    val writes = mutableListOf<String>()
    var activeSource = "content://old.txt"
    var currentFileName = "old.txt"
    val target = resolveTxtSaveRenameTarget("new", "old.txt")

    repeat(2) {
        val result = writeAndMaybeRenameTxt(
            source = activeSource,
            currentFileName = currentFileName,
            target = target,
            write = { source -> writes += source },
            rename = { _, _ -> Result.success("content://new.txt") }
        )
        activeSource = result.activeSource
        currentFileName = if (result.renamed) target.fileName else currentFileName
    }

    assertEquals(listOf("content://old.txt", "content://new.txt"), writes)
    assertEquals("content://new.txt", activeSource)
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.eteditor.DocumentUtilityTest.consecutiveTxtSavesUseRenamedLocationAfterFirstSave"
```

Expected: compilation failure because the orchestration result and helper do not exist yet.

- [ ] **Step 3: Add the minimal orchestration helper**

Add a generic result and suspend helper beside the existing filename/rename utilities. It must write to the supplied source first, skip rename when the current name already matches, and otherwise return either the renamed source or the original source plus the failure reason.

```kotlin
internal data class TxtWriteRenameResult<T>(
    val activeSource: T,
    val renamed: Boolean,
    val failureReason: String? = null
)

internal suspend fun <T> writeAndMaybeRenameTxt(
    source: T,
    currentFileName: String?,
    target: TxtSaveRenameTarget,
    write: suspend (T) -> Unit,
    rename: suspend (T, String) -> Result<T>
): TxtWriteRenameResult<T> {
    write(source)
    if (!shouldRenameTxtAfterSave(currentFileName, target.fileName)) {
        return TxtWriteRenameResult(activeSource = source, renamed = false)
    }
    val renameResult = rename(source, target.fileName)
    return renameResult.fold(
        onSuccess = { renamed -> TxtWriteRenameResult(activeSource = renamed, renamed = true) },
        onFailure = { error ->
            TxtWriteRenameResult(
                activeSource = source,
                renamed = false,
                failureReason = error.message ?: error.javaClass.simpleName
            )
        }
    )
}
```

- [ ] **Step 4: Route the existing TXT save path through the helper**

Keep the existing save check and progress sequence. For TXT, supply the current location, displayed filename, target name, current document writer, and Android rename operation to the helper. Apply the returned active location and base name only when rename succeeds; preserve the existing failure message and writable-location registration.

- [ ] **Step 5: Run the focused test and verify GREEN**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.eteditor.DocumentUtilityTest.consecutiveTxtSavesUseRenamedLocationAfterFirstSave"
```

Expected: PASS, with recorded writes in old-then-new order.

- [ ] **Step 6: Run the related save utility test class**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.eteditor.DocumentUtilityTest"
```

Expected: all tests in the class pass with no failures.

- [ ] **Step 7: Review the scoped diff and commit**

Verify only the two save-related production files and the save utility test changed, then commit:

```powershell
git add app/src/main/java/com/eteditor/document/DocumentFileNameUtils.kt app/src/main/java/com/eteditor/document/DocumentSaveController.kt app/src/test/java/com/eteditor/DocumentUtilityTest.kt
git commit -m "test: cover consecutive txt saves after rename"
```
