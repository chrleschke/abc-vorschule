# Kurze Wort-Bauer- und Satz-Architekt-Prompts

## Problem

Wort-Bauer- und Mehrwort-Satz-Architekt-Runden tragen lange Instruktions-Suffixe in
`promptTts`:

- Wort-Bauer: `Baue das Wort Häuser. Suche die passenden Buchstaben und setze sie in
  die richtige Reihenfolge.`
- Satz-Architekt: `Hier sind Häuser. - Ordne die Wörter in die richtige Reihenfolge.`

Das Kind braucht die Aufgabe nicht vorgelesen — das Tray und die Schablonen tragen
die Interaktion. Der Prompt soll sich auf das Zielwort bzw. den Satz konzentrieren.

## Goal

- Wort-Bauer: Speaker spielt `Baue das Wort {Wort}.`
- Satz-Architekt (Mehrwort): Speaker spielt nur den Satz (inkl. Satzzeichen wie im
  Content, z. B. `Hallo Lama!` oder `Hier sind Häuser.`), ohne Ordne-Suffix.
- ContentValidator verhindert, dass die alten Instruktions-Suffixe zurückkehren.
- Produktprinzipien an die autorierte Kurzform anpassen (`Baue`, nicht `Bilde`).

## Decisions

| Frage | Entscheidung |
| ----- | ------------ |
| Wort-Bauer-Audio | Kurzform `Baue das Wort {Wort}.` (nicht nur das Lemma) |
| Einwort-Bild-Zuordnung (`Ordne das Wort - Mama - dem Bild zu.`) | Unverändert |
| Spurensucher / Buchstabenzeichnen / Jagd | Außer Scope |
| Bestehende OGG-Clips / `audio/index.json` | In diesem Change ignorieren / nicht anfassen |
| `locks.json` / `profiles.json` | Nicht automatisiert umschreiben |
| Speak-Pfad in `SessionViewModel` | Unverändert (`promptTts` bleibt Prompt-Quelle) |

## Out of scope

- TTS-Pipeline neu laufen lassen oder neue OGG-Dateien erzeugen
- Aufräumen verwaister Clips in `audio/`
- Änderungen an anderen Trainer-Typen
- Runtime-Ableitung des Prompts aus Atom/Satz (Content bleibt Source of Truth)

## Design

### Content (`tasks.json`)

Mechanische Kürzung der autorierten `promptTts`-Felder:

1. **`word_build` (64 Runden):** alles nach dem ersten Satz streichen.
   - Vorher: `Baue das Wort Mama. Suche die passenden Buchstaben und setze sie in die richtige Reihenfolge.`
   - Nachher: `Baue das Wort Mama.`
2. **`sentence_order` Mehrwort (21 Runden):** Suffix ` - Ordne die Wörter in die richtige Reihenfolge.` streichen.
   - Vorher: `Hier sind Häuser. - Ordne die Wörter in die richtige Reihenfolge.`
   - Nachher: `Hier sind Häuser.`
   - Satzzeichen am Satzende bleiben wie autoriert (`!` oder `.`).
3. **`sentence_order` Einwort-Bild (4 Runden):** keine Änderung
   (`l01-t8`, `l02-t9`, `l19-t9`, `l20-t9`).

Keine Runtime-Umschreibung: Speaker und Fallback ohne TTS lesen weiter `round.promptTts`.

### Validator (`ContentValidator.kt`)

Neue Checks beim Validieren jeder Runde:

- **`word_build`:** `promptTts` muss dem Muster `Baue das Wort ….` entsprechen
  (nicht leer nach dem Präfix; endet mit `.`). Verboten: Teilstrings
  `Suche die passenden` und `richtige Reihenfolge`.
- **`sentence_order`:** Verboten: `Ordne die Wörter`. Erlaubt bleiben die
  Einwort-Bild-Prompts, die mit `Ordne das Wort` beginnen und `dem Bild zu`
  enthalten.

Fehler werden wie bestehende Content-Issues als `ValidationIssue` gemeldet und
laden den Pack nicht.

### Tests

- `ContentValidatorTest` (oder bestehende Validator-Suite): Pack-Mutation
  - langer Wort-Bauer-Prompt → Issue
  - kurzer `Baue das Wort X.` → ok
  - Mehrwort mit Ordne-Suffix → Issue
  - Einwort-Bild-Prompt → ok
- `ClipIndexTest` und andere Fixtures, die den langen Eis-/Weg-Prompt erwarten,
  auf die Kurzform umstellen (soweit sie den ausgelieferten Pack spiegeln).

### Dokumentation

In `docs/PRODUCT_PRINCIPLES.md`:

- Wort-Bauer-Prompt-Beispiel von „Bilde das Wort …“ auf „Baue das Wort …“ korrigieren.
- Kurz festhalten: Satz-Architekt-Mehrwort-Prompt = Satztext ohne Ordne-Instruktion;
  Einwort-Bild-Zuordnung behält ihren eigenen Prompt.

## Testing (DoD)

- `./gradlew :app:testDebugUnitTest` grün
- Stichprobe im Pack: kein Wort-Bauer mehr mit „Suche die passenden…“; kein
  Mehrwort-Satz mehr mit „Ordne die Wörter…“; vier Einwort-Bild-Prompts unverändert
- Keine neuen/geänderten Dateien unter `app/src/main/assets/audio/` oder
  `tools/tts/locks.json` / `profiles.json` in diesem Change
