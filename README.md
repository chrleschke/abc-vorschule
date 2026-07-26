# ABC-Vorschul App

Kostenlose, werbefreie Android-Vorschul-App (ca. 4–7 Jahre) für Lesen, Sprechen und Rechnen auf Deutsch.
Dunkles UI, offline nach Installation, gemischte Kurz-Sessions über einen gemeinsamen Content-Graphen.

## Dokumentation

| Dokument | Inhalt |
|----------|--------|
| [`docs/PRODUCT_PRINCIPLES.md`](docs/PRODUCT_PRINCIPLES.md) | Produkt- und UX-Grundprinzipien (verbindlich) |
| [`AGENTS.md`](AGENTS.md) | Wie Agents arbeiten sollen (brainstorm → … → doc update) |
| [`docs/plans/`](docs/plans/) | Unified Plans / Product Contracts |
| `.cursor/rules/` | Immer aktive Cursor-Regeln |

## Requirements

- JDK 17+
- Android SDK (compileSdk 36)
- Emulator oder Gerät (deutsche TTS-Stimme empfohlen)

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Install:

```bash
./gradlew :app:installDebug
```

## Manual offline session smoke

1. Flugmodus an.
2. **ABC-Vorschul App** starten — Practice-Shell ohne Pack-Titel-Zeile.
3. Fünf Aufgaben: Lesen beginnt mit Buchstaben; Sprechen zeigt großen Sprech-Impuls (Icon) + **➡️ Weiter**; Rechnen mit gruppierten Mengen.
4. Ab der zweiten Session: bekannte Kacheln erscheinen als Distraktoren im Antwort-Tray; falsche Kachel → gesprochener Hinweis. Drag neben einen Slot → Kachel schnappt zurück.
5. Langes Drücken auf **⋯** (~1,5 s) → **Mit Hilfe**.
6. Mathe zweimal falsch → **Auflösen** → keine Punkte für Resolve.
7. App mid-session in den Hintergrund → wieder öffnen → Session setzt fort.
8. Sessionende → Belohnung → **Weiter** startet neuen Mix.

## Product notes

- Keine Werbung, keine Netz-Permission für die Kernpraxis.
- Eltern-Hilfestufe: Auto / Mit Hilfe / Ohne Hilfe hinter Long-Press-Gate.
- Content-Pack: `app/src/main/assets/content/` (Fibel: Buchstabe → Silbe → Wortbau → Wort → Satz).
