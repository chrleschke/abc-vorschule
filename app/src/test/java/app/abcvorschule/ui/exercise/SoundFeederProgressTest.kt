package app.abcvorschule.ui.exercise

import app.abcvorschule.content.FeederSide
import app.abcvorschule.content.SoundFeederCard
import app.abcvorschule.content.SoundFeederRound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundFeederProgressTest {
    private val round = SoundFeederRound(
        promptTts = "Füttere die Laut-Fresser.",
        leftAtomId = "letter-s",
        rightAtomId = "letter-sch",
        cards = listOf(
            SoundFeederCard("sonne", FeederSide.left),
            SoundFeederCard("schuh", FeederSide.right),
            SoundFeederCard("salat", FeederSide.left),
            SoundFeederCard("schaf", FeederSide.right),
        ),
    )
    private val start = SoundFeederProgress.initialState(round)

    @Test
    fun theFirstCardIsUpAndNothingIsEaten() {
        assertEquals("sonne", start.current?.atomId)
        assertEquals(4, start.remaining)
        assertEquals(0, start.eaten)
        assertFalse(start.hintActive)
    }

    @Test
    fun theRightMonsterEatsTheCardAndTheNextOneComesUp() {
        val result = SoundFeederProgress.drop(start, FeederSide.left)
        assertEquals(SoundFeederDropOutcome.Eaten, result.outcome)
        assertEquals("schuh", result.state.current?.atomId)
        assertEquals(1, result.state.eaten)
        assertEquals(3, result.state.remaining)
    }

    @Test
    fun theWrongMonsterReportsAMissOnceAndKeepsTheCard() {
        val first = SoundFeederProgress.drop(start, FeederSide.right)
        assertEquals(SoundFeederDropOutcome.Miss, first.outcome)
        assertEquals("sonne", first.state.current?.atomId)
        assertEquals(FeederSide.right, first.state.wrongSide)

        val second = SoundFeederProgress.drop(first.state, FeederSide.right)
        assertEquals(SoundFeederDropOutcome.MissAlreadyReported, second.outcome)
        // Auch beim zweiten Griff steht die falsche Seite im Zustand — daran hängt,
        // welcher Fresser sich schüttelt.
        assertEquals(FeederSide.right, second.state.wrongSide)
        assertEquals(2, second.state.missesOnCard)
    }

    @Test
    fun aMissOnALaterCardIsNotReportedAgainEither() {
        val eaten = SoundFeederProgress.drop(start, FeederSide.left).state
        val miss = SoundFeederProgress.drop(eaten, FeederSide.left) // schuh gehört rechts
        assertEquals(SoundFeederDropOutcome.Miss, miss.outcome)
        val eatenAgain = SoundFeederProgress.drop(miss.state, FeederSide.right).state
        val missAgain = SoundFeederProgress.drop(eatenAgain, FeederSide.right) // salat gehört links
        assertEquals(SoundFeederDropOutcome.MissAlreadyReported, missAgain.outcome)
    }

    @Test
    fun theHintAppearsOnTheSecondMissOfTheSameCardAndClearsWhenItIsEaten() {
        val once = SoundFeederProgress.drop(start, FeederSide.right).state
        assertFalse(once.hintActive)
        val twice = SoundFeederProgress.drop(once, FeederSide.right).state
        assertTrue(twice.hintActive)
        val eaten = SoundFeederProgress.drop(twice, FeederSide.left).state
        assertFalse(eaten.hintActive)
        assertEquals(0, eaten.missesOnCard)
        assertNull(eaten.wrongSide)
    }

    @Test
    fun theLastCardCompletesTheRound() {
        var state = start
        listOf(FeederSide.left, FeederSide.right, FeederSide.left).forEach {
            state = SoundFeederProgress.drop(state, it).state
        }
        val last = SoundFeederProgress.drop(state, FeederSide.right)
        assertEquals(SoundFeederDropOutcome.RoundComplete, last.outcome)
        assertNull(last.state.current)
        assertEquals(0, last.state.remaining)
    }

    @Test
    fun droppingOutsideBothMonstersOrAfterTheEndChangesNothing() {
        val outside = SoundFeederProgress.drop(start, null)
        assertEquals(SoundFeederDropOutcome.Ignored, outside.outcome)
        assertEquals(start, outside.state)
        var state = start
        repeat(4) { state = SoundFeederProgress.drop(state, round.cards[it].side).state }
        assertEquals(SoundFeederDropOutcome.Ignored, SoundFeederProgress.drop(state, FeederSide.left).outcome)
    }
}
