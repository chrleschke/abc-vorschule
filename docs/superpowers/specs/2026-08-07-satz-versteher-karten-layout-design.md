# Satz-Versteher — Kartenlayout und Antwort-Feedback (Design)

Status: implementation-ready · Datum: 2026-08-07

Folgedesign zu `2026-08-07-satz-versteher-design.md`. Der Trainer ist gebaut und
ausgeliefert; dieses Dokument ändert **nur** seine Bühne: wo die Karten stehen,
wie groß die Bilder sind, wie die Karte auf Treffer und Fehltipp reagiert.
Content, Sprach-Pipeline, Validator und Scoring bleiben unangetastet.

## 1. Problem

Fünf Beobachtungen am ausgelieferten Trainer:

1. **Karten kleben unten.** `ExerciseStage` verankert den Antwortblock am unteren
   Rand. Beim Satz-Versteher besteht der Aufgabenblock aber nur aus dem Speaker —
   es gibt keinen Aufgabentitel, keine Kachelreihe, kein Wort. Über den Karten
   steht damit ein halber Bildschirm Leerraum, und die Karten selbst sitzen dort,
   wo die Hand des Kindes sie beim Tippen verdeckt.
2. **Bilder zu klein.** Die Emoji-Reihe deckelt bei 72/56/44sp, gestaffelt nach
   Atomzahl. Der Trainer ist ein *Bild*-Vergleich; die Bilder sind sein einziger
   Inhalt und dürfen die Bühne dominieren.
3. **Grauer Kartengrund frisst Kontrast.** `CreamElevated` auf `Cream` ist ein
   1.22:1-Unterschied — als Flächentrennung kaum sichtbar, aber genug, um die
   Emojis abzudunkeln. Die Karte gewinnt nichts und die Bilder verlieren.
4. **Fehltipp ist unsichtbar.** Ein Miss löst `nudge`-Haptik und erneutes
   Vorlesen aus. Beides ist richtig, aber nichts davon zeigt dem Kind, *welche*
   Karte es getippt hat. Bei stummgeschaltetem Gerät fehlt jede Rückmeldung.
5. **Treffer ist zu leise gefeiert.** Die richtige Karte bekommt einen grünen
   Rahmen, während der Satz erneut vorgelesen wird — 4dp Rahmen als einzige
   Belohnung, während gleichzeitig eine ganze Vorlesephase läuft.

## 2. Entscheidungen und ihre Grenzen

Zwei Punkte wurden in der Session explizit entschieden und begrenzen den Umbau:

