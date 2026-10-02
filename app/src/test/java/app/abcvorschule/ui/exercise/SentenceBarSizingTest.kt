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
    fun wrapsIntoRowsWhenThePageIsNarrow() {
        val sentence = "Mateo und Lennard sind ins Baumhaus geklettert."
        val wide = SentenceBarSizing.layout(sentence, 1000f)
        val narrow = SentenceBarSizing.layout(sentence, 200f)
        assertEquals(1, wide.rows)
        assertTrue(narrow.rows > 1)
        assertEquals(narrow.rows * SentenceBarSizing.BarHeightDp + (narrow.rows - 1) * SentenceBarSizing.RowGapDp, narrow.heightDp, 0f)
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
