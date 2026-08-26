package app.abcvorschule.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drosselung des Zählkanals. Für Zahlwörter gibt es keine Clips, jede getippte
 * Zahl geht als TTS-Utterance in die geteilte Engine-Queue — und QUEUE_FLUSH ist
 * dort verboten, weil es die laufende Primary-Ansage abwürgen würde. Statt zu
 * flushen wird an der Quelle gedrosselt: höchstens eine Zahl in der Engine,
 * danach nur die ZULETZT getippte.
 */
class CountingSpeechQueueTest {
    @Test
    fun `die erste zahl wird sofort gesprochen`() {
        val queue = CountingSpeechQueue()
        assertTrue(queue.offer("eins", "id-1"))
    }

    @Test
    fun `waehrend eine zahl laeuft wartet nur die zuletzt getippte`() {
        val queue = CountingSpeechQueue()
        queue.offer("eins", "id-1")
        assertFalse(queue.offer("zwei", "id-2"))
        assertFalse(queue.offer("drei", "id-3"))
        assertFalse(queue.offer("vier", "id-4"))
        // Nicht "zwei": die überholten Zahlen fallen weg, statt sich zu stauen.
        assertEquals("vier", queue.onUtteranceFinished("id-1"))
    }

    @Test
    fun `nach der wartenden zahl ist die queue wieder leer`() {
        val queue = CountingSpeechQueue()
        queue.offer("eins", "id-1")
        queue.offer("zwei", "id-2")
        assertEquals("zwei", queue.onUtteranceFinished("id-1"))
        assertTrue(queue.offer("drei", "id-3"))
    }

    @Test
    fun `ohne getippte zahl dazwischen bleibt nichts zurueck`() {
        val queue = CountingSpeechQueue()
        queue.offer("eins", "id-1")
        assertNull(queue.onUtteranceFinished("id-1"))
    }

    @Test
    fun `fremde utterance-ids raeumen den zaehlkanal nicht ab`() {
        val queue = CountingSpeechQueue()
        queue.offer("eins", "id-1")
        queue.offer("zwei", "id-2")
        // Primary- und Feedback-Utterances laufen durch dieselbe Callback-Kette.
        assertNull(queue.onUtteranceFinished("primary-id"))
        assertNull(queue.onUtteranceFinished(null))
        assertEquals("zwei", queue.onUtteranceFinished("id-1"))
    }

    @Test
    fun `nach einem engine-flush klingt keine wartende zahl nach`() {
        val queue = CountingSpeechQueue()
        queue.offer("eins", "id-1")
        queue.offer("zwei", "id-2")
        queue.reset()
        assertNull(queue.onUtteranceFinished("id-1"))
        assertTrue(queue.offer("drei", "id-3"))
    }
}
