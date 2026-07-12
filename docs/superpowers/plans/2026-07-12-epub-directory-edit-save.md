# EPUB Directory Edit Save Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make EPUB directory-item saves non-blocking and avoid copying unchanged chapter bodies and assets.

**Architecture:** Prepare an isolated lightweight EPUB snapshot on a background dispatcher, publish it only after a successful model update, and refresh only the affected directory item. The dialog owns its transient saving/error state and reveals the saving indicator only after 150 milliseconds.

**Tech Stack:** Kotlin, Android Jetpack Compose, Kotlin coroutines, JUnit 4

## Global Constraints

- Fast saves close with no success message.
- Failed saves keep the dialog open, preserve the draft, and show the reason.
- The saving state appears only when work exceeds 150 milliseconds.
- Do not change unrelated structural operations.
- Run focused unit tests only; do not build, package, or install.

---

### Task 1: Lightweight EPUB Snapshot

**Files:**
- Modify: `app/src/main/java/com/eteditor/epub/EpubBookCopyUtils.kt`
- Modify: `app/src/test/java/com/eteditor/EpubStructureUtilsTest.kt`

**Interfaces:**
- Produces: `internal fun EpubBook.mutableStructureCopy(): EpubBook`

- [ ] **Step 1: Write the failing snapshot tests**

Add tests that assert the copied book has independent maps, manifest values, chapter values, and alias sets while unchanged entry byte arrays retain reference identity.

- [ ] **Step 2: Run the focused tests and verify failure**

Run `./gradlew.bat testDebugUnitTest --tests "com.eteditor.EpubStructureUtilsTest"` and expect compilation to fail because `mutableStructureCopy` does not exist.

- [ ] **Step 3: Implement the lightweight snapshot**

Copy the entries map without cloning byte arrays, and copy every mutable structural collection/value exactly as the existing deep copy does.

- [ ] **Step 4: Run the focused tests and verify pass**

Run the same test command and expect all `EpubStructureUtilsTest` cases to pass.

### Task 2: Prepared Directory Save Result

**Files:**
- Modify: `app/src/main/java/com/eteditor/epub/EpubStructureController.kt`
- Modify: `app/src/test/java/com/eteditor/EpubStructureUtilsTest.kt`

**Interfaces:**
- Produces: `internal data class EpubChapterItemSaveResult(val book: EpubBook? = null, val message: String = "")`
- Produces: `internal fun prepareEpubChapterItemSave(source: EpubBook, chapterIndex: Int, fileName: String, chapterTitle: String): EpubChapterItemSaveResult`
- Produces: `internal fun EditorController.applyPreparedEpubChapterItemSave(chapterIndex: Int, result: EpubChapterItemSaveResult): Boolean`

- [ ] **Step 1: Write failing save-result tests**

Add one test proving a valid title edit returns a new book without mutating the source, and one proving duplicate filename validation returns no book plus the existing reason.

- [ ] **Step 2: Run the focused tests and verify failure**

Run the same focused command and expect compilation failure for the missing preparation API.

- [ ] **Step 3: Implement preparation and publishing**

Prepare against `mutableStructureCopy`, reuse `updateEpubChapterItemModel`, and publish only successful results. On publish, mark the document changed, clear the rename plan, refresh the single directory item with preview refresh, and preserve the failure reason.

- [ ] **Step 4: Run the focused tests and verify pass**

Run the same focused command and expect all cases to pass.

### Task 3: Non-blocking Dialog State

**Files:**
- Modify: `app/src/main/java/com/eteditor/ui/directory/DirectoryDialogs.kt`
- Modify: `app/src/main/java/com/eteditor/ui/directory/DirectoryPanelFields.kt`
- Create: `app/src/main/java/com/eteditor/ui/directory/DirectoryEditSaveState.kt`
- Create: `app/src/test/java/com/eteditor/DirectoryEditSaveStateTest.kt`

**Interfaces:**
- Produces: `internal data class DirectoryEditSaveState(val saving: Boolean = false, val message: String = "")`
- Produces: `internal fun directoryEditSaveStarted(): DirectoryEditSaveState`
- Produces: `internal fun directoryEditSaveFailed(message: String): DirectoryEditSaveState`
- The dialog receives a suspending save callback and closes only when it returns success.

- [ ] **Step 1: Write failing state tests**

Assert that starting clears an old error and marks saving, and failure restores editing while retaining the returned reason.

- [ ] **Step 2: Run both focused test classes and verify failure**

Run `./gradlew.bat testDebugUnitTest --tests "com.eteditor.EpubStructureUtilsTest" --tests "com.eteditor.DirectoryEditSaveStateTest"` and expect compilation failure for the missing state helpers.

- [ ] **Step 3: Implement state helpers and asynchronous dialog save**

Launch save preparation on `Dispatchers.Default`, start a 150 ms delayed indicator job, keep controls enabled until the indicator is actually shown, prevent duplicate submission with a private in-flight flag, publish on the main dispatcher, close immediately on success, and show the returned reason on failure.

- [ ] **Step 4: Run both focused test classes and verify pass**

Run the same focused command and expect both test classes to pass.

- [ ] **Step 5: Review and commit**

Run `git diff --check`, inspect the scoped diff, then commit source, tests, and this plan with a concise Chinese commit message.
