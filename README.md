# ABC-Vorschul App

Kostenlose, werbefreie Android-Vorschul-App (ca. 4–7 Jahre) für Lesen und Rechnen auf Deutsch.
Dunkles UI, offline nach Installation. Ein Fibel-Pfad aus 26 Lektionen; jede Lektion läuft
sechs Trainer-Typen in fester didaktischer Reihenfolge über einen gemeinsamen Content-Graphen.
Optional: Buchstaben-/Silben-Jagd als Übungselement zwischen den Trainern.

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

## Content-Pack (Schema v2)

`app/src/main/assets/content/`

| Datei | Inhalt |
|-------|--------|
| `pack.manifest.json` | `schemaVersion`, `packId`, Titel, Locale |
| `atoms.json` | Buchstaben (mit Strichdaten für den Spurensucher), Silben, Wörter, Bildwörter |
| `sentences.json` | Sätze als Atom-Folgen |
| `tasks.json` | Ein Eintrag pro Trainer, `trainer`-Feld als Typ-Diskriminator, 1..n Runden |
| `lessons.json` | 26 Lektionen in Fibel-Reihenfolge; `authored` = spielbar, `planned` = Knoten gesperrt |
| `finales.json` | Ein kurzer Satz plus Bildreihe (`pictureAtomIds`) je Lektions-Ende |

Autoriert: alle 26 Lektionen (Phase 1–7), inklusive der Wiederholungs-Tracks. Derzeit steht keine
Lektion auf `planned`; der Status bleibt im Schema erhalten, damit künftige Lektionen als gesperrte
Pfad-Knoten angelegt werden können, ohne Code zu ändern.

Der Validator lehnt ein Pack ab, wenn eine autorierte Lektion nicht genau die sechs Trainer in
Reihenfolge enthält, Kachelfolgen das Zielwort nicht buchstabieren, eine Summe nicht stimmt,
Strichdaten fehlen oder Referenzen ins Leere zeigen.

## Offline-Smoke-Skript (manuell)

1. `./gradlew :app:installDebug`, Gerät in den Flugmodus.
2. App öffnen → **Pfad-Screen** erscheint, Lektion 1 pulsiert, Lektionen 2–26 sind gesperrt (entsperren sich nach Mastery).
3. Gesperrten Knoten antippen → gesprochener Hinweis, kein stummes No-Op.
4. Lektion 1 öffnen und die Trainer der Reihenfolge nach durchspielen:
   Auditiver Finder (Waggon-Zuordnung) · Visueller Spurensucher (Buchstaben nachspuren) ·
   optional Buchstaben-Jagd (Batterie voll → Feier, automatisch weiter, kein Weiter-Button) ·
   Silben-Verschmelzer · optional Silben-Jagd ·
   Wort-Bauer (Mama bauen) · Satz-Architekt (Wortschild aufhängen) · zwei Rechenaufgaben.
   Bei Wort-Bauer und Satz-Architekt beide Bedienwege prüfen: eine Kachel per Ziehen platzieren
   (die gezogene Kachel liegt sichtbar über den Zielfeldern, nie darunter) und eine per Tippen
   (Tap-Alternative: Kachel antippen, dann Zielfeld antippen).
5. Bei jeder richtigen Antwort: Antwort wird vorgesprochen → Stern oben → dann nächste Runde.
6. Eine Rechenaufgabe zweimal falsch beantworten → gesprochener Hinweis, danach **Auflösen** nutzen:
   keine Punkte, Session läuft weiter.
7. Langer Druck auf ⋯ → Hilfestufe **Ohne Hilfe** erzwingen → nächste Rechenrunde zeigt die
   System-Zahlentastatur; **Mit Hilfe** → drei visuelle Antworten.
8. Mitten in der Lektion App killen und neu öffnen → dieselbe Lektion, dieselbe Runde.
9. Lektion beenden → Belohnungszusammenfassung → Weiter → zurück auf dem Pfad, Lektion 1
   als gemeistert markiert, Lektion 2 freigeschaltet.
10. Back auf dem Pfad verlässt die App; erneutes Öffnen zeigt den Fortschritt unverändert.

## Product notes

- Keine Werbung, keine Netz-Permission für die Kernpraxis.
- Eltern-Hilfestufe: Auto / Mit Hilfe / Ohne Hilfe hinter Long-Press-Gate.

