package app.abcvorschule.content

/**
 * Welcher Index von [round]s gesprochenen Prompt-Teilen (siehe
 * SessionViewModel.currentPromptParts) die Interaktion freigibt — nachfolgende
 * Teile laufen unabhängig davon zu Ende weiter.
 *
 * Wort-Detektiv ist die einzige Runde, deren Ansage nach dem eigentlichen Inhalt
 * (Ziel-Buchstabe/-Laut, Index 1) noch weiterläuft: Konnektor + Wort folgen. Bei
 * jeder anderen Runde IST die ganze Ansage der eigentliche Inhalt, ihr letzter Teil
 * ist also zugleich ihr Freigabe-Punkt.
 */
object PromptUnlock {
    fun unlockIndex(round: TrainerRound, parts: List<String>): Int {
        if (parts.isEmpty()) return 0
        return when (round) {
            is SymbolInWordRound -> 1.coerceAtMost(parts.lastIndex)
            else -> parts.lastIndex
        }
    }
}
