package app.abcvorschule.speech

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundSpeechLifecycleTest {
    @Test
    fun onStopStopsSpeechOnBackground() {
        val events = mutableListOf<String>()
        handleBackgroundSpeechLifecycleEvent(
            event = Lifecycle.Event.ON_STOP,
            onBackground = { events += "background" },
            onForeground = { events += "foreground" },
        )
        assertEquals(listOf("background"), events)
    }

    @Test
    fun onStartResumesSpeechOnForeground() {
        val events = mutableListOf<String>()
        handleBackgroundSpeechLifecycleEvent(
            event = Lifecycle.Event.ON_START,
            onBackground = { events += "background" },
            onForeground = { events += "foreground" },
        )
        assertEquals(listOf("foreground"), events)
    }

    @Test
    fun otherLifecycleEventsAreIgnored() {
        val events = mutableListOf<String>()
        handleBackgroundSpeechLifecycleEvent(
            event = Lifecycle.Event.ON_PAUSE,
            onBackground = { events += "background" },
            onForeground = { events += "foreground" },
        )
        assertEquals(emptyList<String>(), events)
    }
}
