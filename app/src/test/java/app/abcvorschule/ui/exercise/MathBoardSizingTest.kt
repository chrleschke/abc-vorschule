package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Rangfolge aus [MathBoardSizing], gegen die Geometrie gerechnet. Was das
 * gerenderte Layout daraus macht, prüft `MathBoardBoundsTest` am Gerät — diese
 * Rechnung ist die Vorstufe, nicht der Ersatz.
 */
class MathBoardSizingTest {
    /** Bühnenbreiten: 320dp-Gerät, 360dp-Gerät, 412dp-Gerät, Deckel, Tablet. */
    private val stageWidths = listOf(280f, 320f, 372f, 420f, 600f)

    /** Bühnenhöhen von einem kurzen Gerät bis zum Tablet. */
    private val stageHeights = listOf(400f, 480f, 600f, 780f)

    /** Mengen bis 10 ergeben ein bis fünf Paar-Zeilen. */
    private val clusterRows = 1..5

    /**
     * Gerenderte Zeilenhöhe der Ziffer (`headlineMedium`, 34sp) bei
     * `font_scale` 1.0, 1.3 (Testgerät) und 2.0 — gemessene Werte, weil Androids
     * Schriftskalierung nichtlinear ist.
     */
    private val numeralLines = listOf(34f, 40f, 46f)

    private fun tileWidthDp(layout: MathBoardSizing.ChoiceLayout): Float {
        val columns = if (layout.symbolic) 1 else 2
        val content = columns * layout.emojiDp + (columns - 1) * MathBoardSizing.EmojiGapDp
        return maxOf(content + 2 * MathBoardSizing.TilePaddingHorizontalDp, MathBoardSizing.MinTileDp)
    }

    private fun rowWidthDp(layout: MathBoardSizing.ChoiceLayout, stageWidthDp: Float): Float =
        3 * tileWidthDp(layout) + 2 * MathBoardSizing.tileGapDp(stageWidthDp)

    /** Rangfolge 1: die drei Kacheln stehen in einer Reihe — auf jeder Bühne. */
    @Test
    fun threeTilesAlwaysFitOneRow() {
        forEveryCase { width, height, rows, numeralLine ->
            val layout = MathBoardSizing.solveChoices(width, height, rows, numeralLine)
            val row = rowWidthDp(layout, width)
            val available = MathBoardSizing.availableWidthDp(width)
            assertTrue(
                "Kachelreihe ${row}dp > ${available}dp verfügbar " +
                    "(Bühne ${width}x$height, $rows Zeilen, Ziffer ${numeralLine}dp, $layout)",
                row <= available + Slack,
            )
        }
    }

    /** Rangfolge 3: der Antwortblock nimmt nie mehr als seinen Anteil der Bühne. */
    @Test
    fun answersNeverTakeMoreThanTheirShareOfTheStage() {
        forEveryCase { width, height, rows, numeralLine ->
            val layout = MathBoardSizing.solveChoices(width, height, rows, numeralLine)
            val answers = MathBoardSizing.answersHeightDp(layout, rows, numeralLine)
            val share = height * MathBoardSizing.MaxAnswersHeightFraction
            assertTrue(
                "Antwortblock ${answers}dp > ${share}dp Anteil " +
                    "(Bühne ${width}x$height, $rows Zeilen, Ziffer ${numeralLine}dp, $layout)",
                answers <= maxOf(share, tileFloor()) + Slack,
            )
        }
    }

    /**
     * Der eigentliche Befund: Speaker, Aufgabe und Antworten müssen zusammen auf die
     * Bühne passen. Vorher taten sie das nicht — der Aufgabenblock wurde auf null
     * gemessen und die Rechnung war abgeschnitten.
     */
    @Test
    fun promptAndAnswersTogetherFitTheStage() {
        forEveryCase { width, height, rows, numeralLine ->
            val layout = MathBoardSizing.solveChoices(width, height, rows, numeralLine)
            val answers = MathBoardSizing.answersHeightDp(layout, rows, numeralLine)
            val promptRows = if (layout.symbolic) 1 else rows
            val promptDp = MathBoardSizing.solvePromptEmojiDp(
                baseDp = PromptBaseDp,
                stageWidthDp = width,
                stageHeightDp = height,
                rows = promptRows,
                cells = if (layout.symbolic) 2 else 4,
                answersHeightDp = answers,
                operatorWidthDp = OperatorWidthDp,
                numeralLineDp = numeralLine,
            )
            val prompt = 2 * MathBoardSizing.PromptPaddingVerticalDp +
                promptRows * promptDp + promptRows * MathBoardSizing.EmojiGapDp + numeralLine
            val total = MathBoardSizing.ChromeHeightDp + prompt + answers
            // Nur dort, wo ein Boden gewinnt — kleinste Trefferfläche, kleinstes
            // Symbol —, darf es enger werden: das ist eine bewusst unschöne Bühne,
            // keine kaputte. Dieselbe Entscheidung wie beim fehlenden Glyph-Floor
            // in [SentencePegSizing].
            val atFloor = promptDp <= MathBoardSizing.MinSymbolicEmojiDp + Slack ||
                layout.emojiDp <= MathBoardSizing.MinSymbolicEmojiDp + Slack ||
                answers <= tileFloor() + Slack
            if (!atFloor) {
                assertTrue(
                    "Bühne ${width}x$height braucht ${total}dp " +
                        "($rows Zeilen, Ziffer ${numeralLine}dp, $layout, Aufgabe ${promptDp}dp)",
                    total <= height + Slack,
                )
            }
        }
    }

