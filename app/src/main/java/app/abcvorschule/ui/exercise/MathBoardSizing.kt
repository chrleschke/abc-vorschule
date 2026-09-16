package app.abcvorschule.ui.exercise

/**
 * Geometrie der Mengenwahl — Aufgabenblock und Antwortblock —, in blanken
 * dp-Größen. Gleiche Compose-freie Konvention wie [SentencePegSizing] und
 * [MultiplicationMatrix], damit die Rechnung unit-testbar bleibt.
 *
 * **Warum es das braucht.** Die drei Antwort-Kacheln standen mit fest verdrahteten
 * 28sp in einer `FlowRow`. Auf der echten Bühne eines 360dp-Telefons —
 * Bildschirmbreite minus `AbcDimens.screenHorizontal` je Seite, also 320dp —
 * brauchen drei Kacheln à zwei Emojis rund 350dp. Die Reihe brach in zwei Zeilen
 * um, und weil [ExerciseStage] den Antwortblock ungewichtet gegen die **volle**
 * Höhe misst (Absicht, siehe dort: `PromptHeightFraction`), blieb für den
 * Aufgabenblock mit seinem `weight(1f)` nichts übrig: die Rechnung selbst war auf
 * halber Höhe abgeschnitten — bei „4 + 3" (Antworten 6/7/8) aus Lektion 1 ebenso
 * wie bei jeder anderen Runde mit Bildwort, und schon bei `font_scale 1.0`.
 *
 * Rangfolge, verbindlich:
 *
 * 1. **Die drei Kacheln stehen in einer Reihe.** Genau drei Optionen mit gleichen
 *    Dimensionen sind Produktregel (§8); ein Umbruch macht daraus zwei ungleiche
 *    Zeilen und verdoppelt die Höhe.
 * 2. **Jede Kachel bleibt tippbar** ([MinTileDp], `AbcDimens.kidTouch`).
 * 3. **Aufgabe und Antwort teilen sich die Bühne** ([MaxAnswersHeightFraction]) —
 *    einschließlich des Auflösen-Knopfs, der nach zwei Fehlversuchen dazukommt.
 *    Er wird von Anfang an mitgerechnet ([ResolveReserveDp]), sonst rückt der
 *    Aufgabenblock mitten in der Runde hoch (§9: „Der Aufgabenblock steht still").
 * 4. **Der Aufgabenblock nimmt, was der Antwortblock übrig lässt**, gedeckelt bei
 *    dem, was [QuantityGrouping.promptEmojiSizeSp] ohnehin vorsieht. Auf breiten,
 *    hohen Geräten ändert sich also nichts.
 * 5. **Die Emojis nehmen, was übrig ist**, gedeckelt bei [MaxChoiceEmojiDp].
 *
 * Fällt eine Bildmenge unter [MinCountableEmojiDp], ist sie nicht mehr abzuzählen —
 * ein 15dp-Marienkäfer ist kein Zählobjekt, sondern ein Punkt. Dann schaltet die
 * Runde auf die symbolische Darstellung um, die §8 für Mengen ab 11 ohnehin
 * vorsieht: ein Bild neben der Ziffer, für Kacheln **und** Aufgabe
 * ([QuantityRepresentation.forceSymbolicForChoices]) — sonst stünde eine
 * Mengengruppe neben einem einzelnen Symbol.
 *
 * **Warum dp und nicht sp.** Der erste Anlauf rechnete in sp und multiplizierte mit
 * `fontScale`. Das ist seit Androids **nichtlinearer** Schriftskalierung falsch:
 * gemessen rendert 14sp bei `font_scale 1.3` größer als 14 × 1,3, 34sp deutlich
 * kleiner als 34 × 1,3 — kleine Größen werden angehoben, große gestaucht. Die
 * Rechnung hier nimmt deshalb nur noch gerenderte dp entgegen; die Umrechnung
 * macht `Density` in [VisualQuantityBoard], wo die Kurve eingebaut ist. Ein Emoji
 * ist dabei ein Quadrat von [EmojiAspect] mal Schriftgröße (gemessen,
 * `EmojiAspectTest`).
 */
object MathBoardSizing {
    /**
     * Gerenderte Kantenlänge eines Emojis als Anteil seiner Schriftgröße — Emojis
     * sind breiter und höher als lateinische Glyphen ([WordFrameSizing.GlyphAspect]
     * 0,72). Gemessen über 12sp…44sp und `font_scale` 1,0/1,3/2,0: höchstens 1,282
     * breit und 1,209 hoch. Mit Headroom auf 1,3 aufgerundet, damit die Rechnung nie
     * zu klein schätzt. `EmojiAspectTest` hält die Messung am Gerät fest.
     */
    const val EmojiAspect = 1.3f

