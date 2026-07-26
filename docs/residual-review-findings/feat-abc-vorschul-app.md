# Known Residuals — feat/abc-vorschul-app

Source: post-implementation review (2026-07-26)

## Accepted residuals

| Severity | Area | Note |
|----------|------|------|
| P2 | Testing | No DataStore round-trip / corrupt-payload JVM test yet |
| P2 | Testing | No SessionViewModel restore instrumentation; AE9 covered by snapshot model + manual README script |
| P3 | Content | R7 plural letter highlighting not surfaced in math UI (emoji quantities only) |
| P3 | Media | Success audio uses `ToneGenerator` instead of bundled `res/raw` clips |

## Addressed in follow-up commit

- Sentence eligibility before word intros (AE10)
- Sentence prompt leaking gap answers
- Multi-gap miss accounting
- Back from summary exits app (R16)
- Speech/math auto-TTS on task start
- Parent mode scaffold remap deferred to next tasks
- Forced-mode Auto streak freeze
- Advanced math numeral prompt only when TTS unavailable

## Addressed in architecture review (2026-07-26)

- Drag end now uses true slot hit-testing (drop on wrong slot = miss; drop nowhere = snap back)
- Known-atom distractor tiles (max. 2, tray ≤ 5) so single-slot tasks produce a real error signal for the adaptive engine; first encounter stays distractor-free
- `AtomStats`/`MathStats` merged into one `SkillStats` (JSON-compatible; no migration needed)
- `MathHinting.threeChoices` guarantees three options for every answer (previously two for answer = 1)
- Dead UI state removed (`feedback`, `packTitle`, `lastSuccess`)
