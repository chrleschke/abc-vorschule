# Interaktion sperren, bis die Aufgabenansage vorgelesen wurde

**Datum:** 2026-08-07
**Status:** Genehmigt

## Problem

Bei mehreren Übungen (Trainern) wird die Aufgabe zu Beginn vorgelesen (z.B. "Finde
den Buchstaben - A - im Wort - Mama."). Tippt das Kind sofort auf ein Element,
bevor die Ansage fertig ist, wird die laufende Sprachausgabe durch das
Tap-Feedback-Audio unterbrochen — das Kind hört nie die vollständige Aufgabe.

Betroffen sind alle Trainer, deren Tap-Handler selbst Audio abspielt (das
Sprachausgabe-System arbeitet mit "neuer Aufruf stoppt vorherigen" — Flush-
Semantik). Der Spurensucher-Trainer (Buchstabe nachzeichnen) ist NICHT betroffen,
da seine Dreh-/Tipp-Gesten nie eine eigene Sprachausgabe auslösen — es gibt dort
nichts, das die Ansage unterbrechen könnte.

## Betroffene Trainer (7 von 8)

| Trainer (Rundentyp) | Datei |
|---|---|
| Wort-Detektiv (`SymbolInWordRound`) | `ui/exercise/SymbolInWordTrainer.kt` |
| Buchstaben-/Silben-Jagd (`SymbolHuntRound`) | `ui/exercise/SymbolHuntTrainer.kt` |
| Satzbau (`SentenceOrderRound`) | `ui/exercise/SentenceOrderTrainer.kt` |
| Lautposition (`SoundPositionRound`) | `ui/exercise/SoundPositionTrainer.kt` |
| Silben-Verschmelzung (`SyllableMergeRound`) | `ui/exercise/SyllableMergeTrainer.kt` |
| Wort-Bauer (`WordBuildRound`) | `ui/exercise/WordBuildTrainer.kt` |
| Rechnen (`CountAddRound`) | `ui/exercise/MathExercise.kt` |

**Nicht betroffen:** Spurensucher (`ui/exercise/LetterTraceTrainer.kt`) — bleibt
unverändert.

## Architektur

### Freigabe-Index statt Sonderfall-Branching

`SessionViewModel.currentPromptParts()` liefert für jede Runde eine Liste von
Sprach-Teilen. Für 6 der 7 Trainer ist das eine Liste mit genau einem Element
(die komplette Ansage als ein Satz). Nur Wort-Detektiv liefert bis zu 4 Teile:
`[Intro-Phrase, Ziel-Graphem, Konnektor "...im Wort...", Ziel-Wort]`.

Statt die Freigabe-Logik pro Trainer zu verzweigen, bekommt jeder Rundentyp
einen **Freigabe-Index** in die Teile-Liste:

- **Standard (6 Trainer + Buchstaben-/Silben-Jagd):** letzter Teil-Index — die
  Sperre fällt exakt am Ende der vollständigen Ansage. Bei Buchstaben-/Silben-
  Jagd ist der letzte Teil zugleich der Ziel-Laut, es gibt also ohnehin keinen
  Unterschied zu "warte auf volles Ende".
- **Ausnahme Wort-Detektiv:** Index 1 (das Ziel-Graphem/-Phonem). Sobald dieser
  Teil fertig gesprochen ist, kann das Kind die Kacheln durchsuchen — Konnektor
  und Zielwort laufen im Hintergrund weiter zu Ende.

`SpeechController.speakAndAwaitSequence(...)` bekommt einen optionalen
`onPartComplete: (index: Int) -> Unit`-Callback, der nach jedem gesprochenen
Teil (mit Original-Index, auch wenn leere Teile vorher rausgefiltert wurden)
feuert. Die restlichen Teile spielen unabhängig von der Freigabe zu Ende.

### Sperr-Zustand

`TaskShell.kt` hält einen neuen State `interactionLocked: Boolean`, zurückgesetzt
auf `true` im selben `LaunchedEffect`, der aktuell die Rundenansage auslöst
(Key: `task?.spec?.id`, `state.roundIndex`, `ttsAvailable`). Der State wird über
`TrainerCallbacks` an `TrainerHost` und von dort an die jeweilige Trainer-
Composable weitergereicht.

Freigabe passiert, sobald `onPartComplete` für den Freigabe-Index feuert.
Sicherheitsnetze, damit die Sperre nie hängen bleibt:

- Ist `ttsAvailable == false` oder die Teile-Liste leer, wird sofort entsperrt
  (kein Audio zum Warten vorhanden).
- Der bestehende 10s-Timeout in `speakAndAwait` greift weiterhin pro Teil —
  ein defekter/fehlender Clip blockiert die Sperre also maximal 10s.

## Visuelle & interaktive Umsetzung pro Trainer

An den tippbaren Elementen jedes betroffenen Trainers (Buchstaben-Kacheln,
Silben, Antwortoptionen, Zahlen-Pad, Wort-Segmente, Satzbau-Wortkarten):

- `enabled = <bisherige Bedingung> && !interactionLocked`
- Deckkraft-Animation nur auf die interaktiven Elemente selbst (nicht auf
  Titel, Maskottchen, Fortschrittsanzeige, Aufgabentext oder den Lautsprecher-
  Button): `val opacity by animateFloatAsState(if (interactionLocked) 0.5f else 1f, tween(200))`,
  angewendet über `Modifier.alpha(opacity)`.
- Kein Lock-Icon, kein Overlay über die ganze Fläche — nur die reduzierte
  Deckkraft signalisiert den Zustand.
- Tippen während der Sperre wird komplett ignoriert (kein Sound, keine
  Wackel-Animation) — ergibt sich automatisch aus `enabled = false`.
- Der Lautsprecher-Button in `TaskPromptChrome` (Ansage wiederholen) bleibt
  **immer** aktiv, auch während der Sperre.
- Bereits bestehende lokale Sperren (z.B. `locked` in `MathExercise` für
  "schon beantwortet") bleiben als eigenständiges Konzept bestehen und werden
  nur zusätzlich mit `!interactionLocked` UND-verknüpft — keine Umbenennung
  oder Vermischung.

## Sonderfall Wort-Detektiv: gleichzeitige Audios

Nach der Frühfreigabe (Index 1) kann das Kind tippen, während Konnektor+Wort
noch auf dem bisherigen Sprachkanal laufen. Der Tap-Handler löst aber selbst
eine Audio-Ausgabe aus (Echo des angetippten Segments) — mit der heutigen
Flush-Semantik würde dieses Echo die noch laufende Ansage abwürgen.

**Lösung:** neuer `SpeechChannel`-Begriff (`Primary`, `Feedback`) in
`SpeechController`:

- Jeder Channel bekommt eine eigene `ClipPlayer`-Instanz (zwei unabhängige
  `MediaPlayer`), sodass Aufrufe auf verschiedenen Channels sich nicht
  gegenseitig stoppen. Aufrufe auf demselben Channel flushen sich weiterhin
  gegenseitig wie bisher (z.B. schnelles mehrfaches Antippen).
- Die Android-TTS-Engine bleibt eine gemeinsame Instanz (kein Duplizieren) —
  der Fallback-Fall ohne vorgerenderten Clip ist für diesen Trainer selten,
  da die Wort-Detektiv-Prompts bereits mit kuratierten Seeds vorgerendert
  werden (siehe `aa7a51d`, `feat(tts): batch render`-Commits).
- `speak()`, `speakAndAwait()`, `speakAndAwaitSequence()` bekommen einen
  optionalen `channel`-Parameter, Default `Primary` — für alle bisherigen
  Aufrufstellen (Rundenansage, alle anderen Trainer) ändert sich dadurch
  nichts.
- Nur `SymbolInWordTrainer.handleTap()`s `onSpeak(...)`-Aufruf für das
  Segment-Echo wechselt auf `channel = Feedback`.
- `onStopSpeak()` (aufgerufen bei Rundenwechsel) stoppt künftig beide Channel-
  Player plus die TTS-Engine.

Damit laufen Ansage-Rest und Tap-Echo hörbar parallel, statt sich zu
unterbrechen — genau wie gefordert.

## Nicht im Scope

- Spurensucher-Trainer bleibt unverändert (kein Interruption-Problem).
- Kein visuelles/akustisches Feedback bei ignoriertem Tap während der Sperre.
- Keine Vorlauf-Freigabe (Entsperren X ms vor Audio-Ende) für einteilige
  Ansagen — dort wird exakt am Ende entsperrt, da eine Restdauer-Schätzung
  für den TTS-Fallback technisch nicht verlässlich möglich ist (Android TTS
  liefert keine Dauer vorab) und der Zusatznutzen den Aufwand nicht
  rechtfertigt.
- Keine generelle Mehrkanal-Fähigkeit für alle Trainer — nur Wort-Detektiv
  braucht sie, da nur dort nach der Frühfreigabe noch Ansage-Reste laufen.

## Testing

- Manuelle Prüfung auf Testgerät bei `font_scale 1.3` (siehe bestehende
  Projekt-Notiz): Deckkraft-Reduktion muss bei größerer Schrift weiterhin klar
  erkennbar und die gesperrten Elemente optisch konsistent bleiben.
- Für jeden der 7 Trainer: Ansage abspielen lassen, während der Sperre auf
  Kacheln tippen → keine Reaktion, keine Audio-Unterbrechung. Nach Freigabe:
  normales Tap-Verhalten.
- Wort-Detektiv gezielt: nach Ende von Teil 1 (Ziel-Graphem) tippen → Segment-
  Echo hörbar, während Konnektor+Wort im Hintergrund zu Ende laufen (keine der
  beiden Audiospuren bricht ab).
- Rundenwechsel während laufender Ansage (z.B. Zurück-Pfeil): beide Channel-
  Player werden sauber gestoppt, neue Runde startet mit `interactionLocked = true`.
