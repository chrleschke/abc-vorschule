package app.abcvorschule.content

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * One playable step inside a trainer. Every round carries its own spoken prompt.
 * Sealed so that `when` over round types is exhaustive: adding a seventh trainer
 * must break the build at every dispatch site rather than silently no-op.
 */
sealed interface TrainerRound {
    val promptTts: String
}

/**
 * A trainer instance in a lesson. One spec = one screen type played over 1..n rounds;
 * the JSON discriminator is the trainer name, so content stays readable.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("trainer")
sealed interface TaskSpec {
    val id: String
}

enum class TrainerKind {
    sound_position,
    letter_trace,
    syllable_merge,
    word_build,
    sentence_order,
    count_add,
    symbol_hunt,
}

// --- Trainer 1: Auditiver Finder --------------------------------------------

@Serializable
@SerialName("sound_position")
data class SoundPositionSpec(
    override val id: String,
    /** Spoken form of the hunted phoneme, e.g. "Mmm". */
    val phonemeTts: String,
    val rounds: List<SoundPositionRound>,
) : TaskSpec

@Serializable
data class SoundPositionRound(
    override val promptTts: String,
    /** Picture-word the child sorts; rendered as emoji only, never as text. */
    val atomId: String,
    val slot: SoundSlot,
    /** Segmented re-reading used on a miss, e.g. "A - Mmm - eise". */
    val missTts: String,
) : TrainerRound

// --- Trainer 2: Visueller Spurensucher --------------------------------------

@Serializable
@SerialName("letter_trace")
data class LetterTraceSpec(
    override val id: String,
    val rounds: List<LetterTraceRound>,
) : TaskSpec

@Serializable
data class LetterTraceRound(
    override val promptTts: String,
    /** Atom carrying the [Atom.strokes] to trace. */
    val atomId: String,
    /** Uppercase glyph drawn as the road, e.g. "A". */
    val glyph: String,
    /** Spoken reward after the glyph is complete, e.g. "A wie Ampel". */
    val rewardTts: String,
    /** Emoji the road morphs into. Reward visual only — never a button label. */
    val rewardEmoji: String,
) : TrainerRound

// --- Trainer 3: Silben-Verschmelzer -----------------------------------------

@Serializable
@SerialName("syllable_merge")
data class SyllableMergeSpec(
    override val id: String,
    val rounds: List<SyllableMergeRound>,
) : TaskSpec

@Serializable
data class SyllableMergeRound(
    override val promptTts: String,
    val leftAtomId: String,
    val leftDisplay: String,
    val rightAtomId: String,
    val rightDisplay: String,
    val resultAtomId: String,
    val resultDisplay: String,
    /** Stretched consonant played while dragging, e.g. "Mmmmm". */
    val stretchTts: String,
) : TrainerRound

// --- Trainer 4: Wort-Bauer --------------------------------------------------

@Serializable
data class WordBlock(val atomId: String, val display: String)

@Serializable
@SerialName("word_build")
data class WordBuildSpec(
    override val id: String,
    val rounds: List<WordBuildRound>,
) : TaskSpec

@Serializable
data class WordBuildRound(
    override val promptTts: String,
    val targetAtomId: String,
    /** Solution blocks in reading order; their displays must spell the target. */
    val blocks: List<WordBlock>,
    /** Extra tray tiles from already-practiced atoms. Empty on first encounter. */
    val distractors: List<WordBlock> = emptyList(),
) : TrainerRound

// --- Trainer 5: Satz-Architekt ----------------------------------------------

@Serializable
@SerialName("sentence_order")
data class SentenceOrderSpec(
    override val id: String,
    val rounds: List<SentenceOrderRound>,
) : TaskSpec

@Serializable
data class SentenceOrderRound(
    override val promptTts: String,
    val sentenceId: String,
    /** Illustration anchoring the sentence; emoji of this atom. */
    val illustrationAtomId: String? = null,
    val distractors: List<WordBlock> = emptyList(),
    /**
     * Words the child recognizes as a whole picture-word although its graphemes
     * are not taught yet (the curriculum does this for "Tor" in lesson 3, before
     * R is introduced). Documents the exception instead of hiding it.
     */
    val holisticAtomIds: List<String> = emptyList(),
) : TrainerRound

