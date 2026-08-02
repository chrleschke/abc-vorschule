package app.abcvorschule.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

/**
 * Spielt produzierte Clips aus assets/audio/. Ein Player zur Zeit —
 * dieselbe Flush-Semantik wie die TTS-Ausgabe: neuer Clip stoppt den alten.
 *
 * .ogg steht in AAPTs Default-noCompress-Liste, die Assets liegen also
 * unkomprimiert im APK und openFd() funktioniert.
 */
class ClipPlayer(context: Context) {
    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null

    /** onComplete der laufenden Wiedergabe — noch nicht aufgerufen. */
    private var pendingOnComplete: (() -> Unit)? = null

    /**
     * Startet [file]; ruft [onComplete] GENAU EINMAL auf, wenn die Wiedergabe
     * endet, scheitert oder abgebrochen wird ([stop], oder ein neuer [play]-
     * Aufruf, der intern über [stop] geht). Liefert false, wenn sie gar nicht
     * erst startet — dann wurde [onComplete] nicht aufgerufen und der
     * Aufrufer übernimmt (Fallback auf Android-TTS).
     */
    fun play(file: String, onComplete: () -> Unit): Boolean {
        stop()
        return try {
            val mp = MediaPlayer()
            player = mp
            appContext.assets.openFd("audio/$file").use { afd ->
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            mp.setOnCompletionListener {
                release()
                finish()
            }
            mp.setOnErrorListener { _, _, _ ->
                release()
                finish()
                true
            }
            pendingOnComplete = onComplete
            mp.prepare()
            mp.start()
            true
        } catch (_: Exception) {
            pendingOnComplete = null
            release()
            false
        }
    }

    /**
     * Stoppt eine laufende Wiedergabe. Ruft — falls eine lief — das
     * ausstehende `onComplete` GENAU EINMAL auf, dieselbe Garantie wie bei
     * natürlichem Ende oder Fehler. Ohne das bliebe eine wartende
     * `speakAndAwait`-Coroutine bis zum Timeout hängen, wenn `stop()` (oder
     * ein neuer `play()`, der intern hierüber geht) die Wiedergabe beendet.
     */
    fun stop() {
        release()
        finish()
    }

    /** Feuert das ausstehende onComplete genau einmal und räumt es dann weg. */
    private fun finish() {
        val callback = pendingOnComplete ?: return
        pendingOnComplete = null
        callback()
    }

    private fun release() {
        player?.let { runCatching { it.release() } }
        player = null
    }
}
