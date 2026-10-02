package app.abcvorschule.ui.world

import app.abcvorschule.content.CountAddRound
import app.abcvorschule.content.LetterTraceRound
import app.abcvorschule.content.SentenceOrderRound
import app.abcvorschule.content.SentencePictureRound
import app.abcvorschule.content.SoundFeederRound
import app.abcvorschule.content.SyllableMergeRound
import app.abcvorschule.content.SymbolInWordRound
import app.abcvorschule.content.WordBuildRound
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

    /** Rechnen: Wiese am Waldrand bei Nacht, gezeichnet. */
    ForestNight(night = true),

    /** Wort-Detektiv: Sternenhimmel mit Deko-Teleskop, gezeichnet (bis Oktober 2026 ein Dachboden). */
    Stars(night = true),

    /** Wort-Bauer: Werkbank im Lampenlicht, gezeichnet. */
    Workshop(night = true),

    /** Satz-Architekt: Garten in der blauen Stunde, die Wäscheleine zeichnet der Trainer. */
    Garden(night = true),

    /** Satz-Versteher: Leseecke im Lampenschein, gezeichnet; Buch und Tisch zeichnet der Trainer. */
    ReadingNook(night = true),

    /** Laut-Fresser: Pilzhöhle (`world_cave.webp`), die Fresser zeichnet der Trainer. */
    Cave(night = true),

    /** Silben-Verschmelzer: Waldlichtung mit Glühwürmchen, gezeichnet. */
    Clearing(night = true),
    ;

    val chrome: ChromeColors get() = if (night) NightChrome else PaperChrome

    companion object {
        fun of(round: TrainerRound?): TrainerWorld = when (round) {
            is SymbolHuntRound -> DeepSea
            is LetterTraceRound -> Jungle
            is CountAddRound -> ForestNight
            is SymbolInWordRound -> Stars
            is WordBuildRound -> Workshop
            is SentenceOrderRound -> Garden
            is SentencePictureRound -> ReadingNook
            is SoundFeederRound -> Cave
            is SyllableMergeRound -> Clearing
            else -> Paper
        }
    }
}
