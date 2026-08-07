package app.abcvorschule.debug

import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.LetterTraceRound
import app.abcvorschule.content.SentencePictureRound
import app.abcvorschule.content.SentencePictureSpec
import app.abcvorschule.content.SoundPositionRound
import app.abcvorschule.content.SoundPositionSpec
import app.abcvorschule.content.SymbolHuntDerivation
import app.abcvorschule.content.SyllableMergeRound
import app.abcvorschule.content.rounds

enum class TtsDebugGroup { Atom, Sentence, Task }

/** One content-authored string the app can pass to `SpeechController.speak`. */
data class TtsDebugEntry(
    val id: String,
    val group: TtsDebugGroup,
    val label: String,
    val originalText: String,
    val sourceFile: String,
)

fun ContentPack.ttsDebugEntries(): List<TtsDebugEntry> {
    val entries = mutableListOf<TtsDebugEntry>()

    atoms.values.sortedBy { it.id }.forEach { atom ->
        entries += TtsDebugEntry(
            id = "atom:${atom.id}:lemma",
            group = TtsDebugGroup.Atom,
            label = "${atom.display} (${atom.kind})",
            originalText = atom.lemma,
            sourceFile = "atoms.json",
        )
    }

    sentences.values.sortedBy { it.id }.forEach { sentence ->
        entries += TtsDebugEntry(
            id = "sentence:${sentence.id}:tts",
            group = TtsDebugGroup.Sentence,
            label = sentence.id,
            originalText = sentence.tts,
            sourceFile = "sentences.json",
        )
    }

    tasks.values.sortedBy { it.id }.forEach { task ->
        // Task-Level-Strings: gesprochene Felder, die nicht an einer Runde hängen.
        // IDs wie in tools/tts/ttskit/extract.py, damit Debug-Screen und Sprach-
        // Pipeline denselben Schlüssel benutzen.
        if (task is SoundPositionSpec) {
            entries += TtsDebugEntry(
                id = "task:${task.id}:phonemeTts",
                group = TtsDebugGroup.Task,
                label = "${task.id} · phonemeTts",
                originalText = task.phonemeTts,
                sourceFile = "tasks.json",
            )
        }
        if (task is SentencePictureSpec) {
            entries += TtsDebugEntry(
                id = "task:${task.id}:instructionTts",
                group = TtsDebugGroup.Task,
                label = "${task.id} · instructionTts",
                originalText = task.instructionTts,
                sourceFile = "tasks.json",
            )
        }
        task.rounds.forEachIndexed { index, round ->
            val roundNumber = index + 1
            // Der Satz-Versteher nennt sein Rundenfeld `sentenceTts` (anderes
            // Synthese-Profil, siehe SentencePictureRound). Die Debug-IDs folgen der
            // Pipeline-Konvention `task:{id}:round:{n}:{feld}`, also muss der Name hier
            // mitziehen — sonst zeigt der Screen eine ID, die es im Clip-Plan nicht gibt.
            val roundField = if (round is SentencePictureRound) "sentenceTts" else "promptTts"
            entries += TtsDebugEntry(
                id = "task:${task.id}:round:$index:$roundField",
                group = TtsDebugGroup.Task,
                label = "${task.id} · round $roundNumber · $roundField",
                originalText = round.promptTts,
                sourceFile = "tasks.json",
            )
            when (round) {
                is SoundPositionRound -> entries += TtsDebugEntry(
                    id = "task:${task.id}:round:$index:missTts",
                    group = TtsDebugGroup.Task,
                    label = "${task.id} · round $roundNumber · missTts",
                    originalText = round.missTts,
                    sourceFile = "tasks.json",
                )
                is LetterTraceRound -> entries += TtsDebugEntry(
                    id = "task:${task.id}:round:$index:rewardTts",
                    group = TtsDebugGroup.Task,
                    label = "${task.id} · round $roundNumber · rewardTts",
                    originalText = round.rewardTts,
                    sourceFile = "tasks.json",
                )
                is SyllableMergeRound -> entries += TtsDebugEntry(
                    id = "task:${task.id}:round:$index:stretchTts",
                    group = TtsDebugGroup.Task,
                    label = "${task.id} · round $roundNumber · stretchTts",
                    originalText = round.stretchTts,
                    sourceFile = "tasks.json",
                )
                else -> Unit
            }
        }
    }

    entries += TtsDebugEntry(
        id = "ui:huntPromptLetter",
        group = TtsDebugGroup.Task,
        label = "Jagd · Finde alle Buchstaben",
        originalText = SymbolHuntDerivation.PromptLetter,
        sourceFile = "SymbolHuntDerivation.kt",
    )
    entries += TtsDebugEntry(
        id = "ui:huntPromptLaut",
        group = TtsDebugGroup.Task,
        label = "Jagd · Finde alle Laute",
        originalText = SymbolHuntDerivation.PromptDigraph,
        sourceFile = "SymbolHuntDerivation.kt",
    )
    entries += TtsDebugEntry(
        id = "ui:huntPromptSilbe",
        group = TtsDebugGroup.Task,
        label = "Jagd · Finde alle Silben",
        originalText = SymbolHuntDerivation.PromptSyllable,
        sourceFile = "SymbolHuntDerivation.kt",
    )

    return entries
}
