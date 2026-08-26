package app.abcvorschule.ui.exercise

/**
 * Peg-Geometrie des Satz-Architekten, in blanken dp-Größen, damit die Rechnung
 * unit-testbar bleibt (gleiche Compose-freie Konvention wie [WordFrameSizing]
 * und [TraceGeometry]).
 *
 * **Warum ein eigenes Objekt und nicht [WordFrameSizing].** Dessen Mathematik ist
 * für den Wort-Bauer gebaut, wo ein Rahmen *einen Buchstaben* hält: sie verteilt
 * die Bühne gleichmäßig, löst den Glyphen gegen den längsten Eintrag und weitet
 * dann mit `fittedFrameWidthDp` den Rahmen, wenn der `MinGlyphSp`-Floor gewonnen
 * hat. Auf ganze Wörter angewandt ist genau diese Kette der Fehler gewesen: „der
 * Fisch schwimmt" ergab drei Pegs à 20sp × 0,72 × 8 Zeichen + Polster = 131dp,
 * also 418dp Reihenbreite gegen die 396dp, die [ExerciseStage] überhaupt hergibt
 * — bei `font_scale 1.3` 521dp. `Arrangement.spacedBy(…, CenterHorizontally)`
 * verteilt den Überlauf auf **beide** Seiten, also rutschten erstes *und* letztes
 * Wort aus dem Bild und waren nicht mehr antippbar — die Runde war unlösbar. Bei
 * `font_scale 1.3`, der Einstellung des Testgeräts, traf das **jeden** mehrwortigen
 * Satz des Trainers.
 *
 * Diese Rechnung dreht die Prioritäten um:
 *
 * 1. **Die Reihe passt.** Immer, auf jeder Breite, bei jeder Systemschriftgröße.
 *    Ein unerreichbarer Peg ist kein Layoutfehler, sondern eine kaputte Aufgabe.
 * 2. **Jeder Peg bleibt tippbar** ([MinPegWidthDp], der harte Trefferflächen-Boden
 *    aus [WordFrameSizing.MinFrameDp]).
 * 3. **Jeder Peg ist so breit wie sein eigenes Wort**, nicht wie das längste der
 *    Runde. Das ist der Löwenanteil der zurückgewonnenen Breite („ist" zahlt nicht
 *    mehr für „schwimmt") und gibt der leeren Lücke die Silhouette ihres Wortes —
 *    Längen-Matching ist eine echte Vorlese-Vorstufe, kein Zufallsprodukt.
 * 4. **Der Glyph nimmt, was übrig ist**, gedeckelt bei [MaxGlyphDp].
 *
 * Punkt 4 heißt bewusst: kein Glyph-Floor im Layout. Der Satz-Architekt bricht
 * **nicht** in eine zweite Zeile um (ausdrückliche Produktentscheidung), also kann
 * der Glyph auf schmalen Geräten unter [WordFrameSizing.MinGlyphSp] fallen: ein
 * Fünf-Wort-Satz landet auf 296dp bei rund 14dp, weil fünf 56dp-Trefferflächen plus
 * Abstände die Breite dort schon allein aufbrauchen. Der Satz-Architekt stellt
 * derzeit keinen solchen Satz — seine längsten („der Fisch schwimmt", „Oma hat einen
 * Hut") landen auf 296dp bei 20,8dp und 21,8dp. Statt die Grenze
 * im Layout zu verstecken, hält [ReadableGlyphDp] sie fest und
 * `SentencePegSizingTest` prüft den echten Content-Pack dagegen: ein neu
 * autorierter Satz, der zu lang ist, bricht den Test, nicht den Bildschirm.
 */
object SentencePegSizing {
    /** Deckel wie [WordFrameSizing.MaxGlyphSp] / `AbcDimens.syllableSp`. */
    const val MaxGlyphDp = 46f

    /** Harter Trefferflächen-Boden, identisch zu [WordFrameSizing.MinFrameDp]. */
    const val MinPegWidthDp = 56f

    /** Polster im Peg, je Seite — wie [WordFrameSizing.FramePaddingDp]. */
    const val PegPaddingDp = 8f

    /** Bequemer Abstand zwischen zwei Pegs. */
    const val MaxGapDp = 12f

    /** Enger Abstand, nur um den Glyphen über [ComfortGlyphDp] zu halten. */
    const val MinGapDp = 4f

    /** Zeichenbreite als Anteil der Schriftgröße — wie [WordFrameSizing.GlyphAspect]. */
    const val GlyphAspect = 0.72f

