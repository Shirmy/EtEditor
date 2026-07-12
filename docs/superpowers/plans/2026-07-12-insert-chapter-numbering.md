# Inserted Chapter Numbering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Continue inserted EPUB chapter numbering from the nearest real preceding chapter number.

**Architecture:** Add one pure backward-search helper for the starting number, then use its nullable result to decide whether inserted non-volume chapters should be renumbered. Existing title rendering remains unchanged.

**Tech Stack:** Kotlin, JUnit 4

## Global Constraints

- Preserve source titles when no preceding numbered chapter exists.
- Ignore covers, volumes, and unnumbered titles during backward search.
- Do not change file naming or insertion positions.
- Run focused tests only; do not package or install.

---

### Task 1: Number Resolution

**Files:**
- Modify: `app/src/main/java/com/eteditor/tools/insertchapter/InsertChapterPositionUtils.kt`
- Modify: `app/src/test/java/com/eteditor/InsertChapterUtilsTest.kt`

- [ ] Add failing tests for chapter 68, skipped unnumbered titles, and no numbered predecessor.
- [ ] Implement the backward-search helper.
- [ ] Run `InsertChapterUtilsTest`.

### Task 2: Import Integration

**Files:**
- Modify: `app/src/main/java/com/eteditor/tools/insertchapter/InsertChapterEpubImportUtils.kt`
- Modify: `app/src/test/java/com/eteditor/InsertChapterEpubImportUtilsTest.kt`

- [ ] Add failing integration tests for sequential insertion and no-number preservation.
- [ ] Replace count-based numbering with the nullable resolved start number.
- [ ] Run both focused insertion test classes.

### Task 3: Verification and Commit

- [ ] Run focused insertion tests and `git diff --check`.
- [ ] Commit source, tests, specification, and plan.
