package app.abcvorschule.ui.exercise

/**
 * Layout-Regeln der Zähl-Hilfe — der antippbaren Menge, die nach zwei
 * Fehlversuchen an die Stelle der Aufgabenvisualisierung tritt (design doc
 * 2026-08-26-rechnen-ohne-raten).
 *
 * Zwei Dinge unterscheiden sie vom Aufgaben-Prompt:
 *
 * 1. Mengen ab 11 werden hier **ausgeschrieben** statt als Symbol + Ziffer
 *    ([QuantityRepresentation]). §8 verbietet die Emoji-Wand als *Aufgabe*;
 *    hier ist die Menge Werkzeug, und ohne Objekte gäbe es nichts anzutippen.
 * 2. Gebündelt wird in **Fünfern**, nicht in Paaren wie [QuantityGrouping].
 *    Die Fünferbündelung ist die Struktur, die das Kind für den Zahlenraum
 *    20/30 ohnehin braucht.
 *
 * Compose-frei, damit die Rechnungen als JVM-Test prüfbar bleiben.
 */
object CountingField {
    /** Objekte pro Zeile. */
    const val RowSize = 5

    /** Vertikaler Abstand zwischen zwei Zeilen, in dp — muss zu [CountingAid] passen. */
    const val RowGapDp = 4f

    /** Schriftskalierung, gegen die ausgelegt wird: das Testgerät steht auf 1.3,
     * und gegen 1.0 gerechnete Größen laufen dort über. */
    const val LayoutFontScale = 1.3f

    /** Volle Größe, wenn Platz ist. */
    const val MaxEmojiSp = 34

    /** Untergrenze: kleiner wird ein Emoji weder erkennbar noch sicher treffbar.
     * Der entartetste Fall ("26 − 26", zwölf Zeilen) landet bei 16sp, bleibt also
     * darüber. */
    const val MinEmojiSp = 14

    /**
     * Reserve auf die Höhenschranke. Zwei Gründe: die Rechnung zählt einen
     * Zeilenabstand zu viel (unter der letzten Zeile sitzt keiner), und ohne
     * Reserve landet der höchste Fall auf exakt [TaskBlockDp] — wo
     * Float-Rundung („20 × 1.3f") die Schranke kippen lässt.
     */
    const val SafetyDp = 2f

    /**
     * Höhe, die der Aufgabenblock der Zähl-Hilfe zugesteht. Grobe, bewusst
     * konservative Schranke für ein Telefon in Hochkant — sie deckelt die
     * Emoji-Größe, statt eine echte Messung zu ersetzen.
     */
    const val TaskBlockDp = 300f

    /** Fünferzeilen einer Menge; die letzte Zeile ist kürzer. */
    fun rows(count: Int): List<Int> {
        if (count <= 0) return emptyList()
        val full = count / RowSize
        val rest = count % RowSize
        return buildList {
            repeat(full) { add(RowSize) }
            if (rest > 0) add(rest)
        }
    }

    /**
     * Die Objektgruppen in Anzeigereihenfolge.
     *
     * Plus behält seine zwei Gruppen — der Zähler läuft über beide durch, und die
     * Aufgabe bleibt als Bild erkennbar. Minus zeigt nur die Ausgangsmenge; die
     * weggenommenen Objekte wandern in die Weg-Zone, statt Teil dieses Feldes zu
     * sein. Malnehmen liefert alle Matrixzellen als eine Gruppe — gerendert wird
     * es ohnehin als Raster, nicht in Fünferzeilen.
     */
    fun groupSizes(operation: MathOperation, left: Int, right: Int): List<Int> =
        when (operation) {
            MathOperation.Add -> listOf(left, right)
            MathOperation.Subtract -> listOf(left)
            MathOperation.Multiply -> listOf(left * right)
        }

    /** Wie viele Objekte insgesamt antippbar auf dem Schirm stehen. */
    fun objectCount(operation: MathOperation, left: Int, right: Int): Int =
        groupSizes(operation, left, right).sum()

    /**
     * Leere Plätze der Weg-Zone. Nur Minus hat eine: sie ist der Grund, warum das
     * Kind nicht mitzählen muss, wie viele es schon weggenommen hat — die Struktur
     * trägt die Zahl, und volle Zone heißt fertig.
     */
    fun removeSlots(operation: MathOperation, right: Int): Int =
        if (operation == MathOperation.Subtract) right else 0

    /**
     * Gerenderte Zeilen insgesamt. Bei Minus zählt die Weg-Zone mit — sie steht
     * unter dem Hauptfeld, damit alles fünf Spalten breit bleibt und nicht neben
     * dem Feld in die Breite läuft.
     */
    fun totalRows(operation: MathOperation, left: Int, right: Int): Int =
        when (operation) {
            MathOperation.Multiply -> left
            MathOperation.Add -> rows(left).size + rows(right).size
            MathOperation.Subtract -> rows(left).size + rows(right).size
        }

    /**
     * Emoji-Größe in sp. Malnehmen erbt die Größe der Matrix, die es ohnehin
     * wiederverwendet; sonst wird die Größe aus der verfügbaren Höhe *hergeleitet*
     * statt gestuft. Eine Stufentabelle deckt den entartetsten Fall nicht ab —
     * "26 − 26" ergibt zwölf Zeilen und entsteht, sobald ein fortgeschrittenes
     * Scaffold die Zahlen-Eingabe auch bei kleinem Ergebnis anschaltet. Hergeleitet
     * gilt die Höhenschranke per Konstruktion, für jede Runde, die der Validator
     * zulässt.
     *
     * Eine Zeile belegt `sizeSp × LayoutFontScale + RowGapDp`; bei `rows` Zeilen
     * bleiben also `(TaskBlockDp − SafetyDp) / rows` je Zeile.
     */
    fun emojiSizeSp(operation: MathOperation, left: Int, right: Int): Int {
        if (operation == MathOperation.Multiply) return MultiplicationMatrix.emojiSizeSp(right)
        val rows = totalRows(operation, left, right)
        if (rows <= 0) return MaxEmojiSp
        val perRowDp = (TaskBlockDp - SafetyDp) / rows
        val fitting = ((perRowDp - RowGapDp) / LayoutFontScale).toInt()
        return fitting.coerceIn(MinEmojiSp, MaxEmojiSp)
    }
}
