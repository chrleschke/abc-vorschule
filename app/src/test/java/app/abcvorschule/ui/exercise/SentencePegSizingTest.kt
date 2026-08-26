package app.abcvorschule.ui.exercise

import app.abcvorschule.content.ContentRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentencePegSizingTest {
    /** ExerciseStage deckelt auf 420dp und polstert 12dp je Seite. */
    private val stageWidth = 396f

    /** Pixel-7-Klasse: 412dp minus AbcDimens.screenHorizontal und Stage-Polster. */
    private val referenceWidth = SentencePegSizing.ReferenceWidthDp

    /** Ein 360dp-Telefon, gleiche Abzüge — die Referenz aus WordFrameSizingTest. */
    private val narrowPhone = 296f

    /** Ein 320dp-Telefon: unter der Breite, die fünf volle Trefferflächen brauchen. */
    private val tinyPhone = 256f

    private val pack = ContentRepository.fromClasspath().load()

    private fun authoredSentences(): List<Pair<String, List<String>>> =
        pack.sentences.values.map { it.id to pack.sentenceWords(it) }

    private fun widths() = listOf(stageWidth, referenceWidth, narrowPhone, tinyPhone)

    // --- Die eigentliche Zusicherung: die Reihe passt, immer ------------------

    @Test
    fun everyAuthoredSentenceFitsEveryWidth() {
        authoredSentences().forEach { (id, words) ->
            widths().forEach { available ->
                val row = SentencePegSizing.solve(available, words)
                assertTrue(
                    "$id braucht ${row.widthDp}dp von ${available}dp",
                    row.widthDp <= available + 0.01f,
                )
            }
        }
    }

    @Test
    fun theOldUniformWidthOverflowIsGone() {
        // Der gemeldete Fehler, als Zahl: drei Pegs auf Breite von „schwimmt" bei
        // 20sp Floor waren 3 x 131dp + 24dp = 418dp gegen die 396dp, die
        // ExerciseStage überhaupt hergibt (bei font_scale 1.3 sogar 521dp), und
        // Arrangement.CenterHorizontally schob damit erstes und letztes Wort aus
        // dem Bild.
        listOf(
            listOf("der", "Fisch", "schwimmt"),
            listOf("Oma", "hat", "einen", "Hut"),
        ).forEach { words ->
            listOf(stageWidth, narrowPhone).forEach { available ->
                val row = SentencePegSizing.solve(available, words)
                assertTrue(
                    "${words.joinToString(" ")} braucht ${row.widthDp}dp von ${available}dp",
                    row.widthDp <= available + 0.01f,
                )
                assertEquals(words.size, row.pegWidthsDp.size)
            }
        }
    }

    @Test
    fun aFiveWordSentenceWouldStillFitTheNarrowPhone() {
        // Kein autorierter Satz des Satz-Architekten ist so lang; die Zusicherung
        // gilt trotzdem, damit ein späterer Satz nicht zurück in den Überlauf führt.
        val row = SentencePegSizing.solve(narrowPhone, listOf("der", "Hase", "ist", "im", "Sand"))
        assertTrue("Reihe ${row.widthDp}dp", row.widthDp <= narrowPhone + 0.01f)
    }

    // --- Trefferfläche --------------------------------------------------------

    @Test
    fun everyPegKeepsTheTouchFloorWhereverItFits() {
        authoredSentences().forEach { (id, words) ->
            listOf(stageWidth, referenceWidth, narrowPhone).forEach { available ->
                val row = SentencePegSizing.solve(available, words)
                row.pegWidthsDp.forEachIndexed { index, width ->
                    assertTrue(
                        "$id Peg $index ist ${width}dp bei ${available}dp",
                        width >= SentencePegSizing.MinPegWidthDp - 0.01f,
                    )
                }
            }
        }
    }

    @Test
    fun tinyPhoneTradesTheTouchFloorForVisibility() {
        // 5 x 56dp + 4 x 4dp = 296dp passen nicht in 256dp. Dann schrumpft die
        // Reihe gleichmäßig statt einen Peg über den Rand zu schieben — und bleibt
        // über Androids eigenem 48dp-Minimum.
        val row = SentencePegSizing.solve(tinyPhone, listOf("der", "Hase", "ist", "im", "Sand"))
        assertTrue("Reihe ${row.widthDp}dp", row.widthDp <= tinyPhone + 0.01f)
        assertTrue("engster Peg ${row.pegWidthsDp.min()}dp", row.pegWidthsDp.min() >= 44f)
    }

    // --- Silhouette: jeder Peg trägt sein eigenes Wort ------------------------

    @Test
    fun pegsHugTheirOwnWordInsteadOfTheLongest() {
        val row = SentencePegSizing.solve(referenceWidth, listOf("Oma", "hat", "einen", "Hut"))
        val (oma, hat, einen, hut) = row.pegWidthsDp
        assertTrue("„einen\" $einen muss breiter sein als „hat\" $hat", einen > hat)
        assertEquals("gleich lange Wörter, gleiche Breite", oma, hat, 0.01f)
        assertEquals("gleich lange Wörter, gleiche Breite", hut, hat, 0.01f)
    }

    @Test
    fun aOneWordRoundKeepsTheFullGlyph() {
        val row = SentencePegSizing.solve(narrowPhone, listOf("Mama"))
        assertEquals(SentencePegSizing.MaxGlyphDp, row.glyphDp, 0.01f)
        assertEquals(SentencePegSizing.MaxGapDp, row.gapDp, 0.01f)
    }

    // --- Weißraum gibt vor dem Wort nach -------------------------------------

    @Test
    fun theComfortableGapYieldsBeforeTheGlyphDoes() {
        val words = listOf("der", "Hase", "ist", "im", "Sand")
        val tight = SentencePegSizing.solve(narrowPhone, words)
        assertEquals(SentencePegSizing.MinGapDp, tight.gapDp, 0.01f)

        val roomy = SentencePegSizing.solve(stageWidth, listOf("Oma", "ist", "da"))
        assertEquals(SentencePegSizing.MaxGapDp, roomy.gapDp, 0.01f)
    }

    // --- Systemschriftgröße verschiebt den Fit nicht -------------------------

    @Test
    fun renderedWidthIsIndependentOfFontScale() {
        val row = SentencePegSizing.solve(narrowPhone, listOf("der", "Fisch", "schwimmt"))
        listOf(1f, 1.15f, 1.3f, 2f).forEach { fontScale ->
            val rendered = SentencePegSizing.glyphSp(row.glyphDp, fontScale) * fontScale
            assertEquals("bei font_scale $fontScale", row.glyphDp, rendered, 0.01f)
        }
    }

    @Test
    fun aSmallerFontScaleDoesNotBlowUpTheGlyph() {
        assertEquals(20f, SentencePegSizing.glyphSp(20f, 0.85f), 0.01f)
    }

    // --- Autorierungs-Grenze -------------------------------------------------

    @Test
    fun everyAuthoredSentenceStaysReadableOnTheReferenceWidth() {
        val tooLong = authoredSentences().filter { (_, words) ->
            SentencePegSizing.solve(referenceWidth, words).glyphDp <
                SentencePegSizing.ReadableGlyphDp
        }
        assertEquals(
            "Sätze unter ${SentencePegSizing.ReadableGlyphDp}dp Glyph auf " +
                "${referenceWidth}dp — kürzen oder displayOverride setzen",
            emptyList<String>(),
            tooLong.map { (id, words) -> "$id ${words.joinToString(" ")}" },
        )
    }

    // --- Der fertige Satz ----------------------------------------------------

    @Test
    fun theCompletedSentenceFitsTheWidthToo() {
        authoredSentences().forEach { (id, words) ->
            listOf(referenceWidth, narrowPhone, tinyPhone).forEach { available ->
                val glyph = SentencePegSizing.completedGlyphDp(available, words)
                val chars = words.sumOf { it.length } + (words.size - 1)
                val used = glyph * SentencePegSizing.GlyphAspect * chars
                assertTrue(
                    "$id fertiger Satz braucht ${used}dp von ${available}dp",
                    used <= available + 0.01f,
                )
            }
        }
    }
}
