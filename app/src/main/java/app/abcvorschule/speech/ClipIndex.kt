package app.abcvorschule.speech

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream

@Serializable
data class ClipEntry(val file: String, val profile: String)

@Serializable
private data class ClipIndexFile(
    val version: Int = 1,
    val clips: Map<String, ClipEntry> = emptyMap(),
    /** Variante → Text → Clip; heute nur `monster` (Laut-Fresser, eigene Aufnahmen). */
    val variants: Map<String, Map<String, ClipEntry>> = emptyMap(),
)

/**
 * Text → vorproduzierter Audio-Clip, gespeist aus assets/audio/index.json.
 *
 * Schlüssel ist der Quelltext aus dem Content-Pack — exakt der String, den
 * die Sprech-Call-Sites übergeben. Fehlt der Index oder ist er kaputt,
 * verhält sich die App wie ohne Clips: alles spricht Android-TTS.
 */
class ClipIndex private constructor(
    private val clips: Map<String, ClipEntry>,
    private val caseInsensitive: Map<String, String>,
    private val variants: Map<String, Map<String, ClipEntry>>,
) {

    /** Zählt nur `clips`, nicht `variants` — Varianten wie `monster` sind bewusst
     *  ausgeschlossen, das speist [speechAvailable]. */
    val size: Int get() = clips.size

    /**
     * [variant] zuerst (exakter Text), sonst der normale Clip. Eine unbekannte
     * Variante ist kein Fehler — dann spricht der normale Clip.
     */
    fun lookup(text: String, variant: String? = null): ClipEntry? {
        val trimmed = text.trim()
        if (variant != null) variants[variant]?.get(trimmed)?.let { return it }
        clips[trimmed]?.let { return it }
        val canonical = caseInsensitive[trimmed.lowercase()] ?: return null
        return clips[canonical]
    }

    /** Alle Einträge, für Konsistenz-Checks über den gesamten Index (Tests). */
    /** Nur die Variante, ohne Rückfall auf `clips` — damit der Aufrufer weiß, was er spielt. */
    fun variantEntry(text: String, variant: String): ClipEntry? = variants[variant]?.get(text.trim())

    fun entries(): Collection<ClipEntry> = clips.values + variants.values.flatMap { it.values }

    companion object {
        /** Aufnahmen des Laut-Fressers; Name = Profil in tools/tts (export.VARIANT_PROFILES). */
        const val MONSTER_VARIANT = "monster"

        private val json = Json { ignoreUnknownKeys = true }

        fun empty(): ClipIndex = ClipIndex(emptyMap(), emptyMap(), emptyMap())

        fun parse(raw: String): ClipIndex {
            val file = json.decodeFromString<ClipIndexFile>(raw)
            return ClipIndex(file.clips, buildCaseInsensitive(file.clips), file.variants)
        }

        /**
         * Lowercase → canonical key, but only when the fold is unambiguous.
         * Preserves intentional pairs such as `H`/`h` and `Sp`/`sp` in the index.
         */
        private fun buildCaseInsensitive(clips: Map<String, ClipEntry>): Map<String, String> {
            val folded = mutableMapOf<String, String>()
            for (key in clips.keys) {
                val lower = key.lowercase()
                val existing = folded[lower]
                when {
                    existing == null -> folded[lower] = key
                    existing.equals(key, ignoreCase = true) &&
                        clips[existing]?.file == clips[key]?.file -> Unit
                    else -> folded[lower] = AMBIGUOUS
                }
            }
            return folded.filterValues { it != AMBIGUOUS }
        }

        private const val AMBIGUOUS = "\u0000"

        fun load(openAsset: (String) -> InputStream): ClipIndex = try {
            parse(openAsset("audio/index.json").bufferedReader().use { it.readText() })
        } catch (_: Exception) {
            empty()
        }
    }
}
