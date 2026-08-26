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
    /** Objekte pro Zeile bei kleinen Mengen. */
    const val RowSize = 5

    /**
     * Ab dieser Gruppengröße läuft die Zeile auf zehn Objekte — gerendert als
     * zwei Fünfer mit breiterer Lücke, wie an einem Rechenrahmen. Fünferzeilen
     * überall wären konsequenter, türmen den echten Content aber unbrauchbar hoch:
     * "30 − 17" wären zehn Zeilen und das Emoji müsste auf 14sp schrumpfen. Die
     * Zehnerbündelung ist für den Zahlenraum 20/30 ohnehin die Struktur, die das
     * Kind braucht.
     */
    const val WideRowFrom = 11
    const val WideRowSize = 10

    /** Abstand zwischen zwei Objekten einer Fünfergruppe, in dp. */
    const val RowGapDp = 4f

    /** Breitere Lücke zwischen den beiden Fünfern einer Zehnerzeile, in dp — sie
     * erhält das Subitizing, das die lange Zeile sonst zerstört. */
    const val FiveGapDp = 12f

    /** Schriftskalierung, gegen die ausgelegt wird: das Testgerät steht auf 1.3,
     * und gegen 1.0 gerechnete Größen laufen dort über. */
    const val LayoutFontScale = 1.3f

    /** Volle Größe, wenn Platz ist. */
    const val MaxEmojiSp = 34

    /** Untergrenze: kleiner wird ein Emoji weder erkennbar noch sicher treffbar.
     * Der höchste Fall ("30 − 30", sechs Zehnerzeilen) landet bei 21sp. */
    const val MinEmojiSp = 18

    /**
     * Reserve auf die Höhenschranke: die Rechnung zählt einen Zeilenabstand zu
     * viel (unter der letzten Zeile sitzt keiner), und ohne Reserve landet ein
     * Grenzfall auf exakt [TaskBlockDp] — wo Float-Rundung die Schranke kippt.
     */
    const val SafetyDp = 2f

    /**
     * Zeilenhöhe des einen Beschriftungstexts im Feld: das Operatorzeichen bei
     * Plus, die Ziffer unter der Weg-Zone bei Minus. `headlineMedium.lineHeight`
     * aus `ui/theme/Theme.kt` — dokumentierte Kopie nach dem Muster von
     * [MultiplicationMatrix.RowLabelSp].
     */
    const val LabelLineSp = 34f

    /**
     * Höhe und Breite, die der Aufgabenblock der Zähl-Hilfe zugesteht. Bewusst
     * konservativ für ein schmales Telefon in Hochkant — sie deckeln die
     * Emoji-Größe, statt eine echte Messung zu ersetzen.
     */
    const val TaskBlockDp = 300f
    const val FieldWidthDp = 320f

    /**
     * Zeilenbreite der Runde. Rundenweit entschieden, nicht je Gruppe — sonst
     * stünde bei "15 + 4" eine Zehnerzeile über einer Fünferzeile und die beiden
     * Gruppen wären unvergleichbar. Gleiches Muster wie
     * [QuantityRepresentation.forceSymbolicFor].
     */
    fun rowSize(operation: MathOperation, left: Int, right: Int): Int =
        if (groupSizes(operation, left, right).max() >= WideRowFrom) WideRowSize else RowSize

    /** Zeilen einer Menge zu je [rowSize]; die letzte Zeile ist kürzer. */
    fun rows(count: Int, rowSize: Int): List<Int> {
        if (count <= 0) return emptyList()
        val full = count / rowSize
        val rest = count % rowSize
        return buildList {
            repeat(full) { add(rowSize) }
            if (rest > 0) add(rest)
        }
    }

    /** Eine Zeile in ihre Fünferblöcke — das Subitizing der langen Zeile. */
    fun fiveChunks(rowLength: Int): List<Int> = rows(rowLength, RowSize)

    /**
     * Die Objektgruppen in Anzeigereihenfolge.
     *
     * Plus behält seine zwei Gruppen — der Zähler läuft über beide durch, und die
     * Aufgabe bleibt als Bild erkennbar. Minus zeigt nur die Ausgangsmenge; die
     * weggenommenen Objekte wandern in die Weg-Zone, statt Teil dieses Feldes zu
     * sein. Malnehmen liefert alle Matrixzellen als eine Gruppe — gerendert wird
     * es ohnehin als Raster, nicht in Zeilen dieser Breite.
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
     * unter dem Hauptfeld, damit alles gleich breit bleibt statt daneben in die
     * Breite zu laufen.
     */
    fun totalRows(operation: MathOperation, left: Int, right: Int): Int {
        if (operation == MathOperation.Multiply) return left
        val rowSize = rowSize(operation, left, right)
        return rows(left, rowSize).size + rows(right, rowSize).size
    }

    /** Breite einer vollen Zeile in dp bei gegebener Emoji-Größe. */
    fun rowWidthDp(rowSize: Int, emojiSizeSp: Int): Float {
        val gaps = RowGapDp * (rowSize - 1) +
            if (rowSize > RowSize) FiveGapDp - RowGapDp else 0f
        return rowSize * emojiSizeSp * LayoutFontScale + gaps
    }

    /** Höhe des Feldes in dp bei gegebener Emoji-Größe, inklusive Beschriftung. */
    fun fieldHeightDp(rows: Int, emojiSizeSp: Int): Float =
        rows * (emojiSizeSp * LayoutFontScale + RowGapDp) + LabelLineSp * LayoutFontScale

    /**
     * Emoji-Größe in sp. Malnehmen erbt die Größe der Matrix, die es ohnehin
     * wiederverwendet; sonst wird die Größe aus dem verfügbaren Platz
     * *hergeleitet* statt gestuft. Eine Stufentabelle deckt den echten Content
     * nicht ab — "30 − 17" steht so im Pack. Hergeleitet gelten beide Schranken
     * per Konstruktion, für jede Runde, die der Validator zulässt.
     *
     * Beide Richtungen binden: die Zehnerzeile ist breitengetrieben, der hohe
     * Minus-Fall höhengetrieben.
     */
    fun emojiSizeSp(operation: MathOperation, left: Int, right: Int): Int {
        if (operation == MathOperation.Multiply) return MultiplicationMatrix.emojiSizeSp(right)
        val rows = totalRows(operation, left, right)
        if (rows <= 0) return MaxEmojiSp
        val rowSize = rowSize(operation, left, right)
        val gaps = RowGapDp * (rowSize - 1) +
            if (rowSize > RowSize) FiveGapDp - RowGapDp else 0f
        val byWidth = (FieldWidthDp - gaps) / rowSize / LayoutFontScale
        val available = TaskBlockDp - SafetyDp - LabelLineSp * LayoutFontScale
        val byHeight = (available / rows - RowGapDp) / LayoutFontScale
        return minOf(byWidth, byHeight).toInt().coerceIn(MinEmojiSp, MaxEmojiSp)
    }
}