    /** Rangfolge 4/5: wo Platz ist, bleibt alles wie vorher. */
    @Test
    fun aRoomyStageKeepsTheOldSizes() {
        val layout = MathBoardSizing.solveChoices(420f, 780f, rows = 3, numeralLineDp = 34f)
        assertFalse("Auf einer großen Bühne bleiben die Mengen Bilder", layout.symbolic)
        assertEquals(MathBoardSizing.MaxChoiceEmojiDp, layout.emojiDp, 0.01f)
        assertEquals(
            PromptBaseDp,
            MathBoardSizing.solvePromptEmojiDp(
                baseDp = PromptBaseDp,
                stageWidthDp = 420f,
                stageHeightDp = 780f,
                rows = 3,
                cells = 4,
                answersHeightDp = MathBoardSizing.answersHeightDp(layout, 3, 34f),
                operatorWidthDp = OperatorWidthDp,
                numeralLineDp = 34f,
            ),
            0.01f,
        )
    }

    /**
     * Die erste Lektion, die den Fehler gemeldet hat: „4 + 3" mit den Antworten
     * 6/7/8 auf einem 360dp-Telefon bei der Schriftgröße des Testgeräts. Vier
     * Paar-Zeilen je Kachel, und trotzdem bleibt die Menge ein Bild.
     */
    @Test
    fun theFirstLessonStaysPictorialOnAPhone() {
        val layout = MathBoardSizing.solveChoices(320f, 600f, rows = 4, numeralLineDp = 40f)
        assertFalse("4 + 3 ist eine Subitizing-Runde und bleibt ein Bild", layout.symbolic)
        assertTrue(
            "Kachel-Emoji auf ${layout.emojiDp}dp geschrumpft",
            layout.emojiDp >= MathBoardSizing.MinCountableEmojiDp,
        )
        assertTrue(
            "Kachelreihe passt nicht: ${rowWidthDp(layout, 320f)}dp",
            rowWidthDp(layout, 320f) <= MathBoardSizing.availableWidthDp(320f) + Slack,
        )
    }

    /** Wird es zu eng für eine abzählbare Menge, fällt die Runde auf das Symbol zurück. */
    @Test
    fun aStageTooTightForCountingFallsBackToTheSymbol() {
        val layout = MathBoardSizing.solveChoices(320f, 400f, rows = 5, numeralLineDp = 46f)
        assertTrue("Fünf Zeilen à zwei Emojis passen hier nicht mehr", layout.symbolic)
    }

    private fun tileFloor(): Float =
        MathBoardSizing.MinTileDp + MathBoardSizing.BottomPaddingDp + MathBoardSizing.ResolveReserveDp

    private fun forEveryCase(block: (width: Float, height: Float, rows: Int, numeralLineDp: Float) -> Unit) {
        stageWidths.forEach { width ->
            stageHeights.forEach { height ->
                clusterRows.forEach { rows ->
                    numeralLines.forEach { numeralLine -> block(width, height, rows, numeralLine) }
                }
            }
        }
    }

    private companion object {
        /** Rundungsluft der Rechnung, nicht der erlaubte Überstand. */
        const val Slack = 0.01f

        /** `QuantityGrouping.promptEmojiSizeSp(44, …)` gerendert bei font_scale 1.0. */
        const val PromptBaseDp = 44f * MathBoardSizing.EmojiAspect

        /** `displayMedium` (45sp) mal [WordFrameSizing.GlyphAspect], font_scale 1.0. */
        const val OperatorWidthDp = 45f * WordFrameSizing.GlyphAspect
    }
}
