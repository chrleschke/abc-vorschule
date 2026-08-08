package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiplicationMatrixTest {
    @Test
    fun onlyTheFirstRowIsConcrete() {
        assertTrue(MultiplicationMatrix.isConcreteRow(0))
        (1 until MultiplicationMatrix.MaxRows).forEach { row ->
            assertFalse("row $row must be a ghost placeholder", MultiplicationMatrix.isConcreteRow(row))
        }
    }

    @Test
    fun ghostRowsStayVisibleButClearlySecondary() {
        assertTrue(MultiplicationMatrix.GhostAlpha > 0f)
        assertTrue(MultiplicationMatrix.GhostAlpha < 0.5f)
    }

    @Test
    fun wideGridsShrinkTheirEmoji() {
        assertTrue(
            MultiplicationMatrix.emojiSizeSp(MultiplicationMatrix.MaxColumns) <
                MultiplicationMatrix.emojiSizeSp(2),
        )
    }

    @Test
    fun gridCapsAllowTheFullCurriculumRangeUpToThirty() {
        assertEquals(30, MultiplicationMatrix.MaxRows * MultiplicationMatrix.MaxColumns)
    }

    @Test
    fun rowsAreNumberedFromOne() {
        assertEquals("1", MultiplicationMatrix.rowLabel(0))
        assertEquals(
            (1..MultiplicationMatrix.MaxRows).map { it.toString() },
            (0 until MultiplicationMatrix.MaxRows).map { MultiplicationMatrix.rowLabel(it) },
        )
    }

    @Test
    fun rowLabelsStaySingleDigitWithinTheGrid() {
        // The gutter is a fixed width, so the cap must keep the labels one digit wide.
        assertTrue(MultiplicationMatrix.MaxRows < 10)
        assertTrue(MultiplicationMatrix.RowLabelGutterDp > 0)
    }

    @Test
    fun theGutterHoldsOneDigitAtEverySystemFontScale() {
        // The gutter is dp, the numeral is sp: the derivation must cover the
        // largest scale Android offers, not just the 1.3 the old comment named.
        listOf(1f, 1.3f, MultiplicationMatrix.MaxFontScale).forEach { scale ->
            assertTrue(
                "a digit at scale $scale must fit the ${MultiplicationMatrix.RowLabelGutterDp}dp gutter",
                MultiplicationMatrix.rowLabelAdvanceDp(scale) <=
                    MultiplicationMatrix.RowLabelGutterDp.toFloat(),
            )
        }
    }

    @Test
    fun theGutterNeverShrinksBelowItsShippedWidth() {
        // Every row starts at the same x as before unless the theme forces more.
        assertTrue(MultiplicationMatrix.RowLabelGutterDp >= 24)
    }

    @Test
    fun equationLabelSpellsOutBothFactors() {
        assertEquals("3 × 4", MultiplicationMatrix.equationLabel(3, 4))
        assertEquals(
            "${MultiplicationMatrix.MaxRows} × ${MultiplicationMatrix.MaxColumns}",
            MultiplicationMatrix.equationLabel(
                MultiplicationMatrix.MaxRows,
                MultiplicationMatrix.MaxColumns,
            ),
        )
    }

    @Test
    fun equationLabelUsesTheSharedMultiplicationSymbol() {
        assertTrue(
            MultiplicationMatrix.equationLabel(2, 5).contains(MathOperation.Multiply.symbol),
        )
    }
}
