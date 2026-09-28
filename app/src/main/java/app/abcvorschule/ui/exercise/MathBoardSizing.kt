package app.abcvorschule.ui.exercise

/**
 * Maße der Antwortreihe im Rechen-Trainer (§8), in gerenderten dp. Die drei Kacheln
 * stehen in **einer** Reihe und behalten ihre volle Trefferfläche; wird es eng, gibt der
 * Abstand nach, nicht die Kachel. Seit den Zahlentürmen zeigen die Kacheln nur noch die
 * Zahl, die Emoji-Rechnung von früher entfällt.
 */
object MathBoardSizing {
    /** Bequemer Abstand zwischen zwei Kacheln. */
    const val TileGapDp = 12f

    /** Enger Abstand, wenn drei volle Kacheln sonst nicht in die Reihe passen. */
    const val MinTileGapDp = 4f

    /** `AbcDimens.kidTouch`: kleinste Kachel. */
    const val MinTileDp = 80f

    /** `ExerciseStage` deckelt auf 420dp und polstert 12dp je Seite. */
    const val MaxStageWidthDp = 420f
    const val SideGutterDp = 12f

    /** Breite, die einem Block auf einer [stageWidthDp] breiten Bühne bleibt. */
    fun availableWidthDp(stageWidthDp: Float): Float =
        minOf(stageWidthDp, MaxStageWidthDp) - 2 * SideGutterDp

    /**
     * Abstand zwischen den Kacheln: [TileGapDp], solange drei Kacheln mit voller
     * Trefferfläche damit in die Reihe passen, sonst [MinTileGapDp].
     */
    fun tileGapDp(stageWidthDp: Float): Float =
        if (3 * MinTileDp + 2 * TileGapDp <= availableWidthDp(stageWidthDp)) TileGapDp else MinTileGapDp
}
