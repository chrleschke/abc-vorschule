package app.abcvorschule.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentedProgressTest {
    @Test
    fun `erledigte Trainer sind voll, kommende leer`() {
        assertEquals(1f, SegmentedProgress.fillOf(0, currentIndex = 2, roundIndex = 0, roundCount = 4), 0f)
        assertEquals(1f, SegmentedProgress.fillOf(1, currentIndex = 2, roundIndex = 0, roundCount = 4), 0f)
        assertEquals(0f, SegmentedProgress.fillOf(3, currentIndex = 2, roundIndex = 0, roundCount = 4), 0f)
    }

    @Test
    fun `das laufende Segment folgt dem Rundenanteil`() {
        // Runde 1 von 4 ist eine geschaffte Runde, nicht null Fortschritt: das
        // Kind steht schon in der Aufgabe.
        assertEquals(0.25f, SegmentedProgress.fillOf(2, currentIndex = 2, roundIndex = 0, roundCount = 4), 1e-4f)
        assertEquals(0.5f, SegmentedProgress.fillOf(2, currentIndex = 2, roundIndex = 1, roundCount = 4), 1e-4f)
        assertEquals(1f, SegmentedProgress.fillOf(2, currentIndex = 2, roundIndex = 3, roundCount = 4), 1e-4f)
    }

    @Test
    fun `ein einziger Trainer fuellt genau ein Segment`() {
        assertEquals(0.5f, SegmentedProgress.fillOf(0, currentIndex = 0, roundIndex = 0, roundCount = 2), 1e-4f)
        assertEquals(1f, SegmentedProgress.fillOf(0, currentIndex = 0, roundIndex = 1, roundCount = 2), 1e-4f)
    }

    @Test
    fun `ein Trainer ohne Runden haengt nicht auf leer`() {
        assertEquals(1f, SegmentedProgress.fillOf(0, currentIndex = 0, roundIndex = 0, roundCount = 0), 0f)
    }

    @Test
    fun `ein Rundenindex jenseits der Rundenzahl laeuft nicht ueber`() {
        assertEquals(1f, SegmentedProgress.fillOf(1, currentIndex = 1, roundIndex = 9, roundCount = 3), 0f)
    }
}
