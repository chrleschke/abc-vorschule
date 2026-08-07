# Agent Guide — ABC-Vorschul App

Dieses Repo wird mit Compound-Engineering-Workflows und Cursor-Agents bearbeitet.
Lies zuerst `[docs/PRODUCT_PRINCIPLES.md](docs/PRODUCT_PRINCIPLES.md)`.

## Verbindliche Produktquelle

1. **Produktprinzipien** — `docs/PRODUCT_PRINCIPLES.md`
2. **Aktueller Plan** — `docs/plans/*-plan.md` (wenn `implementation-ready`)
3. **Code & Content-Pack** — `app/src/main/...` und `app/src/main/assets/content/`

Bei Widerspruch: Prinzipien und ausdrückliche Nutzerentscheidungen in der Session gewinnen;
Plan-Artefakte nicht stillschweigend umbiegen — Abweichungen dokumentieren.

## Standard-Arbeitsablauf

Für Feature- oder größere Änderungsarbeit diese Reihenfolge einhalten
(entsprechende CE-/Cursor-Skills nutzen, wenn verfügbar):


| Schritt | Skill / Aktion    | Zweck                                                 |
| ------- | ----------------- | ----------------------------------------------------- |
| 1       | **brainstorm**    | Problem, Alternativen, Entscheidung mit Nutzer klären |
| 2       | **plan**          | Unified Plan / Contract schreiben oder aktualisieren  |
| 3       | **doc-review**    | Plan/Doku gegen Prinzipien und Lücken prüfen          |
| 4       | **work**          | Implementieren (Tests + Build grün)                   |
| 5       | **code-review**   | Multi-Agent-Review vor Merge/Übergabe                 |
| 6       | **simplify-code** | Verhaltenstreue Vereinfachung der Branch-Diffs        |
| 7       | **doc update**    | Doku und Regeln an den Ist-Stand anpassen             |


Kleine, klar begrenzte Fixes dürfen Schritte 1–3 überspringen, **müssen aber Schritt 7 erfüllen**,
wenn sich UX-, Content- oder Prozessregeln ändern.

Nutze /superpowers:subagent-driven-development o.ä. um große Tasks sinvoll zu splitten.

## Dokumentation selbstständig aktualisieren

Der Agent **aktualisiert die Projektdokumentation von sich aus**, ohne extra Aufforderung, wenn:

- Produktprinzipien oder UX-Regeln sich ändern
- Content-Progression / Fibel-Regeln sich ändern
- der empfohlene Agent-Workflow sich ändert
- README-Build-/Smoke-Schritte veralten
- neue Konventionen entstehen, die künftige Sessions wissen müssen

Mindestens prüfen/aktualisieren:

- `docs/PRODUCT_PRINCIPLES.md`
- `AGENTS.md`
- `README.md`
- `.cursor/rules/*` (wenn Regeln für den Agenten gelten sollen)
- ggf. kurze Notiz in `docs/residual-review-findings/` bei bewussten offenen Resten

**Nicht** den Plan-Body als Fortschrittslog missbrauchen (Fortschritt lebt in Git).
Plan nur anfassen, wenn der Contract selbst falsch oder unvollständig ist.

## Kind-UI-Regeln & Trainer-Typen

**Vollständige, verbindliche Details:** [`docs/PRODUCT_PRINCIPLES.md` → Abschnitte 2–10](docs/PRODUCT_PRINCIPLES.md)

Kernpunkte (Kurzfassung):

