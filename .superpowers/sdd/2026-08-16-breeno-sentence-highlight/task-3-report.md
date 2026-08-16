# Task 3 report: stream adapter integration

## Changed files

- `app/src/main/java/dev/breenottshook/hook/BreenoHostCallbacks.kt`
  - Stream callbacks now forward `onUtteranceStarted(index)` through the existing reflective method resolver.
- `app/src/main/java/dev/breenottshook/hook/BreenoEngineRuntime.kt`
  - Added a `submitStream` adapter while preserving the existing `submit(TtsInvocation)` API/default behavior.
  - Stream text is passed through `splitUtterances` and submitted with stream callbacks and the original-call fallback.
  - Existing explicit/implicit stream start, O0/J0 handling, cancellation, overflow, and fallback replay paths remain intact.
- `app/src/main/java/dev/breenottshook/hook/BreenoHooker.kt`
  - Wired the runtime stream adapter to `TtsSessionCoordinator.submitStream`.
- `app/src/test/java/dev/breenottshook/hook/BreenoHostCallbacksTest.kt`
  - Added reflective utterance-index callback coverage.
- `app/src/test/java/dev/breenottshook/hook/BreenoEngineRuntimeTest.kt`
  - Added sentence-split stream submission coverage.

## TDD and tests

The new tests first failed during compilation because `submitStream` was absent. After implementation:

`gradlew.bat :app:testDebugUnitTest --tests dev.breenottshook.hook.BreenoHostCallbacksTest --tests dev.breenottshook.hook.BreenoEngineRuntimeTest --no-daemon`

Result: `BUILD SUCCESSFUL`.

`gradlew.bat :app:testDebugUnitTest --tests dev.breenottshook.session.TtsSessionCoordinatorTest --tests dev.breenottshook.session.TtsUtteranceTest --no-daemon`

Result: `BUILD SUCCESSFUL`.

One intermediate run hit a transient Windows file-lock error while bundling `classes.jar`; the immediate rerun passed.

## Self-review and concerns

- Reflection remains compatible with listeners declaring any `onUtteranceStarted` method with one parameter, matching the existing resolver strategy.
- The default runtime adapter preserves current test/caller behavior, while production wiring uses `submitStream`.
- `BreenoHooker.kt` also contains unrelated pre-existing worktree changes; the review-fix commit includes the complete currently modified file so the production constructor wiring is committed and independently compilable.

## Review fix

The production wiring was subsequently included in the follow-up commit rather than left as an unstaged worktree hunk. The constructor now passes both `cancelHandler = { coordinator.cancelActive(it) }` and `submitStream = { utterances, callbacks, originalCall -> coordinator.submitStream(utterances, callbacks, originalCall) }`.

## Dependency review fix

`BreenoHooker` also uses `AndroidApiDiagnostics`; `ApiDiagnostics.kt` was previously untracked in the shared worktree. It is now included in the follow-up fix commit so a clean checkout has the complete production dependency.
