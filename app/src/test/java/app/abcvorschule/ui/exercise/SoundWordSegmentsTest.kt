package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Test

class SoundWordSegmentsTest {
    @Test fun keepsGermanGraphemeClustersTogether() {
        assertEquals(listOf("F", "i", "sch"), SoundWordSegments.split("Fisch"))
    }

    @Test fun keepsPfTogether() {
        assertEquals(listOf("Pf", "e", "r", "d"), SoundWordSegments.split("Pferd"))
    }

    @Test fun keepsQuTogether() {
        assertEquals(listOf("Qu", "a", "l", "l", "e"), SoundWordSegments.split("Qualle"))
    }
}