- **Kein Rot bei Fehltipp — nur Bewegung.** Der Fehltipp wird *bewegt*
  quittiert, nicht gefärbt. Damit bleibt der Umbau vollständig innerhalb der
  Produktprinzipien: §8 („Falsche Antwort wird **nicht** rot markiert") und §10
  (`ClayRed` = Fehlertext für Erwachsene) gelten unverändert weiter, für alle
  Trainer. Kein Ausnahmeeintrag, keine neue Farbrolle.
- **Emojis bleiben in *einer* Zeile.** Die Alternative — ein Grid, das 2 Emojis
  übereinander und 3 als 2+1 setzt — hätte die Bilder um ~70 % vergrößert, weil
  jeder Glyph dann das doppelte Breitenbudget bekommt. Sie ist verworfen: die
  Reihe ist die eingeführte Lesart („Emoji-Reihen, 1–3 Bilder", §6), und ein
  Umbruch würde die Kartenform je Runde wechseln lassen.

  Die Folge muss beim Namen genannt werden: bei einer Zeile ist die **Breite** die
  bindende Grenze, nicht die Basisgröße. Auf einem 411dp-Gerät (371dp Bühne)
  bleibt für eine 3-Emoji-Karte auch nach dem Umbau nur ~48sp statt 44sp. Nur die
  1- und 2-Emoji-Karten gewinnen wirklich — das sind 127 der 144 ausgelieferten
  Karten, also der Normalfall.

## 3. Karten weiter oben: `ExerciseStage.answerAnchor`

`ExerciseStage` ist die verbindliche Grundform aller Übungen (§9) und wird von
neun Trainern benutzt. Sie bekommt deshalb keinen Sonderfall, sondern einen
opt-in Parameter mit unveränderter Vorbelegung:

```kotlin
enum class AnswerAnchor { Bottom, BelowCenter }

@Composable
fun ExerciseStage(
    modifier: Modifier = Modifier,
    answerAnchor: AnswerAnchor = AnswerAnchor.Bottom,
    prompt: @Composable ColumnScope.() -> Unit,
    answers: @Composable ColumnScope.() -> Unit,
)
```

- `Bottom` ist das heutige Verhalten, Zeichen für Zeichen: Aufgabenblock in einer
  `weight(1f)`-Box zentriert, Antwortblock unten mit 8dp Luft. **Alle bestehenden
  Aufrufer bleiben unverändert** und dürfen sich visuell nicht bewegen.
- `BelowCenter` gibt dem Aufgabenblock eine **feste Bruchhöhe der Bühne** statt
  eines Gewichts: `BoxWithConstraints` misst die Bühne, der Aufgabenblock erhält
  `maxHeight * PromptHeightFraction` (0.52f), der Antwortblock schließt direkt
  darunter an.

Warum eine Bruchhöhe und kein zweites `weight`: Gewichte teilen den *Restraum*
nach Abzug der Karten auf. Die Kartenhöhe wächst hier mit der Emoji-Größe, also
würde die Kartenoberkante mit jeder Größenänderung wandern — bei hohen Karten
sogar über die Bildschirmmitte hinaus, also in die Gegenrichtung. Eine
Bruchhöhe der Bühne ist von der Kartenhöhe unabhängig und liefert genau die
Zusage: Oberkante der Karten bei 52 % der Bühnenhöhe, knapp unterhalb der Mitte.

`PromptHeightFraction` ist eine benannte Konstante in `ExerciseStage.kt`, damit
der Wert eine Begründung tragen kann und nicht als nackte 0.52f im Layout steht.

Die 52 % gelten für die Bühne, also für den Bereich *unter* dem Chrome (Punkte,
Chevrons, Fortschrittsbalken) — dieser Bereich ist es, den `TaskShell` als
`weight(1f)`-Box an `TrainerHost` gibt.

## 4. Kartenoptik: Rahmen statt Fläche

`PictureCard` verliert `background(color = CreamElevated, …)`. Die Karte ist dann
eine reine Rahmenfläche auf `Cream`.

Der neutrale Rahmen bleibt `WarmMuted.copy(alpha = 0.9f)` bei 3dp — auf `Cream`
sind das 4.45:1 und damit eine **stärkere** Kartengrenze als die 1.22:1-Füllung,
die er ersetzt. Der Punkt der Änderung ist genau das: die Grenze wird von der
Fläche auf die Linie verlagert, wo sie sichtbar ist, und die Emojis stehen auf
der hellsten Fläche der Übung statt auf Beige.

**Mitzunehmen: `CardBorderGreen` entfällt.** Die Konstante `Color(0xFF3A7A44)`
existiert ausschließlich, weil volles `LeafGreen` auf `CreamElevated` nur 2.87:1
erreicht und damit die 3:1-Schwelle für UI-Komponenten verfehlt. Ohne die
`CreamElevated`-Füllung liegt `LeafGreen` auf `Cream` bei 3.5:1 und erfüllt sie.
Die Sonderfarbe wird gelöscht, der Bestätigungsrahmen benutzt die Rollenfarbe
`LeafGreen` aus §10 direkt. Das ist keine Nebenaufräumung, sondern die
Voraussetzung dafür, dass §10 („eine Bedeutung pro Farbe") hier wieder ohne
Fußnote gilt.

## 5. Größere Bilder

Zwei Hebel, beide in `SentencePictureCardSizing` bzw. `PictureCard`:

**(a) Basisgrößen hoch.** 72/56/44sp → **110/76/56sp**. Die 2er- und 3er-Werte
liegen bewusst *über* dem, was ein 411dp-Gerät durchlässt: der Breitendeckel
schneidet sie dort auf ~72 bzw. ~48sp zu. Das ist beabsichtigt — auf breiteren
Geräten (Tablet, Landscape) darf die Karte den Gewinn mitnehmen, und der Deckel
ist die Instanz, die Überlauf verhindert, nicht die Basisstaffelung.

**(b) Breitenbudget vergrößern.** Karten-Innenabstand horizontal 10dp → **4dp**,
Kartenabstand in der Reihe 14dp → **8dp**. Beides fließt direkt in
`contentWidthDp` und damit in den Deckel. Der vertikale Innenabstand bleibt bei
18dp; er begrenzt nichts.

Ergebnis auf einem 411dp-Gerät (371dp Bühnenbreite, Karte 181.5dp, Inhalt
173.5dp), fontScale 1.0:

| Emojis je Karte | ausgelieferte Karten | heute | neu | bindende Grenze |
| --- | --- | --- | --- | --- |
| 1 | 6 | 72sp | **110sp** | Basis (Deckel 144) |
| 2 | 121 | 56sp | **~72sp** | Breitendeckel |
| 3 | 17 | 44sp | **~48sp** | Breitendeckel |

Auf dem schmalsten unterstützten Gerät (320dp → 280dp Reihe → 136dp Karte →
128dp Inhalt) ergeben sich 106 / 53 / **35**sp. Die 35sp für drei Emojis sind der
kritische Wert und lösen den bestehenden Test
`emojiRowFitsTheNarrowestSupportedCardForEveryValidatorPermittedCount` ab, der
heute mit 113dp Inhaltsbreite rechnet — die Konstante dort muss auf 128dp
nachgezogen werden, sonst prüft er eine Karte, die es nicht mehr gibt.

Ebenso muss `emojiSizeIsUnchangedAtFontScaleOneWhenThereIsRoom` auf die neuen
Basiswerte gezogen werden. Er ist ein Kein-Regressions-Anker für die
*Staffelung*, kein Denkmal für die Zahl 72 — er bleibt also erhalten und nennt
110/76/56.

Unverändert bleibt: `MinEmojiSp`, `EmojiAdvanceEm`, das fontScale-Zurückgeben
(Emojis sind Bilder, keine Prosa) und das Abrunden.

## 6. Fehltipp: Schütteln

Getippte falsche Karte wackelt horizontal. Alles Bestehende bleibt: `nudge`-
Haptik, `onResult(false, …)`, erneutes Vorlesen des Satzes über `missCueForCurrent`,
„Zeig mir" nach zwei Misses, keine Punktstrafe. Farben und Rahmen ändern sich nicht.

**Bewegung:** ±12dp Amplitude, 2.5 Schwingungen, 420ms, linear abklingende
Amplitude (die letzte Schwingung ist die kleinste, damit die Karte ruhig
ausläuft statt abrupt zu stoppen).

**Testbarkeit.** Das Repo hat keine androidTests, also darf die Kurve nicht in
der Composable stecken. Sie wird ein Compose-freies Objekt neben
`SentencePictureCardSizing`, nach dem Muster von `BurstGeometry.sparkOffsets`:

```kotlin
object SentencePictureCardShake {
    const val AmplitudeDp = 12f
    const val DurationMs = 420
    const val Cycles = 2.5f

    /** Horizontaler Versatz in dp für [progress] 0f..1f. */
    fun offsetDp(progress: Float): Float
}
```

Zusagen, die der Test festhält: `offsetDp(0f) == 0f`, `offsetDp(1f) == 0f` (die
Karte landet exakt dort, wo sie stand — kein Pixel-Drift), `|offsetDp(p)| <=
AmplitudeDp` für alle p, Vorzeichenwechsel entsprechend `Cycles`, und
monoton fallende Hüllkurve (späte Extrema sind kleiner als frühe).

**Zustand.** `PictureCard` bekommt kein eigenes Gedächtnis; der Trainer merkt
sich, welche Seite zuletzt falsch getippt wurde, und zählt einen Auslöser hoch
(`wrongTick`), damit zwei Fehltipps auf dieselbe Karte zwei Schüttler auslösen.
Der Versatz wird über `graphicsLayer { translationX = … }` gezeichnet — eine
reine Zeichenoperation, die kein Neu-Layout der Reihe auslöst und die
Nachbarkarte nicht verschiebt.

## 7. Treffer: grüner Rahmen und Karte groß in die Mitte

Bei richtiger Antwort läuft ein einziger Fortschrittswert
(`animateFloatAsState`, 0f → 1f, 360ms, `FastOutSlowInEasing`) und treibt drei
Dinge gleichzeitig:

1. **Gewinnerkarte breiter:** ihr `weight` in der Reihe 1f → 3f.
2. **Verliererkarte weg:** ihr `weight` 1f → 0.001f und ihre Alpha 1f → 0f. Mit
   fast null Gewicht schrumpft ihr Slot mit, der Kartenabstand von 8dp bleibt —
   die Gewinnerkarte landet also 4dp neben der optischen Mitte. Das ist unter der
   Wahrnehmungsschwelle und billiger als eine zusätzlich animierte
   `Arrangement`-Lücke.
3. **Emojis größer:** ein `baseScale`-Faktor 1f → **1.6f** in
   `emojiSp(atomCount, contentWidthDp, fontScale, baseScale)`.

Der grüne Rahmen (`LeafGreen`, 4dp) setzt sofort beim Tap ein, ohne Animation —
er ist die Antwort auf „war es richtig?" und darf nicht erst einlaufen.

**Warum Gewicht + `baseScale` und nicht `graphicsLayer { scaleX/scaleY }`:** eine
`graphicsLayer`-Skalierung rastert den Text in seiner ursprünglichen Größe und
zieht die Bitmap auf 1.6× — bei einem Emoji-Glyphen, der die halbe Bühne füllt
und dort mehrere hundert Millisekunden stehen bleibt, ist das sichtbar weich.
Über das Gewicht wird die Karte *echt* breiter gemessen, `BoxWithConstraints`
liefert die neue Breite, und dieselbe Sizing-Funktion rechnet die Emoji-Größe
neu — der Glyph wird in seiner Endgröße gerastert und ist scharf.

Deshalb braucht `emojiSp` den `baseScale`-Parameter überhaupt: die reine
Verbreiterung würde nichts bringen, weil auf der breiten Karte weiter die
*Basisgröße* bindet, nicht der Deckel. `baseScale` hebt die Basis, der Deckel
bleibt die Obergrenze. Endgröße bei 2 Emojis auf voller Bühnenbreite:
76 × 1.6 = 121sp, Deckel bei ~150sp — also 121sp, gut das Doppelte des heutigen
Zustands.

Der Parameter ist mit `baseScale: Float = 1f` vorbelegt; der Normalpfad ändert
sich nicht.

**Dauer.** Die Karte hält ihren Endzustand, bis die Runde weiterläuft — sie
braucht keine Kenntnis der `SuccessPhase`. Die Sequenz im ViewModel liefert das
gewünschte „solange das Audio wiederholt wird" von selbst: `SpeakAnswer` liest
den Satz erneut vor, `onSuccessSpeechFinished` schaltet auf `ShowBurst`, erst
`onSuccessBurstFinished` wechselt die Runde und nimmt die Composable mit. Die
vergrößerte Karte steht also über die gesamte Vorlesephase *und* den Stern.

**Kollision mit dem Stern:** keine. `SuccessBurst` zeichnet im oberen 34 % der
Bühne (`fillMaxHeight(0.34f)`, `TopCenter`), die Karten beginnen bei 52 %.

**Interaktion.** Nach dem Treffer sind beide Karten schon heute nicht mehr
tappbar (`enabled = !solvedCorrect && !resolved`). Das bleibt; die Verliererkarte
ist zusätzlich unsichtbar.

## 8. „Zeig mir" (`resolved`)

Unverändert im Verhalten, aber es muss ausdrücklich festgehalten werden, weil
Abschnitt 7 sonst mitgenommen würde: Auflösen bekommt **keine**
Vergrößerungsanimation und **keinen** grünen Rahmen. §8 sagt „Auflösen ist nicht
grün", und die Feier gehört dem Kind, das die Runde gelöst hat. Die richtige
Karte wird beim Auflösen weiter nur markiert wie bisher — über `highlight`, das
`resolved` mit einschließt. Damit trennt sich die Logik: `highlight` (Rahmen,
auch beim Auflösen) und `celebrate` (Vergrößerung, nur bei `solvedCorrect`).

Das ist der eine Punkt, an dem die heutige Implementierung eine Bedeutung
mitträgt, die jetzt gespalten werden muss — `(solvedCorrect || resolved)` reicht
nicht mehr als einziges Signal an die Karte.

## 9. Satz oben einblenden? Nein.

Die Frage war ausdrücklich didaktisch zu entscheiden. Entscheidung: der Satz
erscheint **nicht** als Text, solange deutsches TTS verfügbar ist.

1. **Das Lernziel ist Hören.** Der Trainer prüft Satzverständnis über Plural,
   Partizip II und Präteritum — allein über das Ohr. Ein sichtbarer Satz ist für
   ein Vorschulkind nicht dekodierbar und konkurriert genau mit den zwei Bildern,
   die es vergleichen soll.
2. **Er verführt den Erwachsenen zum Vorlesen.** Damit ersetzt die
   Erwachsenenstimme das eigene Zuhören, und der Trainer misst die Aufmerksamkeit
   der Eltern statt die des Kindes.
3. **Schriftbewusstsein ist Trainer 5.** Beim Satz-Architekt hängen Wörter als
   anfassbare Schilder an der Wäscheleine — dort lernt das Kind, dass Wörter
   Zeichen sind. Es hier zu doppeln verwischt die Trainerprofile, was §9
   ausdrücklich verbietet („Keine doppelte Aufgabe+Vorschau desselben Tokens").
4. **Der Miss-Pfad liest erneut vor.** Stünde der Satz sichtbar da, wäre das
   Wiederholen redundant, und das Kind lernte zu warten statt noch einmal
   hinzuhören.

Der TTS-Fallback bleibt unangetastet: ohne deutsche Stimme muss ein Erwachsener
vorlesen, und das ist der eine Fall, in dem der Satz sichtbar sein *muss*
(`sentence_picture_fallback_text`).

## 10. Was sich nicht ändert

- Content-Pack, `SentencePictureSpec`, Validator-Regeln, TTS-Pipeline.
- Seitenwahl (`SentencePictureSides.correctOnLeft`) und ihre vier Tests.
- Scoring, `misses`-Zählung, „Zeig mir" nach zwei Misses, `interactionLocked`-
  Muster (Opacity 0.5↔1.0, `tween(200)`).
- `MinEmojiSp`, `EmojiAdvanceEm`, fontScale-Verhalten der Emoji-Größe.
- Jeder andere Trainer. `ExerciseStage` ohne `answerAnchor`-Argument verhält sich
  identisch zu heute.

## 11. Testplan

JVM-Tests (`./gradlew :app:testDebugUnitTest`), keine androidTests im Repo — die
Logik muss also Compose-frei liegen, damit sie prüfbar ist.

**`SentencePictureCardShake`** (neu, in `SentencePictureSidesTest` oder eigener
Datei): Nullpunkt bei 0f und 1f, Amplitudendeckel, Vorzeichenwechsel-Zahl,
fallende Hüllkurve.

**`SentencePictureCardSizing.emojiSp`** (bestehende Tests anpassen):
- `emojiSizeIsUnchangedAtFontScaleOneWhenThereIsRoom` → 110/76/56.
- `emojiRowFitsTheNarrowestSupportedCardForEveryValidatorPermittedCount` →
  Inhaltsbreite 113dp → 128dp (4dp Innenabstand, 8dp Kartenabstand).
- Neu: `baseScale` skaliert die Basis, respektiert aber den Breitendeckel —
  `emojiSp(2, wide, 1f, 1.6f) == 76 * 1.6` und `emojiSp(3, narrow, 1f, 1.6f)`
  bleibt beim Deckelwert der schmalen Karte.
- Neu: `baseScale = 1f` ist verhaltensgleich mit dem Aufruf ohne Parameter.
- Unverändert weiterlaufen müssen: `emojiShrinksWithMoreAtoms`,
  `emojiSizeStaysPositiveForDegenerateInput`, `emojiSizeNeverGrowsWithFontScale`.

**`ExerciseStage`**: `PromptHeightFraction` ist eine Konstante, kein Verhalten —
sie wird nicht eigens getestet. Die Zusage „`Bottom` verhält sich wie heute"
sichert der Compiler (Default-Argument, kein Aufrufer geändert) plus
`./gradlew :app:assembleDebug`.

**Manuelle Sichtprüfung** (kein Test kann sie ersetzen, das Repo hat keine
Screenshot-Tests): Kartenoberkante knapp unter der Mitte · Emojis deutlich
größer · kein grauer Kartengrund · Fehltipp wackelt die getippte Karte · Treffer
zieht die Karte groß in die Mitte und hält sie bis zum Sternflug · bei
`font_scale 1.3` läuft keine 3-Emoji-Reihe über.

## 12. Doku-Folgen

- **§6 (Satz-Versteher)**: Kartenoptik — Rahmen ohne Fläche, Emojis füllen die
  Karte; Fehltipp wird *bewegt* quittiert, nicht gefärbt (damit die nächste
  Session nicht doch nach Rot greift); Treffer zieht die Karte groß in die Mitte,
  Auflösen nicht.
- **§9 (Layout-Grundform)**: `BelowCenter` als benannte Ausnahme — der
  Antwortblock des Satz-Verstehers beginnt bei 52 % der Bühnenhöhe, weil sein
  Aufgabenblock nur den Speaker trägt.
- **§10 (Design-System)**: `CardBorderGreen` entfällt, `LeafGreen` gilt direkt;
  `ExerciseStage` hat einen `answerAnchor`-Parameter mit `Bottom` als Vorbelegung.
- **§8 und §10 bleiben unverändert** in der Sache: kein Rot für Kinder,
  `ClayRed` bleibt Erwachsenen-Fehlertext. Das ist die Session-Entscheidung und
  gehört als solche nicht in die Prinzipien — dort steht die Regel schon.
