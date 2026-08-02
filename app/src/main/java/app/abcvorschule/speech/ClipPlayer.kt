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

    /**
     * Startet [file]; ruft [onComplete] genau einmal auf, wenn die Wiedergabe
     * endet oder scheitert. Liefert false, wenn sie gar nicht erst startet —
     * dann wurde [onComplete] nicht aufgerufen und der Aufrufer übernimmt
     * (Fallback auf Android-TTS).
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
                onComplete()
            }
            mp.setOnErrorListener { _, _, _ ->
                release()
                onComplete()
                true
            }
            mp.prepare()
            mp.start()
            true
        } catch (_: Exception) {
            release()
            false
        }
    }

    fun stop() {
        release()
    }

    private fun release() {
        player?.let { runCatching { it.release() } }
        player = null
    }
}
