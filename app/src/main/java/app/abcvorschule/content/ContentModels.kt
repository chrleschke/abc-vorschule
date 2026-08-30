package app.abcvorschule.content

import kotlinx.serialization.SerialName
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
    /** Single grapheme, upper/lower case pair (M, A). */
    letter,

    /** Multi-letter grapheme spoken as one sound (Ei, Au, Sch, ck). */
    digraph,
    syllable,
    word,

    /** Picture-only vocabulary used for listening/counting, never read or spelled. */
    other,
}

/** Grammatisches Genus eines Substantiv-Atoms. */
@Serializable
enum class Gender { m, f, n }

/** Wie ein Substantiv beim Vorsprechen seinen Artikel bekommt. */
@Serializable
enum class NounClass {
    /** Gegenstand, Tier, Pflanze, Abstraktum — bestimmter Artikel. */
    thing,

    /** Personenbezeichnung (Oma, Opa, Clown) — unbestimmter Artikel, Neutrum ausgenommen. */
    person,

    /**
     * Eigenname (Tom, Mimi) — kein Artikel.
     *
     * Heißt in Kotlin `properName`, weil `name` mit `kotlin.Enum.name` kollidiert;
     * der JSON-Wert bleibt `"name"`. Gleiches `@SerialName`-Muster wie bei
     * `sentenceTts`/`promptTts` in `TaskSpecs.kt`.
     */
    @SerialName("name")
    properName,
}

/**
 * One pen stroke of a glyph, as normalized points in a 0..1 box, y pointing down.
 * Stroke order and point order encode the writing direction taught in Trainer 2.
 */
@Serializable
data class GlyphStroke(val points: List<List<Double>>)

@Serializable
data class Atom(
    val id: String,
    val lemma: String,
    val display: String,
    val emoji: String,
    val kind: AtomKind = AtomKind.word,
    val pluralDisplay: String? = null,
    /**
     * Der Plural-Endungsteil ("Rosen" → "n"), an 46 Atomen kuratiert, aber derzeit
     * **ohne Leser**: Rechnen zeigt bewusst keine Wörter, und kein anderer Trainer
     * hebt die Endung hervor. Bleibt stehen, weil Löschen die 46 kuratierten Werte
     * in atoms.json entwerten würde — nicht, weil es jemand benutzt.
     */
    val pluralHighlight: String? = null,
    /** Genus; gesetzt für [NounClass.thing] und [NounClass.person], null bei Namen. */
    val gender: Gender? = null,
    /** Gesetzt an jedem Substantiv-Atom; null heißt „kein Substantiv". */
    val nounClass: NounClass? = null,
    /** Fertiger Artikel-Sprechtext, wenn die Ableitung nicht passt (Plural-Atome). */
    val articleSpeechOverride: String? = null,
    /** Uppercase glyph strokes; required for atoms used by a letter_trace round. */
    val strokes: List<GlyphStroke> = emptyList(),
)

@Serializable
data class AtomsFile(val atoms: List<Atom>)

@Serializable
data class Sentence(
    val id: String,
    val atomIds: List<String>,
    val tts: String,
    /** Rendered word forms when they differ from the atom display (inflection, punctuation). */
    val displayOverride: List<String>? = null,
)

@Serializable
data class SentencesFile(val sentences: List<Sentence>)

/**
 * Der Belohnungssatz einer Lektion: wird beim Abschluss vorgelesen und als Bildreihe
 * visualisiert. Anders als [Sentence] ist er nicht baubar — er enthält bewusst Wörter
 * außerhalb des Atom-Graphen (Verben, Adjektive), weil sie nie gelesen oder gebaut
 * werden müssen. Nur die bildtragenden Nomen sind Atome.
 */
@Serializable
data class LessonFinale(
    val id: String,
    /** Schriftbild für den mitlesenden Erwachsenen. */
    val text: String,
    /** Was TTS spricht; kann von [text] abweichen (Betonung, Satzzeichen). */
    val tts: String,
    /** Nomen-Atome in Satzreihenfolge; jedes muss ein Emoji tragen. */
    val pictureAtomIds: List<String>,
)

@Serializable
data class FinalesFile(val finales: List<LessonFinale>)

data class ContentPack(
    val manifest: PackManifest,
    val atoms: Map<String, Atom>,
    val sentences: Map<String, Sentence>,
    val tasks: Map<String, TaskSpec>,
    val finales: Map<String, LessonFinale>,
    val lessons: List<Lesson>,
) {
    val authoredLessons: List<Lesson> = lessons.filter { it.status == LessonStatus.authored }

    fun atom(id: String): Atom = atoms.getValue(id)

    fun sentence(id: String): Sentence = sentences.getValue(id)

    fun finale(id: String): LessonFinale = finales.getValue(id)

    fun task(id: String): TaskSpec = tasks.getValue(id)

    fun lesson(id: String): Lesson = lessons.first { it.id == id }

    /**
     * Das Finale einer Lektion, oder null. Bewusst tolerant, wo [lesson] wirft: ein
     * veralteter Resume-Snapshot darf den Abschluss-Übergang nicht zum Absturz bringen.
     */
    fun finaleIdOf(lessonId: String): String? =
        lessons.firstOrNull { it.id == lessonId }?.finaleId

    fun tasksOf(lesson: Lesson): List<TaskSpec> = lesson.taskIds.map { task(it) }

    /** Rendered words of a sentence, aligned with [Sentence.atomIds]. */
    fun sentenceWords(sentence: Sentence): List<String> =
        sentence.displayOverride ?: sentence.atomIds.map { atom(it).display }
}
