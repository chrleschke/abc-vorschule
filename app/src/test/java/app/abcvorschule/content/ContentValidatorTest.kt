package app.abcvorschule.content

import org.junit.Assert.assertTrue
import org.junit.Test

class ContentValidatorTest {
    @Test
    fun validMiniPackPasses() {
        val pack = ContentRepository.fromClasspath().load()
        val issues = ContentValidator.validate(pack)
        assertTrue(issues.joinToString(), issues.isEmpty())
    }

    @Test
    fun missingAtomRefFails() {
        val pack = ContentRepository.fromClasspath().load()
        val broken = pack.copy(
            tasks = pack.tasks + TaskTemplate(
                id = "broken",
                domain = Domain.reading,
                type = TaskType.cloze,
                atomId = "does-not-exist",
                promptTts = "x",
                slots = listOf("does-not-exist"),
            ),
        )
        val issues = ContentValidator.validate(broken)
        assertTrue(issues.any { it.message.contains("does-not-exist") })
    }

    @Test
    fun domainBelowMinimumFails() {
        val pack = ContentRepository.fromClasspath().load()
        val stripped = pack.copy(tasks = pack.tasks.filter { it.domain != Domain.speech }.take(2))
        val issues = ContentValidator.validate(stripped)
        assertTrue(issues.any { it.message.contains("domain") })
    }
}
