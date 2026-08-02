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
class ClipIndex private constructor(private val clips: Map<String, ClipEntry>) {

    val size: Int get() = clips.size

    fun lookup(text: String): ClipEntry? = clips[text.trim()]

    /** Alle Einträge, für Konsistenz-Checks über den gesamten Index (Tests). */
    fun entries(): Collection<ClipEntry> = clips.values

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun empty(): ClipIndex = ClipIndex(emptyMap())

        fun parse(raw: String): ClipIndex =
            ClipIndex(json.decodeFromString<ClipIndexFile>(raw).clips)

        fun load(openAsset: (String) -> InputStream): ClipIndex = try {
            parse(openAsset("audio/index.json").bufferedReader().use { it.readText() })
        } catch (_: Exception) {
            empty()
        }
    }
}
