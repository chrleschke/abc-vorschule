package app.abcvorschule.ui.exercise

/**
 * Welche Seite die richtige Karte des Satz-Verstehers bekommt. Deterministisch
 * aus einem Runden-Seed (Hash des Satzes), damit die Zuordnung über
 * Recompositions stabil bleibt und kein Autoren-Bias entsteht.
 *
 * [TrayOrder.arrange] ist hier bewusst NICHT wiederverwendet: es garantiert
 * "nie exakt die Lösungsreihenfolge" und würde bei zwei Karten die richtige
 * systematisch auf eine Seite legen.
 */
object SentencePictureSides {
    fun correctOnLeft(seed: Int): Boolean {
        var h = seed * 0x2545F491
        h = h xor (h ushr 13)
        return (h and 1) == 0
    }
}

/** Emoji-Größe der Bildkarten, gestaffelt nach Atomzahl (1–3, siehe Validator). */
object SentencePictureCardSizing {
    fun emojiSp(atomCount: Int): Float = when {
        atomCount <= 1 -> 72f
        atomCount == 2 -> 56f
        else -> 44f
    }
}
