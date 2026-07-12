# EPUB Interaction Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep common EPUB body and directory actions responsive without copying unchanged book contents.

**Architecture:** A shared preparation helper creates a lightweight isolated book snapshot on a background dispatcher and rejects stale results before publication. Covered controller actions become suspending operations; body and directory UI callbacks launch them through one delayed-progress runner.

**Tech Stack:** Kotlin, Jetpack Compose, Kotlin coroutines, JUnit 4

## Global Constraints

- Progress remains hidden for work completing within 150 milliseconds.
- Failures never replace the active book.
- Body-only changes refresh one chapter; structural changes rebuild the directory.
- Batch tools are excluded.
- Run focused tests only; do not package or install.

---

### Task 1: Shared Prepared Mutation

**Files:**
- Create: `app/src/main/java/com/eteditor/epub/EpubStructureMutationController.kt`
- Modify: `app/src/test/java/com/eteditor/EpubStructureUtilsTest.kt`

**Interfaces:**
- Produces: `PreparedEpubMutation<R>` containing the source, prepared book, and model result.
- Produces: a background preparation helper using `mutableStructureCopy()`.
- Produces: a publication guard that rejects results when the active book changed.

- [ ] Write failing tests proving the source remains unchanged and stale results are rejected.
- [ ] Run `EpubStructureUtilsTest` and verify the new API is missing.
- [ ] Implement the shared preparation and publication helpers.
- [ ] Run `EpubStructureUtilsTest` and verify it passes.

### Task 2: Body Operations

**Files:**
- Modify: `app/src/main/java/com/eteditor/epub/EpubStructureController.kt`
- Modify: `app/src/main/java/com/eteditor/ui/body/BodyPreviewFields.kt`
- Modify: `app/src/test/java/com/eteditor/EpubStructureUtilsTest.kt`

**Interfaces:**
- Covered suspending actions: selection deletion, paragraph wrapping, selection-to-volume, line deletion, line-to-volume, and chapter splitting.
- Body-only actions publish with one-chapter refresh; hierarchy-changing actions publish with full directory refresh.

- [ ] Write failing preparation tests for one body-only action and one hierarchy-changing action.
- [ ] Run the focused tests and verify failure.
- [ ] Convert covered body actions to prepare off the UI thread and publish on success.
- [ ] Replace the body runner's fixed delays with delayed progress and suspending work.
- [ ] Route all covered long-press callbacks through the shared runner.
- [ ] Run the focused tests and verify pass.

### Task 3: Directory Operations

**Files:**
- Modify: `app/src/main/java/com/eteditor/epub/EpubStructureController.kt`
- Modify: `app/src/main/java/com/eteditor/ui/directory/DirectoryPanelFields.kt`
- Modify: `app/src/main/java/com/eteditor/ui/directory/DirectoryDialogs.kt`
- Modify: `app/src/main/java/com/eteditor/ui/directory/DirectoryMoveDialogs.kt`
- Modify: `app/src/test/java/com/eteditor/EpubStructureUtilsTest.kt`

**Interfaces:**
- Covered suspending actions: add volume, delete one/many chapters, and move one/many chapters.
- Every caller launches through delayed progress and prevents duplicate work via the existing busy state.

- [ ] Write failing preparation tests for delete and move without source mutation.
- [ ] Run the focused tests and verify failure.
- [ ] Convert covered directory actions to prepared suspending operations.
- [ ] Update every directory caller to launch the operation and close dialogs only on success.
- [ ] Run the focused tests and verify pass.

### Task 4: Verification and Commit

**Files:**
- Modify: `docs/superpowers/plans/2026-07-12-epub-interaction-performance.md`

- [ ] Run the focused EPUB structure and operation-state tests.
- [ ] Run `git diff --check` and inspect all covered call sites.
- [ ] Commit source, tests, and plan with a concise Chinese message.
