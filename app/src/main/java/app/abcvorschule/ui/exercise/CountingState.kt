package app.abcvorschule.ui.exercise

/**
 * Tipp-Zustand der Zähl-Hilfe. Die Geste **ist** die Rechenart: Plus und
 * Malnehmen sammeln von null aufwärts ein, Minus nimmt von der Ausgangsmenge
 * abwärts weg. Genau darum führt das Kind die Rechnung aus, statt ein Ergebnis
 * abzuzählen, das die App schon hergestellt hat.
 *
 * Compose-frei; [CountingAid] rendert diesen Zustand nur.
 */
data class CountingState(
    val operation: MathOperation,
    /** Antippbare Objekte im Hauptfeld. */
    val objectCount: Int,
    /** Plätze der Weg-Zone; nur Minus hat welche, sonst 0. */
    val removeSlots: Int,
    val tapped: Set<Int> = emptySet(),
) {
    /**
     * Der Wert, der ins Antwortfeld gespiegelt wird — `null`, solange nichts
     * angetippt ist. Ohne dieses `null` stünde bei Plus sofort eine 0 im Feld und
     * bei Minus sofort der linke Operand, und das Kind könnte absenden, ohne
     * etwas getan zu haben.
     */
    val counted: Int?
        get() = when {
            tapped.isEmpty() -> null
            operation == MathOperation.Subtract -> objectCount - tapped.size
            else -> tapped.size
        }

    /** Alles eingesammelt bzw. die Weg-Zone voll. */
    val complete: Boolean
        get() = tapped.size == if (operation == MathOperation.Subtract) removeSlots else objectCount

    fun isTapped(index: Int): Boolean = index in tapped

    /**
     * Ein Tipp. Ein zweiter Tipp auf dasselbe Objekt nimmt ihn zurück — ein
     * Verzähler bleibt korrigierbar, und keine Fehltipp-Serie wird zur Sackgasse.
     * Am Deckel der Weg-Zone tut ein neuer Tipp nichts: die sechs Plätze sind die
     * ganze Information, die das Kind über „wie viele weg" braucht.
     */
    fun tap(index: Int): CountingState {
        if (index !in 0 until objectCount) return this
        if (isTapped(index)) return copy(tapped = tapped - index)
        if (complete) return this
        return copy(tapped = tapped + index)
    }

    companion object {
        fun forRound(operation: MathOperation, left: Int, right: Int): CountingState =
            CountingState(
                operation = operation,
                objectCount = CountingField.objectCount(operation, left, right),
                removeSlots = CountingField.removeSlots(operation, right),
            )
    }
}
