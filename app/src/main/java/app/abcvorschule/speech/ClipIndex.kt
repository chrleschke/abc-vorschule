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
) {

    val size: Int get() = clips.size

    fun lookup(text: String): ClipEntry? {
        val trimmed = text.trim()
        clips[trimmed]?.let { return it }
        val canonical = caseInsensitive[trimmed.lowercase()] ?: return null
        return clips[canonical]
    }

    /** Alle Einträge, für Konsistenz-Checks über den gesamten Index (Tests). */
    fun entries(): Collection<ClipEntry> = clips.values

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun empty(): ClipIndex = ClipIndex(emptyMap(), emptyMap())

        fun parse(raw: String): ClipIndex {
            val clips = json.decodeFromString<ClipIndexFile>(raw).clips
            return ClipIndex(clips, buildCaseInsensitive(clips))
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
