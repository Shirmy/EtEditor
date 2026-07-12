# EPUB Directory Edit Save Performance Design

## Goal

Make saving an edited EPUB directory item feel immediate on books with many chapters, while preserving validation and failure feedback in the edit dialog.

## Current Problem

Saving one directory item currently deep-copies every EPUB entry, including all chapter bodies and assets, and then rebuilds the complete directory and preview. The amount of work grows with the whole book even when only one title changes. Because this work runs while the dialog is handling the save click, the dialog appears frozen.

## Save Behavior

1. The user edits a title and, when filename display is enabled, may also edit the filename.
2. Saving validates the draft before starting work. A validation failure remains in the dialog and displays its reason.
3. The save runs without blocking dialog rendering.
4. If the save finishes before 150 milliseconds, the dialog closes immediately with no success message or visible loading state.
5. If it is still running after 150 milliseconds, the dialog shows a saving state and disables editing and repeated submission.
6. A successful save closes the dialog without a success message.
7. A failed save keeps the dialog open, restores its controls, preserves the draft, and displays the failure reason in the dialog.

## Data Handling

The directory-item save path will use a lightweight editable book snapshot. It will copy the book structure needed for rollback but share unchanged entry byte arrays. Operations that change an entry already replace that entry's value, so unchanged chapter bodies and assets do not need byte-for-byte duplication.

After a successful title-only change, only the affected directory item and preview are refreshed. A filename change may update references in other EPUB files, but it still avoids duplicating unchanged entry contents. The existing full deep-copy behavior remains unchanged for unrelated structural operations.

## Error Handling

The save operation returns a failure reason to the dialog rather than relying only on the page-level status area. Unexpected failures are converted to a readable save-failure message. No partially edited book becomes the active document because work is performed against the lightweight snapshot and published only after success.

## Testing

Focused tests will verify:

- the lightweight snapshot does not duplicate unchanged entry byte arrays;
- changing an entry in the snapshot does not alter the original entry mapping;
- title-only edits update the selected chapter and its stored HTML;
- filename edits preserve existing validation and reference updates;
- failed saves preserve the dialog draft and expose the failure reason through the save result;
- fast success closes directly, while delayed work exposes the saving state only after the threshold.

No build, package, or install step is part of this change. Only directly related tests will run.
