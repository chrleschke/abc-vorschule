package app.abcvorschule.ui.exercise

/**
 * Maße des Laut-Fressers, Compose-frei (design doc §8). Alles, was mit der
 * Systemschriftgröße wächst, nimmt `fontScale` als Parameter — das Testgerät steht
 * auf 1.3, und ein Bauch-Glyph, der bei 1.0 passt, darf bei 1.3 nicht aus der
 * Figur laufen.
 */
object SoundFeederSizing {
    /** ExerciseStage deckelt auf 420dp und polstert 12dp je Seite. */
    const val StageContentDp = 396f
    const val CreatureGapDp = 16f
    const val MinCreatureDp = 120f
    const val MaxCreatureDp = 176f
    /** Höhe = Breite × Aspekt: ein Fresser ist etwas höher als breit. */
    const val CreatureAspect = 1.1f
    /** Anteil der Figurbreite, den der Bauchfleck einnimmt. */
    const val BellyWidthFraction = 0.78f
    /** Vorschub eines Buchstabens als Vielfaches der Schriftgröße (Näherung wie
     * `SentencePictureCardSizing.EmojiAdvanceEm`). */
    const val GlyphAdvanceEm = 0.62f
    const val MaxBellyGlyphSp = 36f
    /**
     * 14, nicht 16: auf einer 296dp-Bühne (320dp-Gerät) trägt der Bauch bei font_scale
     * 1.3 „Sch / sch" nur bei rund 15sp. Ein Glyph, der aus der Figur läuft, ist der
     * schlimmere Fehler als ein kleiner — der Bauch-Glyph ist Aufgabe, kein Fließtext,
     * und die Satz-Architekt-Regel „kein Glyph-Floor über die Erreichbarkeit" (§9)
     * gilt hier sinngemäß.
     */
    const val MinBellyGlyphSp = 14f
    /** 1,5 × `AbcDimens.kidTouch` (80dp) — eine Karte, die ein Kind sicher greift. */
    const val MinCardDp = 120f
    const val CardEmojiFactor = 1.5f
    /** `titleLarge` aus `ui/theme/Theme.kt` — der Stil des Kartenworts ohne TTS. */
    const val CardWordSp = 22f
    /** Der Stil gibt keine `lineHeight` vor; die Schrift belegt rund 1,35 × Schriftgröße. */
    const val CardWordLineEm = 1.35f
    /** Luft zwischen Emoji und Wort plus Rahmen-Innenabstand. */
    const val CardWordPadDp = 8f
    const val PileCardWidthDp = 22f
    const val PileCardHeightDp = 30f
    const val PileStepDp = 4f

    fun creatureWidthDp(stageWidthDp: Float): Float =
        ((stageWidthDp.coerceAtMost(StageContentDp) - CreatureGapDp) / 2f)
            .coerceIn(MinCreatureDp, MaxCreatureDp)

    fun creatureHeightDp(widthDp: Float): Float = widthDp * CreatureAspect

    /** "S / s" → 5, "Sch / sch" → 9, "ck" → 2. */
    fun labelChars(primary: String, alternate: String?): Int =
        primary.length + (alternate?.let { it.length + 3 } ?: 0)

    /** So groß wie möglich, aber der Bauch bleibt die Grenze — in dp, also mit fontScale. */
    fun bellyGlyphSp(labelChars: Int, creatureWidthDp: Float, fontScale: Float): Float {
        val budget = creatureWidthDp * BellyWidthFraction
        val fits = budget / (labelChars.coerceAtLeast(1) * GlyphAdvanceEm * fontScale)
        return fits.coerceIn(MinBellyGlyphSp, MaxBellyGlyphSp)
    }

    /** Karte um das gedeckelte Aufgabenbild herum (`TaskPromptSizing.pictureSp`). */
    fun cardSizeDp(fontScale: Float): Float =
        (TaskPromptSizing.pictureSp(fontScale) * fontScale * CardEmojiFactor).coerceAtLeast(MinCardDp)

    /**
     * Die Zeile unter dem Emoji, wenn keine deutsche Stimme da ist. Ungedeckelt: das
     * Wort ist das Einzige, was ein Erwachsener dann vorlesen kann (PRODUCT_PRINCIPLES
     * §7), es muss also mit der Systemschrift mitwachsen dürfen.
     */
    fun cardWordLineDp(fontScale: Float): Float = CardWordSp * fontScale * CardWordLineEm + CardWordPadDp

    /**
     * Höhe der Bildkarte: quadratisch, solange nur das Emoji darin steht — und um
     * genau eine Wortzeile höher, wenn das Wort darunter muss. [cardSizeDp] budgetiert
     * nur das Emoji; ohne den Zuschlag würde das Wort aus der Karte gedrückt.
     */
    fun cardHeightDp(fontScale: Float, hasWord: Boolean): Float =
        cardSizeDp(fontScale) + if (hasWord) cardWordLineDp(fontScale) else 0f

    fun pileWidthDp(count: Int): Float =
        if (count <= 0) 0f else PileCardWidthDp + (count - 1) * PileStepDp
}
