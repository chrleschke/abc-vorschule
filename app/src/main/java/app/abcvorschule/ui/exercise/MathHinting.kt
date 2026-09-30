package app.abcvorschule.ui.exercise

import app.abcvorschule.progress.ParentMode
import app.abcvorschule.progress.ScaffoldLevel

object MathHinting {
    /** Exactly three numeric choices including the answer (near distractors only). */
    fun threeChoices(answer: Int): List<Int> {
        val opts = linkedSetOf(answer)
        if (answer > 1) opts += answer - 1
        var next = answer + 1
        while (opts.size < 3) {
            opts += next
            next++
        }
        return opts.toList()
    }

    /**
     * Ab diesem Ergebnis wird getippt statt gewählt — das Band `hard`
     * (ProgressionEngine.bandFor) beginnt bei 11.
     */
    const val TypedAnswerFrom = 11

    /** Lobt die Erfolgs-Zeremonie diesen Versuch? Nur eine selbst gefundene Antwort. */
    fun praises(attempt: MathAttempt): Boolean =
        attempt.correct && !attempt.resolved

    /**
     * Getippt wird bei fortgeschrittenem Scaffold — oder sobald das Ergebnis über
     * zehn liegt. Die zweite Hälfte prüft den *Eltern-Modus*, nicht das abgeleitete
     * Scaffold: der Default ist [ParentMode.Auto], und dort startet ein frisches Kind
     * auf [ScaffoldLevel.Beginner]. Gegen das Scaffold geprüft würde die Regel beim
     * Normalnutzer also nie greifen. Nur ein ausdrücklich gesetztes
     * [ParentMode.Beginner] behält überall die Kacheln — Elternentscheidung schlägt
     * Aufgabenschwere.
     */
    fun inputFor(scaffold: ScaffoldLevel, parentMode: ParentMode, answer: Int): MathInputMode {
        val typed = scaffold == ScaffoldLevel.Advanced ||
            (parentMode != ParentMode.Beginner && answer >= TypedAnswerFrom)
        return if (typed) MathInputMode.Typed else MathInputMode.Tiles
    }
}

/** Wie die Antwort einer Rechenrunde eingegeben wird. */
enum class MathInputMode { Tiles, Typed }
