package app.abcvorschule.session

import app.abcvorschule.content.AtomArticleSpeech
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.CountAddRound
import app.abcvorschule.content.LetterTraceRound
import app.abcvorschule.content.SentenceOrderRound
import app.abcvorschule.content.SentencePictureRound
import app.abcvorschule.content.SyllableMergeRound
import app.abcvorschule.content.SyllableMergeSpeech
import app.abcvorschule.content.SymbolHuntRound
import app.abcvorschule.content.SymbolInWordRound
import app.abcvorschule.content.TrainerRound
import app.abcvorschule.content.WordBuildRound
import app.abcvorschule.content.spokenAnswer
import app.abcvorschule.ui.rewards.PraisePhrases
import kotlin.random.Random

/** Ordered success speech — one clip/utterance per part, spoken in list order. */
object SuccessSpeech {
    fun partsForRound(
        round: TrainerRound?,
        pack: ContentPack,
        praise: Boolean,
        random: Random = Random.Default,
    ): List<String> = when (round) {
        is CountAddRound -> {
            // Praise first, answer last (§7): the quantity must be the last thing
            // the child hears — "Ausgezeichnet! Zwei Ameisen", never the reverse.
            val answer = round.spokenAnswer(pack.atoms[round.iconAtomId])
            if (praise) listOf(PraisePhrases.pick(random), answer) else listOf(answer)
        }
        is SyllableMergeRound -> listOf(
            SyllableMergeSpeech.resultSpeech(round, pack.atoms[round.resultAtomId]),
        )
        // §7: Die Antwort nennt bei Substantiven den Artikel ("das Haus"), die
        // Aufgabe nicht ("Baue das Wort Haus"). Nicht-Substantive bleiben nackt.
        is WordBuildRound -> listOfNotNull(
            pack.atoms[round.targetAtomId]?.let { AtomArticleSpeech.forAtom(it) ?: it.display }
                ?: round.promptTts.takeIf { it.isNotBlank() },
        )
        is SentenceOrderRound -> listOf(pack.sentence(round.sentenceId).tts)
        is SentencePictureRound -> listOfNotNull(round.promptTts.takeIf { it.isNotBlank() })
        is LetterTraceRound -> listOfNotNull(round.rewardTts.takeIf { it.isNotBlank() })
        is SymbolHuntRound -> listOfNotNull(
            pack.atoms[round.targetAtomId]?.lemma ?: round.promptTts.takeIf { it.isNotBlank() },
        )
        is SymbolInWordRound -> listOfNotNull(
            pack.atoms[round.wordAtomId]?.let { AtomArticleSpeech.forAtom(it) ?: it.display }
                ?: round.promptTts.takeIf { it.isNotBlank() },
        )
        else -> emptyList()
    }
}
