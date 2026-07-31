# Known Residuals — feat/wort-detektiv

Source: whole-branch review + scoped fix re-review (2026-08-01)

## Accepted residuals

| Severity | Area | Note |
|----------|------|------|
| P1 | Layout | Umgebrochenes Wort passt auf der 640dp-Höhenklasse nicht ins Höhenbudget — Details unten |
| P2 | Testing | Kein Compose-Testrunner im Projekt; Flug-Geometrie und vertikale Passung nur durch Codelesen geprüft, nie auf einem Gerät |
| P3 | Derivation | `PromptSyllableMany` („Finde alle Silben …") ist mit dem aktuellen Content unerreichbar und untested — korrekt für künftigen Content, kein toter Code |
| P3 | Derivation | Silben-Treffer werden über `atomId` gematcht; ein Wort mit zwei gleich benannten Silben-Atomen und abweichenden Block-Displays könnte ein unehrliches Label erzeugen. Latent — der Invarianten-Test schlägt laut fehl, falls solcher Content entsteht |
| P3 | Trainer | Zwei richtige Tipps innerhalb von 350ms lassen den ersten Glyphen in der Luft verschwinden; Endzustand bleibt korrekt |
| P3 | Trainer | Animationswerte werden in der Composition gelesen (~21 Recompositions pro Treffer) statt in Layout/Draw. Gleiches Muster wie im Geschwister-Trainer |
| P3 | Trainer | Während des letzten 350ms-Flugs pulsiert auf `Beginner`-Stufe die Silhouette des noch leeren Strichs mit |

## P1 im Detail — Höhenbudget bei umgebrochenem Wort

Die Umbruchschwelle hängt an der Gerätebreite: sechs Segmente brauchen 356dp nutzbare
Bühnenbreite, die erst ein ~420dp-Gerät liefert. Auf einem Pixel 7 (393dp → 329dp Bühne)
bricht „Häuser" also um. Das ist korrekt — Klickbarkeit vor Einzeiligkeit.

Das Problem ist die Höhe. `ExerciseStage` clippt seinen Aufgabenblock nicht und scrollt
nicht; Compose klemmt stattdessen die Höhe der letzten Kind-Komponente, und das ist das
Wort. Gemessen für 360×640dp mit 3-Knopf-Navigation:

- Trainer-Box 354dp, davon 67dp für den Antwortblock (137dp sobald „Zeig mir" erscheint)
- Aufgabenblock braucht ~296–305dp, hat ~271dp — also ~1–34dp zu wenig, mit „Zeig mir"
  ~71–104dp zu wenig
- Sichtbare Folge: zweite Wortreihe ~30dp hoch (abgeschnittene Glyphen, Trefferfläche
  unter 56dp); mit „Zeig mir" verschwindet sie ganz

Die 64dp-Reihe (statt 80dp) hat den Bedarf von ~706dp auf ~674dp Gerätehöhe gesenkt, die
Lücke aber nicht geschlossen. Betroffen: L12 („Häuser") auf der 640dp-Höhenklasse und auf
Geräten mit vergrößerter Display-Größe. Bei unter ~320dp Breite fangen auch die
5-Segment-Wörter (Wolke, Zebra, Spinne, Qualle, Bäume, Apfel) an umzubrechen.

`rowHeightDp` liefert außerdem 64dp für *jeden* Umbruch, unabhängig von der Reihenzahl —
ein künftiges Wort mit drei Reihen sprengt das Budget erneut.

**Saubere Lösung, bewusst aufgeschoben:** den Aufgabenblock gegen die gemessene Resthöhe
dimensionieren (eigenes `BoxWithConstraints` in `WordSegments`) oder `ExerciseStage` seinen
Aufgabenblock scrollen/clippen lassen. Letzteres betrifft alle sieben Trainer und gehört
deshalb in eine eigene Änderung.
