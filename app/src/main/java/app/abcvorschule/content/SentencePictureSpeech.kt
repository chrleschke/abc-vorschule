package app.abcvorschule.content

/**
 * Ansage-Teile des Satz-Verstehers. Die Instruktion kommt nur vor Runde 1 —
 * danach trägt der Satz allein die Aufgabe. Getrennte Teile halten die
 * Audio-Clips wiederverwendbar (eine Instruktions-Aufnahme für alle Lektionen).
 */
object SentencePictureSpeech {
    fun promptParts(
        spec: SentencePictureSpec?,
        round: SentencePictureRound,
        roundIndex: Int,
    ): List<String> = listOfNotNull(
        spec?.instructionTts?.takeIf { it.isNotBlank() && roundIndex == 0 },
        round.promptTts.takeIf { it.isNotBlank() },
    )
}
