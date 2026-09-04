package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundPairsTest {
    private val pack = ContentRepository.fromClasspath().load()

    // --- Segmentierung -------------------------------------------------------

    @Test
    fun multiLetterUnitsStayTogether() {
        assertEquals(listOf("sch", "u", "h"), SoundPairs.segments("Schuh"))
        assertEquals(listOf("st", "e", "r", "n"), SoundPairs.segments("Stern"))
        assertEquals(listOf("pf", "e", "r", "d"), SoundPairs.segments("Pferd"))
        assertEquals(listOf("f", "eu", "e", "r"), SoundPairs.segments("Feuer"))
        assertEquals(listOf("b", "ie", "n", "e"), SoundPairs.segments("Biene"))
    }

    @Test
    fun onsetIsTheFirstSegmentOnly() {
        assertTrue(SoundPairs.startsWith("Sonne", "S"))
        assertFalse(SoundPairs.startsWith("Schuh", "S"))
        assertFalse(SoundPairs.startsWith("Stern", "S"))
        assertFalse(SoundPairs.startsWith("Pferd", "P"))
        assertTrue(SoundPairs.startsWith("Pferd", "Pf"))
        assertTrue(SoundPairs.startsWith("Eule", "Eu"))
    }

    @Test
    fun containsSeesEveryPosition() {
        assertTrue(SoundPairs.contains("Zahnbürste", "S")) // mittleres st ist [st]
        assertTrue(SoundPairs.contains("Fußball", "S")) // ß zählt als S
        assertTrue(SoundPairs.contains("Nuss", "S"))
        assertTrue(SoundPairs.contains("Sonnenblume", "B"))
        assertFalse(SoundPairs.contains("Feuer", "U")) // eu ist kein u
        assertFalse(SoundPairs.contains("Eis", "I")) // ei ist kein i
        assertTrue(SoundPairs.contains("Biene", "I")) // ie ist ein i
        assertTrue(SoundPairs.contains("Kopfhörer", "Ö"))
        assertTrue(SoundPairs.contains("Lampe", "L")) // Anlaut zählt auch als enthalten
        assertFalse(SoundPairs.contains("Stern", "S")) // Anlaut-st ist [ʃt]
        assertFalse(SoundPairs.contains("Spinne", "S"))
        assertFalse(SoundPairs.contains("Schuh", "S")) // sch enthält kein s
        assertFalse(SoundPairs.contains("Pferd", "P"))
    }

    @Test
    fun restDropsTheOnsetSegment() {
        assertEquals(SoundPairs.rest("Fisch"), SoundPairs.rest("Tisch"))
        assertEquals(SoundPairs.rest("Kanne"), SoundPairs.rest("Tanne"))
        assertEquals("erd", SoundPairs.rest("Pferd"))
    }

    // --- Kartentauglichkeit --------------------------------------------------

    @Test
    fun emojiClustersAreCountedAsAChildSeesThem() {
        assertEquals(1, SoundPairs.emojiClusterCount("🐜"))
        assertEquals(1, SoundPairs.emojiClusterCount("🏘️")) // Variation Selector
        assertEquals(1, SoundPairs.emojiClusterCount("👦🏽")) // Hautton
        assertEquals(1, SoundPairs.emojiClusterCount("🐻‍❄️")) // ZWJ-Sequenz
        assertEquals(1, SoundPairs.emojiClusterCount("🧑‍🤝‍🧑"))
        assertEquals(2, SoundPairs.emojiClusterCount("🌳🌳"))
        assertEquals(2, SoundPairs.emojiClusterCount("🥚🥚"))
        assertEquals(0, SoundPairs.emojiClusterCount(""))
    }

    @Test
    fun onlyPictureNounsWithOneEmojiQualify() {
        assertTrue(SoundPairs.isCardWorthy(pack.atom("ameise")))
        assertTrue(SoundPairs.isCardWorthy(pack.atom("mama")))
        assertTrue(SoundPairs.isCardWorthy(pack.atom("tom"))) // Name ist ein Substantiv
        assertFalse(SoundPairs.isCardWorthy(pack.atom("gelb")))
        assertFalse(SoundPairs.isCardWorthy(pack.atom("ich")))
        assertFalse(SoundPairs.isCardWorthy(pack.atom("baeume")))
        assertFalse(SoundPairs.isCardWorthy(pack.atom("letter-s")))
        assertFalse(SoundPairs.isCardWorthy(pack.atom("giraffe")))
    }

    // --- Tabelle und Pack-Bezug ---------------------------------------------

    @Test
    fun everyPairInTheTableNamesTwoLetterAtomsOfThePack() {
        SoundPairs.table.forEach { pair ->
            assertNotNull("no letter atom for ${pair.left}", SoundPairs.letterAtom(pack, pair.left))
            assertNotNull("no letter atom for ${pair.right}", SoundPairs.letterAtom(pack, pair.right))
        }
    }

    @Test
    fun onlyVowelPairsMatchAnywhereInTheWord() {
        assertTrue(SoundPairs.table.filter { it.tier == SoundPairTier.Vowel }.all { it.anywhere })
        assertTrue(SoundPairs.table.filter { it.tier != SoundPairTier.Vowel }.none { it.anywhere })
    }

    @Test
    fun introductionIndexIsTheFirstLessonThatFocusesTheGrapheme() {
        assertEquals(7, SoundPairs.introductionIndex(pack, "S"))
        assertEquals(13, SoundPairs.introductionIndex(pack, "Sch"))
        assertEquals(17, SoundPairs.introductionIndex(pack, "St"))
        assertEquals(null, SoundPairs.introductionIndex(pack, "Ng"))
    }
}
