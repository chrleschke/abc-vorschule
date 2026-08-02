package app.abcvorschule.speech

import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `laedt den echten committeten Asset-Index`() {
        // src/main/assets ist als JVM-Test-Resource eingebunden (build.gradle.kts) —
        // dieser Test sieht also genau die Dateien, die auch im APK landen.
        val index = ClipIndex.load { path ->
            javaClass.classLoader!!.getResourceAsStream(path)
                ?: throw FileNotFoundException(path)
        }
        assertTrue("committed index.json darf nicht leer sein", index.size > 0)

        val fileNamePattern = Regex(
            "^(word|phoneme|prompt|miss|reward|sentence|finale|ui)_[0-9a-f]{12}\\.ogg$",
        )
        index.entries().forEach { entry ->
            assertTrue(
                "Dateiname ${entry.file} entspricht nicht dem Exporter-Schema",
                fileNamePattern.matches(entry.file),
            )
        }

        // Pinnt die Exporter→App-Naht an einen echten, aktuell gelockten Clip.
        // Falls dieser Text jemals entlockt wird, verschwindet der Eintrag aus
        // dem committeten index.json — dann muss dieser Test aktualisiert werden.
        assertEquals(
            "prompt_206bc14a3673.ogg",
            index.lookup("Baue das Wort Eis. Suche die passenden Buchstaben und " +
                "setze sie in die richtige Reihenfolge.")?.file,
        )
    }
}
