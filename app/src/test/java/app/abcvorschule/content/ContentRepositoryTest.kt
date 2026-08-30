package app.abcvorschule.content

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentRepositoryTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun packLoadsThirtyFourAuthoredLessons() {
        assertEquals(34, pack.lessons.size)
        assertEquals(34, pack.authoredLessons.size)
    }

    @Test
    fun schemaVersionIsTwo() {
        assertEquals(2, pack.manifest.schemaVersion)
    }

    @Test
    fun lessonOneIsMundA() {
        val lesson = pack.lesson("l01")
        assertEquals(1, lesson.index)
        assertEquals(listOf("letter-m", "letter-a"), lesson.focusAtomIds)
        assertTrue(lesson.taskIds.isNotEmpty())
    }

    @Test
    fun polymorphicTasksDeserializeToTheirTrainerType() {
        val tasks = pack.tasksOf(pack.lesson("l01"))
        assertTrue(tasks.first() is LetterTraceSpec)
        assertTrue(tasks.last() is CountAddSpec)
        assertTrue(tasks.any { it is LetterTraceSpec })
    }

    @Test
    fun atomsAreSharedAcrossTrainers() {
        // AE6 in new clothes: one atom, one emoji, reused by several trainers.
        val ma = pack.atom("ma")
        assertEquals("ma", ma.display)
        val merge = pack.tasksOf(pack.lesson("l01")).filterIsInstance<SyllableMergeSpec>().first()
        val build = pack.tasksOf(pack.lesson("l01")).filterIsInstance<WordBuildSpec>()
            .first { spec -> spec.rounds.any { it.blocks.any { block -> block.atomId == "ma" } } }
        assertEquals("ma", merge.rounds.first().resultAtomId)
        assertTrue(build.rounds.any { it.blocks.any { it.atomId == "ma" } })
    }

    @Test
    fun traceRoundsResolveStrokeDataFromAtoms() {
        pack.tasksOf(pack.lesson("l01")).filterIsInstance<LetterTraceSpec>().forEach { trace ->
            trace.rounds.forEach { round ->
                val atom = pack.atom(round.atomId)
                assertTrue("${atom.id} needs strokes", atom.strokes.isNotEmpty())
            }
        }
        assertNotNull(pack.atom("letter-a").strokes.firstOrNull())
    }

    @Test
    fun countAddRoundsUseLessonContextIcons() {
        pack.tasksOf(pack.lesson("l01")).filterIsInstance<CountAddSpec>().forEach { math ->
            math.rounds.forEach { round ->
                // Ohne Bildwort zeigt die Aufgabe nur Ziffern; steht eins da,
                // muss es auflösen und ein Emoji tragen.
                round.iconAtomId?.let { icon ->
                    assertTrue(icon in pack.atoms.keys)
                    assertTrue(pack.atom(icon).emoji.isNotBlank())
                }
            }
        }
    }

    @Test
    fun duplicateIdsFailParsingInsteadOfSilentlyKeepingTheLastOne() {
        // `associateBy` is last-wins: without the parse-time check a duplicated id
        // would shadow its first definition and the validator could never see it.
        val json = Json { ignoreUnknownKeys = true }
        val classLoader = Thread.currentThread().contextClassLoader!!
        val shippedAtoms = json.decodeFromString<AtomsFile>(
            classLoader.getResourceAsStream("content/atoms.json")!!
                .bufferedReader().use { it.readText() },
        ).atoms
        val doubledId = shippedAtoms.first().id
        val doubled = json.encodeToString(AtomsFile(shippedAtoms + shippedAtoms.first()))
        val repository = ContentRepository { path ->
            if (path == "content/atoms.json") {
                doubled.byteInputStream()
            } else {
                classLoader.getResourceAsStream(path) ?: error("Missing classpath resource: $path")
            }
        }
        val failure = runCatching { repository.load() }.exceptionOrNull()
        assertTrue("expected ContentValidationException, got $failure", failure is ContentValidationException)
        val messages = (failure as ContentValidationException).issues.map { it.message }
        assertTrue(
            messages.toString(),
            messages.any { it.contains("duplicate id") && it.contains(doubledId) && it.contains("atoms") },
        )
    }

    @Test
    fun finalesAreParsedFromTheirOwnFile() {
        val pack = ContentRepository.fromClasspath().load()
        val finale = pack.finale("f-l01")
        assertEquals("Mama Maus mampft einen dicken Apfel!", finale.text)
        assertEquals(listOf("mama", "maus", "apfel"), finale.pictureAtomIds)
    }

    @Test
    fun finalePicturesResolveToAtomsWithEmojis() {
        val pack = ContentRepository.fromClasspath().load()
        val emojis = pack.finale("f-l01").pictureAtomIds.map { pack.atom(it).emoji }
        assertEquals(listOf("👩", "🐭", "🍎"), emojis)
    }
}