- **Sieben autorierte Trainer-Typen** pro Lektion in fester Rangfolge (Auditiver Finder → … → Satz-Versteher → Rechnen). Reihenfolge fällt nicht zurück; `ContentValidator` erzwingt Struktur. Zur Laufzeit können sich abgeleitete Zusatz-Trainer (Jagd, Wort-Detektiv, siehe unten) dazwischenschieben — sie sind keine achten/neunten autorierten Typen.
- **Buchstaben-/Silben-Jagd**: Optional bis zu 2× pro Lektion, keine separaten Autorierungen — wird zur Laufzeit aus letter_trace/syllable_merge abgeleitet (`SymbolHuntInsertion`). Batterie voll → kurze Feier, dann automatisch weiter — kein „Weiter"-Button, das Kind kann ihn nicht lesen.
- **Wort-Detektiv**: „Finde den Buchstaben / den Laut / die Silbe im Wort", ebenfalls abgeleitet (`SymbolInWordInsertion`), eine Runde pro eingeführtem `word_build`-Wort, direkt nach dem letzten Wort-Bauer. Mehrzeichen-Grapheme (`Sch`, `ei`, …) heißen „Laut", nicht „Buchstabe". Grapheme kommen aus `WordGraphemes` — pack-abgeleitet und auf bereits eingeführte Lektionen beschränkt, sonst würde „Nest" in L07 zu `N·e·st` verschmelzen und das gesuchte `S` unantippbar machen.
- **Distraktoren**: Im autorieren Content (Tray ≤ 5–6), oder verstreut im Hunt-Feld (bis 6, mit Wiederholungen).
- **Session-Modell**: Pfad-Screen → freigeschaltete Knoten starten Trainer-Sequenz → Fortschritt persistent, Vor/Zurück immer möglich.
- **Audio-First**: Kinder lesen nicht. Bilder, Icons, Layout, Sprache. Lesbare Labels nur wo nötig (Atom-Namen sind Aufgabe selbst).
- **Drag & Drop**: Committet bei echtem Treffer, sonst Snap-back. Keine Strafen.
- **Rechnen**: Icons (keine Wörter), 3 Optionen (visuell) oder System-Zahlentastatur; Erfolg vorgesprochen (kein sichtbarer Text), Miss gesprochenes Feedback. Ab 11 Mengen nur als Symbol + Ziffer; Progression Plus → Wegnehmen → gleiche Gruppen.



## Technik-Kurzüberblick

- Kotlin + Jetpack Compose, helles Warmer-Tag-Theme (Farbrollen + Haptik-Vokabular siehe PRODUCT_PRINCIPLES §10)
- Content: versioniertes JSON unter `app/src/main/assets/content/`
- Progress: DataStore
- Content-Schema v2: ein polymorpher `TaskSpec` pro Trainer (`trainer`-Diskriminator), Lektionen in `lessons.json`
- Lektions-Freischaltung wird aus `taskStats` abgeleitet (`progress/LessonGating.kt`) — keine Extra-Persistenz
- Tests: `./gradlew :app:testDebugUnitTest`. Die Unit-Tests laden das **ausgelieferte**
  Content-Pack: `src/main/assets` ist als Test-Resource-Root eingetragen, es gibt **keine**
  zweite Kopie unter `src/test/resources/content/` mehr (die war unbemerkt veraltet, u. a.
  ohne die gesamte Rechnen-Progression — `ContentValidatorTest` war grün auf altem Content).
  Content-Fixtures also nie duplizieren, sondern den Pack im Test mutieren (`pack.copy(...)`).
- Sprachaufnahmen: `tools/tts/` erzeugt mit lokalem Qwen3-TTS ein Audio-Paket aus dem
  Content-Pack (`tools/tts/README.md`). Läuft mit `~/qwen-tts-test/.venv/bin/python`,
  nicht mit dem System-Python. `tools/tts/profiles.json` und `tools/tts/locks.json`
  enthalten kuratierte Entscheidungen — nie automatisiert überschreiben.
- Build: `./gradlew :app:assembleDebug`



## Definition of Done (Agent)

- Verhalten entspricht den Produktprinzipien
- Relevante Unit-Tests grün
- Doku/Rules bei Regeländerungen mitgezogen
- Keine Secrets committen; keine Force-Pushes ohne explizite Nutzeranweisung
