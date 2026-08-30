package app.abcvorschule.ui.exercise

/**
 * Tipp-Zustand des Zehnerfeldes. Die erste Zahl liegt als echte Bilder da, die
 * zweite als Platzhalter — und das Kind macht sie einen nach dem anderen echt.
 * Die Geste **ist** die Addition: dazulegen, bis die Reihe voll ist.
 *
 * Zwei Regeln, die [CountingState] bewusst anders hat:
 *
 * - **Antippbar ist nur [nextIndex].** So kann sich das Kind nicht verzählen und
 *   die Reihenfolge ist die des Zählens.
 * - **Kein Widerruf.** Ein zweiter Tipp nimmt nichts zurück. Das ist nur
 *   zusammen mit der Regel darüber vertretbar: wo kein Fehltipp möglich ist,
 *   braucht es keinen Rückweg. Widerruf zu sperren, während man überall
 *   hintippen kann, wäre eine Falle.
 *
 * Compose-frei; [TenFrameBoard] rendert diesen Zustand nur.
 */
data class TenFrameState(
    /** Erster Summand — steht von Anfang an als echte Bilder da. */
    val given: Int,
    /** Zweiter Summand — liegt als Platzhalter bereit. */
    val added: Int,
    /** Wie viele Platzhalter das Kind schon gesetzt hat. */
    val filled: Int = 0,
) {
    /** Alle Plätze im Feld, echte wie leere. */
    val total: Int get() = given + added

    /** Objekte, die gerade als echt gezeichnet werden. */
    val realCount: Int get() = given + filled

    /** Der nächste offene Platzhalter — er pulsiert und ist der einzige, der auf
     * einen Tipp reagiert. `null`, wenn das Feld voll ist. */
    val nextIndex: Int? get() = if (filled < added) given + filled else null

    val complete: Boolean get() = filled == added

    /** Der Stand in vollen Zehnern — nur ganze Zeilen zählen. Genau diese Zahl
     * steht als Marke am Rahmen und wird beim Einrasten gesprochen. */
    val fullTens: Int get() = TenFrame.fullRowCount(realCount) * TenFrame.RowSize

    fun isReal(index: Int): Boolean = index in 0 until realCount

    fun isTappable(index: Int): Boolean = nextIndex != null && index == nextIndex

    /** Ein Tipp. Alles außer [nextIndex] tut nichts — auch ein zweiter Tipp auf
     * ein schon gesetztes Bild. */
    fun tap(index: Int): TenFrameState =
        if (isTappable(index)) copy(filled = filled + 1) else this

    /**
     * Der Zehner, den der Übergang zu [next] voll gemacht hat — sonst `null`.
     * Der einzige Moment, in dem die Stimme etwas sagt.
     */
    fun completedTenAfter(next: TenFrameState): Int? =
        if (next.fullTens > fullTens) next.fullTens else null

    companion object {
        fun forRound(left: Int, right: Int): TenFrameState =
            TenFrameState(given = left, added = right)
    }
}
