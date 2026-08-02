# Offene Reste — TTS-Tooling (2026-08-02)

## `TtsDebugEntry.kt` kennt `finales.json` nicht

`app/src/main/java/app/abcvorschule/debug/TtsDebugEntry.kt` enumeriert Atome, Sätze und
Tasks, aber nicht die Finale-Sätze aus `finales.json`. Die TTS-Debug-Seite in der App
kann diese 18 Strings daher nicht anzeigen oder überschreiben.

Das Tooling unter `tools/tts/` definiert das Präfix `finale:<id>:tts` bereits. Wird die
Kotlin-Seite nachgezogen, muss sie exakt dieses Schema benutzen, damit Tooling und App
dieselben IDs sprechen.

Bewusst nicht in dieser Session behoben: der Scope war ausdrücklich tooling-only, ohne
App-Änderungen.

## Template-Sprechtexte der abgeleiteten Trainer

Siehe `docs/superpowers/specs/2026-08-02-qwen-tts-audio-pipeline-design.md` §2.
`SymbolHuntDerivation` und `SymbolInWordDerivation` bauen ihre Ansagen zur Laufzeit aus
Format-Templates. Das Tooling erfasst sie nicht; `extra-strings.json` hält mit einem
leeren `templates`-Block den Platz frei.
