package app.abcvorschule.ui.theme

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die gebündelte Emoji-Schrift ([SilboEmoji]) ist auf die Emojis des Packs gekürzt.
 * Kommt ein neues Emoji in den Content, ohne dass `tools/fonts/build_fonts.py` neu
 * lief, zeigt das Gerät es aus seiner eigenen Schrift — oder, ist es jünger als das
 * Gerät, gar nicht. Dieser Test fällt dann, statt dass es auf einem Kinder-Tablet
 * auffällt.
 */
class EmojiFontCoverageTest {
    @Test
    fun everyEmojiOfThePackIsInTheBundledEmojiFont() {
        val bundled = File("../tools/fonts/emoji-glyphs.txt").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.trim().toInt(16) }
            .toSet()
        val used = File("src/main/assets/content").listFiles().orEmpty()
            .filter { it.extension == "json" }
            .flatMap { file -> file.readText().codePoints().toArray().filter(::isPictureCodePoint) }
            .toSet()
        val missing = (used - bundled).sorted()
        assertTrue(
            "Emojis im Pack, aber nicht in silbo_emoji.ttf — tools/fonts/build_fonts.py neu laufen lassen: " +
                missing.joinToString(" ") { "U+%X %s".format(it, String(Character.toChars(it))) },
            missing.isEmpty(),
        )
    }

    /** Dieselben Bereiche wie `is_emoji_char` im Build-Skript, ohne Verbinder und Hauttöne. */
    private fun isPictureCodePoint(c: Int): Boolean {
        if (c == 0x200D || c == 0xFE0F || c == 0x20E3 || c in 0x1F3FB..0x1F3FF) return false
        return c in 0x1F000..0x1FAFF || c in 0x2600..0x27BF || c in 0x2300..0x23FF || c in 0x2B00..0x2BFF
    }
}