    /**
     * Deckel einer Antwort-Kachel: was die bisher fest verdrahteten 28sp auf dem
     * Testgerät gerendert haben. Ein **dp**-Deckel, kein sp-Deckel — die Mengen in
     * der Kachel sind Bilder, keine Schrift; würden sie mit der Systemschriftgröße
     * mitwachsen, wäre genau das wieder der Umbruch, den diese Rechnung verhindert.
     * Die Ziffer darunter skaliert weiterhin mit.
     */
    const val MaxChoiceEmojiDp = 36f

    /** Unter dieser gerenderten Kantenlänge ist eine Menge nicht mehr abzuzählen. */
    const val MinCountableEmojiDp = 20f

    /**
     * Boden der symbolischen Darstellung. Dort zählt niemand mehr Bilder — das Bild
     * steht nur noch neben der Ziffer, die die eigentliche Antwort ist. Deshalb darf
     * es kleiner werden als [MinCountableEmojiDp]; ganz verschwinden soll es nicht.
     */
    const val MinSymbolicEmojiDp = 15f

    /** Innenabstand einer Kachel, je Seite bzw. oben und unten. */
    const val TilePaddingHorizontalDp = 16f
    const val TilePaddingVerticalDp = 12f

    /** Bequemer Abstand zwischen zwei Kacheln. */
    const val TileGapDp = 12f

    /**
     * Enger Abstand, nur damit drei Kacheln mit voller Trefferfläche auf eine
     * schmale Bühne passen: auf einem 320dp-Gerät (280dp Bühne) brauchen drei
     * 80dp-Kacheln mit bequemem Abstand 264dp von 256dp. Weißraum gibt dann nach,
     * nicht die Trefferfläche — gleiche Rangfolge wie [WordFrameSizing.gapDp]
     * („Rahmen gewinnen über Weißraum").
     */
    const val MinTileGapDp = 4f

    /** Abstand zwischen zwei Emojis einer Zeile und zwischen zwei Zeilen. */
    const val EmojiGapDp = 4f

    /** Bequeme Trefferfläche, `AbcDimens.kidTouch`. */
    const val MinTileDp = 80f

    /** Breiten-Deckel und seitliche Luft der Blöcke in [ExerciseStage]. */
    const val MaxStageWidthDp = 420f
    const val SideGutterDp = 12f

    /** Senkrechtes Polster des Aufgabenblocks in [ExerciseStage], je Seite. */
    const val PromptPaddingVerticalDp = 8f

    /** Unterer Rand des Antwortblocks in [ExerciseStage]. */
    const val BottomPaddingDp = 8f

    /**
     * Höhe der Speaker-Kopfzeile: `AbcSpeakerButton` (56dp Trefferflächen-Boden)
     * plus das obere Polster der Kopfzeile in [ExerciseStage]. In der Mengenwahl
     * trägt sie keinen Titel, ist also über alle Runden gleich hoch (§9: „feste
     * Höhe über alle Trainer").
     */
    const val ChromeHeightDp = 56f + 8f

    /**
     * Platz, den der Auflösen-Knopf nach zwei Fehlversuchen braucht: sein
     * Trefferflächen-Boden (`AbcResolveButton`, 56dp) plus der Abstand, mit dem der
     * Antwortblock seine Kinder setzt (14dp).
     */
    const val ResolveReserveDp = 56f + 14f

    /**
     * Anteil der Bühnenhöhe, den der Antwortblock höchstens bekommt. Die andere
     * Hälfte gehört der Aufgabe — sie ist das, was das Kind lesen muss.
     */
    const val MaxAnswersHeightFraction = 0.5f

    /** Abstand zwischen Menge, Rechenzeichen und Menge im Aufgabenblock. */
    const val PromptGapDp = 20f

    /**
     * Gelöste Darstellung: die gerenderte Kantenlänge eines Emojis und die
     * Entscheidung Bildmenge oder Symbol.
     */
    data class ChoiceLayout(val emojiDp: Float, val symbolic: Boolean)

    /** Breite, die einem Block auf einer [stageWidthDp] breiten Bühne bleibt. */
    fun availableWidthDp(stageWidthDp: Float): Float =
        minOf(stageWidthDp, MaxStageWidthDp) - 2 * SideGutterDp


    /**
     * Abstand zwischen den Kacheln: [TileGapDp], solange drei Kacheln mit voller
     * Trefferfläche damit in die Reihe passen, sonst [MinTileGapDp].
     */
    fun tileGapDp(stageWidthDp: Float): Float =
        if (3 * MinTileDp + 2 * TileGapDp <= availableWidthDp(stageWidthDp)) {
            TileGapDp
        } else {
            MinTileGapDp
        }

