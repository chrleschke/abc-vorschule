package app.abcvorschule.ui.exercise

import kotlin.math.floor

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

/**
 * Emoji-Größe der Bildkarten: gestaffelt nach Atomzahl (1–3, siehe Validator)
 * *und* gedeckelt auf die real verfügbare Kartenbreite. Compose-frei, damit die
 * Rechnung testbar bleibt — das Repo hat keine androidTests.
 *
 * Der Breitendeckel ist der eigentliche Fix: vorher setzte die Karte ihre Emoji-
 * Reihe fest in 72/56/44sp. Auf einem schmalen Gerät oder bei System-Schrift-
 * skalierung über 1.0 passte die Reihe dann nicht mehr in eine Zeile, und weil
 * der `Text` `maxLines = 1` hat, wurde die zweite Zeile erzeugt, aber nie
 * gezeichnet: das letzte Emoji verschwand. Bei 16 der 72 ausgelieferten Runden
 * unterscheiden sich die beiden Karten nur durch dieses letzte Emoji — dort sah
 * das Kind zwei identische Karten und konnte die Runde nicht lösen.
 */
object SentencePictureCardSizing {
    /**
     * Vorschub eines Emoji-Glyphen als Vielfaches seiner Schriftgröße. Emoji-Glyphen
     * sind im Kern quadratisch, brauchen aber Seitenluft; 1.2 em liegt bewusst über
     * dem 1.0 em, mit dem `FinaleLayout.pictureRowWidthDp` rechnet. Dort steht jedes
     * Bild in einer eigenen Zelle, hier stehen bis zu drei in *einer* Textzeile, und
     * eine zu kleine Schätzung führt genau in den Fehler zurück, den dieser Deckel
     * verhindern soll. Eine Näherung, keine Messung: reale Glyph-Metriken könnten
     * nur androidTests prüfen, die es hier nicht gibt.
     */
    const val EmojiAdvanceEm = 1.2f

    /**
     * Absolute Untergrenze, damit nie 0 (oder Negatives) herauskommt, wenn die Breite
     * vor der ersten Messung noch 0 ist. Bewusst *unter* jeder Größe, die das
     * ausgelieferte Layout tatsächlich erzeugt (auf dem schmalsten unterstützten
     * Gerät sind es bei drei Emojis noch 31sp): ein Boden oberhalb des Breitenbudgets
     * würde genau den Überlauf zurückholen, gegen den der Deckel existiert.
     */
    const val MinEmojiSp = 12f

    /**
     * @param contentWidthDp Breite *innerhalb* der Karteninnenabstände.
     * @param fontScale System-Schriftskalierung ([androidx.compose.ui.unit.Density.fontScale]).
     */
    fun emojiSp(atomCount: Int, contentWidthDp: Float, fontScale: Float): Float {
        val count = atomCount.coerceAtLeast(1)
        val base = when {
            count <= 1 -> 72f
            count == 2 -> 56f
            else -> 44f
        }
        val widthCap = contentWidthDp / (count * EmojiAdvanceEm)
        // Emojis sind Bilder, keine Prosa — dieselbe Begründung wie
        // FinaleLayout.capEffectiveSize: sie geben ihre fontScale-Vergrößerung als
        // Erstes wieder ab, damit die *gerenderte* Reihe (sp × fontScale) nie breiter
        // wird als das dp-Budget der Karte.
        val scaled = minOf(base, widthCap) / maxOf(fontScale, 1f)
        // Abrunden statt runden: die Näherung darf nie über das Budget rutschen,
        // dieselbe Konvention wie die Ganzzahl-Kürzung in FinaleLayout.
        return floor(scaled).coerceAtLeast(MinEmojiSp)
    }
}
