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
 *
 * Seit der Font-Scale-Anpassung geben [pictureSizeSp] und [sentenceSizeSp] bei
 * `fontScale = 1.0` exakt die bisherigen Werte zurück und schrumpfen darüber, damit die
 * *effektiv gerenderte* Größe nie größer wird als bei `fontScale = 1.0` — siehe
 * [capEffectiveSize]. Das hält den End-Screen bei jeder System-Schriftskalierung
 * innerhalb des am Bildschirm gemessenen Budgets, ohne dass die Spalte scrollen muss.
 */
object FinaleLayout {
    private const val BaseSizeSp = 64
    private const val CrowdedSizeSp = 52
    private const val CrowdedFrom = 4
    private const val RevealStepMillis = 180L

    /** Basisgröße des Satzes bei `fontScale = 1.0`: Material3s Default für headlineSmall. */
    private const val SentenceSizeSp = 24

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

    /**
     * Vier Bilder brauchen weniger Breite pro Stück, damit die Reihe nicht umbricht.
     *
     * Emojis sind Bilder, keine Prosa: dass sie mit der System-Schriftskalierung
     * überhaupt mitwachsen, ist eine Kulanz, keine Pflicht. Deshalb geben sie ihre
     * Vergrößerung als Erstes wieder ab, sobald `fontScale` über 1.0 steigt — die
     * effektive Größe bleibt bei 64sp bzw. 52sp gedeckelt (siehe [capEffectiveSize]).
     */
    fun pictureSizeSp(count: Int, fontScale: Float): Int {
        val base = if (count >= CrowdedFrom) CrowdedSizeSp else BaseSizeSp
        return capEffectiveSize(base, fontScale)
    }

    /**
     * Zielgröße des Satzes: 24sp bei `fontScale = 1.0`, wie Material3s Default für
     * headlineSmall. Gibt oberhalb von 1.0 ebenso Vergrößerung ab wie die Bilder (siehe
     * [capEffectiveSize]) — sonst würde der auf drei Zeilen begrenzte Satz bei großer
     * Schriftskalierung die Spalte sprengen und den Weiter-Button vom Bildschirm
     * schieben, in einer Spalte, die aus gutem Grund nicht scrollt: ein Vorschulkind
     * hätte sonst keinen Weg zurück.
     */
    fun sentenceSizeSp(fontScale: Float): Int = capEffectiveSize(SentenceSizeSp, fontScale)

    /**
     * Deckelt die *effektiv gerenderte* Größe (= zurückgegebenes sp × `fontScale`) auf
     * [baseSp]: bis einschließlich `fontScale = 1.0` unverändert, darüber schrumpft der
     * zurückgegebene sp-Wert so, dass das Produkt konstant bei [baseSp] bleibt (statt es
     * zu überschreiten — die Ganzzahl-Kürzung rundet dafür bewusst ab, nie auf). Monoton:
     * eine größere Skala liefert nie einen größeren Wert.
     */
    private fun capEffectiveSize(baseSp: Int, fontScale: Float): Int =
        if (fontScale <= 1f) baseSp else (baseSp / fontScale).toInt().coerceAtLeast(1)

    /** Staffelung der Einblendung — führt den Blick von links nach rechts. */
    fun revealDelayMillis(index: Int): Long =
        (index.coerceAtLeast(0)) * RevealStepMillis
}
