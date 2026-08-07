package app.abcvorschule.ui.rewards

import kotlin.random.Random

/**
 * Spoken praise for a correct Rechnen answer, varied so the reward does not wear out
 * over the many count-add rounds a lesson run contains.
 *
 * Spoken only, never printed: a pre-reader gains nothing from a written
 * "Ausgezeichnet", and praise is chrome rather than the learning task itself.
 *
 * Each entry is spoken as its own utterance, so it carries exactly the punctuation it
 * needs: the bare words stay bare ("Klasse"), the cheers keep their "!" and "..."
 * ("Bäääm! Volltreffer!", "Und... Tadaaa!") — that punctuation is what makes the TTS
 * land them as a cheer instead of a statement. Nothing ends in "?": praise never asks.
 *
 * Entries stay distinct even ignoring punctuation and case, otherwise the same words
 * would be curated and rendered twice in the TTS pipeline. Every phrase is mirrored in
 * tools/tts/extra-strings.json as a `rewardTts` string (profile `reward`); until a clip
 * exists for it, the phrase simply falls back to Android TTS.
 */
object PraisePhrases {
    val All: List<String> = listOf(
        "Super",
        "Gut gemacht",
        "Ausgezeichnet",
        "Klasse",
        "Genau richtig",
        "Toll gemacht",
        "Perfekt",
        "Stark",
        "Bravo",
        "Wunderbar",
        "Spitze",
        "Sehr gut",
        "Prima",
        "Fantastisch",
        "Großartig",
        "Weiter so",
        "Richtig gut",
        "Genau so",
        "Klasse gemacht",
        "Das hast du toll gemacht",
        "Einfach cool!",
        "Daumen hoch!",
        "Das war spitze!",
        "Du hast es drauf!",
        "Läuft bei dir!",
        "Bäääm! Volltreffer!",
        "High-Five mit Schallgeschwindigkeit!",
        "Gummibären-Siegertanz, jetzt!",
        "Ratzfatz weggezaubert!",
        "Kopf-Gymnastik: Note 1!",
        "Und... Tadaaa!",
        "Zack, Gehirn-Explosion!",
        "Schlau-Schlumpf-Modus aktiviert!",
        "Keks verdient!",
        "Hier hast du einen Keks!",
        "Du kleiner Einstein!",
        "Einfach weggemuckelt!",
        "Einhorn-Power pur!",
        "Voll abgeräumt!",
    )

    /** [random] is injectable so tests stay deterministic. */
    fun pick(random: Random = Random.Default): String = All[random.nextInt(All.size)]
}
