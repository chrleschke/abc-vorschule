# Satz-Versteher — „Ordne das richtige Bild zu“ (Design)

Status: implementation-ready · Datum: 2026-08-07

## 1. Problem & Ziel

Die App übt bisher das **Bauen** von Wörtern und Sätzen. Es fehlt ein Trainer für
**Hörverstehen auf Satzebene**: Sätze mit komplizierteren grammatischen Formen
(Plural, Partizip II, Präteritum — auch kombiniert) sollen verstanden, nicht
geschrieben werden. Das Kind hört einen Satz und entscheidet, welches von zwei
Bildern dazu passt.

- Die Sätze nutzen Wörter der aktuellen Lektion.
- Sie dürfen lustig sein (Cartoon-Logik wie bei den Finale-Sätzen), aber nicht
  absurd — die bestehenden Finale-„Quatsch-Sätze“ sind ausdrücklich **keine**
  Stil-Referenz für Realismusgrad; die Redaktionsregeln unten gelten.
- Die Aufgaben-Instruktion kommt **einmal**, danach folgen 4–5 Sätze als Runden.
- Position in der Lektion: **nach dem Satz-Architekt, vor dem Rechnen**.

## 2. Einordnung ins Produkt

Der Satz-Versteher wird der **siebte autorierte Trainer-Typ** (`sentence_picture`),
mit festem Rang zwischen `sentence_order` und `count_add` in
`ContentValidator.TrainerOrder`. Lektionen beginnen weiter mit dem Auditiven
Finder und enden mit Rechnen; der Typ darf wie alle anderen fehlen oder sich
wiederholen.

Er ist ein **reiner Hör-Trainer**: kein Lesen, kein Bauen, keine Schrift für das
Kind. Zwei große Bildkarten sind die einzigen Handlungsflächen. Damit erfüllt er
Abschnitt 2 der Produktprinzipien ohne Ausnahme.

## 3. Content-Schema

Neuer `TaskSpec` in `TaskSpecs.kt`:

```kotlin
@Serializable
@SerialName("sentence_picture")
data class SentencePictureSpec(
    override val id: String,
    /** Einmalige Aufgabenansage, gesprochen nur vor Runde 1: "Ordne das richtige Bild zu." */
    val instructionTts: String,
    val rounds: List<SentencePictureRound>,
) : TaskSpec

@Serializable
data class SentencePictureRound(
    /** Der Satz selbst — Ansage, Erfolgs-Echo und Miss-Wiederholung zugleich. */
    override val promptTts: String,
    /** Karte, die zum Satz passt: 1..3 Atom-IDs, als Emoji-Reihe gerendert.
     *  Wiederholung derselben ID ist erlaubt und drückt Menge aus (Plural: 🍎🍎). */
    val correctAtomIds: List<String>,
    /** Karte, die nicht passt — plausibler Kontrast (Menge, Akteur oder Objekt). */
    val wrongAtomIds: List<String>,
) : TrainerRound
```

Beispiel-Content:

```json
{
  "trainer": "sentence_picture",
  "id": "l03-t10",
  "instructionTts": "Ordne das richtige Bild zu.",
  "rounds": [
    {
      "promptTts": "Tom hat Opa gerufen.",
      "correctAtomIds": ["tom", "opa"],
      "wrongAtomIds": ["tom", "oma"]
    },
    {
      "promptTts": "Auf dem Teller lagen zwei Tomaten.",
      "correctAtomIds": ["tomate", "tomate"],
      "wrongAtomIds": ["tomate"]
    }
  ]
}
```

Die Sätze leben **im Round selbst**, nicht in `sentences.json`: wie die
Finale-Sätze enthalten sie bewusst flektierte Formen und Wörter außerhalb des
Atom-Graphen (Verben, Partizipien), die nie gebaut oder gelesen werden. Nur die
bildtragenden Karten-Atome müssen auflösen.

## 4. Sprache & Ablauf

- **Prompt-Teile** (`SessionViewModel.currentPromptParts`): Runde 0 →
  `[instructionTts, promptTts]`, alle weiteren Runden → `[promptTts]`.
  Getrennte Teile halten die Audio-Clips wiederverwendbar (eine Instruktions-
  Aufnahme für alle Lektionen).
