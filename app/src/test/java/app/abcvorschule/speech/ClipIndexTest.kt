package app.abcvorschule.speech

import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipIndexTest {

    private val sample = """
        {
          "version": 1,
          "clips": {
            "Mama mag Mais.": { "file": "sentence_0620b64d3955.ogg", "profile": "sentence" },
            "M": { "file": "word_1a2b3c4d5e6f.ogg", "profile": "word" }
          }
        }
    """.trimIndent()

    @Test
    fun `findet Clip per exaktem Text`() {
        val index = ClipIndex.parse(sample)
        assertEquals(
            ClipEntry(file = "sentence_0620b64d3955.ogg", profile = "sentence"),
            index.lookup("Mama mag Mais."),
        )
    }

    @Test
    fun `trimmt den gesuchten Text`() {
        val index = ClipIndex.parse(sample)
        assertEquals("word_1a2b3c4d5e6f.ogg", index.lookup(" M ")?.file)
    }

    @Test
    fun `unbekannter Text liefert null`() {
        assertNull(ClipIndex.parse(sample).lookup("Papa"))
    }

    @Test
    fun `unbekannte JSON-Felder stoeren nicht`() {
        val withExtra = sample.replace("\"version\": 1", "\"version\": 1, \"neu\": true")
        assertEquals(2, ClipIndex.parse(withExtra).size)
    }

    @Test
    fun `unbekanntes Feld in einem Clip-Eintrag stoert nicht`() {
        // Der Exporter schreibt seit dem Fingerprint-Vergleich pro Clip ein
        // "fingerprint"-Feld in index.json — das muss ClipEntry ignorieren.
        val withFingerprint = sample.replace(
            "\"file\": \"sentence_0620b64d3955.ogg\", \"profile\": \"sentence\"",
            "\"file\": \"sentence_0620b64d3955.ogg\", \"profile\": \"sentence\", " +
                "\"fingerprint\": \"abc\"",
        )
        val index = ClipIndex.parse(withFingerprint)
        assertEquals(2, index.size)
        assertEquals(
            ClipEntry(file = "sentence_0620b64d3955.ogg", profile = "sentence"),
            index.lookup("Mama mag Mais."),
        )
    }

    @Test
    fun `fehlender oder kaputter Index ergibt leeren Index`() {
        val missing = ClipIndex.load { throw FileNotFoundException(it) }
        assertNull(missing.lookup("Mama mag Mais."))
        val broken = ClipIndex.load { "kein json".byteInputStream() }
        assertNull(broken.lookup("Mama mag Mais."))
    }
}
