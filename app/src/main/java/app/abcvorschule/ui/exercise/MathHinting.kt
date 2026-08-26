package app.abcvorschule.ui.exercise

import app.abcvorschule.progress.ParentMode
import app.abcvorschule.progress.ScaffoldLevel

object MathHinting {
    const val NearDistanceMax = 2

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

    fun distance(answer: Int, guess: Int): Int = kotlin.math.abs(answer - guess)

    fun isNear(answer: Int, guess: Int): Boolean {
        val d = distance(answer, guess)
        return d in 1..NearDistanceMax
    }

    fun isNear(distance: Int): Boolean = distance in 1..NearDistanceMax

    fun hintKey(answer: Int, guess: Int): String =
        if (isNear(answer, guess)) "near" else "far"

    fun hintText(answer: Int, guess: Int): String =
        missFeedback(distance(answer, guess))

    /** Feedback shown after a miss; [distance] null means the miss has no numeric distance. */
    fun missFeedback(distance: Int?): String = when {
        distance == null -> "Versuch es noch einmal"
        isNear(distance) -> "Du bist nah dran, denk noch einmal nach"
        else -> "Schau noch einmal genau hin"
    }

    /**
     * Ab diesem Ergebnis wird getippt statt gewählt — das Band `hard`
     * (ProgressionEngine.bandFor) beginnt bei 11. Eigene Konstante, obwohl
     * [QuantityRepresentation.SymbolicFrom] denselben Wert trägt: die eine Regel
     * entscheidet über die Eingabeart, die andere über die Darstellung einer Menge.
     * Sie dürfen sich unabhängig voneinander bewegen.
     */
    const val TypedAnswerFrom = 11

    /** Fehlversuche, nach denen die Zähl-Hilfe aufklappt. */
    const val CountingAidFromMisses = 2

    /**
     * Fehlversuche, nach denen im Tipp-Modus der Auflösen-Knopf erscheint — später
     * als im Kachel-Modus (dort weiterhin 2), damit die Zähl-Hilfe nicht
     * übersprungen werden kann. Ein echter Ausweg bleibt sie trotzdem.
     */
    const val ResolveFromMissesTyped = 4

    /**
     * Getippt wird bei fortgeschrittenem Scaffold — oder sobald das Ergebnis über
     * zehn liegt. Die zweite Hälfte prüft den *Eltern-Modus*, nicht das abgeleitete
     * Scaffold: der Default ist [ParentMode.Auto], und dort startet ein frisches Kind
     * auf [ScaffoldLevel.Beginner]. Gegen das Scaffold geprüft würde die Regel beim
     * Normalnutzer also nie greifen. Nur ein ausdrücklich gesetztes
     * [ParentMode.Beginner] behält überall die Kacheln — Elternentscheidung schlägt
     * Aufgabenschwere.
     */
    /**
     * Gesprochene Cues der Zähl-Hilfe. Feste Strings ohne Interpolation, sonst
     * findet die Clip-Suche nie einen kuratierten Clip (`ClipIndex.lookup`) und es
     * bliebe bei der TTS-Stimme. Gepflegt in `tools/tts/extra-strings.json`.
     *
     * Der Cue ist die *Verstärkung* der Anleitung, nicht die Anleitung selbst —
     * die trägt der Puls-Hinweis auf dem nächsten offenen Objekt. Eine Ansage,
     * die nur bei vorhandener Stimme ankommt, wäre keine Anleitung.
     */
    const val CountingAidCueCollect = "Tippe auf die Bilder, um sie zu zählen."
    const val CountingAidCueTakeAway = "Tippe auf die Bilder, um sie wegzunehmen."
    const val CountingAidCueRows = "Tippe auf jede Reihe, um sie zu zählen."

    fun countingAidCue(operation: MathOperation): String = when (operation) {
        MathOperation.Subtract -> CountingAidCueTakeAway
        // Malnehmen zählt reihenweise, nicht Bild für Bild — der Cue muss die
        // Geste benennen, die tatsächlich verlangt ist.
        MathOperation.Multiply -> CountingAidCueRows
        MathOperation.Add -> CountingAidCueCollect
    }

    fun inputFor(scaffold: ScaffoldLevel, parentMode: ParentMode, answer: Int): MathInputMode {
        val typed = scaffold == ScaffoldLevel.Advanced ||
            (parentMode != ParentMode.Beginner && answer >= TypedAnswerFrom)
        return if (typed) MathInputMode.Typed else MathInputMode.Tiles
    }
}

/** Wie die Antwort einer Rechenrunde eingegeben wird. */
enum class MathInputMode { Tiles, Typed }
