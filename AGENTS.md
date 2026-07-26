# Agent Guide — ABC-Vorschul App

Dieses Repo wird mit Compound-Engineering-Workflows und Cursor-Agents bearbeitet.
Lies zuerst [`docs/PRODUCT_PRINCIPLES.md`](docs/PRODUCT_PRINCIPLES.md).

## Verbindliche Produktquelle

1. **Produktprinzipien** — `docs/PRODUCT_PRINCIPLES.md`
2. **Aktueller Plan** — `docs/plans/*-plan.md` (wenn `implementation-ready`)
3. **Code & Content-Pack** — `app/src/main/...` und `app/src/main/assets/content/`

Bei Widerspruch: Prinzipien und ausdrückliche Nutzerentscheidungen in der Session gewinnen;
Plan-Artefakte nicht stillschweigend umbiegen — Abweichungen dokumentieren.

## Standard-Arbeitsablauf

Für Feature- oder größere Änderungsarbeit diese Reihenfolge einhalten
(entsprechende CE-/Cursor-Skills nutzen, wenn verfügbar):

| Schritt | Skill / Aktion | Zweck |
|---------|----------------|-------|
| 1 | **brainstorm** | Problem, Alternativen, Entscheidung mit Nutzer klären |
| 2 | **plan** | Unified Plan / Contract schreiben oder aktualisieren |
| 3 | **doc-review** | Plan/Doku gegen Prinzipien und Lücken prüfen |
| 4 | **work** | Implementieren (Tests + Build grün) |
| 5 | **code-review** | Multi-Agent-Review vor Merge/Übergabe |
| 6 | **simplify-code** | Verhaltenstreue Vereinfachung der Branch-Diffs |
| 7 | **doc update** | Doku und Regeln an den Ist-Stand anpassen |

Kleine, klar begrenzte Fixes dürfen Schritte 1–3 überspringen, **müssen aber Schritt 7 erfüllen**,
wenn sich UX-, Content- oder Prozessregeln ändern.

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

- Keine Anweisungs-Headlines, die Lesekompetenz voraussetzen.
- Aktionsbuttons: Text optional, **Vektor-/ASCII-Icon Pflicht** (keine Emojis in Buttons); Weiter rechts.
- Practice-Chrome: Chevrons über Fortschrittsbalken; Speaker im Aufgabenbereich über dem Titel; Safe-Area + Extra-Abstand unten.
- Buchstaben: zwei Frames Groß/Klein, TTS „Finde den Buchstaben {a}.“; Puzzle ohne Icons; ausgegrautes „ABC“.
- Wort-Spell: Titel = Wort; ein Frame pro Buchstabe; Erfolg = Antwort-TTS → Stern oben → dann weiter.
- Rechnen: 3 Antworten (visuell) bzw. System-Zahlentastatur + CTA-Absenden-Pfeil (Zahlen-Eingabe); Miss-Feedback nur gesprochen.
- Antwort-Tray: bekannte Atome als Distraktoren (max. 2, Tray ≤ 5); erste Begegnung distraktorfrei; Drag committet nur bei Slot-Treffer, sonst Snap-back.
- Buchstaben-Frames zeigen die Silhouette immer (kein Ausblenden nach Scaffold-Aufstieg).
- Lückentext-Sätze: Antwortlücken inline im Satztext, nicht als separate Kachelreihe.
- Vor/Zurück zwischen Aufgaben ist immer aktiv, unabhängig von Punkten/Fortschritt.
- Keine Speak-only-Screens mit nur Weiter.
- Aufgabe oben mittig, Antworten unten mittig (`ExerciseStage` / Design-Komponenten).

## Technik-Kurzüberblick

- Kotlin + Jetpack Compose, dark-only
- Content: versioniertes JSON unter `app/src/main/assets/content/`
- Progress: DataStore
- Tests: `./gradlew :app:testDebugUnitTest`
- Build: `./gradlew :app:assembleDebug`

## Definition of Done (Agent)

- Verhalten entspricht den Produktprinzipien
- Relevante Unit-Tests grün
- Doku/Rules bei Regeländerungen mitgezogen
- Keine Secrets committen; keine Force-Pushes ohne explizite Nutzeranweisung
