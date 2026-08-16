# Task 1 report: sentence queue and fragment callback contract

## Changed files

- `app/src/main/java/dev/breenottshook/session/TtsUtterance.kt`
  - Added `TtsUtterance(index: Int, text: String)`.
  - Added `splitUtterances(chunks: List<String>)`, which preserves chunk order, joins fragments across chunk boundaries, splits on Chinese/ASCII sentence punctuation and newlines, retains sentence punctuation, and omits blank utterances.
- `app/src/test/java/dev/breenottshook/session/TtsUtteranceTest.kt`
  - Added coverage for Chinese punctuation, newline boundaries, retained punctuation, cross-chunk source order, empty chunks, and blank filtering.

`TtsInvocation.kt` was not changed because the requested metadata can be carried by the new utterance type without changing existing invocation/callback callers.

## Tests and output

TDD red phase: the focused test initially failed at compilation because `TtsUtterance` and `splitUtterances` did not exist.

Focused test:

`gradlew.bat :app:testDebugUnitTest --tests dev.breenottshook.session.TtsUtteranceTest --no-daemon`

Result: `BUILD SUCCESSFUL` (30 actionable tasks; 8 executed, 22 up-to-date).

Relevant existing session test:

`gradlew.bat :app:testDebugUnitTest --tests dev.breenottshook.session.TtsSessionCoordinatorTest --no-daemon`

Result: `BUILD SUCCESSFUL` (30 actionable tasks; 1 executed, 29 up-to-date).

## Self-review

- API is a standalone data class plus pure splitter; no sensitive text is logged.
- Existing `TtsInvocation` and callback behavior remain untouched.
- Empty chunks, whitespace-only fragments, and repeated newline boundaries cannot create blank utterances.
- Indexes are assigned contiguously in emitted source order.

## Concerns

- Sentence-ending punctuation is intentionally limited to `。！？!?；;`; if product requirements include commas, colons, or other locale-specific marks as boundaries, the delimiter set should be expanded with corresponding tests.
