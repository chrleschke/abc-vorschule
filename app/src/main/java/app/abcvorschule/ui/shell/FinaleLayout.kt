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
 * Seit der Font-Scale-Anpassung geben [pictureSizeSp], [sentenceSizeSp],
 * [sentenceLineHeightSp], [headerSizeSp] und [headerLineHeightSp] bei `fontScale = 1.0`
 * exakt die bisherigen Werte zurück und schrumpfen darüber, damit die *effektiv
 * gerenderte* Größe nie größer wird als bei `fontScale = 1.0` — siehe
 * [capEffectiveSize]. Das hält den End-Screen bei jeder System-Schriftskalierung
 * innerhalb des am Bildschirm gemessenen Budgets, ohne dass die Spalte scrollen muss.
 * Jede an dieser Rechnung beteiligte Zahl lebt hier, nicht im Composable — nur so bleibt
 * sie testbar.
 */
object FinaleLayout {
    private const val BaseSizeSp = 64
    private const val CrowdedSizeSp = 52
    private const val CrowdedFrom = 4
    private const val RevealStepMillis = 180L

    /**
     * Horizontaler Abstand zwischen den Bildern der Reihe. Öffentlich, damit sowohl
     * [pictureRowWidthDp] als auch das Composable (`RewardSummaryScreen.FinaleBody`)
     * dieselbe Zahl verwenden statt zweier unsynchronisierter 16dp-Werte.
     */
    const val PictureRowGapDp = 16

    /** Basisgröße des Satzes bei `fontScale = 1.0`: Material3s Default für headlineSmall. */
    private const val SentenceSizeSp = 24

    /** Basis-Zeilenhöhe des Satzes bei `fontScale = 1.0`: Material3s Default für headlineSmall. */
    private const val SentenceLineHeightSp = 32

    /** Basisgröße des Headers bei `fontScale = 1.0`: `headlineMedium` (`ui/theme/Theme.kt`). */
    private const val HeaderSizeSp = 28

    /** Basis-Zeilenhöhe des Headers bei `fontScale = 1.0`: `headlineMedium` (`ui/theme/Theme.kt`). */
    private const val HeaderLineHeightSp = 34

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
     * Geschätzte Breite der ganzen Bildreihe: [pictureSizeSp] je Bild — Emoji-Glyphen
     * sind überwiegend quadratisch, ein Vorschuss von rund 1 em Breite pro Bild ist eine
     * gängige Näherung, keine Messung, denn das Repo hat keine androidTests, die reale
     * Glyph-Metriken prüfen könnten — plus die (count-1) Lücken von [PictureRowGapDp]
     * dazwischen. Macht die Breitenprüfung testbar, die vorher nur als Kommentar-Rechnung
     * in der Residual-Notiz stand: der Validator erlaubt bis zu vier Bilder, aber nichts
     * prüfte automatisiert, ob vier Bilder auf das schmalste unterstützte Gerät passen.
     */
    fun pictureRowWidthDp(count: Int, fontScale: Float): Int {
        if (count <= 0) return 0
        val pictureWidth = pictureSizeSp(count, fontScale)
        return pictureWidth * count + (count - 1) * PictureRowGapDp
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
     * Zeilenhöhe des Satzes, im Gleichschritt mit [sentenceSizeSp] gedeckelt: 32sp bei
     * `fontScale = 1.0`, darüber genauso gebremst. Eine eigene, ebenfalls gedeckelte
     * Zeilenhöhe ist nötig, weil Material3s `headlineSmall`-Stil sonst weiterhin fest
     * 32sp vorgibt, die ungebremst mit `fontScale` wachsen würden — die gedeckelte
     * Schriftgröße allein hielte die Zeile dann nicht klein.
     */
    fun sentenceLineHeightSp(fontScale: Float): Int = capEffectiveSize(SentenceLineHeightSp, fontScale)

    /**
     * Zielgröße des Headers ("Super gemacht!"): 28sp bei `fontScale = 1.0`, wie
     * `headlineMedium`. Ungedeckelt würde der Header bei großer Schriftskalierung nicht
     * nur wachsen, sondern auf zwei Zeilen umbrechen (der `Text` hat kein `maxLines`) —
     * das verdoppelt seine Höhe und frisst genau den Puffer auf, den [sentenceSizeSp]
     * und [pictureSizeSp] freihalten. Deckeln verhindert beides zugleich: Der Header
     * bleibt bei jeder Skalierung so breit wie bei `fontScale = 1.0` und bricht darum nie
     * um.
     */
    fun headerSizeSp(fontScale: Float): Int = capEffectiveSize(HeaderSizeSp, fontScale)

    /** Zeilenhöhe des Headers, im Gleichschritt mit [headerSizeSp] gedeckelt: 34sp bei `fontScale = 1.0`. */
    fun headerLineHeightSp(fontScale: Float): Int = capEffectiveSize(HeaderLineHeightSp, fontScale)

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
