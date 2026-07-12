# EPUB Interaction Performance Design

## Goal

Remove UI freezes from common EPUB body and directory structure actions while preserving atomic failure behavior and all existing structural updates.

## Scope

This change covers:

- deleting selected body text;
- wrapping selected body text in paragraphs;
- creating a volume from a body selection or body line;
- deleting a body line;
- splitting a chapter from the body;
- adding a volume from the directory;
- deleting one or multiple chapters;
- moving one or multiple chapters.

Batch tools such as full-book replacement, title formatting, file renaming, cover writing, fetched-information application, and chapter insertion are excluded.

## Processing Model

Each covered action captures the current EPUB, creates a lightweight structural snapshot on a background dispatcher, and performs the model operation against that snapshot. Unchanged entry byte arrays are shared; any changed entry is replaced in the snapshot rather than mutating shared bytes.

The active document is replaced only after successful preparation and only if the user has not switched or changed the document in the meantime. A failure or stale result leaves the active document untouched.

## Refresh Rules

Body-only changes refresh the affected chapter information and current preview instead of rebuilding the complete directory.

Actions that add, remove, reorder, split, or change volume hierarchy rebuild the directory because indexes, levels, filenames, and references may all change. This rebuild happens only after background preparation has completed.

## UI Behavior

Covered body actions use one shared operation runner. It accepts suspending work, prevents duplicate actions, and keeps the UI responsive. Work completing within 150 milliseconds shows no progress. Slower work shows the existing operation progress area until completion or failure.

Directory dialogs and confirmation actions launch the suspending structure operation and disable repeat submission while it is running. Fast success closes the relevant dialog directly. Slower operations expose the existing busy/progress state. Failures retain or restore an actionable surface and show the existing failure reason.

## Error Handling

Model validation messages remain unchanged. Unexpected failures are converted to a readable action-specific message. Cancellation is propagated. A result prepared for an outdated document is rejected with a retry message.

## Testing

Focused unit tests will verify:

- every prepared operation uses an isolated structure while sharing unchanged entry bytes;
- body-only actions do not mutate the source and report the affected chapter;
- structural actions do not mutate the source and retain existing result metadata;
- failed operations return no replacement book and preserve validation messages;
- delayed progress remains hidden for fast completion and becomes visible only after the threshold;
- stale prepared results are not applied.

Only directly related tests will run. No package, install, or full build step is included.
