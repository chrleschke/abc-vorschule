package app.abcvorschule.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SpeechController(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val utteranceWaiters = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    override fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            _available.value = false
            return
        }
        val german = Locale.GERMANY
        val result = engine.setLanguage(german)
        val languageOk = result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED
        if (languageOk) {
            engine.voices?.firstOrNull { voice ->
                voice.locale.language == german.language &&
                voice.locale.country == german.country &&
                !voice.isNetworkConnectionRequired
            }?.let { engine.voice = it }
        }
        _available.value = languageOk
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _speaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _speaking.value = false
                completeWaiter(utteranceId)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _speaking.value = false
                completeWaiter(utteranceId)
            }
        })
    }

    fun speak(text: String) {
        val engine = tts ?: return
        if (!_available.value || text.isBlank()) return
        clearWaiters()
        engine.stop()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    /** Speaks [text] and suspends until the utterance finishes (or times out). */
    suspend fun speakAndAwait(text: String, timeoutMs: Long = 10_000L) {
        val engine = tts ?: return
        if (!_available.value || text.isBlank()) return
        clearWaiters()
        engine.stop()
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Unit>()
        utteranceWaiters[id] = deferred
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        withTimeoutOrNull(timeoutMs) { deferred.await() }
        utteranceWaiters.remove(id)
    }

    fun stop() {
        tts?.stop()
        _speaking.value = false
        clearWaiters()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _available.value = false
        _speaking.value = false
        clearWaiters()
    }

    private fun completeWaiter(utteranceId: String?) {
        if (utteranceId == null) return
        utteranceWaiters.remove(utteranceId)?.complete(Unit)
    }

    private fun clearWaiters() {
        utteranceWaiters.keys.toList().forEach { key ->
            utteranceWaiters.remove(key)?.complete(Unit)
        }
    }
}