    /**
     * Größte Emoji-Kantenlänge, bei der drei Kacheln nebeneinander passen. Der
     * Trefferflächen-Boden [MinTileDp] steht darunter und weitet nur schmale
     * Kacheln — begrenzen kann er nicht, sonst wäre die Reihe unlösbar.
     */
    fun choiceWidthCapDp(stageWidthDp: Float): Float {
        val tile = (availableWidthDp(stageWidthDp) - 2 * tileGapDp(stageWidthDp)) / 3f
        return (tile - 2 * TilePaddingHorizontalDp - EmojiGapDp) / 2f
    }

    /**
     * Größte Emoji-Kantenlänge, bei der ein [rows]-zeiliger Stapel samt Ziffer
     * ([numeralLineDp], gerendert gemessen) ins Höhenbudget passt.
     */
    fun choiceHeightCapDp(stageHeightDp: Float, rows: Int, numeralLineDp: Float): Float {
        val budget = stageHeightDp * MaxAnswersHeightFraction - BottomPaddingDp - ResolveReserveDp
        val fixed = 2 * TilePaddingVerticalDp + rows * EmojiGapDp + numeralLineDp
        return (budget - fixed) / rows
    }

    private fun choiceCapDp(stageWidthDp: Float, stageHeightDp: Float, rows: Int, numeralLineDp: Float): Float =
        minOf(
            choiceWidthCapDp(stageWidthDp),
            choiceHeightCapDp(stageHeightDp, rows, numeralLineDp),
            MaxChoiceEmojiDp,
        )

    /**
     * [rows] ist die Zeilenzahl der **höchsten** Kachel — alle drei werden mit
     * Geisterzeilen darauf aufgefüllt (§8: gleiche Dimensionen), also zahlt jede
     * Kachel die Höhe der größten Menge.
     */
    fun solveChoices(
        stageWidthDp: Float,
        stageHeightDp: Float,
        rows: Int,
        numeralLineDp: Float,
    ): ChoiceLayout {
        val pictorial = choiceCapDp(stageWidthDp, stageHeightDp, rows.coerceAtLeast(1), numeralLineDp)
        if (pictorial >= MinCountableEmojiDp) return ChoiceLayout(pictorial, symbolic = false)
        // Die symbolische Kachel ist einzeilig und rechnet deshalb neu: was für fünf
        // Emoji-Zeilen zu eng war, reicht für eine meistens bequem.
        val symbolic = choiceCapDp(stageWidthDp, stageHeightDp, rows = 1, numeralLineDp = numeralLineDp)
        return ChoiceLayout(symbolic.coerceAtLeast(MinSymbolicEmojiDp), symbolic = true)
    }

    /** Höhe, die der gelöste Antwortblock am Ende belegt — Auflösen-Knopf inbegriffen. */
    fun answersHeightDp(layout: ChoiceLayout, rows: Int, numeralLineDp: Float): Float {
        val stacked = if (layout.symbolic) 1 else rows.coerceAtLeast(1)
        val tile = 2 * TilePaddingVerticalDp + stacked * layout.emojiDp +
            stacked * EmojiGapDp + numeralLineDp
        return maxOf(tile, MinTileDp) + BottomPaddingDp + ResolveReserveDp
    }

    /**
     * Emoji-Kantenlänge des Aufgabenblocks: [baseDp] — der gerenderte Wert aus
     * [QuantityGrouping.promptEmojiSizeSp] —, gedeckelt durch die Breite der Bühne
     * und durch das, was der Antwortblock an Höhe übrig lässt. Auf breiten, hohen
     * Geräten bleibt es beim Tabellenwert.
     *
     * [rows] ist die Zeilenzahl der höheren der beiden Mengen, [cells] die Summe der
     * Spalten beider Mengen (je 2, bei einer Menge von 1 nur eine) — eine Eins neben
     * einer Vier zahlt nicht die Breite von zwei mal zwei. [operatorWidthDp] und
     * [numeralLineDp] sind gerendert gemessen.
     */
    fun solvePromptEmojiDp(
        baseDp: Float,
        stageWidthDp: Float,
        stageHeightDp: Float,
        rows: Int,
        cells: Int,
        answersHeightDp: Float,
        operatorWidthDp: Float,
        numeralLineDp: Float,
    ): Float {
        val stacked = rows.coerceAtLeast(1)
        val columns = cells.coerceAtLeast(1)
        val heightBudget = stageHeightDp - ChromeHeightDp - answersHeightDp -
            2 * PromptPaddingVerticalDp - stacked * EmojiGapDp - numeralLineDp
        val widthBudget = availableWidthDp(stageWidthDp) - 2 * PromptGapDp - operatorWidthDp -
            (columns - 2).coerceAtLeast(0) * EmojiGapDp - EmojiGapDp
        val cap = minOf(heightBudget / stacked, widthBudget / columns)
        return cap.coerceIn(MinSymbolicEmojiDp, baseDp)
    }
}
