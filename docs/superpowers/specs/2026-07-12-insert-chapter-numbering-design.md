# Inserted Chapter Numbering Design

## Goal

Number inserted EPUB chapters from the nearest real numbered body chapter before the insertion point instead of counting preceding body chapters.

## Rules

1. Search backward from the insertion point.
2. Ignore cover and volume entries.
3. Ignore body entries whose titles have no recognized chapter number and continue searching backward.
4. If the nearest numbered title is chapter 68, the first inserted non-volume chapter is chapter 69.
5. Additional inserted non-volume chapters increment sequentially.
6. Inserted volume entries do not consume a chapter number.
7. If no numbered body title exists before the insertion point, preserve every source title and do not add a chapter prefix.
8. Existing title suffix and formatting behavior remains unchanged when renumbering occurs.

## Impact

Chapter numbering follows the visible numbering near the insertion point and no longer drifts because of covers, volumes, unnumbered entries, or missing chapter numbers. File naming and insertion position behavior are unchanged.

## Testing

Focused tests cover a real chapter-68 predecessor, intervening unnumbered titles, no preceding numbered title, multiple inserted chapters, and volume entries that do not consume a number.