- **Interaktions-Lock**: Standard-`PromptUnlock` (letzter Teil) — das Kind muss
  den ganzen Satz gehört haben, bevor es antworten kann.
- **Tippen = Antwort** (wie beim Auditiven Finder). Kein Vorlese-Echo auf den
  Karten — sie tragen keine Wörter, und ein Tap committet.
- **Treffer**: Karte bestätigt sich grün, `success`-Haptik, Erfolgs-Sprache =
  der Satz noch einmal (`SuccessSpeech` → `[promptTts]`), Stern, weiter.
- **Miss**: `nudge`-Haptik, keine Rot-Markierung; als Miss-Cue wird **der Satz
  erneut vorgelesen** (`missCueForCurrent` → `promptTts`) — „erst hören, dann
  entscheiden“ wird so zur Korrektur-Schleife.
- **Auflösen**: nach 2 Misses erscheint der `AbcResolveButton`; Auflösen zeigt
  die richtige Karte grün (RevealAnswer-Phase, spricht den Satz), keine Punkte.
- **Ohne deutsches TTS**: Der Satz wird als Text im Aufgabenblock angezeigt
  (nur dann!), damit ein Erwachsener ihn vorlesen kann — gleiches Muster wie
  `MathExercise.showSymbolPrompt`.

## 5. UI-Layout

`SentencePictureTrainer.kt`, in `ExerciseStage`:

- **Aufgabenblock**: `TaskPromptChrome` (Speaker, wiederholt die aktuelle
  Runden-Ansage). Kein Satztext, kein Bild — der Satz ist die Aufgabe und bleibt
  Audio. (Ausnahme: TTS-Fallback, s. o.)
- **Antwortblock**: eine `Row` mit **zwei gleich großen Karten** (je `weight(1f)`,
  Mindesthöhe deutlich über `AbcDimens.kidTouch`, `CreamElevated`, runde Ecken,
  `WarmMuted`-Border wie Pegs/Frames). Karteninhalt: die Emoji-Glyphen der
  Atom-Liste nebeneinander, Schriftgröße nach Anzahl gestaffelt (1 Emoji ≈ 72sp,
  2 ≈ 56sp, 3 ≈ 44sp; gegen font_scale 1.3 prüfen).
- **Seitenzuordnung deterministisch**: Helper `SentencePictureSides` entscheidet
  aus einem Runden-Seed (Hash von `promptTts`), ob die richtige Karte links oder
  rechts liegt — stabil über Recompositions, ohne Autoren-Bias, testbar auf
  ~50/50-Balance über den ausgelieferten Content. `TrayOrder.arrange` ist hier
  **falsch** (es garantiert „nie Lösungsreihenfolge“ und würde die richtige Karte
  systematisch auf eine Seite legen).
- Lock-Verhalten wie die übrigen Trainer: `enabled = !interactionLocked`,
  Opacity-Animation 0.5 ↔ 1.0.
- Karten-Feedback: gewählte richtige Karte bekommt Grün-Border
  (`PegBorderGreen`-Muster) während der Erfolgs-/Reveal-Phase.

## 6. Scoring & Progression

- `scoredAtomIds()` = `correctAtomIds.distinct()` — Statistik läuft über die
  bildtragenden Atome.
- Punkte: 1 pro richtiger Erstantwort (Standard-`submitRoundResult`).
- Mastery/Gating: automatisch über `taskStats` (`LessonGating` unverändert).
- Resume: `SessionSnapshot.trainerCount`-Shape-Check fängt alte Snapshots ab;
  `packId` wird auf `fibel-v4` gehoben, weil sich die Lektionsstruktur ändert.

## 7. Content-Umfang & Redaktionsregeln

**Umfang:** Ein `sentence_picture`-Task mit **4 Runden** je Basis-Lektion
l01–l18, in `lessons.json` direkt vor dem `count_add`-Task eingehängt.
Wiederholungslektionen (l19–l26) bekommen **keinen** eigenen — sie bleiben
kompakt; eine spätere Erweiterung kann Tasks teilen.

**Redaktionsregeln (analog Finale-Regeln, §12 der Prinzipien):**

- 4–8 Wörter, ein Hauptsatz, eine Handlung.
- Jeder Satz nutzt **mindestens eine schwierige Form**: Plural (gern
  Umlaut-Plural: Häuser, Füße, Säcke), Partizip II („hat … gerufen/gefressen“)
  oder Präteritum („lagen“, „fraß“, „lief“) — Kombinationen erlaubt.
