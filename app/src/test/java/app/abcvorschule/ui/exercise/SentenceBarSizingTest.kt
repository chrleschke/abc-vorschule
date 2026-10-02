package app.abcvorschule.ui.exercise

import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.SentencePictureSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceBarSizingTest {

    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun punctuationDoesNotCount() {
        assertEquals(listOf("Oma", "hat", "Mama", "gerufen"), SentenceBarSizing.words("Oma hat Mama gerufen."))
        assertEquals(listOf("Da", "ist", "Tom"), SentenceBarSizing.words("Da – ist, Tom!"))
        // „Ball." ist so lang wie „Ball".
        assertEquals(
            SentenceBarSizing.barWidthDp("Ball", 300f),
            SentenceBarSizing.layout("Ball.", 300f).bars.single().widthDp,
            0f,
        )
    }

    @Test
    fun everyWordGetsExactlyOneBar() {
        val sentence = "Zwei Ameisen sind zu Mama gekrabbelt."
        assertEquals(6, SentenceBarSizing.layout(sentence, 300f).bars.size)
    }

    @Test
    fun longWordsAreClampedAndShortOnesKeepAMinimum() {
        assertEquals(SentenceBarSizing.MaxBarDp, SentenceBarSizing.barWidthDp("Schneemann", 300f), 0f)
        assertEquals(SentenceBarSizing.MinBarDp, SentenceBarSizing.barWidthDp("zu", 300f), 0f)
        // Dazwischen wächst die Länge mit dem Wort.
        assertTrue(SentenceBarSizing.barWidthDp("Mama", 300f) < SentenceBarSizing.barWidthDp("Ameise", 300f))
    }

    @Test
    fun noBarRunsOffThePage() {
        val widths = listOf(40f, 90f, 120f, 208f, 260f, 340f)
        val sentences = shippedSentences() + "Donaudampfschifffahrtsgesellschaft"
        widths.forEach { width ->
            sentences.forEach { sentence ->
                SentenceBarSizing.layout(sentence, width).bars.forEach { bar ->
                    assertTrue(
                        "„$sentence“ bei ${width}dp: Balken endet bei ${bar.xDp + bar.widthDp}",
                        bar.xDp >= 0f && bar.xDp + bar.widthDp <= width + 0.001f,
                    )
                }
            }
        }
    }

    @Test
    fun barsInARowDoNotOverlap() {
        shippedSentences().forEach { sentence ->
            val rows = SentenceBarSizing.layout(sentence, 240f).bars.groupBy { it.row }
            rows.values.forEach { row ->
                row.zipWithNext().forEach { (a, b) ->
                    assertTrue("„$sentence“", b.xDp >= a.xDp + a.widthDp + SentenceBarSizing.GapDp - 0.001f)
                }
            }
        }
    }

    @Test
    fun wrapsIntoMoreRowsWhenThePageIsNarrow() {
        val sentence = "Mateo und Lennard sind ins Baumhaus geklettert."
        val wide = SentenceBarSizing.layout(sentence, 1000f)
        val narrow = SentenceBarSizing.layout(sentence, 160f)
        assertTrue(narrow.rows > wide.rows)
        assertEquals(narrow.rows * SentenceBarSizing.BarHeightDp + (narrow.rows - 1) * SentenceBarSizing.RowGapDp, narrow.heightDp, 0f)
    }

    /** Eine Balkenreihe liest sich als Leiste; Text hat mindestens zwei Zeilen. */
    @Test
    fun aSentenceThatFitsInOneRowStillGetsTwo() {
        val sentence = "Oma hat Mama gerufen."
        val layout = SentenceBarSizing.layout(sentence, 1000f)
        assertEquals(2, layout.rows)
        assertEquals(4, layout.bars.size)
        // Die erste Zeile ist die längere, wie bei auslaufendem Text.
        val byRow = layout.bars.groupBy { it.row }.mapValues { (_, bars) -> bars.sumOf { it.widthDp.toDouble() } }
        assertTrue(byRow.getValue(0) >= byRow.getValue(1))
        // Jede Zeile beginnt links.
        assertEquals(listOf(0f, 0f), layout.bars.groupBy { it.row }.values.map { it.first().xDp })
    }

    @Test
    fun everyShippedSentenceHasAtLeastTwoRowsOnAWidePage() {
        shippedSentences().forEach { sentence ->
            assertTrue("„$sentence“", SentenceBarSizing.layout(sentence, 1000f).rows >= SentenceBarSizing.MinRows)
        }
    }

    @Test
    fun aSingleWordStaysOneBar() {
        val layout = SentenceBarSizing.layout("Regenbogen.", 300f)
        assertEquals(1, layout.rows)
        assertEquals(1, layout.bars.size)
    }

    /**
     * Der Rahmen nimmt, was die Balken übrig lassen. Auf dem schmalsten Gerät (320dp,
     * Seiteninneres rund 208dp) bleiben alle ausgelieferten Sätze bei höchstens drei
     * Zeilen — ein neuer, längerer Satz bricht diesen Test, nicht den Rahmen.
     */
    @Test
    fun shippedSentencesFitInThreeRowsOnTheNarrowestPage() {
        shippedSentences().forEach { sentence ->
            val rows = SentenceBarSizing.layout(sentence, 208f).rows
            assertTrue("„$sentence“ braucht $rows Zeilen", rows in 1..3)
        }
    }

    @Test
    fun emptyInputGivesNoBars() {
        assertEquals(0, SentenceBarSizing.layout("", 300f).rows)
        assertEquals(0f, SentenceBarSizing.layout(" . ", 300f).heightDp, 0f)
        assertEquals(0, SentenceBarSizing.layout("Oma ist da", 0f).bars.size)
    }

    private fun shippedSentences(): List<String> =
        pack.tasks.values.filterIsInstance<SentencePictureSpec>().flatMap { spec -> spec.rounds.map { it.promptTts } }
}
