package app.abcvorschule.ui.rewards

import kotlin.random.Random

/**
 * Spoken praise for a correct Rechnen answer, varied so the reward does not wear out
 * over the many count-add rounds a lesson run contains.
 *
 * Spoken only, never printed: a pre-reader gains nothing from a written
 * "Ausgezeichnet", and praise is chrome rather than the learning task itself.
 * Phrases carry no trailing punctuation — the caller composes the sentence.
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
    )

    /** [random] is injectable so tests stay deterministic. */
    fun pick(random: Random = Random.Default): String = All[random.nextInt(All.size)]
}
