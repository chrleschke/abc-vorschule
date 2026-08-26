package app.abcvorschule.ui.exercise

/**
 * Layout-Regeln der Zähl-Hilfe — der antippbaren Menge, die nach zwei
 * Fehlversuchen an die Stelle der Aufgabenvisualisierung tritt (design doc
 * 2026-08-26-rechnen-ohne-raten).
 *
 * Drei Dinge unterscheiden sie vom Aufgaben-Prompt:
 *
 * 1. Mengen ab 11 werden hier **ausgeschrieben** statt als Symbol + Ziffer
 *    ([QuantityRepresentation]). §8 verbietet die Emoji-Wand als *Aufgabe*;
 *    hier ist die Menge Werkzeug, und ohne Objekte gäbe es nichts anzutippen.
 * 2. Gebündelt wird in **Fünfern**, nicht in Paaren wie [QuantityGrouping].
 *    Die Fünferbündelung ist die Struktur, die das Kind für den Zahlenraum
 *    20/30 ohnehin braucht.
 * 3. Beide Operanden liegen in **einem** Feld, statt als zwei Blöcke unter- oder
 *    nebeneinander. Der zweite Operand ist stattdessen gerahmt. Zwei getrennte
 *    Blöcke kosteten bis zu sieben Zeilen und drückten das Emoji auf 20sp; im
 *    gemeinsamen Feld sind es höchstens sechs und mindestens 24sp. Der Rahmen
 *    trägt die Gruppierung dabei genauso gut wie ein Zeilenumbruch.
 *
 * Compose-frei, damit die Rechnungen als JVM-Test prüfbar bleiben.
 */
object CountingField {
    /** Objekte pro Zeile. */
    const val RowSize = 5

    /** Abstand zwischen zwei Zellen, in dp. */
    const val RowGapDp = 4f

    /** Innenabstand einer Zelle, in dp. Jede Zelle trägt ihn, nicht nur die
     * gerahmten — sonst säßen gerahmte und ungerahmte Objekte auf verschiedenen
     * Rastern und die Fünferzeile verliefe krumm. */
    const val CellPadDp = 3f

    /** Schriftskalierung, gegen die ausgelegt wird: das Testgerät steht auf 1.3,
     * und gegen 1.0 gerechnete Größen laufen dort über. */
    const val LayoutFontScale = 1.3f

    /** Volle Größe, wenn Platz ist. */
    const val MaxEmojiSp = 34

    /** Untergrenze: kleiner wird ein Emoji weder erkennbar noch sicher treffbar.
     * Der höchste Fall (sechs Zeilen) landet bei 24sp, bleibt also darüber. */
    const val MinEmojiSp = 20

    /**
     * Reserve auf die Höhenschranke: die Rechnung zählt einen Zeilenabstand zu
     * viel (unter der letzten Zeile sitzt keiner), und ohne Reserve landet ein
     * Grenzfall auf exakt [TaskBlockDp] — wo Float-Rundung die Schranke kippt.
     */
    const val SafetyDp = 2f

    /**
     * Zeilenhöhe der Ziffernzeile über dem Feld („15 − 6 = ?").
     * `headlineMedium.lineHeight` aus `ui/theme/Theme.kt` — dokumentierte Kopie
     * nach dem Muster von [MultiplicationMatrix.RowLabelSp].
     */
    const val LabelLineSp = 34f

    /**
     * Höhe und Breite, die der Aufgabenblock der Zähl-Hilfe zugesteht. Bewusst
     * konservativ für ein schmales Telefon in Hochkant — sie deckeln die
     * Emoji-Größe, statt eine echte Messung zu ersetzen.
     */
    const val TaskBlockDp = 300f
    const val FieldWidthDp = 320f

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

    /** Wie viele Objekte insgesamt antippbar auf dem Schirm stehen. */
    fun objectCount(operation: MathOperation, left: Int, right: Int): Int =
        when (operation) {
            MathOperation.Add -> left + right
            MathOperation.Subtract -> left
            MathOperation.Multiply -> left * right
        }

    /**
     * Ab welchem Index der zweite Operand beginnt — die gerahmten Objekte.
     * `null` bei Malnehmen: die Matrix trägt ihre Struktur bereits in Reihen und
     * Spalten, ein Rahmen darin wäre eine zweite, widersprüchliche Gruppierung.
     *
     * Der Rahmen bedeutet überall dasselbe: *das ist die zweite Zahl*. Was mit
     * ihr passiert, entscheidet die Rechenart — bei Minus geht sie weg (und nur
     * sie ist antippbar), bei Plus kommt sie dazu.
     */
    fun framedFrom(operation: MathOperation, left: Int, right: Int): Int? =
        if (operation == MathOperation.Multiply) null else objectCount(operation, left, right) - right

    /** Gerenderte Zeilen. Malnehmen behält sein Raster, sonst Fünferzeilen. */
    fun totalRows(operation: MathOperation, left: Int, right: Int): Int =
        if (operation == MathOperation.Multiply) left else rows(objectCount(operation, left, right)).size

    /** Kantenlänge einer Zelle in dp bei gegebener Emoji-Größe. */
    fun cellSizeDp(emojiSizeSp: Int): Float = emojiSizeSp * LayoutFontScale + 2 * CellPadDp

    /** Breite einer vollen Fünferzeile in dp. */
    fun rowWidthDp(emojiSizeSp: Int): Float =
        RowSize * cellSizeDp(emojiSizeSp) + RowGapDp * (RowSize - 1)

    /** Höhe des Feldes in dp, inklusive der Ziffernzeile darüber. */
    fun fieldHeightDp(rows: Int, emojiSizeSp: Int): Float =
        rows * (cellSizeDp(emojiSizeSp) + RowGapDp) + LabelLineSp * LayoutFontScale

    /**
     * Emoji-Größe in sp. Malnehmen erbt die Größe der Matrix, die es ohnehin
     * wiederverwendet; sonst wird die Größe aus dem verfügbaren Platz
     * *hergeleitet* statt gestuft. Eine Stufentabelle deckt den echten Content
     * nicht ab — „30 − 17" steht so im Pack. Hergeleitet gelten beide Schranken
     * per Konstruktion, für jede Runde, die der Validator zulässt.
     */
    fun emojiSizeSp(operation: MathOperation, left: Int, right: Int): Int {
        if (operation == MathOperation.Multiply) return MultiplicationMatrix.emojiSizeSp(right)
        val rows = totalRows(operation, left, right)
        if (rows <= 0) return MaxEmojiSp
        val byWidth = ((FieldWidthDp - RowGapDp * (RowSize - 1)) / RowSize - 2 * CellPadDp) / LayoutFontScale
        val available = TaskBlockDp - SafetyDp - LabelLineSp * LayoutFontScale
        val byHeight = (available / rows - RowGapDp - 2 * CellPadDp) / LayoutFontScale
        return minOf(byWidth, byHeight).toInt().coerceIn(MinEmojiSp, MaxEmojiSp)
    }
}
