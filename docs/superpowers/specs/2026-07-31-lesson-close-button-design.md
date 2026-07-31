# Lesson Close Button + Unified Back Behavior

## Problem

The in-lesson (`Practice`) screen has no visible close/exit control. Exiting is only
possible via the Android hardware/gesture back button, and its behavior is
inconsistent: if the child has earned 0 session points so far, back exits straight
to the Path screen; if they've earned >0 points, back instead routes to the
`RewardSummary` end screen before the child can leave. There's no way to close a
lesson directly without seeing (or triggering) the end screen once points exist.

## Goal

- Add a close (X) button to the top-right of the lesson top bar.
- Pressing it exits the lesson immediately, without showing the end screen —
  regardless of session points earned.
- Change the hardware/gesture back button to behave identically while in a lesson:
  always exit directly, never route to the end screen.
- No confirmation dialog on close.

## Out of scope

- Behavior of back on the `RewardSummary` screen itself (unchanged: already exits
  directly to Path).
- The end screen shown for a lesson finished naturally via `advance()` (unchanged).
- Any confirmation/"are you sure" dialog (explicitly not wanted).

## Design

Session points are persisted immediately per correct answer
(`ProgressionEngine.awardPoints` inside `submitRoundResult`/`submitMathResult`), not
just when the `RewardSummary` screen is reached. So skipping the end screen on close
loses no progress — it only skips the celebration screen. The resume snapshot is
kept (`clearSnapshot = false`), matching today's "back with 0 points" path, so the
lesson can still be resumed later.

### `SessionViewModel.kt`

- Add `fun exitLesson() = backToPath(clearSnapshot = false)` — the single shared
  exit action used by both the new close button and the back handler.
- Simplify `onBackPressed()`'s `AppScreen.Practice` branch to always call
  `exitLesson()`, removing the `sessionPoints > 0` check that used to route to
  `RewardSummary`.
- `AppScreen.RewardSummary` branch is unchanged.

### `AbcIcons.kt`

- Add `IconClose`: two crossing strokes drawn with `Canvas`, following the same
  signature/stroke style as the existing `IconChevronLeft`/`IconChevronRight`
  (`tint: Color`, `modifier: Modifier = Modifier`, `size: Dp = 28.dp`, `Stroke` with
  `StrokeCap.Round`/`StrokeJoin.Round`).

### `AbcButtons.kt`

- Add a close icon button matching the visual weight of `ParentGateButton` (48dp
  `Surface`, `MaterialTheme.shapes.medium`, `surfaceVariant` background), with
  `.semantics { contentDescription = ... }` for accessibility, `onClick` parameter.

### `strings.xml`

- Add `close_lesson` string (e.g. "Lektion schließen") for the content description.

### `TaskShell.kt`

- In `PracticeBody`'s top row, replace the existing
  `Spacer(Modifier.size(48.dp))` placeholder (top-right) with the new close button,
  wired to `viewModel.exitLesson()`.

## Testing

- Manual: open a lesson, answer a round correctly (session points > 0), tap the new
  close button → lands on Path screen directly, no end screen shown, points
  persisted.
- Manual: same scenario, press hardware/gesture back → identical behavior.
- Manual: finish a lesson naturally (no more rounds) → end screen still appears as
  before (unaffected).
- Manual: on the end screen, press back → still exits directly to Path (unaffected).