    /**
     * Ab hier ist der bequeme [MaxGapDp] den Platz nicht mehr wert: gewinnt der
     * Abstand, schrumpft das Wort. Gleiche Rangfolge wie
     * [WordFrameSizing.gapDp] („Rahmen gewinnen über Weißraum"), nur an der
     * Lesbarkeitsschwelle des Wort-Bauers aufgehängt.
     */
    const val ComfortGlyphDp = WordFrameSizing.MinGlyphSp

    /**
     * Autorierungs-Grenze, **kein** Layout-Floor: unter dieser gerenderten Größe
     * gilt ein Satz-Peg auf Referenzbreite als nicht mehr vorschultauglich. Wird
     * von `SentencePegSizingTest` gegen den ausgelieferten Pack geprüft, damit ein
     * zu langer neuer Satz beim Autorieren auffällt.
     */
    const val ReadableGlyphDp = 18f

    /**
     * Referenzbreite für die Autorierungs-Grenze: ein Pixel-7-Klasse-Gerät
     * (412dp) minus `AbcDimens.screenHorizontal` je Seite und dem
     * Stage-Polster von `ExerciseStage`. Nicht die 296dp eines 360dp-Geräts —
     * dort ist die Ein-Zeilen-Entscheidung bewusst mit kleinerem Glyphen bezahlt.
     */
    const val ReferenceWidthDp = 348f

    /**
     * Gelöste Reihe: eine gerenderte Glyphgröße für den ganzen Satz (ein Satz mit
     * gemischten Schriftgrößen liest sich nicht als Satz) und eine eigene Breite je
     * Peg.
     */
    data class Row(
        val glyphDp: Float,
        val gapDp: Float,
        val pegWidthsDp: List<Float>,
    ) {
        val widthDp: Float
            get() = pegWidthsDp.sum() + gapDp * (pegWidthsDp.size - 1).coerceAtLeast(0)
    }

    /**
     * Löst die Reihe für [words] auf [availableDp]. Erst mit dem bequemen
     * [MaxGapDp]; nur wenn der Glyph dann unter [ComfortGlyphDp] fiele, wird der
     * Abstand auf [MinGapDp] gezogen — ein engerer Abstand kann Breite nur
     * freigeben, also ist das Ergebnis nie schlechter.
     */
    fun solve(availableDp: Float, words: List<String>): Row {
        if (words.isEmpty()) return Row(MaxGlyphDp, MaxGapDp, emptyList())
        val comfortable = solveAtGap(availableDp, words, MaxGapDp)
        return if (comfortable.glyphDp >= ComfortGlyphDp) {
            comfortable
        } else {
            solveAtGap(availableDp, words, MinGapDp)
        }
    }

    /**
     * Größe des fertigen Satzes, wenn er nach dem letzten Peg als eine Zeile Text
     * erscheint. Derselbe Deckel wie die Pegs, nur gegen den ganzen Satz gelöst
     * (Wörter plus Leerzeichen), damit auch dieser Text nicht über den Rand läuft —
     * bei `headlineSmall` tat er das: „Oma hat einen Hut" braucht 17 Zeichen, bei
     * `font_scale 1.3` also rund 382dp von 296dp.
     */
    fun completedGlyphDp(availableDp: Float, words: List<String>): Float {
        if (words.isEmpty()) return MaxGlyphDp
        val chars = words.sumOf { it.length } + (words.size - 1)
        return (availableDp / (GlyphAspect * chars.coerceAtLeast(1))).coerceIn(1f, MaxGlyphDp)
    }

    /**
     * Rechnet eine gerenderte dp-Größe in den `sp`-Wert um, der bei [fontScale]
     * genau so groß rendert. Gleiche Richtung wie [WordFrameSizing.glyphSp] und
     * `FinaleLayout.capEffectiveSize`: beide Budgets sind dp, also teilt die
     * Systemschriftgröße das Ergebnis — **und genau das macht den Fit
     * skalenunabhängig.** Die gerenderte Reihenbreite ist damit bei `font_scale
     * 1.0`, `1.3` und `2.0` dieselbe, statt bei jeder Stufe weiter über den Rand
     * zu wachsen. Unter 1.0 wird nicht hochskaliert: wer seine Schrift kleiner
     * stellt, bekommt das Design, nicht mehr.
     */
    fun glyphSp(glyphDp: Float, fontScale: Float): Float =
        if (fontScale > 1f) glyphDp / fontScale else glyphDp

