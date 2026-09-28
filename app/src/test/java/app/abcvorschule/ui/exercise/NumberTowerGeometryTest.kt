package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberTowerGeometryTest {
    @Test
    fun blocksStackInColumnsOfFive() {
        assertEquals(1, NumberTowerGeometry.columns(1))
        assertEquals(1, NumberTowerGeometry.columns(5))
        assertEquals(2, NumberTowerGeometry.columns(6))
        assertEquals(2, NumberTowerGeometry.columns(10))
        assertEquals(6, NumberTowerGeometry.columns(30))
        // Block 6 (Index 5) beginnt die zweite Säule unten.
        val sixth = NumberTowerGeometry.slot(5, 10f, 2f)
        assertEquals(12f, sixth.x, 0.001f)
        assertEquals(0f, sixth.y, 0.001f)
        assertEquals(-40f, NumberTowerGeometry.slot(4, 10f, 2f).y, 0.001f)
    }

    /** Das große Gesicht sitzt nie über leerer Fläche: das Rechteck ist ganz gefüllt. */
    @Test
    fun theBigFaceSitsOnAFilledRectangle() {
        for (n in 1..30) {
            val (cols, rows) = NumberTowerGeometry.faceRect(n)
            assertTrue("$n: $cols Säulen", cols in 1..NumberTowerGeometry.columns(n))
            for (c in 0 until cols) {
                val height = minOf(5, n - c * 5)
                assertTrue("$n: Säule $c hat nur $height, Gesicht braucht $rows", height >= rows)
            }
            if (NumberTowerGeometry.columns(n) > 1) assertTrue("$n: mindestens zwei Säulen", cols >= 2)
        }
        assertEquals(2 to 2, NumberTowerGeometry.faceRect(7))
        assertEquals(1 to 3, NumberTowerGeometry.faceRect(3))
        assertEquals(3 to 5, NumberTowerGeometry.faceRect(17))
    }

    /** Jede Aufgabe bis 30 passt in die schmalste Bühne, und die Blöcke bleiben erkennbar. */
    @Test
    fun everyTaskFitsTheNarrowStage() {
        val width = 256f
        val towerHeight = 150f - 54f
        val cases = buildList {
            for (l in 1..30) for (r in 1..30) {
                if (l + r <= 30) add(Triple(MathOperation.Add, l, r))
                if (r < l) add(Triple(MathOperation.Subtract, l, r))
            }
            for (l in 1..5) for (r in 1..6) add(Triple(MathOperation.Multiply, l, r))
        }
        cases.forEach { (op, l, r) ->
            val b = NumberTowerGeometry.blockSize(op, l, r, width, towerHeight, 34f)
            assertTrue("$l $op $r: Block $b", b > 0f && b <= 34f)
            assertTrue("$l $op $r: zu breit", NumberTowerGeometry.spanInBlocks(op, l, r) * b <= width)
            assertTrue("$l $op $r: zu hoch", 5 * b <= towerHeight)
        }
    }

    @Test
    fun aTappedLeftTowerSaysWhatItIsNow() {
        assertEquals(4, TowerTiming.leftNumber(MathOperation.Add, 4, 3, jumped = false, merged = false))
        assertEquals(7, TowerTiming.leftNumber(MathOperation.Add, 4, 3, jumped = true, merged = false))
        assertEquals(3, TowerTiming.leftNumber(MathOperation.Subtract, 9, 6, jumped = true, merged = false))
        assertEquals(4, TowerTiming.leftNumber(MathOperation.Multiply, 3, 4, jumped = false, merged = false))
        assertEquals(12, TowerTiming.leftNumber(MathOperation.Multiply, 3, 4, jumped = false, merged = true))
    }
}
