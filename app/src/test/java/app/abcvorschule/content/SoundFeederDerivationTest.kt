package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundFeederDerivationTest {
    private val pack = ContentRepository.fromClasspath().load()
    private val rounds = SoundFeederDerivation.derive(pack)
    private val assignments = SoundFeederDerivation.assignments(pack)

    private fun pairOf(lessonId: String) = assignments.getValue(lessonId).pair.toString()

    // --- Snapshot der Zuordnung (design doc Anhang) --------------------------

    @Test
    fun theAssignmentMatchesTheDesignAppendix() {
        val expected = mapOf(
            "l03" to "P/T", "l04" to "L/H", "l05" to "F/T", "l06" to "M/N", "l07" to "S/T",
            "l08" to "D/T", "l09" to "F/W", "l10" to "G/K", "l11" to "W/B", "l12" to "Ö/Ü",
            "l13" to "S/Sch", "l14" to "S/Z", "l15" to "B/P", "l16" to "Pf/F", "l17" to "St/Sp",
            "l18" to "K/T", "l19" to "M/N", "l20" to "I/O", "l21" to "D/T", "l22" to "Ei/Au",
            "l23" to "S/Sch", "l24" to "St/Sp", "l25" to "Ö/Ü", "l26" to "L/R", "l27" to "S/Sch",
            "l28" to "G/K", "l29" to "W/B", "l30" to "Ei/Eu", "l31" to "M/N", "l32" to "S/Z",
            "l33" to "B/P", "l34" to "L/R",
        )
        assertEquals(expected, assignments.mapValues { it.value.pair.toString() })
    }

    @Test
    fun theFirstTwoLessonsHaveNoFeeder() {
        assertNull(assignments["l01"])
        assertNull(assignments["l02"])
        assertTrue(SoundFeederDerivation.buildRounds(pack, pack.lesson("l01")).isEmpty())
    }

    @Test
    fun replaysAreMarked() {
        assertTrue(assignments.getValue("l15").replay)
        assertTrue(assignments.getValue("l18").replay)
        assertTrue(assignments.getValue("l26").replay)
        assertTrue(assignments.values.count { it.replay } == 3)
    }

    @Test
    fun theLeastRecentlyPlayedPairWinsAmongPlayedCandidates() {
        // L21 (P & T) könnte B/P, D/T oder K/T spielen; B/P lief in L15, D/T in L08.
        assertEquals("D/T", pairOf("l21"))
        // L30 (Ei) könnte Ei/Au oder Ei/Eu; Ei/Au lief in L22.
        assertEquals("Ei/Eu", pairOf("l30"))
    }

    // --- Karten -------------------------------------------------------------

    @Test
    fun everyCardSitsOnTheSideWhoseSoundItCarriesAndNeverCarriesTheOther() {
        rounds.forEach { (lessonId, round) ->
            val pair = assignments.getValue(lessonId).pair
            round.cards.forEach { card ->
                val word = pack.atom(card.atomId).display
                val own = if (card.side == FeederSide.left) pair.left else pair.right
                val other = if (card.side == FeederSide.left) pair.right else pair.left
                val matches = if (pair.anywhere) SoundPairs.contains(word, own) else SoundPairs.startsWith(word, own)
                assertTrue("$lessonId: $word is not a $own word", matches)
                assertTrue("$lessonId: $word contains the partner $other", !SoundPairs.contains(word, other))
            }
        }
    }

    @Test
    fun everyCardIsAPictureNounWithOneEmojiAndNoEmojiRepeatsInARound() {
        rounds.forEach { (lessonId, round) ->
            val emojis = round.cards.map { pack.atom(it.atomId).emoji }
            assertEquals("$lessonId repeats an emoji", emojis.size, emojis.toSet().size)
            round.cards.forEach { assertTrue("$lessonId: ${it.atomId}", SoundPairs.isCardWorthy(pack.atom(it.atomId))) }
        }
    }

    @Test
    fun roundsHaveAtMostSevenCardsAndAtLeastTwoPerSide() {
        rounds.forEach { (lessonId, round) ->
            assertTrue("$lessonId has ${round.cards.size} cards", round.cards.size <= SoundFeederDerivation.MaxCards)
            FeederSide.entries.forEach { side ->
                assertTrue("$lessonId: too few $side cards", round.cards.count { it.side == side } >= 2)
            }
        }
        assertEquals(6, rounds.getValue("l17").cards.size) // St/Sp: 4 + 2 im Vorrat
    }

    @Test
    fun twinsSitNextToEachOther() {
        val l05 = rounds.getValue("l05").cards.map { pack.atom(it.atomId).display }
        val fisch = l05.indexOf("Fisch")
        val tisch = l05.indexOf("Tisch")
        assertTrue("Fisch/Tisch missing in $l05", fisch >= 0 && tisch >= 0)
        assertEquals(1, kotlin.math.abs(fisch - tisch))
        val l18 = rounds.getValue("l18").cards.map { pack.atom(it.atomId).display }
        assertEquals(1, kotlin.math.abs(l18.indexOf("Kanne") - l18.indexOf("Tanne")))
    }

    @Test
    fun derivationIsDeterministicButRotatesBetweenLessons() {
        assertEquals(rounds, SoundFeederDerivation.derive(pack))
        val l13 = rounds.getValue("l13").cards.map { it.atomId }.toSet()
        val l23 = rounds.getValue("l23").cards.map { it.atomId }.toSet()
        assertNotEquals(l13, l23)
    }

    @Test
    fun everyNewAtomEndsUpOnACard() {
        val shown = SoundFeederDerivation.shownAtomIds(pack)
        listOf("turm", "wurm", "kanne", "tanne", "wanne", "moehre", "muetze", "pfanne", "pfeil", "ziege")
            .forEach { assertTrue("$it never shown", it in shown) }
    }

    @Test
    fun vowelRoundsCarryTheAnywhereFlagConsonantRoundsDoNot() {
        assertFalse(rounds.getValue("l13").anywhere) // S/Sch
        assertTrue(rounds.getValue("l22").anywhere) // Ei/Au
    }

    @Test
    fun theRoundCarriesTheLetterAtomsAndThePrompt() {
        val round = rounds.getValue("l13")
        assertEquals("letter-s", round.leftAtomId)
        assertEquals("letter-sch", round.rightAtomId)
        assertEquals(SoundFeederDerivation.Prompt, round.promptTts)
        // Aufforderung, keine Frage — nur Rechnen fragt (§7).
        assertTrue(SoundFeederDerivation.Prompt.endsWith("."))
    }

    @Test
    fun splitCountsKeepBothSidesAtTwoAndFollowTheSupply() {
        assertEquals(2 to 5, SoundFeederDerivation.splitCounts(7, 6, 18))
        assertEquals(4 to 3, SoundFeederDerivation.splitCounts(7, 9, 8))
        assertEquals(5 to 2, SoundFeederDerivation.splitCounts(7, 17, 2))
        assertEquals(4 to 2, SoundFeederDerivation.splitCounts(6, 4, 2))
    }

    @Test
    fun aPairWithoutEnoughWordsIsNeverAssigned() {
        // Synthetisch: alle Z-Wörter bis auf eines entfernen — S/Z darf dann nirgends
        // mehr gewählt werden.
        val zWords = pack.atoms.values.filter { SoundPairs.isCardWorthy(it) && SoundPairs.startsWith(it.display, "Z") }
        val thinned = pack.copy(atoms = pack.atoms - zWords.drop(1).map { it.id })
        assertTrue(SoundFeederDerivation.assignments(thinned).values.none { it.pair.toString() == "S/Z" })
    }

    @Test
    fun aLessonWhoseCardsCollapseToOneEmojiPerSideGetsNoFeederInsteadOfAnException() {
        // Der Vorrat zählt Wörter, die Runde zählt Emojis: hier tragen alle Sch-Wörter
        // dieselbe Glyphe, S/Sch bleibt also „ausreichend", liefert aber nur eine
        // einzige rechte Karte. Statt in SoundFeederRound.init zu werfen, lässt die
        // Ableitung die betroffenen Lektionen still aus (design doc §3.4).
        val schWords = pack.atoms.values.filter { SoundPairs.isCardWorthy(it) && SoundPairs.startsWith(it.display, "Sch") }
        assertTrue("Vorrat zu klein für den Testaufbau", schWords.size >= 2)
        val collapsed = pack.copy(
            atoms = pack.atoms + schWords.associate { it.id to it.copy(emoji = "👟") },
        )
        val derived = SoundFeederDerivation.derive(collapsed)
        // L13, L23 und L27 spielen S/Sch — genau die fallen weg, der Rest bleibt.
        listOf("l13", "l23", "l27").forEach { assertNull("$it sollte ohne Fresser sein", derived[it]) }
        assertNotNull(derived["l14"])
        assertTrue(derived.values.all { round -> round.cards.count { it.side == FeederSide.right } >= 2 })
    }
}
