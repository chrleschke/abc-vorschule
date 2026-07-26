package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SpokenAnswerTest {
    private val pack = ContentRepository.fromClasspath().load()

    private val ameise = Atom(
        id = "ameise",
        lemma = "Ameise",
        display = "Ameise",
        emoji = "🐜",
        kind = AtomKind.other,
        pluralDisplay = "Ameisen",
    )

    private fun round(answer: Int) = CountAddRound(
        promptTts = "x",
        iconAtomId = ameise.id,
        left = 1,
        right = answer - 1,
        answer = answer,
    )

    @Test
    fun singularResultUsesTheSingularDisplay() {
        assertEquals("1 Ameise", round(1).spokenAnswer(ameise))
    }

    @Test
    fun pluralResultUsesThePluralDisplay() {
        assertEquals("2 Ameisen", round(2).spokenAnswer(ameise))
    }

    @Test
    fun missingPluralFallsBackToDisplayWithoutCrashing() {
        val noPlural = ameise.copy(pluralDisplay = null)
        assertEquals("2 Ameise", round(2).spokenAnswer(noPlural))
    }

    @Test
    fun nullIconYieldsJustTheNumberWithNoTrailingSpace() {
        val spoken = round(3).spokenAnswer(null)
        assertEquals("3", spoken)
        assertFalse(spoken.endsWith(" "))
    }

    @Test
    fun everyShippedCountAddRoundProducesANonBlankSpokenAnswer() {
        // Authoring gap guard: every counted atom must carry a usable noun form.
        pack.tasks.values.filterIsInstance<CountAddSpec>().forEach { spec ->
            spec.rounds.forEach { round ->
                val icon = pack.atom(round.iconAtomId)
                val spoken = round.spokenAnswer(icon)
                assertFalse(
                    "task ${spec.id} round for ${icon.id} produced a blank spoken answer",
                    spoken.substringAfter(' ', missingDelimiterValue = "").isBlank(),
                )
            }
        }
    }
}