// --- Trainer 6: Rechnen -----------------------------------------------------

@Serializable
@SerialName("count_add")
data class CountAddSpec(
    override val id: String,
    val rounds: List<CountAddRound>,
) : TaskSpec

/**
 * Pure quantity arithmetic. No words are shown or built here — singular/plural
 * lives in [promptTts] only, and the counted objects come from the lesson's own
 * picture vocabulary so the icons stay in context.
 */
@Serializable
data class CountAddRound(
    override val promptTts: String,
    val iconAtomId: String,
    val left: Int,
    val right: Int,
    val answer: Int,
    val operation: String = "add",
    val difficultyBand: String? = null,
) : TrainerRound

// --- Buchstaben-/Silben-Jagd — derived at runtime, never authored --------------

enum class SymbolHuntMode { letter, syllable }

/**
 * Never appears in authored JSON — [SessionViewModel]'s SymbolHuntInsertion
 * derives instances at runtime from a lesson's own letter_trace/syllable_merge
 * rounds (design doc §2). Still `@Serializable`/`@SerialName` because TaskSpec
 * is a kotlinx.serialization sealed hierarchy — every member needs both for the
 * polymorphic parent to compile, even members that are never deserialized.
 */
@Serializable
@SerialName("symbol_hunt")
data class SymbolHuntSpec(override val id: String, val rounds: List<SymbolHuntRound>) : TaskSpec

@Serializable
data class SymbolHuntRound(
    override val promptTts: String,
    val targetAtomId: String,
    val mode: SymbolHuntMode,
    /** Resolved once at derivation time — see SymbolHuntDerivation.distractorPool. */
    val distractorPool: List<String>,
) : TrainerRound

@Serializable
data class TasksFile(val tasks: List<TaskSpec>)

val TaskSpec.kind: TrainerKind
    get() = when (this) {
        is SoundPositionSpec -> TrainerKind.sound_position
        is LetterTraceSpec -> TrainerKind.letter_trace
        is SyllableMergeSpec -> TrainerKind.syllable_merge
        is WordBuildSpec -> TrainerKind.word_build
        is SentenceOrderSpec -> TrainerKind.sentence_order
        is CountAddSpec -> TrainerKind.count_add
        is SymbolHuntSpec -> TrainerKind.symbol_hunt
    }

val TaskSpec.rounds: List<TrainerRound>
    get() = when (this) {
        is SoundPositionSpec -> rounds
        is LetterTraceSpec -> rounds
        is SyllableMergeSpec -> rounds
        is WordBuildSpec -> rounds
        is SentenceOrderSpec -> rounds
        is CountAddSpec -> rounds
        is SymbolHuntSpec -> rounds
    }

val TaskSpec.roundCount: Int get() = rounds.size

fun TaskSpec.round(index: Int): TrainerRound? = rounds.getOrNull(index)

/**
 * Spoken answer for a counting round: "1 Ameise" / "2 Ameisen" — never a bare
 * number. Rechnen shows no words, so the plural is carried entirely by speech.
 */
fun CountAddRound.spokenAnswer(icon: Atom?): String {
    val noun = when {
        icon == null -> ""
        answer == 1 -> icon.display
        else -> icon.pluralDisplay ?: icon.display
    }
    return "$answer $noun".trim()
}

/** Atom ids a round scores against, used for per-atom stats and scaffolds. */
fun TrainerRound.scoredAtomIds(): List<String> = when (this) {
    is SoundPositionRound -> listOf(atomId)
    is LetterTraceRound -> listOf(atomId)
    is SyllableMergeRound -> listOf(leftAtomId, rightAtomId, resultAtomId).distinct()
    is WordBuildRound -> (blocks.map { it.atomId } + targetAtomId).distinct()
    // Sentence atom ids are only resolvable via the pack, so SessionViewModel fills
    // them in; count_add scores against a math key, not against atoms.
    is SentenceOrderRound -> emptyList()
    is CountAddRound -> emptyList()
    is SymbolHuntRound -> listOf(targetAtomId)
}
