# EPUB Batch Tool Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move EPUB batch snapshot creation and mutation off the UI thread while preserving progress, cancellation, and output.

**Architecture:** Reuse the lightweight structural snapshot introduced for interactive EPUB operations. Tools that already use background dispatchers will create their snapshots inside those blocks; cooperative batch loops will process EPUB mutations in background chunks and report progress on the main dispatcher.

**Tech Stack:** Kotlin, Jetpack Compose state controllers, Kotlin coroutines, JUnit 4

## Global Constraints

- Preserve existing progress labels and completion messages.
- Never publish partial or stale results.
- Keep TXT behavior unchanged.
- Keep current full-directory refresh behavior after successful batch publication.
- Run focused unit tests only; do not package or install.

---

### Task 1: Existing Background Tools

**Files:**
- Modify: `app/src/main/java/com/eteditor/tools/textreplace/TextReplaceController.kt`
- Modify: `app/src/main/java/com/eteditor/tools/insertchapter/InsertChapterController.kt`
- Modify: `app/src/main/java/com/eteditor/tools/cover/CoverController.kt`
- Test: `app/src/test/java/com/eteditor/EpubStructureUtilsTest.kt`

- [ ] Add a failing helper test proving background-tool snapshots share unchanged bytes.
- [ ] Move lightweight snapshot creation into each existing background block.
- [ ] Guard publication with source identity.
- [ ] Run replacement, insertion, cover, and EPUB structure tests.

### Task 2: Rename and Format Tools

**Files:**
- Modify: `app/src/main/java/com/eteditor/tools/titlerename/TitleRenameController.kt`
- Modify: `app/src/main/java/com/eteditor/tools/filerename/FileRenameController.kt`
- Modify: `app/src/main/java/com/eteditor/tools/titleformat/TitleFormatController.kt`
- Test: `app/src/test/java/com/eteditor/TitleRenameUtilsTest.kt`
- Test: `app/src/test/java/com/eteditor/FileRenameUtilsTest.kt`
- Test: `app/src/test/java/com/eteditor/TitleFormatUtilsTest.kt`

- [ ] Add failing tests for a reusable background batch progress bridge.
- [ ] Process EPUB snapshot mutations on `Dispatchers.Default`.
- [ ] Deliver ordered progress updates on `Dispatchers.Main.immediate` between chunks.
- [ ] Reject stale prepared books and preserve existing result messages.
- [ ] Run the three focused tool test classes.

### Task 3: Fetched Information

**Files:**
- Modify: `app/src/main/java/com/eteditor/tools/fetchinfo/FetchInfoController.kt`
- Test: `app/src/test/java/com/eteditor/FetchInfoEpubWriteUtilsTest.kt`
- Test: `app/src/test/java/com/eteditor/FetchInfoProgressUtilsTest.kt`

- [ ] Add a failing preparation test covering catalog and introduction writes.
- [ ] Create the lightweight snapshot in the background.
- [ ] Move catalog and introduction mutation off the UI thread while keeping cover download on the IO dispatcher.
- [ ] Guard publication and run focused fetch-info tests.

### Task 4: Verification and Commit

- [ ] Run all directly related tool tests and EPUB structure tests.
- [ ] Run `git diff --check` and inspect every remaining deep-copy call in covered tools.
- [ ] Commit source, tests, and this plan with a concise Chinese message.
