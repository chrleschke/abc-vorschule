package app.abcvorschule.ui.rewards

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Die Geräusche der App (PRODUCT_PRINCIPLES §7, „Geräusche"). Erzeugt von
 * `tools/sfx/generate_sfx.py`, ausgeliefert unter `assets/sfx/`, zusammen ~35 KB.
 *
 * Jedes Geräusch hat **eine** Tat, genau wie die Haptik-Verben (§10): dieselbe Tat
 * klingt überall gleich, und kein Geräusch klingt nach „falsch" — auch der Rückflug
 * einer Karte ist ein weiches Federn, kein Summer.
 */
enum class Sfx(val asset: String) {
    /** Eine Jagd-Kugel wird eingesammelt und verlässt das Feld. */
    Pop("pop"),

    /** Ein Baustein, ein Wortschild, eine Silbe rastet ein. */
    Snap("snap"),

    /** Eine Karte fliegt an ihren Platz zurück (daneben oder in den falschen Slot). */
    Boing("boing"),

    /** Der Erfolgs-Stern fliegt los. */
    Whoosh("whoosh"),

    /** Der Erfolgs-Stern schlägt im Punktestand ein. */
    Ding("ding"),

    /** Eine Runde ist geschafft (der große Stern poppt auf). */
    Chime("chime"),

    /** Eine Lektion ist geschafft (End-Screen). */
    Fanfare("fanfare"),

    /** Eine Taste des Kinder-Ziffernblocks. */
    Tap("tap"),

    /** Ein Stern im Spurensucher — die Tonhöhe steigt mit jedem Stern ([AbcSfx.blipRate]). */
    Blip("blip"),

    /** Das Jagd-Feld mischt neu. */
    Shuffle("shuffle"),

    /** Etwas geht (noch) nicht: gesperrtes Schild, Fehltipp ohne deutsche Stimme, Fehlversuch im Rechnen. */
    Blocked("blocked"),

    /** Ein Tipp, während die Ansage noch läuft: „hör erst zu" — leise gespielt. */
    Blubb("blubb"),
}

/**
 * Spielt die [Sfx] über einen [SoundPool] — geringe Latenz, mehrere gleichzeitig, und
 * auf demselben Medien-Kanal wie die Sprache (siehe `playTone`: auf dem System-Kanal
 * wären sie im Stumm-/Vibrationsmodus lautlos, während die Sprache weiterliefe).
 *
 * Lautstärke: die Clips liegen bei −8 dBFS Spitze und werden zusätzlich mit
 * [Volume] gespielt — sie sollen die Sprache begleiten, nicht übertönen.
 */
object AbcSfx {
    /** Relativ zur Sprache leiser; die Stimme trägt die Aufgabe, der Klang die Tat. */
    const val Volume = 0.7f

    private var pool: SoundPool? = null
    private val soundIds = ConcurrentHashMap<Sfx, Int>()
    private val ready = ConcurrentHashMap.newKeySet<Int>()

    /** Einmal beim App-Start. Laden läuft asynchron; bis ein Clip bereit ist, spielt er nicht. */
    fun init(context: Context) {
        if (pool != null) return
        val created = runCatching {
            SoundPool.Builder()
                .setMaxStreams(MaxStreams)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .build()
        }.getOrNull() ?: return
        created.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                ready.add(sampleId)
                if (ready.size == soundIds.size) Log.i(Tag, "alle ${ready.size} Geräusche bereit")
            } else {
                // Ohne Clip fällt der Aufrufer auf einen Sinus-Ton zurück — still ist nichts,
                // aber im Log soll stehen, welcher Clip das Gerät nicht dekodiert hat.
                Log.w(Tag, "Geräusch $sampleId nicht ladbar (status $status)")
            }
        }
        Sfx.entries.forEach { sfx ->
            runCatching {
                context.assets.openFd("sfx/${sfx.asset}.ogg").use { fd ->
                    soundIds[sfx] = created.load(fd, 1)
                }
            }
        }
        pool = created
    }

    /**
     * @return false, wenn der Clip (noch) nicht spielbar ist — dann darf der Aufrufer
     * auf einen synthetisierten Ton zurückfallen, damit ein Tipp nie stumm bleibt.
     */
    fun play(sfx: Sfx, rate: Float = 1f, volume: Float = Volume): Boolean {
        val p = pool ?: return false
        val id = soundIds[sfx] ?: return false
        if (id !in ready) return false
        return runCatching { p.play(id, volume, volume, 1, 0, rate.coerceIn(0.5f, 2f)) != 0 }.getOrDefault(false)
    }

    private const val MaxStreams = 6
    private const val Tag = "AbcSfx"

    /** Grundton des Blip-Clips (G5); die Tonleiter unten wird relativ dazu abgespielt. */
    private const val BlipBaseHz = 784.0f

    /** C-Dur ab C5, wie der frühere Sinus-Blip — nach einer Oktave von vorn. */
    private val BlipScaleHz = floatArrayOf(523.25f, 587.33f, 659.25f, 698.46f, 783.99f, 880.0f, 987.77f, 1046.5f)

    /** Abspielrate für den [step]-ten Stern: eine Tonleiterstufe höher pro Stern. */
    fun blipRate(step: Int): Float = BlipScaleHz[step.coerceAtLeast(0) % BlipScaleHz.size] / BlipBaseHz
}
