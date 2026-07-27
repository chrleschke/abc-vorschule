package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Test

class SoundWordSegmentsTest {
    @Test fun keepsGermanGraphemeClustersTogether() {
        assertEquals(listOf("F", "i", "sch"), SoundWordSegments.split("Fisch"))
    }
}
