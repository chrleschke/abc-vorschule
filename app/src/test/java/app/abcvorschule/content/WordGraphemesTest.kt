package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordGraphemesTest {
    private val pack = ContentRepository.fromClasspath().load()

    private fun indexOf(lessonId: String) = pack.lesson(lessonId).index

    @Test
    fun tableIsEmptyBeforeTheFirstMultiLetterGraphemeIsIntroduced() {
        // "Ei" (l09) is the curriculum's first multi-letter grapheme, so nothing
        // before it may fuse two characters into one segment.
        assertTrue(WordGraphemes.table(pack, indexOf("l07")).isEmpty())
    }

    /** Every grapheme traced up to and including [lessonId], in curriculum order. */
    private fun tracedUpTo(lessonId: String): List<Atom> =
        pack.lessons.filter { it.index <= indexOf(lessonId) }
            .flatMap { pack.tasksOf(it) }
            .filterIsInstance<LetterTraceSpec>()
            .flatMap { spec -> spec.rounds.map { pack.atom(it.atomId) } }

    @Test
    fun tableHoldsEveryIntroducedMultiLetterGraphemeByTheLastLesson() {
        val traced = tracedUpTo("l18")
        val table = WordGraphemes.table(pack, indexOf("l18"))
        assertEquals(
            traced.filter { it.display.length > 1 }.map { it.display }.toSet(),
            table.toSet(),
        )
    }

    @Test
    fun theFirstEighteenLessonsTeachEveryDigraphOfTheFibel() {
        // Checked by atom id, not by display form: a display may carry a spelling
        // trick for the speech output (the Eu-grapheme ships as "Oy"), and this
        // assertion is about the curriculum, not about that spelling.
        val tracedIds = tracedUpTo("l18").map { it.id }.toSet()
        listOf(
            "letter-ei", "letter-ch", "letter-au", "letter-sch", "letter-eu",
            "letter-ck", "letter-pf", "letter-st", "letter-sp", "letter-qu",
        ).forEach { assertTrue("$it must be traced by l18, got $tracedIds", it in tracedIds) }
    }

    @Test
    fun tableIsSortedLongestFirstSoLongestMatchWins() {
        val table = WordGraphemes.table(pack, indexOf("l18"))
        assertEquals(table.sortedByDescending { it.length }, table)
    }

    @Test
    fun stIsOneSegmentOnlyOnceItHasBeenIntroduced() {
        // The whole reason the table is lesson-scoped: l07 hunts the S in "Nest".
        assertEquals(listOf("N", "e", "s", "t"), WordGraphemes.split(pack, indexOf("l07"), "Nest"))
        assertEquals(listOf("St", "e", "r", "n"), WordGraphemes.split(pack, indexOf("l17"), "Stern"))
    }

    @Test
    fun pfIsASegmentWhichTheHardcodedSoundWordSegmentsTableCannotDo() {
        assertEquals(listOf("A", "pf", "e", "l"), WordGraphemes.split(pack, indexOf("l16"), "Apfel"))
    }

    @Test
    fun doubleVowelsStaySeparateBecauseTheyAreNotAtoms() {
        // "Erdbeere" -> E·r·d·b·e·e·r·e: a child hunting "all E" taps two separate
        // letters, not one fused "ee" block.
        assertEquals(
            listOf("E", "r", "d", "b", "e", "e", "r", "e"),
            WordGraphemes.split(pack, indexOf("l18"), "Erdbeere"),
        )
    }

    @Test
    fun umlautPlusVowelIsNotFusedBecauseAuDoesNotMatchAeu() {
        assertEquals(
            listOf("H", "ä", "u", "s", "e", "r"),
            WordGraphemes.split(pack, indexOf("l12"), "Häuser"),
        )
    }

    @Test
    fun longestMatchWinsOverAShorterPrefix() {
        assertEquals(listOf("Sch", "a", "f"), WordGraphemes.split(pack, indexOf("l13"), "Schaf"))
    }

    @Test
    fun matchingIsCaseInsensitive() {
        assertEquals(listOf("i", "ch"), WordGraphemes.split(pack, indexOf("l10"), "ich"))
    }

    @Test
    fun splitPreservesTheWordsOwnCasing() {
        // The segment carries the word's spelling, not the atom's display form.
        assertEquals(listOf("P", "a", "p", "a"), WordGraphemes.split(pack, indexOf("l03"), "Papa"))
    }
}
