package app.abcvorschule.content

import kotlinx.serialization.Serializable

@Serializable
enum class LessonStatus {
    /** Fully authored: six trainer types in non-decreasing rank, playable. */
    authored,

    /** Declared in the Fibel order, content still to come. Shown locked on the path. */
    planned,
}

@Serializable
data class Lesson(
    val id: String,
    /** 1-based position in the Fibel order; also the path node order. */
    val index: Int,
    /** Curriculum phase 1..5, used for path grouping only. */
    val phase: Int,
    /** Parent-facing label, e.g. "M & A". Never read aloud to the child as an instruction. */
    val title: String,
    /** Minimal path node label: a letter, a syllable, or a word pair. */
    val nodeLabel: String,
    val status: LessonStatus,
    val focusAtomIds: List<String> = emptyList(),
    /** Trainer kinds in non-decreasing ContentValidator.TrainerOrder rank when
     * authored — a kind may repeat or be skipped, but the sequence never goes
     * backward, always starts with sound_position, and always ends with count_add. */
    val taskIds: List<String> = emptyList(),
)

@Serializable
data class LessonsFile(val lessons: List<Lesson>)
