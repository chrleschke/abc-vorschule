package app.abcvorschule.ui.world

import app.abcvorschule.content.LetterTraceRound
import app.abcvorschule.content.SymbolHuntRound
import app.abcvorschule.content.TrainerRound

/**
 * Welche Welt hinter einem Trainer liegt (PRODUCT_PRINCIPLES §10, „Nachtwelten").
 *
 * Jeder Trainer hat **immer dieselbe** Welt — das Kind erkennt die Aufgabe am Ort,
 * bevor es die Ansage hört: Tiefsee heißt „suchen", Dschungel heißt „nachspuren".
 * Welten ohne eigenes Motiv bleiben auf dem Papiergrund, bis sie eines bekommen.
 */
enum class TrainerWorld(val night: Boolean) {
    /** Der bisherige Papiergrund (radialer Verlauf, `TaskShell`). */
    Paper(night = false),

    /** Buchstaben- und Silben-Jagd: nativ gezeichnetes Meer, keine Bilddatei. */
    DeepSea(night = true),

    /** Spurensucher: abgedunkeltes Dschungelbild (`world_jungle.webp`). */
    Jungle(night = true),
    ;

    val chrome: ChromeColors get() = if (night) NightChrome else PaperChrome

    companion object {
        fun of(round: TrainerRound?): TrainerWorld = when (round) {
            is SymbolHuntRound -> DeepSea
            is LetterTraceRound -> Jungle
            else -> Paper
        }
    }
}
