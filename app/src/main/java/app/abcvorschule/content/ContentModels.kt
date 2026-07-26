package app.abcvorschule.content

import kotlinx.serialization.Serializable

@Serializable
data class PackManifest(
    val schemaVersion: Int,
    val packId: String,
    val title: String,
    val locale: String = "de",
)

@Serializable
enum class AtomKind {
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
