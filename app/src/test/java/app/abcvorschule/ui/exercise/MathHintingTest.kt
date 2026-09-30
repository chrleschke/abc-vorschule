package app.abcvorschule.ui.exercise

import app.abcvorschule.progress.ParentMode
import app.abcvorschule.progress.ScaffoldLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MathHintingTest {
    @Test
    fun advancedScaffoldTypesRegardlessOfHowSmallTheAnswerIs() {
        assertEquals(
            MathInputMode.Typed,
            MathHinting.inputFor(ScaffoldLevel.Advanced, ParentMode.Auto, answer = 2),
        )
    }

    @Test
    fun answersOverTenTypeEvenWhenTheDerivedScaffoldIsStillBeginner() {
        // Der Default ist ParentMode.Auto, und dort startet ein frisches Kind auf
        // ScaffoldLevel.Beginner. Griffe die Regel gegen das Scaffold statt gegen den
        // Eltern-Modus, liefe sie beim Normalnutzer ins Leere — das ist der Kern.
        assertEquals(
            MathInputMode.Tiles,
            MathHinting.inputFor(ScaffoldLevel.Beginner, ParentMode.Auto, answer = 10),
        )
        assertEquals(
            MathInputMode.Typed,
            MathHinting.inputFor(ScaffoldLevel.Beginner, ParentMode.Auto, answer = 11),
        )
    }

    @Test
    fun explicitBeginnerParentModeKeepsTilesEvenForTheHardestAnswer() {
        assertEquals(
            MathInputMode.Tiles,
            MathHinting.inputFor(ScaffoldLevel.Beginner, ParentMode.Beginner, answer = 30),
        )
        // Ein ausdrücklich fortgeschrittenes Scaffold bleibt davon unberührt:
        // die Ausnahme gilt der Schwere-Regel, nicht dem Scaffold.
        assertEquals(
            MathInputMode.Typed,
            MathHinting.inputFor(ScaffoldLevel.Advanced, ParentMode.Beginner, answer = 30),
        )
    }

    @Test
    fun onlyAnAnswerFoundByTheChildIsPraised() {
        assertTrue(MathHinting.praises(attempt(correct = true)))
        assertFalse(MathHinting.praises(attempt(correct = true, resolved = true)))
        assertFalse(MathHinting.praises(attempt(correct = false)))
    }

    private fun attempt(
        resolved: Boolean = false,
        correct: Boolean = false,
    ) = MathAttempt(resolved, correct)

    @Test
    fun threeChoicesAlwaysExactlyThreeIncludingAnswer() {
        val choices = MathHinting.threeChoices(4)
        assertEquals(3, choices.size)
        assertTrue(choices.contains(4))
        assertEquals(choices.toSet().size, choices.size)
    }
}
