package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class SoundFeederSpecTest {
    private val cards = listOf(
        SoundFeederCard("sonne", FeederSide.left),
        SoundFeederCard("salat", FeederSide.left),
        SoundFeederCard("schuh", FeederSide.right),
        SoundFeederCard("schaf", FeederSide.right),
    )
    private val round = SoundFeederRound(
        promptTts = "Füttere die Laut-Fresser.",
        leftAtomId = "letter-s",
        rightAtomId = "letter-sch",
        cards = cards,
    )
    private val spec = SoundFeederSpec(id = "l13:sound_feeder", rounds = listOf(round))

    @Test
    fun kindMapsToTheNewTrainer() {
        assertEquals(TrainerKind.sound_feeder, spec.kind)
    }

    @Test
    fun roundsAreReachableThroughTheSealedAccessor() {
        assertEquals(1, spec.roundCount)
        assertEquals(round, spec.round(0))
    }

    @Test
    fun scoresAgainstBothSounds() {
        assertEquals(listOf("letter-s", "letter-sch"), round.scoredAtomIds())
    }

    @Test
    fun sideResolvesToItsSoundAtom() {
        assertEquals("letter-s", round.atomIdFor(FeederSide.left))
        assertEquals("letter-sch", round.atomIdFor(FeederSide.right))
    }

    @Test
    fun theNewKindIsNotPartOfTheAuthoredTrainerOrder() {
        assertFalse(ContentValidator.TrainerOrder.contains(TrainerKind.sound_feeder))
    }

    @Test
    fun aRoundNeedsTwoCardsPerSide() {
        assertThrows(IllegalArgumentException::class.java) {
            round.copy(cards = cards.filter { it.side == FeederSide.left } + cards[2])
        }
    }

    @Test
    fun aRoundRejectsDuplicateCardsAndIdenticalSides() {
        assertThrows(IllegalArgumentException::class.java) {
            round.copy(cards = cards + SoundFeederCard("sonne", FeederSide.left))
        }
        assertThrows(IllegalArgumentException::class.java) {
            round.copy(rightAtomId = "letter-s")
        }
    }
}
