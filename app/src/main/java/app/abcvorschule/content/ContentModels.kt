package app.abcvorschule.content

import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class PackManifest(
    val schemaVersion: Int,
    val packId: String,
    val title: String,
    val locale: String = "de",
)

@Serializable
enum class AtomKind {
    letter,
    syllable,
    word,
    other,
}

@Serializable
data class Atom(
    val id: String,
    val lemma: String,
    val display: String,
    val emoji: String,
    val kind: AtomKind = AtomKind.word,
    val prerequisites: List<String> = emptyList(),
    val pluralDisplay: String? = null,
    val pluralHighlight: String? = null,
)

@Serializable
data class AtomsFile(val atoms: List<Atom>)

@Serializable
data class Sentence(
    val id: String,
    val atomIds: List<String>,
    val tts: String,
    val displayOverride: List<String>? = null,
)

@Serializable
data class SentencesFile(val sentences: List<Sentence>)

@Serializable
enum class Domain {
    reading,
    speech,
    math,
}

@Serializable
enum class TaskType {
    cloze,
    sentence_cloze,
    speech_cloze,
    visual_add,
    number_entry,
}

@Serializable
data class TaskTemplate(
    val id: String,
    val domain: Domain,
    val type: TaskType,
    val atomId: String? = null,
    val sentenceId: String? = null,
    val promptTts: String,
    val promptSymbols: String? = null,
    val slots: List<String> = emptyList(),
    val gapAtomIds: List<String> = emptyList(),
    /** Ordered atom ids for syllable/letter composition (supports duplicates, e.g. Ma⋅ma). */
    val composeParts: List<String> = emptyList(),
    /** Optional per-part display overrides aligned with [composeParts] (e.g. Ma, ma). */
    val composeDisplays: List<String> = emptyList(),
    val targetAtomId: String? = null,
    val tier: String? = null,
    val left: Int? = null,
    val right: Int? = null,
    val answer: Int? = null,
    val operation: String? = null,
    val difficultyBand: String? = null,
)

@Serializable
data class TasksFile(val tasks: List<TaskTemplate>)

data class ComposePart(
    val slotKey: String,
    val atomId: String,
    val display: String?,
)

fun TaskTemplate.resolvedGapAtomIds(): List<String> = when {
    composeParts.isNotEmpty() -> composeParts
    type == TaskType.sentence_cloze -> gapAtomIds
    type == TaskType.cloze || type == TaskType.speech_cloze -> slots.ifEmpty {
        listOfNotNull(targetAtomId ?: atomId)
    }
    else -> emptyList()
}

fun TaskTemplate.composePartsResolved(): List<ComposePart> {
    val ids = resolvedGapAtomIds()
    return ids.mapIndexed { index, atomId ->
        ComposePart(
            slotKey = "$atomId#$index",
            atomId = atomId,
            display = composeDisplays.getOrNull(index),
        )
    }
}

/** Letter tasks expose uppercase + lowercase as two slots/pieces. */
fun TaskTemplate.composePartsFor(atom: Atom?): List<ComposePart> {
    if (composeParts.isNotEmpty()) return composePartsResolved()
    if (tier == "letter" && atom != null && atom.kind == AtomKind.letter) {
        val (upper, lower) = atom.casePair()
        return listOf(
            ComposePart(slotKey = "${atom.id}#U", atomId = atom.id, display = upper),
            ComposePart(slotKey = "${atom.id}#L", atomId = atom.id, display = lower),
        )
    }
    return composePartsResolved()
}

fun Atom.casePair(): Pair<String, String> {
    val ch = lemma.trim().firstOrNull()?.toString() ?: display.trim().firstOrNull()?.toString() ?: "?"
    val locale = Locale.GERMAN
    return ch.uppercase(locale) to ch.lowercase(locale)
}

fun TaskTemplate.isComposeTask(): Boolean = composeParts.isNotEmpty() || tier == "compose"

/** Word built from individual letter frames (e.g. H·a·u·s). */
fun TaskTemplate.isSpellTask(): Boolean =
    tier == "spell" ||
        (
            composeParts.isNotEmpty() &&
                composeParts.all { it.startsWith("letter-") }
            )

fun TaskTemplate.isLetterTask(): Boolean = tier == "letter"

fun TaskTemplate.isSyllableTask(): Boolean = tier == "syllable"

fun TaskTemplate.tierRank(): Int = when (tier) {
    "letter" -> 0
    "syllable" -> 1
    "compose" -> 2
    "word" -> 3
    "sentence" -> 4
    else -> 5
}

data class ContentPack(
    val manifest: PackManifest,
    val atoms: Map<String, Atom>,
    val sentences: Map<String, Sentence>,
    val tasks: List<TaskTemplate>,
) {
    fun atom(id: String): Atom = atoms.getValue(id)

    fun sentence(id: String): Sentence = sentences.getValue(id)

    fun tasksFor(domain: Domain): List<TaskTemplate> = tasks.filter { it.domain == domain }
}
