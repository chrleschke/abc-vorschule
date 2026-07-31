# Lektions-Finale auf dem End-Screen — Design

Datum: 2026-07-31
Status: entwurf (Review durch Nutzer ausstehend)

## 1. Problem

Der End-Screen einer Lektion (`ui/shell/RewardSummaryScreen.kt`) besteht heute aus drei
Elementen, vertikal zentriert:

- `IconStar` bei 64 dp mit Pop-Animation (scale 0.7 → 1, 500 ms),
- der Titelzeile `reward_title` („Super gemacht!"),
- der Punktezeile `"+$sessionPoints  ·  Gesamt $totalPoints"`.

Für ein Kind, das nicht lesen kann, ist der Screen damit **inhaltlich leer**. Die
Punktezeile ist doppelt redundant: der Punktestand steht im Übungs-Chrome oben und auf
dem Pfad-Screen. Der eigentliche Moment — eine Lektion ist geschafft — bekommt kein
Bild, keinen Ton und keinen Bezug zum Gelernten.

Zusätzlich erscheint derselbe Screen an **zwei** Stellen: nach echtem Abschluss
(`SessionViewModel`, Übergang auf `AppScreen.RewardSummary` am Sessionende) und beim
Zurückgehen aus einer angefangenen Lektion, sobald `sessionPoints > 0`. Beides sieht
identisch aus, obwohl nur das eine eine Leistung ist.

## 2. Ziel

Nach dem echten Abschluss einer Lektion hört das Kind einen kurzen, lustigen Einzeiler
aus dem Vokabular genau dieser Lektion, und sieht die Nomen des Satzes als Bildreihe in
Satzreihenfolge. Der Satz steht zusätzlich als Text da — nicht für das Kind, sondern für
den Erwachsenen daneben.

Nicht-Ziele in dieser Ausbaustufe:

- keine Animation der Szene („Quatsch-Maschine" mit tippbarem Wal, der Wasser spritzt),
- keine Sprachaufnahme und keine Aussprachebewertung,
- kein Sammelalbum / „Quatsch-Buch",
- keine wortsynchrone Einblendung zum TTS-Audio.

## 3. Entscheidungen

| Frage | Entscheidung | Begründung |
| --- | --- | --- |
| Was zeigen die Bilder? | Nomen des Satzes in Satzreihenfolge (Rebus) | Visualisiert den Satz selbst; „sinnvolle Reihenfolge" ist die Leserichtung. Die Fokus-Grapheme haben keine Bilder (`letter-*`-Atome tragen `emoji: ""`), und `letter_trace.rewardEmoji` ist bewusst die Belohnung des Spurensuchers und darf nicht vorweggenommen werden. |
| Satztext sichtbar? | Ja, groß und mittig | Bricht Prinzip 2 nicht: keine Handlung hängt am Text. Erzeugt den Eltern-Kind-Moment und koppelt Wort an Bild. |
| Stern | Großer Stern im Hintergrund, gedämpft | Erhält das etablierte Erfolgssymbol, ohne einen zweiten Fokuspunkt neben der Bildreihe zu setzen. Passt zur Dark-Only-Nachtästhetik. |
| Punktezeile | Entfällt | Doppelt redundant (Übungs-Chrome, Pfad-Screen). |
| Erfolgsmeldung | Header oben statt zentriert | Macht Platz für Bildreihe und Satz als Bühnenmitte. |
| Wann? | Nur bei echtem Abschluss | Der Satz nutzt sich nicht ab und belohnt Durchhalten. (Ursprünglich war für den Abbruch ein schlanker Screen vorgesehen — siehe Nachtrag am Ende: main hat den Abbruch-Pfad zum End-Screen inzwischen ganz entfernt.) |
| Content-Typ | Eigener Typ `LessonFinale`, nicht `Sentence` | `Sentence.atomIds` sind Bausteine des Satz-Architekten und unterliegen der Fibel-Reihenfolge-Prüfung. Finale-Sätze enthalten Wörter wie *mampft* und *dicken*, die keine Atome sind und nie eingeführt werden. |
| Bildauswahl | Redaktionell autoriert, nicht aus dem Text abgeleitet | Automatisches Wort→Atom-Matching scheitert an Flexion (*roten Hut*), an geteilten Glyphen (`dach` und `haus` sind beide 🏠) und an der Frage, welches Nomen ein Bild verdient. |

## 4. Content-Modell

### 4.1 Neue Datei `app/src/main/assets/content/finales.json`

```json
{
  "finales": [
    {
      "id": "f-l01",
      "text": "Mama Maus mampft einen dicken Apfel!",
      "tts": "Mama Maus mampft einen dicken Apfel!",
      "pictureAtomIds": ["mama", "maus", "apfel"]
    }
  ]
}
```

`text` und `tts` sind getrennt, weil TTS-Aussprache und Schriftbild auseinandergehen
können (Ausrufezeichen, Betonungspausen) — dasselbe Muster wie bei `Sentence`.

### 4.2 Neues Modell in `content/ContentModels.kt`

```kotlin
/**
 * Der Belohnungssatz einer Lektion: wird beim Abschluss vorgelesen und als
 * Bildreihe visualisiert. Anders als [Sentence] ist er nicht baubar — er enthält
 * bewusst Wörter außerhalb des Atom-Graphen (Verben, Adjektive).
 */
@Serializable
data class LessonFinale(
    val id: String,
    val text: String,
    val tts: String,
    /** Nomen-Atome in Satzreihenfolge; jedes muss ein Emoji tragen. */
    val pictureAtomIds: List<String>,
)

@Serializable
data class FinalesFile(val finales: List<LessonFinale>)
```

`ContentPack` bekommt `val finales: Map<String, LessonFinale>` und
`fun finale(id: String): LessonFinale`, analog zu `atoms` / `sentences`.

### 4.3 Verweis an `Lesson` (`content/LessonModels.kt`)

```kotlin
/** Belohnungssatz beim Abschluss. Wiederholungslektionen teilen den ihrer Basis. */
val finaleId: String? = null,
```

Ein Verweis statt eines Inline-Objekts, damit die acht Wiederholungslektionen (L19–L26)
den Satz ihrer Basis-Lektion erben, ohne ihn zu duplizieren: L01 und L19 zeigen beide auf
`f-l01`. **18 Finale-Objekte decken 26 Lektionen.**

### 4.4 Laden (`content/ContentRepository.kt`)

`parsePack()` liest `content/finales.json` analog zu `sentences.json` und übergibt die
Map an `ContentPack`. `pack.manifest.schemaVersion` bleibt bei 2 — das Feld `finaleId`
ist optional, alte Packs laden weiter.

## 5. Der Content: 18 Finale-Sätze

Quelle ist das Konzeptpapier `vorschul_einzeiler_lernplan.md`. Die dort angenommene
Lektionsreihenfolge deckt sich **exakt** mit `lessons.json` (L05 = F & U, L12 = Umlaut,
L18 = C, Y, X & Qu). Der älteren Fassung `lehrplan_vorschulapp.md` mit 16 Lektionen
folgen wir nicht.

| ID | Lektion | Satz | Bilder |
| --- | --- | --- | --- |
| `f-l01` | 1 · M & A | Mama Maus mampft einen dicken Apfel! | `mama` `maus` `apfel` |
| `f-l02` | 2 · I & O | Oma und Mimi fliegen im Ufo! | `oma` `mimi` `ufo` |
| `f-l03` | 3 · P & T | Papa und Opa tanzen auf dem Tisch! | `papa` `opa` |
| `f-l04` | 4 · L & H | Das Lama hat einen roten Hut! | `lama` `hut` |
| `f-l05` | 5 · F & U | Der Fuchs futtert den Kuchen! | `fuchs` `kuchen` |
| `f-l06` | 6 · R & N | Deine Nase ist rot wie eine Rose. | `nase` `rose` |
| `f-l07` | 7 · S & E | Der Fuchs schläft im Nest! | `fuchs` `nest` |
| `f-l08` | 8 · D & K | Die Katze klaut den Keks! | `katze` `keks` |
| `f-l09` | 9 · Ei & W | Der Wal will mein Eis! | `wal` `eis` |
| `f-l10` | 10 · G & Ch | Die Giraffe angelt auf dem Dach! | `giraffe` `dach` |
| `f-l11` | 11 · Au & B | Der Ball saust aus dem Haus! | `ball` `haus` |
| `f-l12` | 12 · Umlaute | Der Löwe klaut die Rübe! | `loewe` `ruebe` |
| `f-l13` | 13 · Sch | Das Schaf steckt im Schuh! | `schaf` `schuh` |
| `f-l14` | 14 · J, Z & Eu | Das Zebra jongliert mit dem Jojo! | `zebra` `jojo` |
| `f-l15` | 15 · ß & V | Der Vogel klaut die Vase! | `vogel` `vase` |
| `f-l16` | 16 · ck & Pf | Das Pferd knackt den Sack! | `pferd` `sack` |
| `f-l17` | 17 · St & Sp | Eine Spinne bewundert sich im Spiegel! | `spinne` `spiegel` |
| `f-l18` | 18 · C, Y, X & Qu | Die Qualle quetscht sich ins Taxi! | `qualle` `taxi` |

Zuordnung der Wiederholungen: L19 → `f-l01`, L20 → `f-l02`, L21 → `f-l03`,
L22 (Ei & Au) → `f-l11`, L23 (Sch & Ch) → `f-l13`, L24 (St & Sp) → `f-l17`,
L25 (Ö & Ü) → `f-l12`, L26 (Qu & X) → `f-l18`.

### 5.1 Zwei Abweichungen vom Konzeptpapier

**L06 ersetzt.** „Die rote Nase rennt weg!" ist genau das Bild, das das Konzeptpapier in
Abschnitt 1 selbst als AI-Slop verwirft. Eine Nase ohne Gesicht, die wegläuft, ist keine
Cartoon-Logik, sondern eine Abstraktion. Neu: **„Deine Nase ist rot wie eine Rose."** —
realistisch, mit einer Pointe, die das Kind nicht erwartet, und beide Nomen sind
Lektionsvokabular (R, N).

**L05 bleibt.** *futtern* ist kein Alltagsverb, klingt für Kinder aber lustig, und genau
darin liegt der Reiz — bewusste Nutzerentscheidung.

**L02 gekürzt.** „Oma und Katze Mimi fliegen mit dem Ufo!" zählt acht Wörter und
verletzt damit die eigene 4–7-Regel. Neu: **„Oma und Mimi fliegen im Ufo!"** — sechs
Wörter, alle drei Bilder bleiben, und der Satz gewinnt an Rhythmus.

### 5.2 Bild-Lücken

- **`kuchen` fehlt als Atom** und wird neu angelegt: `kind: other` (Bild-Vokabular, nie
  gelesen oder gebaut), Emoji 🍰, keine `strokes`.
- **`tisch` existiert, trägt aber `emoji: ""`.** Es gibt kein brauchbares Tisch-Emoji
  (🪑 ist ein Stuhl). L03 zeigt deshalb nur `papa` `opa` — zwei Bilder sind erlaubt.
- **`katze` und `mimi` teilen 🐱.** In `f-l02` steht `mimi`, nicht beide.
- **`dach` und `haus` teilen 🏠**, treten aber nie im selben Finale auf.

## 6. Validierung (`content/ContentValidator.kt`)

Neue Prüfungen, fail-fast beim Laden über `requireValid`:

1. Jede Lektion mit `status == authored` hat ein nicht-leeres `finaleId`, das in
   `pack.finales` auflösbar ist.
2. Jedes `pictureAtomIds`-Element existiert (`requireAtom`) **und** trägt ein
   nicht-leeres `emoji`.
3. Die Emoji-Glyphen innerhalb eines Finales sind eindeutig. Zwei identische Bilder
   lesen sich als Bug.
4. `pictureAtomIds.size` liegt zwischen `MinFinalePictures = 2` und
   `MaxFinalePictures = 4`.
5. Die Wortzahl von `text` liegt zwischen `MinFinaleWords = 4` und
   `MaxFinaleWords = 7`.
6. Kein Finale-Objekt ist unreferenziert (Hinweis auf toten Content).

Regeln 4 und 5 setzen die Redaktionsregeln aus Abschnitt 8 maschinell durch, statt sie
nur zu dokumentieren.

## 7. Session-Zustand

`SessionUiState` bekommt:

```kotlin
/** Gesetzt nur beim echten Lektionsabschluss; beim Abbruch null. */
val completedFinaleId: String? = null,
```

- Am Abschluss-Übergang auf `AppScreen.RewardSummary` wird das Feld aus
  `pack.lessons.first { it.id == lessonId }.finaleId` gesetzt.
- Am Abbruch-Übergang (Back mit `sessionPoints > 0`) bleibt es `null`.
- `continueAfterSummary()` setzt es auf `null` zurück, damit ein späterer Abbruch nicht
  versehentlich den Satz erbt.

## 8. UI (`ui/shell/RewardSummaryScreen.kt`)

### 8.1 Signatur

```kotlin
@Composable
fun RewardSummaryScreen(
    finale: LessonFinale?,          // null → schlanke Abbruch-Variante
    atoms: Map<String, Atom>,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeak: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
)
```

`sessionPoints` und `totalPoints` entfallen als Parameter. `TaskShell` reicht `onSpeak`,
`speaking` und `ttsAvailable` durch — alle drei liegen dort bereits vor.

### 8.2 Aufbau

```
Column(padding 24 horizontal, 24 top, 40 bottom)
├── Text(reward_title, headerSizeSp/headerLineHeightSp)   ← Header oben
├── Box(weight 1f, contentAlignment = Center)             ← Mittelblock
│   └── Column(zentriert)
│       ├── Box                                           ← Bildreihe + Stern
│       │   ├── IconStar(300.dp, alpha 0.12, Pop)         ← aus der Messung ausgeschlossen
│       │   └── Row(pictureAtomIds → Emoji, pictureSizeSp) ← Rebus, gestaffelt
│       ├── Text(finale.text, sentenceSizeSp, center, +20dp Seitenpolster, max 4 Zeilen)
│       └── AbcSpeakerButton                              ← Satz erneut vorlesen
└── AbcContinueButton(centered)
```

**Der Stern beansprucht keinen Layout-Platz.** Er sitzt im `Box` hinter der Bildreihe, wird
aber über einen `Modifier.layout {}`-Wrapper aus der Messung genommen, damit die Spalte
genau so hoch bleibt wie ohne ihn. Ein dekoratives Element darf nie mitbestimmen, wie viel
Raum der Speaker- oder Weiter-Button bekommt — ein früherer Zwischenstand tat das und
schnitt auf schmalen Geräten den Speaker-Button ab. Kein `clipToBounds`: der Stern darf über
die Ränder der Bildreihe hinausragen, ein Bedienelement abzuschneiden wäre schlimmer.

**`weight(1f)` auf dem Mittelblock ist der Überlaufschutz.** Wächst der Inhalt über den
verfügbaren Platz, wird er beschränkt statt den Weiter-Button aus dem Bild zu schieben.
Der Inhalt darin ist vertikal **zentriert** — ein oben ausgerichteter Zwischenstand ließ
alles im oberen Bildschirmviertel kleben und wurde am Gerät verworfen.

Die Bildreihe blendet gestaffelt ein (≈ 180 ms Versatz pro Bild), was den Blick von
links nach rechts führt. Die Staffelung läuft **nicht** synchron zum Audio: System-TTS
liefert Wortgrenzen nur über `UtteranceProgressListener.onRangeStart` (API 26+) mit
unzuverlässigem Timing, und der Aufwand steht nicht zum Gewinn.

Bei mehr als drei Bildern schrumpft die Emoji-Größe von 64 auf 52 sp. Der Satztext darf bis
zu vier Zeilen umbrechen und trägt zusätzlich 20 dp Seitenpolster (44 dp inklusive Spalten-
Padding), damit er nicht bis an die Ränder läuft.

### 8.2.1 Größen passen sich der Systemschrift an

Alle Schriftgrößen laufen über `FinaleLayout`, das `LocalDensity.current.fontScale`
entgegennimmt und die **effektive** Größe deckelt: `zurückgegeben × fontScale ≤ Basis`.
Bei `fontScale = 1.0` sind das die Ausgangswerte (Bilder 64/52 sp, Satz 24 sp,
Header 28 sp bei 34 sp Zeilenhöhe); darüber gibt der Screen die Vergrößerung zurück,
statt den Weiter-Button aus dem Bild zu drängen.

Warum das nötig war: bei `fontScale = 2.0` auf einem 360×640-dp-Gerät summierten sich
Header, Bildreihe, Satz und Buttons auf mehr als die nutzbare Höhe — und der Header
„Super gemacht!" in 28 sp Serif brach dabei auf zwei Zeilen um, was die Rechnung
zusätzlich sprengte. Emojis sind Bilder, nicht Text; dass sie mit der Schrifteinstellung
mitwachsen, ist eine Höflichkeit und darum das Erste, was nachgibt.

Restrisiko: im pessimistischsten Fall (320×568 dp, `fontScale` 2.0, ein Satz der alle vier
Zeilen braucht) reicht der Platz um ~16 dp nicht. Der Weiter-Button bleibt davon unberührt,
weil `weight(1f)` inhaltsunabhängig ist; betroffen wäre allenfalls ein Randstreifen der
Speaker-Fläche. Reale Sätze belegen zwei bis drei Zeilen. Als Residual geführt in
`docs/residual-review-findings/feat-lektions-finale.md`.

### 8.3 Abbruch-Variante (`finale == null`)

Header, Hintergrundstern, Weiter-Button. Keine Bildreihe, kein Satz, kein Speaker, keine
Punktezeile.

## 9. Audio

- Beim Erscheinen des Screens spricht TTS `finale.tts` — `LaunchedEffect(finale.id)`,
  Muster wie die Prompt-Ansage in `TaskShell.PracticeBody`.
- `AbcSpeakerButton` wiederholt `finale.tts`.
- **Tippen auf ein Bild spricht sein Wort** (`atom.lemma`). Prinzip 7 verlangt, dass
  antippbare Items vorgelesen werden, und es ist die kleine Variante der
  „Quatsch-Maschine" aus dem Konzeptpapier ohne zusätzliche Mechanik.
- Ohne deutsches TTS (`ttsAvailable == false`) bleibt der Screen vollständig sichtbar und
  der Speaker-Button deaktiviert. Bilder und Text tragen den Moment (Prinzip 7).

## 10. Betroffene Dateien

```
app/src/main/assets/content/finales.json        (neu)     18 Finale-Objekte
app/src/main/assets/content/atoms.json          (geändert) Atom `kuchen` 🍰
app/src/main/assets/content/lessons.json        (geändert) finaleId an 26 Lektionen
app/src/test/resources/content/*.json           (geändert) Test-Pack mitziehen
content/ContentModels.kt                        (geändert) LessonFinale, FinalesFile, ContentPack, finaleIdOf
content/LessonModels.kt                         (geändert) Lesson.finaleId
content/ContentRepository.kt                    (geändert) finales.json laden
content/ContentValidator.kt                     (geändert) sieben neue Prüfungen
session/SessionModels.kt                        (geändert) completedFinaleId
session/SessionViewModel.kt                     (geändert) Feld setzen/zurücksetzen
ui/shell/FinaleLayout.kt                        (neu, rein) Bildableitung, Größen, Staffelung
ui/shell/RewardSummaryScreen.kt                 (geändert) neues Layout
ui/shell/TaskShell.kt                           (geändert) Parameter durchreichen
docs/PRODUCT_PRINCIPLES.md                      (geändert) Abschnitte 4, 5, 11, 12
```

`FinaleLayout.kt` ist Compose-frei. Alles, was eine Entscheidung trifft — welche Atome ein
Bild bekommen, wie groß die Emojis werden, wann sie erscheinen — liegt dort, damit es
testbar bleibt. Nächster Verwandter im Content-Graph: `content/LessonEmojis.kt` leitet die
Emojis der Pfad-Schilder nach demselben Prinzip ab (deterministisch, Dedupe auf dem Glyph).
Für das reine Compose-frei-Muster ohne Emoji-Bezug: `ui/exercise/WordFrameSizing.kt`,
`ui/exercise/SyllableFrameSizing.kt`.

## 11. Tests

Reine JVM-Unit-Tests, im Stil der 37 vorhandenen Suites — das Repo hat keine
androidTests.

| Suite | Prüft |
| --- | --- |
| `ContentValidatorTest` (erweitert) | fehlendes `finaleId`, tote Atom-Referenz, Atom ohne Emoji, doppelter Glyph, Bildzahl < 2 und > 4, Wortzahl außerhalb 4–7, unreferenziertes Finale |
| `ContentRepositoryTest` (erweitert) | `finales.json` wird geparst; Bilder lösen auf Atome mit Emoji auf |
| `LessonFinaleTest` (neu) | alle 26 Lektionen lösen auf ein Finale auf; 18 Finales decken 26 Lektionen; Wiederholungen erben korrekt; alle Sätze halten die Redaktionsregeln |
| `FinaleLayoutTest` (neu) | Bildreihenfolge und Vorlesewort; Atome ohne Emoji werden übersprungen; Emoji-Größe bei 2/3/4 Bildern; Staffelungs-Delays |
| `LessonSessionTest` (erweitert) | `ContentPack.finaleIdOf` liefert je Lektion das richtige Finale und null bei unbekannter Lektion |

**Was nicht automatisiert geprüft wird:** dass `advance()` das Feld setzt und
`onBackPressed()` nicht. Dieses Repo hat keinen Aufbau für `SessionViewModel`-Tests — kein
`Dispatchers.setMain`, kein Fake-`ProgressRepository` für das ViewModel — und keine
androidTests für Compose. Die Unterscheidung Abschluss/Abbruch und das Rendering hängen
deshalb am manuellen Smoke-Test unten. Wer das ändern will, baut zuerst den
ViewModel-Testaufbau, statt ihn nebenbei mitzuerfinden.

Manueller Smoke-Test: Lektion 1 durchspielen → Satz wird gesprochen, drei Bilder
erscheinen gestaffelt, Speaker wiederholt, Tippen auf 🍎 sagt „Apfel". Lektion 1
abbrechen → schlanker Screen ohne Satz.

## 12. Redaktionsregeln (gehen nach `PRODUCT_PRINCIPLES.md`)

Diese Regeln gelten für das Verfassen neuer Finale-Sätze und werden dort dauerhaft
verankert:

- **Kurz:** 4 bis 7 Wörter. Vom Validator erzwungen.
- **Ein Bild, eine Handlung.** Keine Mini-Geschichte, kein zweiter Nebensatz.
- **Komisch durch Handlung**, nicht durch Wortwahl: klauen, mampfen, stecken, knacken,
  bewundern, jonglieren.
- **Cartoon-Logik statt Surrealismus.** Ein Tier mit Hut oder ein Tier, das etwas
  Alltägliches tut, ist verständlich. Eine Nase, die wegläuft, ist es nicht.
- **Kein AI-Slop:** keine Ansammlung seltener Wörter, keine Situation, deren einziger
  Zweck maximale Absurdität ist.
- **Adjektive sparsam** — nur wenn sie für Bild oder Laut etwas leisten („dicker Apfel",
  „roter Hut").
- **Reim und Alliteration sind erlaubt, nie Pflicht.** Klang darf helfen, aber nie den
  Satz erzwingen.
- **Vokabular aus der eigenen Lektion.** Die bildtragenden Nomen sind Atome derselben
  Lektion; Verben und Adjektive dürfen frei sein, weil sie nie gelesen werden müssen.
- **Mindestens zwei bildtragende Nomen**, maximal vier. Vom Validator erzwungen.
- **Kein Nomen doppelt bebildern**, wenn zwei Atome denselben Emoji-Glyph teilen.

---

## Nachtrag 2026-07-31: main hat den Abbruch-Pfad entfernt

Während der Umsetzung landete auf `main` der Commit `664e440`
(*feat(lesson): add close button and unify back behavior to skip end screen*). Er führt einen
Schließen-Button ein und vereinheitlicht das Zurück-Verhalten: `onBackPressed()` ruft jetzt
immer `exitLesson()` und verlässt eine laufende Lektion **direkt** zum Pfad.

Damit ist die Ausgangslage aus Abschnitt 1 überholt. Der End-Screen wird nicht mehr an zwei
Stellen erreicht, sondern nur noch an einer: `advance()`, wenn keine Runde mehr folgt. Was das
für dieses Design bedeutet:

- **Die Entscheidung „nur bei echtem Abschluss" ist stärker geworden**, nicht schwächer — sie
  wird jetzt vom Session-Modell erzwungen statt von einem Feld, das an einer von zwei Stellen
  gesetzt wird. `completedFinaleId` bleibt trotzdem sinnvoll: es trägt *welches* Finale gilt.
- **Die schlanke Variante (Abschnitt 8.3) ist von einem regulären Zweig zum Defensivpfad
  geworden.** Erreichbar nur noch, wenn sich eine `finaleId` nicht auflösen lässt — was der
  Validator für autorierte Lektionen verbietet. Sie bleibt bewusst erhalten: ein reduzierter
  Screen ist besser als ein leerer oder ein Absturz, und `TaskShell` nutzt deshalb
  `pack.finales[id]` (nullable) statt `pack.finale(id)` (wirft).
- **Smoke-Test-Punkt 10 der Residual-Notiz** („Abbruch mit Punkten zeigt kein Finale") ist damit
  gegenstandslos: der Abbruch erreicht den End-Screen gar nicht mehr.

Der Merge war konfliktfrei (`git merge-tree`), Build und die 256 Unit-Tests blieben grün.
Abschnitt 1 und die Problembeschreibung bleiben unverändert stehen — sie beschreiben den Stand,
gegen den dieses Design entworfen wurde.
