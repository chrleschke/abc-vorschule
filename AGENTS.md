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

## Kind-UI-Regeln (Kurz)

- Sechs Trainer-**Typen** pro Lektion in fester Rangfolge: Auditiver Finder · Spurensucher · Verschmelzer · Wort-Bauer · Satz-Architekt · Rechnen. Ein Typ kann sich wiederholen oder fehlen, die Reihenfolge geht nie zurück; jede Lektion startet mit Auditiver Finder und endet mit Rechnen (`ContentValidator` erzwingt das).
- Pfad-Screen ist der Einstieg; gesperrte Knoten antworten mit gesprochenem Hinweis, nie stumm.
- Trainer 1: Lok mit Anfang/Mitte/Ende-Waggon; Miss spielt das segmentierte Wort (`missTts`).
- Trainer 2: Straße aus autorierten `Atom.strokes`; gelbe Sterne nur in Strichreihenfolge (aktiver Balken leuchtend, kommende blass), Haptik-Tick pro Stern; fertiger Balken füllt sich ease-in und übergibt das Fahrzeug an den nächsten Startpunkt; Korridor-Verlassen stoppt das Fahrzeug (langes Rumpeln), zählt aber nicht als Fehlversuch. Nach dem letzten Stern 500 ms Standbild, dann Belohnungsseite (Bild + Wortzeile, Graphem fett).
- Trainer 3: Verschmelzen erst nahe am Vokal; kurzer Zug rutscht straffrei zurück; Tap-Alternative Pflicht.
- Trainer 4/5: Rahmen bzw. Wäscheleine tragen das Gerüst pro Atom (Silhouette/Ghost vs. leer); Distraktoren sind **im Content autoriert**, Tray ≤ 5 (Wort) bzw. ≤ 6 (Satz).
- Buchstaben-/Silben-Jagd (bis zu zwei Schritte je Lektion, nach Spurensucher bzw. Verschmelzer, sofern die Lektion den jeweiligen Trainer führt): **kein autorierter Content** — Runden werden zur Laufzeit aus den letter_trace-/syllable_merge-Runden derselben Lektion abgeleitet (`SymbolHuntInsertion`). Kacheln verstreuen sich über den Aufgabenbereich (Ausnahme zu Prinzip 9), die 5-Segment-Batterie sitzt im Antwortbereich; Fehltipp mischt neu ohne Batterieverlust, meldet aber genau einmal pro Runde einen Fehlversuch; nach 6 aufeinanderfolgenden Fehltipps: Auflösen. Batterie voll → lokaler „Weiter"-Button, erst danach die normale Erfolgs-Pipeline.
- Rechnen: 3 Antworten (visuell) bzw. System-Zahlentastatur + CTA-Absenden-Pfeil; in jeder Lektion; keine Lesewörter; Miss-Feedback nur gesprochen; korrekte Antwort bestätigt sich grün (kein Rot bei Miss, kein Grün beim Auflösen); Erfolg wird mit zufälligem Lob aus `PraisePhrases` **vorgesprochen**, nie angezeigt.
- Drag committet nur bei echtem Zonentreffer (größte Überlappung), sonst Snap-back.
- Vor/Zurück zwischen Runden ist immer aktiv, unabhängig von Punkten/Fortschritt.
- Aufgabe oben mittig, Antworten unten mittig (`ExerciseStage` / Design-Komponenten).



## Technik-Kurzüberblick

- Kotlin + Jetpack Compose, dark-only
- Content: versioniertes JSON unter `app/src/main/assets/content/`
- Progress: DataStore
- Content-Schema v2: ein polymorpher `TaskSpec` pro Trainer (`trainer`-Diskriminator), Lektionen in `lessons.json`
- Lektions-Freischaltung wird aus `taskStats` abgeleitet (`progress/LessonGating.kt`) — keine Extra-Persistenz
- Tests: `./gradlew :app:testDebugUnitTest`
- Build: `./gradlew :app:assembleDebug`



## Definition of Done (Agent)

- Verhalten entspricht den Produktprinzipien
- Relevante Unit-Tests grün
- Doku/Rules bei Regeländerungen mitgezogen
- Keine Secrets committen; keine Force-Pushes ohne explizite Nutzeranweisung