- Mindestens ein Wort aus dem Vokabular der Lektion (Wort-Bauer-Ziele,
  Fokus-Nomen oder Rechnen-Icon); die Karten-Nomen tragen nach Möglichkeit die
  Fokus-Grapheme.
- Lustig durch **Handlung**, Cartoon-Logik, Kinderbuch-Prüffrage. Kein
  Surrealismus, kein AI-Slop. Realistischer als die Finale-Sätze.
- **Die falsche Karte unterscheidet sich genau in der geprüften Dimension**:
  Menge (🍎🍎 vs 🍎), Akteur (👴 vs 👵) oder Objekt (🎩 vs 👟). Nie ein
  erfundenes Fantasie-Bild.
- Karten: 1–3 Atom-IDs, Wiederholung = Menge; jedes Atom braucht ein Emoji;
  die Emoji-Reihen beider Karten müssen sich unterscheiden.
- TTS-Konventionen aus §7 der Prinzipien gelten (keine Einzelbuchstaben, letzter
  Satz ohne Frageton — die Sätze sind ohnehin Aussagesätze).

## 8. Validator-Regeln (`ContentValidator`)

- `sentence_picture` in `TrainerOrder` zwischen `sentence_order` und `count_add`.
- `instructionTts` nicht leer.
- 3–6 Runden pro Task (Autorierung zielt auf 4–5).
- Satz (`promptTts`): 4–8 Wörter; enthält nicht „Ordne“ (Instruktion ist separat).
- Beide Karten: 1–3 Atom-IDs; alle Atome existieren und tragen ein Emoji.
- Die zusammengesetzten Emoji-Strings beider Karten sind verschieden
  (sonst ist die Aufgabe nicht fehlschlagbar).

## 9. Sprach-Pipeline (`tools/tts`)

- `extract.py`: Task-Feld `instructionTts` extrahieren (Profil `prompt`,
  analog `phonemeTts`); Runden-`promptTts` läuft über die bestehenden
  `ROUND_FIELDS`.
- `audit_missing_audio.py`: liest `tasks.json` generisch über `promptTts` —
  prüfen, dass `instructionTts` dort ebenfalls auftaucht.
- Ohne erzeugte Clips spricht die App wie überall Android-TTS.

## 10. Tests

- `ContentValidatorTest`: neue Regeln (Rang, Kartengrößen, Emoji-Pflicht,
  identische Karten, Wortzahl, Instruktions-Checks) + `shippedPackIsValid`.
- `SentencePictureSidesTest`: deterministisch, und über den ausgelieferten
  Content grob balanciert (keine Seite > 70 %).
- `PromptUnlockTest`/`SuccessSpeechTest`/`SessionTrainersTest`: neue Runde
  ergänzt (Unlock = letzter Teil, Erfolg = Satz, Scaffold-Invariante).
- `LessonCoverageTest`: jede Basis-Lektion l01–l18 führt genau einen
  `sentence_picture`-Task mit 4 Runden vor dem Rechnen; Karten-Atome nutzen
  Lektions- oder bereits eingeführtes Vokabular ist **nicht** erzwungen
  (redaktionelle Freiheit wie beim Finale), aber jede Runde erfüllt die
  Validator-Regeln.
- Bestehende Tests, die über alle `TaskSpec`-Zweige dispatchen, brechen beim
  Kompilieren (sealed) — gewollt, sie werden mit erweitert.

## 11. Doku-Pflichten

- `PRODUCT_PRINCIPLES.md` §3: „sechs“ → „sieben“ Trainer-Typen, Beschreibung
  Nr. 6 Satz-Versteher einfügen (Rechnen wird Nr. 7); Review-Tabelle ergänzen.
- `AGENTS.md`: Kurzfassung anpassen.
- `README.md`: nur falls Smoke-Schritte betroffen (nicht erwartet).

## 12. Bewusst nicht in v1

- Kein dritter Karten-Slot / keine 3-fach-Wahl.
- Keine Wiederholungslektionen-Abdeckung (l19–l26).
- Keine eigenen Illustrationen — Emoji-Reihen wie überall.
- Kein sichtbarer Satztext bei verfügbarem TTS (bewusst reiner Hör-Trainer).
