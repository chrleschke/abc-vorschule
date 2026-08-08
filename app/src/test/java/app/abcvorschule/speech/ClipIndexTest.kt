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
    fun `eindeutige Gross-kleinschreibung findet Phonem-Clip`() {
        val index = ClipIndex.parse(
            """
            {
              "version": 1,
              "clips": {
                "Sch": { "file": "phoneme_07af03fb0629.ogg", "profile": "phoneme" }
              }
            }
            """.trimIndent(),
        )
        assertEquals("phoneme_07af03fb0629.ogg", index.lookup("sch")?.file)
        assertEquals("phoneme_07af03fb0629.ogg", index.lookup("Sch")?.file)
    }

    @Test
    fun `mehrdeutige Gross-kleinschreibung bleibt exakt`() {
        val index = ClipIndex.parse(
            """
            {
              "version": 1,
              "clips": {
                "H": { "file": "phoneme_h.ogg", "profile": "phoneme" },
                "h": { "file": "phoneme_h_lower.ogg", "profile": "phoneme" }
              }
            }
            """.trimIndent(),
        )
        assertEquals("phoneme_h.ogg", index.lookup("H")?.file)
        assertEquals("phoneme_h_lower.ogg", index.lookup("h")?.file)
    }

    @Test
    fun `mehrdeutige Paare ohne exakten Treffer liefern null`() {
        val index = ClipIndex.parse(
            """
            {
              "version": 1,
              "clips": {
                "Sp": { "file": "phoneme_sp_upper.ogg", "profile": "phoneme" },
                "sp": { "file": "phoneme_sp_lower.ogg", "profile": "phoneme" }
              }
            }
            """.trimIndent(),
        )
        assertNull(index.lookup("SP"))
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
            "^(article_word|word|phoneme|prompt|miss|reward|sentence|finale|ui)_[0-9a-f]{12}\\.ogg$",
        )
        index.entries().forEach { entry ->
            assertTrue(
                "Dateiname ${entry.file} entspricht nicht dem Exporter-Schema",
                fileNamePattern.matches(entry.file),
            )
        }
        // article_word steht vor word in der Alternation: "das Haus" erzeugt
        // article_word_e537e289a34f.ogg, dessen Präfix "article_" sonst am
        // ^word-Anker vorbeiläuft. Pinnt den konkreten Fall, nicht nur "im Prinzip".
        assertTrue(fileNamePattern.matches("article_word_e537e289a34f.ogg"))

        // Pinnt die Exporter→App-Naht an einen echten, aktuell gelockten Clip.
        // Falls dieser Text jemals entlockt wird, verschwindet der Eintrag aus
        // dem committeten index.json — dann muss dieser Test aktualisiert werden.
        // Pinnt die Exporter→App-Naht an einen echten, aktuell gelockten Clip.
        assertEquals(
            "prompt_0845c380bb11.ogg",
            index.lookup(
                "Wo hörst du den Buchstaben - I? Am Anfang, in der Mitte oder am Ende.",
            )?.file,
        )
        // Verschmelzer: display "ro" maps to phoneme lemma clip "roh"; "na" is keyed directly.
        assertEquals("phoneme_57fbb5e0d270.ogg", index.lookup("roh")?.file)
        assertNull("display grapheme ro is not indexed — speech must use lemma roh", index.lookup("ro"))
        assertEquals("phoneme_3d9fc4bde7ce.ogg", index.lookup("na")?.file)
    }
}
