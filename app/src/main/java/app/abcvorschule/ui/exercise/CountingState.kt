package app.abcvorschule.ui.exercise

/**
 * Tipp-Zustand der Zähl-Hilfe. Die Geste **ist** die Rechenart: Plus sammelt von
 * null aufwärts ein, Minus nimmt von der Ausgangsmenge abwärts weg, Malnehmen
 * zählt in Reihenschritten. Genau darum führt das Kind die Rechnung aus, statt
 * ein Ergebnis abzuzählen, das die App schon hergestellt hat.
 *
 * Compose-frei; [CountingAid] rendert diesen Zustand nur.
 */
data class CountingState(
    val operation: MathOperation,
    /** Antippbare Einheiten: Objekte, bei Malnehmen ganze Reihen. */
    val unitCount: Int,
    /** Wie viel eine Einheit zählt: 1, bei Malnehmen die Spaltenzahl. */
    val stepSize: Int,
    /** Ab hier beginnt der zweite Operand: die gerahmten Objekte. `null` bei
     * Malnehmen, dessen Matrix ihre Struktur selbst trägt. */
    val framedFrom: Int?,
    val tapped: Set<Int> = emptySet(),
) {
    /** Gehört diese Einheit zum zweiten Operanden? */
    fun isFramed(index: Int): Boolean = framedFrom != null && index >= framedFrom

    /**
     * Bei Minus sind **nur** die gerahmten Objekte antippbar — sie sind die, die
     * weggehen. Damit kann sich das Kind nicht verzählen, wie viele es schon
     * weggenommen hat: es kann gar nicht zu viele wegnehmen. Bei Plus und
     * Malnehmen wird alles eingesammelt, der Rahmen zeigt dort nur, wo die zweite
     * Zahl anfängt.
     */
    fun isTappable(index: Int): Boolean {
        if (index !in 0 until unitCount) return false
        return operation != MathOperation.Subtract || isFramed(index)
    }

    /**
     * Die nächste offene Einheit — der Puls-Hinweis läuft darauf mit.
     *
     * Minus läuft **von hinten**: weggenommen wird vom Ende der Menge, und der
     * Zähler zählt rückwärts. Ein Puls, der vorne anfinge, liefe der Zahl entgegen.
     */
    val nextIndex: Int?
        get() {
            val open = (0 until unitCount).filter { isTappable(it) && it !in tapped }
            return if (operation == MathOperation.Subtract) open.lastOrNull() else open.firstOrNull()
        }

    /**
     * Der Wert, der ins Antwortfeld gespiegelt wird — `null`, solange nichts
     * angetippt ist. Ohne dieses `null` stünde bei Plus sofort eine 0 im Feld und
     * bei Minus sofort der linke Operand, und das Kind könnte absenden, ohne
     * etwas getan zu haben.
     */
    val counted: Int?
        get() = when {
            tapped.isEmpty() -> null
            operation == MathOperation.Subtract -> unitCount - tapped.size
            else -> tapped.size * stepSize
        }

    /** Alles eingesammelt bzw. alles Gerahmte weggenommen. */
    val complete: Boolean
        get() = nextIndex == null

    fun isTapped(index: Int): Boolean = index in tapped

    /**
     * Ein Tipp. Ein zweiter Tipp auf dieselbe Einheit nimmt ihn zurück — ein
     * Verzähler bleibt korrigierbar, und keine Fehltipp-Serie wird zur Sackgasse.
     * Ein Tipp auf eine nicht antippbare Einheit tut nichts: bei Minus sind die
     * bleibenden Objekte kein Fehler, sie sind schlicht nicht Teil der Aufgabe.
     */
    fun tap(index: Int): CountingState {
        if (isTapped(index)) return copy(tapped = tapped - index)
        if (!isTappable(index)) return this
        return copy(tapped = tapped + index)
    }

    companion object {
        fun forRound(operation: MathOperation, left: Int, right: Int): CountingState =
            CountingState(
                operation = operation,
                unitCount = CountingField.unitCount(operation, left, right),
                stepSize = CountingField.stepSize(operation, right),
                framedFrom = CountingField.framedFrom(operation, left, right),
            )
    }
}
