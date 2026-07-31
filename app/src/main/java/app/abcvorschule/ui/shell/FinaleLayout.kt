package app.abcvorschule.ui.shell

import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.LessonFinale

/** Ein Bild der Finale-Reihe: Glyph zum Zeigen, Lemma zum Vorsprechen. */
data class FinalePicture(
    val atomId: String,
    val emoji: String,
    val lemma: String,
)

/**
 * Ableitung und Maße der Finale-Bildreihe. Compose-frei, damit die Entscheidungen
 * testbar bleiben — das Repo hat keine androidTests.
 *
 * Deterministisch wie [app.abcvorschule.ui.exercise.WordFrameSizing]: kein Random, keine
 * Sortierung. Die Reihenfolge ist die des Satzes, weil sie den Satz erzählt.
 */
object FinaleLayout {
    private const val BaseSizeSp = 64
    private const val CrowdedSizeSp = 52
    private const val CrowdedFrom = 4
    private const val RevealStepMillis = 180L

    /**
     * Bilder in Satzreihenfolge. Atome ohne Emoji oder ohne Eintrag im Pack werden
     * übersprungen: der Validator lehnt solchen Content ab, aber eine Lücke in der
     * Reihe ist besser als ein leerer Platzhalter.
     */
    fun picturesOf(pack: ContentPack, finale: LessonFinale): List<FinalePicture> =
        finale.pictureAtomIds.mapNotNull { id ->
            val atom = pack.atoms[id] ?: return@mapNotNull null
            if (atom.emoji.isBlank()) return@mapNotNull null
            FinalePicture(atomId = id, emoji = atom.emoji, lemma = atom.lemma)
        }

    /** Vier Bilder brauchen weniger Breite pro Stück, damit die Reihe nicht umbricht. */
    fun pictureSizeSp(count: Int): Int =
        if (count >= CrowdedFrom) CrowdedSizeSp else BaseSizeSp

    /** Staffelung der Einblendung — führt den Blick von links nach rechts. */
    fun revealDelayMillis(index: Int): Long =
        (index.coerceAtLeast(0)) * RevealStepMillis
}
