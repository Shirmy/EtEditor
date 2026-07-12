# EPUB Batch Tool Performance Design

## Goal

Remove UI stalls and avoid unnecessary memory duplication when EPUB batch tools begin and execute.

## Scope

This change covers EPUB paths for:

- full-book or selected-scope text replacement;
- title renaming;
- chapter filename renaming;
- title formatting;
- chapter insertion;
- cover and image writing;
- applying fetched catalog, introduction, and cover information.

TXT paths and unrelated EPUB editing actions are excluded.

## Processing Model

Each covered EPUB tool captures the active book on the main thread, then creates a lightweight structural snapshot and performs all book mutation on an appropriate background dispatcher. Unchanged entry byte arrays are shared. Replaced or newly written entries receive new byte arrays in the snapshot.

The prepared book is published only when the tool succeeds and the captured source is still the active book. If the document changed while the task was running, the prepared result is rejected with a retry message.

## Tool Groups

### Already Backgrounded

Text replacement, chapter insertion, and asynchronous cover/image writing already move their main processing off the UI thread. Their snapshot creation will move into the same background block and switch from deep content copying to the lightweight structural snapshot.

### Currently Cooperative on the UI Thread

Title renaming, filename renaming, title formatting, and fetched-information application currently update progress and yield between steps but still perform book mutation on the UI thread. Their EPUB mutation loops will move to a background dispatcher. Progress callbacks will be delivered on the main dispatcher at the same logical milestones.

## Progress and Cancellation

Existing progress labels and completion messages remain unchanged. Cancellation is checked during long loops and propagated without publishing partial work. Progress callbacks must not mutate UI state from a background dispatcher.

## Failure Safety

All covered tools keep atomic behavior: failures leave the active book unchanged. Validation and domain-specific failure messages remain unchanged. Unexpected failures use the tool's existing error reporting path.

## Refresh Behavior

After successful publication, existing refresh behavior remains unchanged because batch tools may affect many chapters, filenames, directory titles, package entries, or preview sources.

## Testing

Focused tests will verify:

- lightweight snapshots share unchanged entry bytes and isolate structural mutation;
- each tool's EPUB model operation changes the snapshot without mutating the source;
- stale prepared results are rejected;
- progress callbacks remain ordered and reach completion;
- cancellation does not publish a prepared book;
- existing replacement, rename, formatting, insertion, cover, and fetched-information output remains unchanged.

Only directly related unit tests will run. No package, install, or full build step is included.