    /** Breite, die ein Wort aus [chars] Zeichen bei [glyphDp] braucht. */
    fun naturalPegWidthDp(glyphDp: Float, chars: Int): Float =
        glyphDp * GlyphAspect * chars.coerceAtLeast(1) + 2 * PegPaddingDp

    /**
     * Der eigentliche Solve, bei festem Abstand. „Water-filling": der Glyph wird
     * gegen die freie Breite gelöst, dann wird der erste Peg, der unter
     * [MinPegWidthDp] fiele, auf den Boden **festgenagelt** und der Rest neu
     * gelöst. Festnageln kostet Breite, kann also weitere Pegs unter den Boden
     * drücken — die Schleife ist monoton und endet nach höchstens einem Durchlauf
     * je Wort.
     *
     * Ohne diesen Schritt wäre der Fit gelogen: „im" auf 56dp anzuheben, nachdem
     * der Glyph gegen seine natürlichen 41dp gelöst wurde, holt sich 15dp aus dem
     * Nichts — und das ist wieder Überlauf.
     */
    private fun solveAtGap(availableDp: Float, words: List<String>, gapDp: Float): Row {
        val chars = words.map { it.length.coerceAtLeast(1) }
        val pinned = BooleanArray(words.size)
        val gaps = gapDp * (words.size - 1)
        var glyphDp = MaxGlyphDp

        repeat(words.size + 1) {
            val freeIndices = chars.indices.filterNot { pinned[it] }
            if (freeIndices.isEmpty()) {
                // Alles am Boden: der Glyph ist dann nicht mehr von der Reihe,
                // sondern vom engsten Peg begrenzt — er muss in seine 56dp passen.
                val pennedIn = chars.indices.minOf { index ->
                    (MinPegWidthDp - 2 * PegPaddingDp) / (GlyphAspect * chars[index])
                }.coerceIn(1f, MaxGlyphDp)
                return fitted(Row(pennedIn, gapDp, widths(pennedIn, chars, pinned)), availableDp)
            }
            val fixedWidth = MinPegWidthDp * (words.size - freeIndices.size)
            val freePadding = 2 * PegPaddingDp * freeIndices.size
            val freeChars = freeIndices.sumOf { chars[it] }
            val budget = availableDp - gaps - fixedWidth - freePadding
            glyphDp = (budget / (GlyphAspect * freeChars)).coerceIn(1f, MaxGlyphDp)

            val violator = freeIndices.firstOrNull {
                naturalPegWidthDp(glyphDp, chars[it]) < MinPegWidthDp
            } ?: return fitted(Row(glyphDp, gapDp, widths(glyphDp, chars, pinned)), availableDp)
            pinned[violator] = true
        }
        return fitted(Row(glyphDp, gapDp, widths(glyphDp, chars, pinned)), availableDp)
    }

    /**
     * Letzte Reißleine, und die einzige Stelle, an der [MinPegWidthDp] nachgibt:
     * fünf Pegs à 56dp plus [MinGapDp] brauchen 296dp, ein 320dp-Gerät stellt aber
     * nur 256dp bereit. Ein Satz aus fünf Wörtern ist dort in **einer** Zeile
     * physikalisch nicht mit vollen Trefferflächen darstellbar. Dann schrumpft die
     * ganze Reihe gleichmäßig auf die vorhandene Breite — rund 48dp je Peg, also
     * weiter über Androids eigenem 48dp-Minimum, und vor allem vollständig
     * sichtbar. Ein etwas kleineres Ziel ist immer noch tippbar; ein Peg jenseits
     * des Bildschirmrands ist es nicht.
     */
    private fun fitted(row: Row, availableDp: Float): Row {
        if (row.pegWidthsDp.isEmpty() || row.widthDp <= availableDp) return row
        val gaps = row.gapDp * (row.pegWidthsDp.size - 1)
        val pegs = row.widthDp - gaps
        if (pegs <= 0f) return row
        val scale = ((availableDp - gaps) / pegs).coerceIn(0.1f, 1f)
        return Row(row.glyphDp * scale, row.gapDp, row.pegWidthsDp.map { it * scale })
    }

    private fun widths(glyphDp: Float, chars: List<Int>, pinned: BooleanArray): List<Float> =
        chars.indices.map { index ->
            if (pinned[index]) {
                MinPegWidthDp
            } else {
                naturalPegWidthDp(glyphDp, chars[index]).coerceAtLeast(MinPegWidthDp)
            }
        }
}
