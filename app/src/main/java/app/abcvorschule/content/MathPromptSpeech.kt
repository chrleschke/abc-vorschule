package app.abcvorschule.content

import app.abcvorschule.speech.GermanNumberWord
import app.abcvorschule.ui.exercise.MathOperation

/**
 * Wie Rechnen seine Aufgabe spricht: genau zwei Clips — die Einleitung „Wie viel ist"
 * und die ganze Aufgabe („fünf plus zwanzig", [taskText]) als **ein** Clip im TTS-Profil
 * `math`, einzeln im TTS-UI kuratiert. Eine Zwischenfassung setzte Zahl · Rechenwort ·
 * Zahl aus Einzelclips zusammen; Qwen klang dabei in Betonung und Stimme zu
 * uneinheitlich, das Stückwerk war hörbar.
 *
 * Gebaut aus den strukturierten Feldern der Runde, nicht aus `promptTts` — der bleibt
 * im Content als lesbare Fassung und als Rückfall für eine unbekannte Operation.
 * `tools/tts/ttskit/extract.py` baut denselben Text für die Clips nach.
 */
object MathPromptSpeech {
    const val INTRO = "Wie viel ist"

    private const val PLUS = "plus"
    private const val MINUS = "minus"
    private const val TIMES = "mal"

    /** Ordered strings for [app.abcvorschule.speech.SpeechController]. */
    fun promptParts(round: CountAddRound): List<String> {
        val task = taskText(round)
            ?: return listOfNotNull(round.promptTts.takeIf { it.isNotBlank() })
        return listOf(INTRO, task)
    }

    /**
     * Die Aufgabe als ein gesprochener Text, klein und ohne Satzzeichen: „fünf plus
     * zwanzig". `null` bei einer unbekannten Operation.
     */
    fun taskText(round: CountAddRound): String? {
        val op = when (MathOperation.fromWireName(round.operation)) {
            MathOperation.Add -> PLUS
            MathOperation.Subtract -> MINUS
            MathOperation.Multiply -> TIMES
            null -> return null
        }
        return "${GermanNumberWord.of(round.left)} $op ${GermanNumberWord.of(round.right)}"
    }
}
