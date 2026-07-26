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

## Residuen aus dem Trainer-/Pfad-Umbau (2026-07-26)

| Severity | Area | Note |
|----------|------|------|
| P2 | Content | Lektionen 7–16 sind als gesperrte Pfad-Knoten angelegt, aber noch nicht autoriert (Nutzerentscheidung: Engines zuerst) |
| P2 | Trainer 3 | System-TTS kann einen Laut nicht kontinuierlich dehnen; der Dehnton spielt einmal beim Ziehstart, die Intensivierung ist visuell |
| P2 | Trainer 2 | Strichdaten sind handautoriert pro Graphem; für Lektionen 7–16 müssen `Sch`, `St`, `Qu`, `ß` und die Umlaute noch ergänzt werden |
| P2 | Content | Anlaut-S ist im Deutschen vor Vokal stimmhaft (`Sonne` [ˈzɔnə], `Nase` [ˈnaːzə]), Lektion 6 fordert aber ein stimmloses „Sss". Ein stimmloses initiales S vor Vokal existiert im Deutschen nicht, und System-TTS kann den [s]/[z]-Unterschied nicht rendern — das Anlaut-Beispiel kann also nicht lautgetreu sein. Ein echter zischend/summend-Kontrast bräuchte neue Bildwörter und idealerweise Audioaufnahmen; als Curriculum-Entscheidung zurückgestellt |
| P3 | Trainer 2 | Kein „Straße wird zur Rakete"-Morph-Video, nur ein kurzer Emoji-Reveal |
| P3 | Testing | Weiterhin kein DataStore-Round-Trip-Test und keine Compose-UI-Tests für die Drag-Commits |
| P3 | Content | `Atom.pluralHighlight` wird von keinem Trainer mehr gerendert (Rechnen zeigt bewusst keine Wörter) |
