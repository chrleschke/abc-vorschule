package app.abcvorschule.ui.exercise

/**
 * Maße des Laut-Fressers, Compose-frei (design doc §8). Alles, was mit der
 * Systemschriftgröße wächst, nimmt `fontScale` als Parameter — das Testgerät steht
 * auf 1.3, und ein Bauch-Glyph, der bei 1.0 passt, darf bei 1.3 nicht aus der
 * Figur laufen.
 */
object SoundFeederSizing {
    /**
     * Obergrenze der Bühne: `ExerciseStage` deckelt auf 420dp und polstert 12dp je
     * Seite. Die 20dp der `TaskShell` je Seite greifen auf breiten Geräten nicht mehr
     * — dort schlägt der 420dp-Deckel zuerst zu.
     */
    const val StageContentDp = 396f
    /**
     * Untergrenze der Bühne, und die Zahl, gegen die die Figuren gerechnet werden:
     * 320 − 2 × 20 (`AbcDimens.screenHorizontal` in `TaskShell`) − 2 × 12
     * (`ExerciseStage`) = **256dp**. Vorher stand hier 296 — die 20dp der Shell waren
     * schlicht vergessen, und der Bauch-Glyph wurde gegen eine Bühne geprüft, die es
     * auf einem 320dp-Gerät nie gibt.
     */
    const val NarrowestStageDp = 256f
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
     * 12, nicht 14 oder 16: auf der schmalsten Bühne ([NarrowestStageDp]) ist eine
     * Figur 120dp breit, der Bauch trägt also rund 94dp — „Sch / sch" bei font_scale
     * 1.3 passt dort nur bis knapp 13sp. Ein Glyph, der aus der Figur läuft, ist der
     * schlimmere Fehler als ein kleiner — der Bauch-Glyph ist Aufgabe, kein Fließtext,
     * und die Satz-Architekt-Regel „kein Glyph-Floor über die Erreichbarkeit" (§9)
     * gilt hier sinngemäß.
     *
     * In der App **greift** dieser Boden nie: Konsonantenpaare zeigen nur die Großform
     * („Sch", 3 Zeichen), das längste tatsächlich vorkommende Label ist „Ei / ei"
     * (7 Zeichen) und kommt bei 1.3 noch auf ~16sp. „Sch / sch" bleibt als Stresstest
     * im [SoundFeederSizingTest] stehen, damit der Boden nachweislich hält, falls je
     * wieder ein Paar beide Formen zeigt.
     */
    const val MinBellyGlyphSp = 12f
    /** 1,5 × `AbcDimens.kidTouch` (80dp) — eine Karte, die ein Kind sicher greift. */
    const val MinCardDp = 120f
    const val CardEmojiFactor = 1.5f
    /** `titleLarge` aus `ui/theme/Theme.kt` — der Stil des Kartenworts ohne TTS. */
    const val CardWordSp = 22f
    /** Der Stil gibt keine `lineHeight` vor; die Schrift belegt rund 1,35 × Schriftgröße. */
    const val CardWordLineEm = 1.35f
    /** Luft zwischen Emoji und Wort plus Rahmen-Innenabstand. */
    const val CardWordPadDp = 8f
    /** Der Futterhaufen: ein Stapel halbgroßer Kartenrückseiten, keine Reihe. */
    const val PileCardWidthDp = 48f
    const val PileCardHeightDp = 60f
    /** Wie weit eine Karte im Stapel höchstens verrutscht (je Achse). */
    const val PileJitterDp = 4f
    /** Wie weit eine Karte im Stapel höchstens verdreht liegt. */
    const val PileRotationDeg = 8f

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

    /**
     * Der Stapel ist so breit wie **eine** Karte plus den Versatz nach beiden Seiten —
     * egal wie viele noch liegen. Ein Haufen, der mit jeder gefressenen Karte
     * schrumpft, schöbe die Bildkarte daneben bei jedem Zug ein Stück zur Seite (§9).
     */
    fun pileWidthDp(count: Int): Float =
        if (count <= 0) 0f else PileCardWidthDp + 2 * PileJitterDp

    /**
     * Versatz und Drehung der [index]-ten Karte im Stapel: (dx, dy in dp, Rotation in
     * Grad). Aus [index] **und** [seed] gesät: innerhalb eines Spiels liegt der Haufen
     * still (derselbe Seed über alle Neukompositionen, sonst zappelte er bei jedem
     * Frame), aber jedes neue Spiel würfelt einen neuen Seed und der Stapel liegt
     * anders da. Nur der *Anblick* ist zufällig — Kartenwahl und -reihenfolge bleiben
     * deterministisch aus der Lektion (design doc §4).
     */
    fun pileOffset(index: Int, seed: Int): Triple<Float, Float, Float> {
        val rng = kotlin.random.Random(seed * 31 + index * 7919 + 17)
        val dx = rng.nextFloat() * 2 * PileJitterDp - PileJitterDp
        val dy = rng.nextFloat() * 2 * PileJitterDp - PileJitterDp
        val rot = rng.nextFloat() * 2 * PileRotationDeg - PileRotationDeg
        return Triple(dx, dy, rot)
    }
}
